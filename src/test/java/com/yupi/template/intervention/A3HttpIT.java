package com.yupi.template.intervention;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Inherits the unchanged nine A2 HTTP contracts and adds normal Runtime intake. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"spring.profiles.active=a2-isolated","server.port=8567","article.agent.orchestrator.enabled=true","article.agent.review-loop.enabled=true","article.agent.intervention.enabled=true","article.runtime.enabled=true"})
class A3HttpIT extends A2HttpIT {
 @Test @Order(10) void normalCreationUsesDurableOperationsAndUserGoal() throws Exception {
  var prompts=new CopyOnWriteArrayList<String>();
  when(model.call(any(Prompt.class))).thenAnswer(c->{String p=((Prompt)c.getArgument(0)).getContents();prompts.add(p);String result;
   if(p.contains("爆款文章标题专家")) result="[{\"mainTitle\":\"开始行动\",\"subTitle\":\"从小事开始\"}]";
   else if(p.contains("专业的文章策划师"))result="{\"sections\":[{\"section\":1,\"title\":\"开始行动\",\"points\":[\"选择任务\"]}]}";
   else if(p.contains("资深的内容创作者"))result=BODY;
   else result="{\"contentWithPlaceholders\":\"ignored\",\"imageRequirements\":[]}";
   return new ChatResponse(List.of(new Generation(new AssistantMessage(result))));
  });
  when(model.stream(any(Prompt.class))).thenAnswer(c->reactor.core.publisher.Flux.just(model.call((Prompt)c.getArgument(0))));
  var request=Map.of("topic","学习行动","requestId",UUID.randomUUID().toString(),"enabledImageMethods",List.of("PEXELS"));
  var created=call(owner,"POST","/article/create",request);assertEquals(0,created.get("code").getAsInt(),created.toString());String id=created.get("data").getAsString();
  assertEquals(id,call(owner,"POST","/article/create",request).get("data").getAsString());waitOperation(id,"normal-title-0001");
  assertEquals("TITLE_SELECTING",data(owner,"/article/"+id).get("phase").getAsString());
  assertEquals(0,call(owner,"POST","/article/confirm-title",Map.of("taskId",id,"selectedMainTitle","开始行动","selectedSubTitle","从小事开始","userDescription","A3_TARGET 面向初学者，解释行动步骤")).get("code").getAsInt());waitOperation(id,"normal-outline-0001");
  assertEquals(0,call(owner,"POST","/article/confirm-outline",Map.of("taskId",id,"outline",List.of(Map.of("section",1,"title","开始行动","points",List.of("选择任务"))))).get("code").getAsInt());waitOperation(id,"normal-body-0001");
  assertEquals("COMPLETED",data(owner,"/article/"+id).get("status").getAsString());assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId=? AND runId IS NOT NULL AND status='COMPLETED'",Integer.class,id));
  assertTrue(prompts.stream().anyMatch(p->p.contains("资深的内容创作者")&&p.contains("A3_TARGET")));
 }

