package com.yupi.template.runtime;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.ArticleService;
import com.yupi.template.exception.*;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class RuntimeIntake {
 private final RuntimeStore store;private final ArticleService articles;private final JdbcTemplate jdbc;
 public boolean enabled(){return store.enabled();}
 @Transactional public String create(ArticleCreateRequest request,User user){return createMode(request,user,false);}
 @Transactional public String quick(ArticleCreateRequest request,User user){return createMode(request,user,true);}
 private String createMode(ArticleCreateRequest request,User user,boolean quick){
  jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE",user.getId());
  String key=request.getRequestId();if(key==null)key=UUID.randomUUID().toString();
  if(!key.matches("[A-Za-z0-9_-]{16,64}"))throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求编号无效");
  String json=GsonUtils.toJson(request),hash=RuntimeExternal.hash((quick?"QUICK:":"")+json);
  var rows=jdbc.queryForList("SELECT taskId,createPayloadHash FROM article_runtime WHERE userId=? AND createRequestId=?",user.getId(),key);
  if(!rows.isEmpty()){if(!hash.equals(rows.getFirst().get("createPayloadHash")))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"同一请求编号内容已改变");return rows.getFirst().get("taskId").toString();}
  String id=articles.createArticleTaskWithQuotaCheck(request.getTopic(),request.getStyle(),request.getEnabledImageMethods(),user);
  store.enqueue(id,quick?"quick-create-0001":"normal-title-0001",quick?"QUICK_CREATE":"CREATE",quick?"Q_TITLE":"TITLE",json);
  jdbc.update("UPDATE article_runtime SET createRequestId=?,createPayloadHash=? WHERE taskId=?",key,hash,id);return id;
 }
 @Transactional public void title(ArticleConfirmTitleRequest r,User user){
  store.lock(r.getTaskId());articles.getArticleDetail(r.getTaskId(),user);
  if(!"PROCESSING".equals(articles.getByTaskId(r.getTaskId()).getStatus()))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"任务已暂停或结束");
  if(replay(r.getTaskId(),"normal-outline-0001",r))return;
  articles.confirmTitle(r.getTaskId(),r.getSelectedMainTitle(),r.getSelectedSubTitle(),r.getUserDescription(),user);
  store.enqueue(r.getTaskId(),"normal-outline-0001","CONFIRM_TITLE","OUTLINE",GsonUtils.toJson(r));
 }
 @Transactional public void outline(ArticleConfirmOutlineRequest r,User user){
  store.lock(r.getTaskId());articles.getArticleDetail(r.getTaskId(),user);
  if(replay(r.getTaskId(),"normal-body-0001",r))return;
  articles.confirmOutline(r.getTaskId(),r.getOutline(),user);
  store.enqueue(r.getTaskId(),"normal-body-0001","CONFIRM_OUTLINE","BODY",GsonUtils.toJson(r));
 }
 private boolean replay(String id,String requestId,Object input){var rows=jdbc.queryForList("SELECT payloadHash FROM article_operation WHERE taskId=? AND requestId=?",id,requestId);if(rows.isEmpty())return false;if(!RuntimeExternal.hash(GsonUtils.toJson(input)).equals(rows.getFirst().get("payloadHash")))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"该确认已提交，请读取当前状态");return true;}
}
