package com.yupi.template.service;

import com.yupi.template.agent.config.AgentConfig;
import com.yupi.template.exception.*;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.entity.*;
import com.yupi.template.repository.ReviewTraceStore;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ArticleInterventionService {
    private final JdbcTemplate jdbc;
    private final ArticleService articles;
    private final ReviewTraceStore reviews;
    private final AgentConfig config;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.yupi.template.runtime.RuntimeStore runtime;
    public record Receipt(String requestId, String status, boolean replayed) {}

    @Transactional(readOnly = true)
    public InterventionView view(String taskId, User user) {
        articles.getArticleDetail(taskId,user);
        Article a=articles.getByTaskId(taskId);
        if (!config.isInterventionEnabled()) return new InterventionView(false,taskId,a.getStatus(),a.getPhase(),0,null,null,List.of(),a.getErrorMessage());
        var trace=reviews.find(taskId);
        return new InterventionView(true,taskId,a.getStatus(),a.getPhase(),revision(taskId),trace,media(taskId),allowed(a,trace),a.getErrorMessage());
    }
    private List<String> allowed(Article a, ReviewTrace trace) {
        if(trace==null) return List.of();
        if("NEEDS_REVIEW".equals(a.getStatus()) && "NEEDS_REVIEW".equals(trace.status())) return List.of("ACCEPT","EDIT_REVIEW");
        if(List.of("IMAGES_FAILED","COMPLETED").contains(a.getStatus())) return List.of("RETRY_IMAGE");
        if("FAILED".equals(a.getStatus()) && List.of("PASS","HUMAN_ACCEPTED").contains(trace.status())) return List.of("CONTINUE_IMAGES");
        return List.of();
    }
    @Transactional
    public Receipt submit(String taskId, InterventionRequest request, User user) {
        if(!config.isInterventionEnabled()) throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,"人工介入功能未启用");
        validate(request);
        lock(taskId);
        // Authorize/read only after the lock: MySQL repeatable-read must see the last operation.
        articles.getArticleDetail(taskId,user);
        var a=articles.getByTaskId(taskId);
        var hash=hash(request);
        var old=jdbc.queryForList("SELECT payloadHash,status FROM article_operation WHERE taskId=? AND requestId=?",taskId,request.requestId());
        if(!old.isEmpty()) {
            conflict(!hash.equals(old.getFirst().get("payloadHash")),"同一请求编号不能用于不同操作");
            return new Receipt(request.requestId(),old.getFirst().get("status").toString(),true);
        }
        var trace=reviews.find(taskId);
        conflict(trace==null || !allowed(a,trace).contains(request.action()),"当前状态不允许该操作");
        conflict(trace.currentVersion()!=request.expectedVersion() || revision(taskId)!=request.expectedRevision(),"版本已变化，请刷新后重试");
        conflict(!jdbc.queryForList("SELECT requestId FROM article_operation WHERE taskId=? AND status IN ('QUEUED','RUNNING')",taskId).isEmpty(),"已有操作正在执行");
        if("ACCEPT".equals(request.action())) {
            conflict(!request.acknowledgeRisks(),"请确认理解未核查事实的风险");
            var decisions=new ArrayList<>(trace.humanDecisions());
            decisions.add(new ReviewTrace.HumanDecision(trace.currentVersion(),user.getId(),Instant.now().toString(),Objects.toString(request.note(),"")));
            trace=new ReviewTrace(1,"HUMAN_ACCEPTED",trace.currentVersion(),2,trace.stopReason(),trace.versions(),trace.review(),trace.round(),trace.roundStartVersion(),decisions);
        } else if("EDIT_REVIEW".equals(request.action())) {
            if(request.content()==null || request.content().isBlank() || request.content().length()>64000) throw new BusinessException(ErrorCode.PARAMS_ERROR,"正文长度须为1至64000字符");
            var current=trace.versions().getLast().content();
            conflict(current.replaceAll("\\s","").equals(request.content().replaceAll("\\s","")),"正文没有实质修改，不能开启新一轮");
            int version=trace.currentVersion()+1;
            var versions=new ArrayList<>(trace.versions());versions.add(new ReviewTrace.DraftVersion(version,request.content(),null));
            trace=new ReviewTrace(1,"REVIEWING",version,2,null,versions,null,trace.round()+1,version,trace.humanDecisions());
            jdbc.update("UPDATE article SET content=?,fullContent=?,images=NULL,coverImage=NULL,completedTime=NULL WHERE taskId=?",request.content(),request.content(),taskId);
            jdbc.update("UPDATE article_intervention SET mediaJson=NULL WHERE taskId=?",taskId);
        } else if("RETRY_IMAGE".equals(request.action())) {
            var media=media(taskId);
            conflict(media==null || media.slots().stream().noneMatch(s->s.id().equals(request.imageId())),"目标配图不存在或没有可重试记录");
        }
        reviews.save(taskId,trace);
        jdbc.update("INSERT INTO article_intervention(taskId,revision) VALUES (?,1) ON DUPLICATE KEY UPDATE revision=revision+1",taskId);
        jdbc.update("INSERT INTO article_operation(taskId,requestId,payloadHash,action,requestJson,status) VALUES (?,?,?,?,?,'QUEUED')",taskId,request.requestId(),hash,request.action(),GsonUtils.toJson(request));
        jdbc.update("UPDATE article SET status='PROCESSING',phase=?,errorMessage=NULL WHERE taskId=?", "EDIT_REVIEW".equals(request.action())?"REVIEWING":"IMAGE_GENERATING",taskId);
        if (runtime != null && runtime.enabled()) runtime.enroll(taskId,request.requestId(),"EDIT_REVIEW".equals(request.action())?"REVIEW":"MEDIA");
        return new Receipt(request.requestId(),"QUEUED",false);
    }
    private void validate(InterventionRequest r) {
        if(r==null || r.requestId()==null || !r.requestId().matches("[A-Za-z0-9_-]{16,64}") || r.expectedVersion()==null || r.expectedRevision()==null
                || r.action()==null || !List.of("ACCEPT","EDIT_REVIEW","RETRY_IMAGE","CONTINUE_IMAGES").contains(r.action())
                || (r.note()!=null && r.note().length()>500)) throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求编号、版本或操作参数无效");
    }
    private static String hash(InterventionRequest r) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(GsonUtils.toJson(r).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static void conflict(boolean condition,String message) { if(condition) throw new BusinessException(ErrorCode.CONFLICT_ERROR,message); }
    public void lock(String taskId) {
        if(jdbc.queryForList("SELECT id FROM article WHERE taskId=? AND isDelete=0 FOR UPDATE",taskId).isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }
    public long revision(String taskId) {
        var rows=jdbc.queryForList("SELECT revision FROM article_intervention WHERE taskId=?",Long.class,taskId);return rows.isEmpty()?0:rows.getFirst();
    }
    public MediaState media(String taskId) {
        var rows=jdbc.query("SELECT mediaJson FROM article_intervention WHERE taskId=?",(rs,n)->GsonUtils.fromJson(rs.getString(1),MediaState.class),taskId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public InterventionRequest claim(String taskId,String requestId) {
        if(jdbc.update("UPDATE article_operation SET status='RUNNING' WHERE taskId=? AND requestId=? AND status='QUEUED'",taskId,requestId)!=1)return null;
        return GsonUtils.fromJson(jdbc.queryForObject("SELECT requestJson FROM article_operation WHERE taskId=? AND requestId=?",String.class,taskId,requestId),InterventionRequest.class);
    }
    public void finish(String taskId,String requestId) { jdbc.update("UPDATE article_operation SET status='COMPLETED' WHERE taskId=? AND requestId=? AND status='RUNNING'",taskId,requestId); }
    @Transactional
    public void fail(String taskId,String requestId) {
        lock(taskId);
        jdbc.update("UPDATE article_operation SET status='FAILED',errorMessage='操作未完成，请检查已保存状态' WHERE taskId=? AND requestId=?",taskId,requestId);
        jdbc.update("UPDATE article SET status='FAILED',errorMessage='操作未完成，正文和评审已保留' WHERE taskId=?",taskId);
    }
    public ArticleState state(String taskId) {
        var a=articles.getByTaskId(taskId);var s=new ArticleState();s.setTaskId(taskId);s.setTopic(a.getTopic());s.setStyle(a.getStyle());s.setUserDescription(a.getUserDescription());
        var t=new ArticleState.TitleResult();t.setMainTitle(a.getMainTitle());t.setSubTitle(a.getSubTitle());s.setTitle(t);
        var o=new ArticleState.OutlineResult();o.setSections(Arrays.asList(GsonUtils.fromJson(a.getOutline(),ArticleState.OutlineSection[].class)));s.setOutline(o);
        s.setReviewTrace(reviews.find(taskId));s.setContent(s.getReviewTrace().versions().getLast().content());
        if(a.getEnabledImageMethods()!=null)s.setEnabledImageMethods(Arrays.asList(GsonUtils.fromJson(a.getEnabledImageMethods(),String[].class)));
        return s;
    }
}
