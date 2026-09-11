package com.yupi.template.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.yupi.template.runtime.RuntimeStore.number;

/** Short transactions only. No provider I/O may run inside this service. */
@Service @RequiredArgsConstructor
public class RuntimeLedger {
 private final JdbcTemplate jdbc;private final RuntimeStore store;private final RuntimeConfig config;
 public record Call(String id,boolean fresh,String status,String result,String jobId,long deadlineMs) {}
 @Transactional public Call prepare(RuntimeScope.Ticket t,String step,String provider,String payload){
  store.guard(t);
  String id=RuntimeExternal.hash(t.runId()+"\n"+t.requestId()+"\n"+step+"\n"+payload);
  var rows=jdbc.queryForList("SELECT *,UNIX_TIMESTAMP(deadlineAt)*1000 deadlineMs FROM article_call WHERE callId=?",id);
  if(!rows.isEmpty()&&!"AUTHORIZED_RETRY".equals(rows.getFirst().get("status"))){var r=rows.getFirst();return new Call(id,false,r.get("status").toString(),(String)r.get("resultJson"),(String)r.get("providerJobId"),number(r,"deadlineMs"));}
  var r=store.runtime(t.taskId());boolean image=step.startsWith("IMAGE:");long reserve="image:DEMO".equals(provider)?0:(image?config.getImageReserveMicros():config.getTextReserveMicros());
  String action=jdbc.queryForObject("SELECT action FROM article_operation WHERE taskId=? AND requestId=?",String.class,t.taskId(),t.requestId());boolean authorizedImageRetry=image&&!rows.isEmpty()&&"AUTHORIZED_RETRY".equals(rows.getFirst().get("status"));
  // Count explicitly authorized retries against the image cap as well as call/cost caps. This does not authorize submission.
  boolean retry=image&&java.util.Set.of("RETRY_IMAGE","QUICK_IMAGE_RETRY").contains(action)||authorizedImageRetry;
  if(number(r,"callsUsed")>=number(r,"maxCalls") || reserve>number(r,"maxEstimatedCostMicros")-number(r,"reservedCostMicros") || (retry&&number(r,"imageRetriesUsed")>=number(r,"maxImageRetries")))throw new RuntimeStop("BUDGET_EXHAUSTED");
  long stepLimit=config.getStepTimeoutMs();
  if(java.util.Set.of("QUICK_CREATE","QUICK_IMAGE_RETRY","QUICK_ADVICE_RETRY").contains(action)){try{var json=com.google.gson.JsonParser.parseString(payload).getAsJsonObject();var profile=image?json.getAsJsonObject("profile"):json.getAsJsonObject("configuration");if(profile!=null&&profile.has("timeoutSeconds"))stepLimit=Math.min(180000,Math.max(5000,profile.get("timeoutSeconds").getAsLong()*1000));}catch(RuntimeException ignored){/* fall back to configured bound */}}
  long duration=Math.min(stepLimit,number(r,"remainingMs"));if(duration<=0)throw new RuntimeStop("TIMED_OUT");
  jdbc.update("UPDATE article_runtime SET callsUsed=callsUsed+1,reservedCostMicros=reservedCostMicros+?,imageRetriesUsed=imageRetriesUsed+? WHERE taskId=?",reserve,retry?1:0,t.taskId());
  if(!rows.isEmpty()) jdbc.update("UPDATE article_call SET status='IN_FLIGHT',attempts=attempts+1,reservedCostMicros=reservedCostMicros+?,deadlineAt=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)) WHERE callId=?",reserve,duration*1000,id);
  else jdbc.update("INSERT INTO article_call(callId,taskId,requestId,stepId,provider,payloadHash,status,attempts,reservedCostMicros,deadlineAt) VALUES (?,?,?,?,?,?,'IN_FLIGHT',1,?,TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)))",id,t.taskId(),t.requestId(),step,provider,RuntimeExternal.hash(payload),reserve,duration*1000);
  store.event(t.taskId(),"CALL_STARTED",Map.of("callId",id,"step",step,"provider",provider,"estimatedCostMicros",reserve));
  long deadline=jdbc.queryForObject("SELECT UNIX_TIMESTAMP(deadlineAt)*1000 FROM article_call WHERE callId=?",Long.class,id).longValue();
  return new Call(id,true,"IN_FLIGHT",null,null,deadline);
 }
 @Transactional public long queryDeadline(RuntimeScope.Ticket t,String id){
  store.guard(t);var r=store.runtime(t.taskId());
  if(number(r,"callsUsed")>=number(r,"maxCalls"))throw new RuntimeStop("BUDGET_EXHAUSTED");
  jdbc.update("UPDATE article_runtime SET callsUsed=callsUsed+1 WHERE taskId=?",t.taskId());
  jdbc.update("UPDATE article_call SET queryAttempts=queryAttempts+1,deadlineAt=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)) WHERE callId=?",Math.min(config.getStepTimeoutMs(),number(r,"remainingMs"))*1000,id);
  return jdbc.queryForObject("SELECT UNIX_TIMESTAMP(deadlineAt)*1000 FROM article_call WHERE callId=?",Long.class,id).longValue();
 }
 @Transactional public void save(RuntimeScope.Ticket t,String id,RuntimeProvider.Result result){
  store.guard(t);
  boolean done="SUCCEEDED".equals(result.status());
  if(done&&result.result()==null)throw new IllegalArgumentException("Successful external result is missing");
  jdbc.update("UPDATE article_call SET status=?,providerJobId=?,resultJson=?,actualCostMicros=? WHERE callId=?",done?"SUCCEEDED":"UNCERTAIN",result.jobId(),result.result(),result.actualCostMicros(),id);
  // Unknown invoices remain NULL, never reported as zero cost.
  jdbc.update("UPDATE article_runtime SET actualCostMicros=(SELECT IF(COUNT(*)=COUNT(actualCostMicros),SUM(actualCostMicros),NULL) FROM article_call WHERE taskId=?) WHERE taskId=?",t.taskId(),t.taskId());
  store.event(t.taskId(),done?"CALL_SAVED":"EXTERNAL_UNCERTAIN",Map.of("callId",id));
 }
 @Transactional public void imageRejected(RuntimeScope.Ticket t,String id,com.yupi.template.service.image.ImageProviderException failure){store.guard(t);var details=new java.util.LinkedHashMap<String,Object>();details.put("errorCategory",failure.category().name());if(failure.requestId()!=null)details.put("requestId",failure.requestId());jdbc.update("UPDATE article_call SET status=?,resultJson=? WHERE callId=?",failure.uncertain()?"UNCERTAIN":"REJECTED",com.yupi.template.utils.GsonUtils.toJson(details),id);store.event(t.taskId(),"IMAGE_PROVIDER_ERROR",java.util.Map.of("callId",id,"errorCategory",failure.category().name(),"uncertain",failure.uncertain()));}
 @Transactional public void storageFailed(RuntimeScope.Ticket t,String id){store.guard(t);jdbc.update("UPDATE article_call SET status='STORAGE_FAILED' WHERE callId=? AND status<>'SUCCEEDED'",id);store.event(t.taskId(),"IMAGE_STORAGE_FAILED",Map.of("callId",id));}
 @Transactional public void uncertain(RuntimeScope.Ticket t,String id){store.guard(t);jdbc.update("UPDATE article_call SET status='UNCERTAIN' WHERE callId=? AND status<>'SUCCEEDED'",id);}
}
