package com.yupi.template.runtime;
import com.yupi.template.exception.*;
import com.yupi.template.service.*;
import com.yupi.template.model.entity.User;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class RuntimeControl {
 private final RuntimeStore store;private final JdbcTemplate jdbc;private final ArticleService articles;
 public record Command(String requestId,String action,Long expectedStateVersion,boolean acknowledgePossibleCharge) {}
 @Transactional(readOnly=true) public Map<String,Object> view(String id,User user){
  articles.getArticleDetail(id,user);
  var r=store.runtime(id);if(r==null)return Map.of("enabled",false);
  var result=new LinkedHashMap<String,Object>(r);var a=articles.getByTaskId(id);
  result.put("enabled",true);result.put("status",a.getStatus());result.put("phase",a.getPhase());result.put("errorMessage",a.getErrorMessage());
  result.put("calls",jdbc.queryForList("SELECT callId,stepId,provider,payloadHash,status,attempts,queryAttempts,providerJobId,reservedCostMicros,actualCostMicros,deadlineAt FROM article_call WHERE taskId=? ORDER BY createdTime",id));
  var diagnostics=new ArrayList<Map<String,Object>>();for(var call:jdbc.queryForList("SELECT callId,resultJson FROM article_call WHERE taskId=? AND provider IN ('image:NANO_BANANA','image:DOUBAO')",id)){
   if(call.get("resultJson")==null)continue;try{var json=com.google.gson.JsonParser.parseString(call.get("resultJson").toString()).getAsJsonObject();var safe=new LinkedHashMap<String,Object>();safe.put("callId",call.get("callId"));if(json.has("errorCategory"))safe.put("errorCategory",json.get("errorCategory").getAsString());if(json.has("requestId"))safe.put("requestId",json.get("requestId").getAsString());if(json.has("metadata"))safe.put("metadata",GsonUtils.fromJson(json.get("metadata").toString(),com.yupi.template.service.image.ImageMetadata.class));diagnostics.add(safe);}catch(RuntimeException ignored){}
  }result.put("imageDiagnostics",diagnostics);
  result.put("operations",jdbc.queryForList("SELECT requestId,action,status,phase,fence,stateVersion FROM article_operation WHERE taskId=? AND runId IS NOT NULL ORDER BY createdTime",id));
  result.put("mayStillCharge",Set.of("CANCELLED","TIMED_OUT","EXTERNAL_UNCERTAIN").contains(a.getStatus()));return result;
 }
 @Transactional public Map<String,Object> submit(String id,Command c,User user){
  if(store.runtime(id)==null)throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,"任务没有持久执行状态");
  if(c==null||c.requestId()==null||!c.requestId().matches("[A-Za-z0-9_-]{16,64}")||c.expectedStateVersion()==null||!Set.of("CANCEL","RECHECK_EXTERNAL","RETRY_UNCERTAIN").contains(Objects.toString(c.action(),"")))throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求编号、状态版本或操作无效");
  store.lock(id);articles.getArticleDetail(id,user);var a=articles.getByTaskId(id);
  if(!Objects.equals(a.getUserId(),user.getId()))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
  var r=store.runtime(id);if(r==null)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"文章未接入 Runtime");
  String hash=RuntimeExternal.hash(GsonUtils.toJson(c));var old=jdbc.queryForList("SELECT payloadHash FROM article_operation WHERE taskId=? AND requestId=?",id,c.requestId());
  if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("payloadHash")))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"重复请求内容不同");return Map.of("replayed",true);}
  if(RuntimeStore.number(r,"stateVersion")!=c.expectedStateVersion())throw new BusinessException(ErrorCode.CONFLICT_ERROR,"状态版本变化，请刷新");
  if("CANCEL".equals(c.action())){
   if(!Set.of("PROCESSING","RECOVERING","NEEDS_REVIEW","IMAGES_FAILED","EXTERNAL_UNCERTAIN").contains(a.getStatus()))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"当前状态不可取消");
   jdbc.update("UPDATE article_operation SET status='CANCELLED',fence=fence+1,leaseUntil=NULL,stateVersion=stateVersion+1 WHERE taskId=? AND status IN ('QUEUED','RUNNING','EXTERNAL_UNCERTAIN')",id);
   jdbc.update("UPDATE article SET status='CANCELLED',errorMessage='任务已取消，草稿保留；已发出的供应商请求可能继续计费' WHERE taskId=?",id);
  }else{
   if(!"EXTERNAL_UNCERTAIN".equals(a.getStatus()))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"仅不确定结果可申请查询");
   if("RETRY_UNCERTAIN".equals(c.action())){
    if(!c.acknowledgePossibleCharge())throw new BusinessException(ErrorCode.CONFLICT_ERROR,"必须明确接受可能重复收费的风险");
    jdbc.update("UPDATE article_call c JOIN article_operation o ON c.taskId=o.taskId AND c.requestId=o.requestId SET c.status='AUTHORIZED_RETRY' WHERE c.taskId=? AND o.status='EXTERNAL_UNCERTAIN' AND c.status IN ('IN_FLIGHT','UNCERTAIN')",id);
   }
   // Re-query alone never authorizes another paid submission; explicit retry is recorded below.
   jdbc.update("UPDATE article_operation SET status='QUEUED',fence=fence+1,leaseUntil=NULL WHERE taskId=? AND status='EXTERNAL_UNCERTAIN'",id);
   jdbc.update("UPDATE article SET status='RECOVERING',errorMessage=NULL WHERE taskId=?",id);
  }
  jdbc.update("INSERT INTO article_operation(taskId,requestId,payloadHash,action,requestJson,status) VALUES (?,?,?,?,?,'COMPLETED')",id,c.requestId(),hash,c.action(),GsonUtils.toJson(c));
  store.event(id,"CANCEL".equals(c.action())?"CANCELLED":"RUNTIME_QUEUED",Map.of("requestId",c.requestId()));return Map.of("replayed",false);
 }
}
