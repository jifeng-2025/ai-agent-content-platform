package com.yupi.template.runtime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PreDestroy;

/** Replay and tail read the same durable journal; there is no separate live-subscription handoff. */
@Service @RequiredArgsConstructor
public class RuntimeEvents {
 private final RuntimeEventStore events;private final RuntimeStore store;
 private final Set<Thread> streams=ConcurrentHashMap.newKeySet();
 public boolean handles(String id){return store.runtime(id)!=null;}
 public SseEmitter open(String id,long initialCursor){
  var emitter=new SseEmitter(1800000L);var active=new AtomicBoolean(true);
  Thread thread=Thread.ofVirtual().unstarted(()->{
   long cursor=initialCursor;long lastHeartbeat=0;
   try{while(active.get()){
    if(System.currentTimeMillis()-lastHeartbeat>=15000){emitter.send(SseEmitter.event().comment("runtime-connected"));lastHeartbeat=System.currentTimeMillis();}
    var page=events.read(id,cursor);
    boolean terminal=Set.of("COMPLETED","FAILED","NEEDS_REVIEW","IMAGES_FAILED","CANCELLED","TIMED_OUT","BUDGET_EXHAUSTED","EXTERNAL_UNCERTAIN").contains(page.status());
    if(cursor<page.floor()-1||cursor>page.latest()){
     cursor=page.latest();emitter.send(SseEmitter.event().id(Long.toString(cursor)).data(Map.of("type","SNAPSHOT_REQUIRED","resetCursor",cursor,"status",page.status(),"terminal",terminal)));
    }else for(var row:page.events()){
     cursor=((Number)row.get("seq")).longValue();emitter.send(SseEmitter.event().id(Long.toString(cursor)).data(row.get("payloadJson").toString()));
    }
    if(terminal&&cursor>=page.latest()){
     emitter.send(SseEmitter.event().data(Map.of("type","RUNTIME_SNAPSHOT","status",page.status(),"terminal",true)));emitter.complete();break;
    }
    Thread.sleep(500);
   }}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){if(active.get())emitter.completeWithError(e);}finally{active.set(false);streams.remove(Thread.currentThread());}
  });
  Runnable close=()->{active.set(false);thread.interrupt();};emitter.onCompletion(close);emitter.onTimeout(close);emitter.onError(e->close.run());streams.add(thread);thread.start();return emitter;
 }
 @PreDestroy public void close(){streams.forEach(Thread::interrupt);}
}
