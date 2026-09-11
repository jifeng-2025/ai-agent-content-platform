package com.yupi.template.runtime;

import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.yupi.template.runtime.RuntimeScope.Ticket;

@Service @RequiredArgsConstructor
public class RuntimeStore {
 private final JdbcTemplate jdbc;
 private final RuntimeConfig config;
 public record Work(Ticket ticket,String action,String phase,String requestJson) {}
 public boolean enabled(){return config.isEnabled();}
 public void lock(String taskId){if(jdbc.queryForList("SELECT id FROM article WHERE taskId=? AND isDelete=0 FOR UPDATE",taskId).isEmpty())throw new RuntimeStop("NOT_FOUND");}
 public Map<String,Object> runtime(String id){var r=jdbc.queryForList("SELECT * FROM article_runtime WHERE taskId=?",id);return r.isEmpty()?null:r.getFirst();}
 public static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 public void ensure(String taskId){
  jdbc.update("INSERT IGNORE INTO article_runtime(taskId,runId,userId,remainingMs,maxCalls,maxImageRetries,maxEstimatedCostMicros) SELECT taskId,?,userId,?,?,?,? FROM article WHERE taskId=? AND isDelete=0",UUID.randomUUID().toString(),config.getActiveBudgetMs(),config.getMaxCalls(),config.getMaxImageRetries(),config.getMaxEstimatedCostMicros(),taskId);
 }
 /** Caller transaction also contains the article/user decision. */
 public void enroll(String taskId,String requestId,String phase){
  lock(taskId);ensure(taskId);
  jdbc.update("UPDATE article_operation SET runId=(SELECT runId FROM article_runtime WHERE taskId=?),phase=?,checkpointJson=? WHERE taskId=? AND requestId=? AND runId IS NULL",taskId,phase,GsonUtils.toJson(Map.of("schemaVersion",1,"next",phase)),taskId,requestId);
  event(taskId,"RUNTIME_QUEUED",Map.of("requestId",requestId,"phase",phase));
 }
 @Transactional public void enqueue(String taskId,String id,String action,String phase,String json){
  lock(taskId);ensure(taskId);
  if(!jdbc.queryForList("SELECT requestId FROM article_operation WHERE taskId=? AND status IN ('QUEUED','RUNNING')",taskId).isEmpty())throw new RuntimeStop("CONFLICT");
  jdbc.update("INSERT INTO article_operation(taskId,requestId,payloadHash,action,requestJson,status) VALUES (?,?,?,?,?,'QUEUED')",taskId,id,RuntimeExternal.hash(json),action,json);
  enroll(taskId,id,phase);
  jdbc.update("UPDATE article SET status='PROCESSING',phase=?,errorMessage=NULL WHERE taskId=?",phase,taskId);
 }
 public List<Map<String,Object>> candidates(){return jdbc.queryForList("SELECT taskId,requestId,action FROM article_operation WHERE runId IS NOT NULL AND (status='QUEUED' OR (status='RUNNING' AND leaseUntil<CURRENT_TIMESTAMP(3))) AND (? OR action IN ('QUICK_CREATE','QUICK_IMAGE_RETRY','QUICK_ADVICE_RETRY')) ORDER BY createdTime LIMIT 20",config.isEnabled());}
 @Transactional public Work claim(String taskId,String requestId,String owner){
  lock(taskId);
  var rows=jdbc.queryForList("SELECT *, (leaseUntil<CURRENT_TIMESTAMP(3)) expired FROM article_operation WHERE taskId=? AND requestId=? FOR UPDATE",taskId,requestId);
  if(rows.isEmpty())return null;var row=rows.getFirst();boolean recovering="RUNNING".equals(row.get("status"));
  if(!"QUEUED".equals(row.get("status")) && !(recovering && (Boolean.TRUE.equals(row.get("expired")) || (row.get("expired") instanceof Number n && n.intValue()==1))))return null;
  if(recovering)chargeTime(taskId,requestId);
  jdbc.update("UPDATE article_operation SET status='RUNNING',owner=?,fence=fence+1,heartbeatAt=CURRENT_TIMESTAMP(3),leaseUntil=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)),stateVersion=stateVersion+1 WHERE taskId=? AND requestId=?",owner,config.getLeaseMs()*1000,taskId,requestId);
  long fence=number(row,"fence")+1;
  jdbc.update("UPDATE article SET status=?,errorMessage=NULL WHERE taskId=?",recovering?"RECOVERING":"PROCESSING",taskId);
  event(taskId,recovering?"RECOVERING":"RUNTIME_CLAIMED",Map.of("fence",fence,"requestId",requestId));
  return new Work(new Ticket(taskId,requestId,row.get("runId").toString(),fence),row.get("action").toString(),row.get("phase").toString(),row.get("requestJson").toString());
 }
 @Transactional public void guard(Ticket t){
  lock(t.taskId());
  var n=jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId=? AND requestId=? AND fence=? AND status='RUNNING' AND leaseUntil>CURRENT_TIMESTAMP(3)",Integer.class,t.taskId(),t.requestId(),t.fence());
  if(n==null||n!=1)throw new RuntimeStop("FENCED");
  var remaining=jdbc.queryForObject("SELECT r.remainingMs-GREATEST(0,TIMESTAMPDIFF(MICROSECOND,o.heartbeatAt,LEAST(CURRENT_TIMESTAMP(3),o.leaseUntil)) DIV 1000) FROM article_runtime r JOIN article_operation o ON r.taskId=o.taskId WHERE o.taskId=? AND o.requestId=?",Long.class,t.taskId(),t.requestId());
  if(remaining==null||remaining<=0)throw new RuntimeStop("TIMED_OUT");
 }
 private void chargeTime(String taskId,String requestId){
  // Charge only active lease time. Human waiting and process downtime beyond the lease are excluded.
  jdbc.update("UPDATE article_runtime r JOIN article_operation o ON r.taskId=o.taskId SET r.remainingMs=GREATEST(0,r.remainingMs-GREATEST(0,TIMESTAMPDIFF(MICROSECOND,o.heartbeatAt,LEAST(CURRENT_TIMESTAMP(3),o.leaseUntil)) DIV 1000)),o.heartbeatAt=CURRENT_TIMESTAMP(3) WHERE o.taskId=? AND o.requestId=?",taskId,requestId);
 }
 @Transactional public boolean heartbeat(Ticket t){
  try{guard(t);}catch(RuntimeStop e){return false;}
  chargeTime(t.taskId(),t.requestId());
  jdbc.update("UPDATE article_operation SET leaseUntil=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)) WHERE taskId=? AND requestId=? AND fence=?",config.getLeaseMs()*1000,t.taskId(),t.requestId(),t.fence());return true;
 }
 /** Must execute in the same short transaction as the stage result. */
 public void checkpoint(Ticket t,String next,String type){
  guard(t);chargeTime(t.taskId(),t.requestId());
  jdbc.update("UPDATE article_operation SET phase=?,checkpointJson=?,stateVersion=stateVersion+1 WHERE taskId=? AND requestId=?",next,GsonUtils.toJson(Map.of("schemaVersion",1,"next",next)),t.taskId(),t.requestId());
  event(t.taskId(),type,Map.of("requestId",t.requestId(),"phase",next));
 }
 @Transactional public void saveStage(Ticket t,ArticleState s,String next,String articlePhase,String type){
  guard(t);
  switch(next){
   case "TITLE_WAIT" -> jdbc.update("UPDATE article SET titleOptions=?,phase=?,status='PROCESSING' WHERE taskId=?",GsonUtils.toJson(s.getTitleOptions()),articlePhase,t.taskId());
   case "OUTLINE_WAIT" -> jdbc.update("UPDATE article SET outline=?,phase=?,status='PROCESSING' WHERE taskId=?",GsonUtils.toJson(s.getOutline().getSections()),articlePhase,t.taskId());
   case "REVIEW" -> jdbc.update("UPDATE article SET content=?,fullContent=?,phase='REVIEWING',status='PROCESSING' WHERE taskId=?",s.getContent(),s.getContent(),t.taskId());
   default -> throw new IllegalArgumentException(next);
  }
  checkpoint(t,next,type);
 }
 @Transactional public void finish(Ticket t){
  guard(t);chargeTime(t.taskId(),t.requestId());
  jdbc.update("UPDATE article_operation SET status='COMPLETED',leaseUntil=NULL,stateVersion=stateVersion+1 WHERE taskId=? AND requestId=? AND fence=?",t.taskId(),t.requestId(),t.fence());
  var article=jdbc.queryForMap("SELECT status,phase FROM article WHERE taskId=?",t.taskId());
  String status=article.get("status").toString();String type="COMPLETED".equals(status)?"ALL_COMPLETE":Set.of("NEEDS_REVIEW","IMAGES_FAILED").contains(status)?status:"RUNTIME_WAITING";
  event(t.taskId(),type,Map.of("articleStatus",status,"phase",article.get("phase")));
 }
 @Transactional public void stop(Ticket t,String status){
  lock(t.taskId());
  if(jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId=? AND requestId=? AND status='RUNNING' AND fence=?",Integer.class,t.taskId(),t.requestId(),t.fence())!=1)return;
  chargeTime(t.taskId(),t.requestId());
  if(jdbc.update("UPDATE article_operation SET status=?,leaseUntil=NULL,fence=fence+1,stateVersion=stateVersion+1 WHERE taskId=? AND requestId=? AND status='RUNNING' AND fence=?",status,t.taskId(),t.requestId(),t.fence())!=1)return;
  jdbc.update("UPDATE article SET status=?,errorMessage=? WHERE taskId=?",status,status,t.taskId());event(t.taskId(),status,Map.of("requestId",t.requestId()));
 }
 public void event(String taskId,String type,Map<String,Object> body){
  // Article lock serializes allocation and INSERT; consumers only see committed state/events.
  jdbc.update("UPDATE article_runtime SET stateVersion=stateVersion+1,lastEventId=lastEventId+1 WHERE taskId=?",taskId);
  var r=runtime(taskId);if(r==null)return;
  var payload=new LinkedHashMap<>(body);payload.put("type",type);payload.put("taskId",taskId);payload.put("runId",r.get("runId"));payload.put("seq",r.get("lastEventId"));payload.put("stateVersion",r.get("stateVersion"));
  if("TITLES_GENERATED".equals(type))payload.put("titleOptions",GsonUtils.fromJson(jdbc.queryForObject("SELECT titleOptions FROM article WHERE taskId=?",String.class,taskId),Object.class));
  if("OUTLINE_GENERATED".equals(type))payload.put("outline",GsonUtils.fromJson(jdbc.queryForObject("SELECT outline FROM article WHERE taskId=?",String.class,taskId),Object.class));
  jdbc.update("INSERT INTO article_event(taskId,seq,runId,type,payloadJson) VALUES (?,?,?,?,?)",taskId,r.get("lastEventId"),r.get("runId"),type,GsonUtils.toJson(payload));
  jdbc.update("DELETE FROM article_event WHERE taskId=? AND seq<=?",taskId,number(r,"lastEventId")-config.getEventRetentionCount());
 }
}