 @org.springframework.beans.factory.annotation.Autowired com.yupi.template.runtime.RuntimeStore runtimeStore;
 @Test @Order(11) void cancelDuringExternalCallRejectsLateResultAndReplaysTerminalEvents() throws Exception {
  seed("cancel-case",false);
  when(model.call(any(Prompt.class))).thenAnswer(c->{try{Thread.sleep(2500);}catch(InterruptedException ignored){}return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"contentWithPlaceholders\":\"ignored\",\"imageRequirements\":[]}"))));});
  assertEquals(0,call(owner,"POST",endpoint("cancel-case"),request("cancel-case","ACCEPT")).get("code").getAsInt());
  for(int i=0;i<100&&jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId='cancel-case'",Integer.class)==0;i++)Thread.sleep(100);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId='cancel-case'",Integer.class));
  String path="/article/cancel-case/runtime";var before=data(owner,path);
  var command=Map.of("requestId",UUID.randomUUID().toString(),"action","CANCEL","expectedStateVersion",before.get("stateVersion").getAsLong());
  assertEquals(0,call(owner,"POST",path,command).get("code").getAsInt());assertTrue(call(owner,"POST",path,command).getAsJsonObject("data").get("replayed").getAsBoolean());
  Thread.sleep(2800);var after=data(owner,path);assertEquals("CANCELLED",after.get("status").getAsString());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId='cancel-case' AND status='SUCCEEDED'",Integer.class));
  assertEquals(BODY,data(owner,"/article/cancel-case").get("content").getAsString());assertEquals(40101,call(other,"GET",path,null).get("code").getAsInt());
  var stale=new HashMap<String,Object>(command);stale.put("requestId",UUID.randomUUID().toString());assertEquals(40900,call(owner,"POST",path,stale).get("code").getAsInt());
  var uri=java.net.URI.create("http://localhost:8567/api/article/progress/cancel-case?cursor=0");
  String stream=owner.send(java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body();
  assertTrue(stream.contains("CANCELLED"));assertTrue(stream.contains("id:"));assertFalse(stream.contains("ALL_COMPLETE"));
  long last=after.get("lastEventId").getAsLong();
  String resumed=owner.send(java.net.http.HttpRequest.newBuilder(uri).header("Last-Event-ID",Long.toString(last)).timeout(java.time.Duration.ofSeconds(5)).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body();assertFalse(resumed.contains("CALL_STARTED"));assertTrue(resumed.contains("RUNTIME_SNAPSHOT"));
  jdbc.update("DELETE FROM article_event WHERE taskId='cancel-case' AND seq<?",last);
  String expired=owner.send(java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body();assertTrue(expired.contains("SNAPSHOT_REQUIRED"));
 }
 @Test @Order(12) void durableBudgetRejectsCallBeforeProviderInvocation() throws Exception {
  seed("budget-case",false);runtimeStore.ensure("budget-case");jdbc.update("UPDATE article_runtime SET maxCalls=0 WHERE taskId='budget-case'");
  assertEquals(0,call(owner,"POST",endpoint("budget-case"),request("budget-case","ACCEPT")).get("code").getAsInt());
  for(int i=0;i<100&&!"BUDGET_EXHAUSTED".equals(data(owner,"/article/budget-case/runtime").get("status").getAsString());i++)Thread.sleep(100);
  assertEquals("BUDGET_EXHAUSTED",data(owner,"/article/budget-case/runtime").get("status").getAsString());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId='budget-case'",Integer.class));
 }
 @Test @Order(13) void unknownExternalFailurePausesAndCannotBeBlindlyRetried() throws Exception {
  seed("uncertain-case",false);var calls=new java.util.concurrent.atomic.AtomicInteger();when(model.call(any(Prompt.class))).thenAnswer(c->{calls.incrementAndGet();throw new IllegalStateException("response lost");});
  assertEquals(0,call(owner,"POST",endpoint("uncertain-case"),request("uncertain-case","ACCEPT")).get("code").getAsInt());
  String path="/article/uncertain-case/runtime";
  for(int i=0;i<100&&!"EXTERNAL_UNCERTAIN".equals(data(owner,path).get("status").getAsString());i++)Thread.sleep(100);
  var view=data(owner,path);assertEquals("EXTERNAL_UNCERTAIN",view.get("status").getAsString());assertEquals(1,calls.get());
  assertEquals(0,call(owner,"POST",path,Map.of("requestId",UUID.randomUUID().toString(),"action","RECHECK_EXTERNAL","expectedStateVersion",view.get("stateVersion").getAsLong())).get("code").getAsInt());
  for(int i=0;i<100&&!"EXTERNAL_UNCERTAIN".equals(data(owner,path).get("status").getAsString());i++)Thread.sleep(100);
  assertEquals("EXTERNAL_UNCERTAIN",data(owner,path).get("status").getAsString());assertEquals(1,calls.get());assertEquals(1,jdbc.queryForObject("SELECT callsUsed FROM article_runtime WHERE taskId='uncertain-case'",Integer.class));
 }
 void waitOperation(String id,String operation) throws Exception {
  for(int i=0;i<150;i++){String status=jdbc.queryForObject("SELECT status FROM article_operation WHERE taskId=? AND requestId=?",String.class,id,operation);if("COMPLETED".equals(status))return;if(!List.of("RUNNING","QUEUED").contains(status))fail("Runtime stopped: "+status);Thread.sleep(100);}fail("Runtime operation timeout");
 }

 @org.springframework.beans.factory.annotation.Autowired com.yupi.template.config.DoubaoConfig doubaoConfig;
 @org.springframework.beans.factory.annotation.Autowired com.yupi.template.service.image.ImageProfileStore profiles;
 @Test @Order(14) void providerSelectionIsPersistedThroughRealCreationHttp()throws Exception{
  String oldKey=doubaoConfig.getApiKey(),oldModel=doubaoConfig.getModel();try{
   doubaoConfig.setApiKey("isolated-not-a-real-credential");doubaoConfig.setModel("test-seedream-model");
   when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("[{\"mainTitle\":\"测试标题\",\"subTitle\":\"测试副标题\"}]")))));
   var req=Map.of("topic","供应商快照测试","requestId",UUID.randomUUID().toString(),"enabledImageMethods",List.of("DOUBAO"));var result=call(owner,"POST","/article/create",req);assertEquals(0,result.get("code").getAsInt(),result.toString());String id=result.get("data").getAsString();assertEquals(id,call(owner,"POST","/article/create",req).get("data").getAsString());
   doubaoConfig.setModel("changed-global-model");assertEquals("test-seedream-model",profiles.find(id,"DOUBAO").model());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM article_image_profile WHERE taskId=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId=? AND provider LIKE 'image:%'",Integer.class,id));
   var cap=call(owner,"GET","/article/image-capabilities",null);assertTrue(cap.getAsJsonObject("data").get("doubaoConfigured").getAsBoolean());assertFalse(cap.toString().contains("isolated-not-a-real-credential"));assertEquals(40100,call(client(),"GET","/article/image-capabilities",null).get("code").getAsInt());
  }finally{doubaoConfig.setApiKey(oldKey);doubaoConfig.setModel(oldModel);}
 }
}
