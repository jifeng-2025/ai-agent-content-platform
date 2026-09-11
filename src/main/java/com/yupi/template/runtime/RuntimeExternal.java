package com.yupi.template.runtime;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor
public class RuntimeExternal {
 private final RuntimeLedger ledger;private final RuntimeStore store;private final List<RuntimeProvider> providers;
 public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @org.springframework.beans.factory.annotation.Autowired(required=false) private com.yupi.template.modelconfig.ModelSettings modelSettings;
 public String call(RuntimeScope.Ticket t,String step,String providerHint,String payload,Callable<String> action){
  var selected="dashscope".equals(providerHint)&&modelSettings!=null?modelSettings.taskText(t.taskId()):null;
  final String provider=selected==null?providerHint:selected.protocol();
  final String ledgerPayload=selected==null?payload:com.yupi.template.utils.GsonUtils.toJson(java.util.Map.of("configuration",selected,"input",payload));
  var call=ledger.prepare(t,step,provider,ledgerPayload);if("SUCCEEDED".equals(call.status()))return call.result();
  if("REJECTED".equals(call.status())){var detail=com.google.gson.JsonParser.parseString(call.result()).getAsJsonObject();var category=detail.get("errorCategory").getAsString();throw new com.yupi.template.service.image.ImageProviderException(com.yupi.template.service.image.ImageProviderException.Category.valueOf(category),false,detail.has("requestId")?detail.get("requestId").getAsString():null);}
  RuntimeProvider adapter=providers.stream().filter(p->p.supports(provider)).findFirst().orElse(null);
  boolean demo="image:DEMO".equals(provider);
  if(!call.fresh()&&adapter==null&&!demo)throw new RuntimeStop("EXTERNAL_UNCERTAIN");
  long deadline=call.fresh()?call.deadlineMs():ledger.queryDeadline(t,call.id());
  if(System.currentTimeMillis()>=deadline){ledger.uncertain(t,call.id());throw new RuntimeStop("EXTERNAL_UNCERTAIN");}
  var scope=RuntimeScope.current();
  FutureTask<RuntimeProvider.Result> future=new FutureTask<>(()->{
   if(scope!=null)RuntimeScope.set(scope);
   try{
    if(!call.fresh() && !demo){
     var known=adapter.query(call.id(),call.jobId());
     if(!"NOT_FOUND".equals(known.status())||!adapter.idempotentSubmit())return known;
    }
    store.guard(t);
    if(System.currentTimeMillis()>=deadline)throw new RuntimeStop("EXTERNAL_UNCERTAIN");
    if(adapter!=null)return adapter.submit(call.id(),payload);
    String value=action.call();
    if(Set.of("image:NANO_BANANA","image:DOUBAO","image:SVG_DIAGRAM").contains(provider)){
     var image=com.google.gson.JsonParser.parseString(value).getAsJsonObject();
     if(!image.has("success")||!image.get("success").getAsBoolean()||!image.has("method")||!provider.substring(6).equals(image.get("method").getAsString()))throw new RuntimeStop("EXTERNAL_UNCERTAIN");
    }
    return new RuntimeProvider.Result("SUCCEEDED",null,value,demo?0L:null);
   }finally{RuntimeScope.clear();}
  });
  Thread.ofVirtual().name("runtime-call-"+call.id().substring(0,12)).start(future);
  try{
   while(true){
    store.guard(t);
    long remaining=deadline-System.currentTimeMillis();
    if(remaining<=0){ledger.uncertain(t,call.id());throw new RuntimeStop("EXTERNAL_UNCERTAIN");}
    try{
     var result=future.get(Math.min(250,remaining),TimeUnit.MILLISECONDS);
     ledger.save(t,call.id(),result);
     if(!"SUCCEEDED".equals(result.status()))throw new RuntimeStop("EXTERNAL_UNCERTAIN");return result.result();
    }catch(TimeoutException ignored){/* Re-check cancellation, fence and durable deadline. */}
   }
  }catch(RuntimeStop stop){throw stop;}
  catch(Exception e){if(e instanceof ExecutionException && e.getCause() instanceof com.yupi.template.service.image.ImageProviderException failure){ledger.imageRejected(t,call.id(),failure);if(failure.uncertain())throw new RuntimeStop("EXTERNAL_UNCERTAIN");throw failure;}if(e instanceof ExecutionException && e.getCause() instanceof com.yupi.template.storage.ImageStorageException failure){ledger.storageFailed(t,call.id());throw failure;}try{ledger.uncertain(t,call.id());}catch(RuntimeStop stop){throw stop;}throw new RuntimeStop("EXTERNAL_UNCERTAIN");}
  finally{future.cancel(true);}
 }
}
