package com.yupi.template.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.yupi.template.agent.agents.*;
import com.yupi.template.agent.review.ArticleReviewLoop;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.repository.ReviewTraceStore;
import com.yupi.template.service.*;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor @Slf4j
public class RuntimeWorker {
 private final RuntimeStore store;private final RuntimeConfig config;private final RuntimeExternal external;
 private final ArticleService articles;private final ReviewTraceStore reviews;
 private final TitleGeneratorAgent titles;private final OutlineGeneratorAgent outlines;private final ContentGeneratorAgent content;
 private final ArticleReviewLoop review;private final ArticleMediaProcessor media;
 @jakarta.annotation.Resource private QuickCreation quick;
 private final String owner=UUID.randomUUID().toString();
 private final ExecutorService pool=Executors.newFixedThreadPool(2);
 private final ConcurrentMap<String,RuntimeScope.Ticket> active=new ConcurrentHashMap<>();
 private final Set<String> dispatched=ConcurrentHashMap.newKeySet();
 @Scheduled(fixedDelayString="${article.runtime.poll-ms:1000}",initialDelayString="${article.runtime.initial-dispatch-delay-ms:0}")
 public void scan(){

  try{for(var row:store.candidates()){
   if(!config.isEnabled()&&!Set.of("QUICK_CREATE","QUICK_IMAGE_RETRY","QUICK_ADVICE_RETRY").contains(row.get("action")))continue;
   if(dispatched.size()>=2)break;
   String task=row.get("taskId").toString(),id=row.get("requestId").toString(),key=task+":"+id;
   if(dispatched.add(key))pool.submit(()->{try{var work=store.claim(task,id,owner);if(work!=null)execute(work);}catch(Exception e){log.warn("Runtime dispatch failed: {}",key,e);}finally{dispatched.remove(key);}});
  }}catch(Exception e){log.warn("Runtime polling unavailable: {}",e.getClass().getSimpleName());}
 }
 @Scheduled(fixedDelayString="${article.runtime.heartbeat-ms:2000}")
 public void heartbeat(){for(var t:active.values()){try{store.heartbeat(t);}catch(Exception e){log.warn("Runtime heartbeat unavailable: {}",t.taskId());}}}
 public void execute(RuntimeStore.Work work){
  var t=work.ticket();active.put(t.taskId(),t);RuntimeScope.set(new RuntimeScope.Execution(t,store,external));
  try{run(work);store.finish(t);}catch(RuntimeStop stop){if(!"FENCED".equals(stop.status))store.stop(t,stop.status);}catch(Exception e){log.warn("Runtime execution failed: {}",t.taskId(),e);store.stop(t,"FAILED");}
  finally{RuntimeScope.clear();active.remove(t.taskId(),t);}
 }
 @SuppressWarnings("unchecked")
 private void run(RuntimeStore.Work work) throws Exception {
  if(Set.of("QUICK_CREATE","QUICK_IMAGE_RETRY","QUICK_ADVICE_RETRY").contains(work.action())){quick.run(work);return;}
  var t=work.ticket();String phase=work.phase();var a=articles.getByTaskId(t.taskId());
  var s=new ArticleState();s.setTaskId(t.taskId());s.setTopic(a.getTopic());s.setStyle(a.getStyle());s.setUserDescription(a.getUserDescription());
  var title=new ArticleState.TitleResult();title.setMainTitle(a.getMainTitle());title.setSubTitle(a.getSubTitle());s.setTitle(title);
  if(a.getOutline()!=null){var o=new ArticleState.OutlineResult();o.setSections(Arrays.asList(GsonUtils.fromJson(a.getOutline(),ArticleState.OutlineSection[].class)));s.setOutline(o);}
  if(a.getEnabledImageMethods()!=null)s.setEnabledImageMethods(Arrays.asList(GsonUtils.fromJson(a.getEnabledImageMethods(),String[].class)));
  s.setContent(a.getContent());s.setReviewTrace(reviews.find(t.taskId()));
  if(Set.of("DONE","TITLE_WAIT","OUTLINE_WAIT").contains(phase))return;
  var input=new HashMap<String,Object>();input.put("topic",s.getTopic());input.put("style",s.getStyle());input.put("mainTitle",title.getMainTitle());input.put("subTitle",title.getSubTitle());input.put("userDescription",s.getUserDescription());input.put("outline",s.getOutline());input.put("reviewLoopEnabled",true);
  if("TITLE".equals(phase)){
   var json=RuntimeScope.call("TITLE","dashscope",GsonUtils.toJson(input),()->GsonUtils.toJson(titles.apply(new OverAllState(input)).get("titleOptions")));
   s.setTitleOptions(Arrays.asList(GsonUtils.fromJson(json,ArticleState.TitleOption[].class)));store.saveStage(t,s,"TITLE_WAIT","TITLE_SELECTING","TITLES_GENERATED");return;
  }
  if("OUTLINE".equals(phase)){
   var json=RuntimeScope.call("OUTLINE","dashscope",GsonUtils.toJson(input),()->GsonUtils.toJson(outlines.apply(new OverAllState(input)).get("outline")));
   s.setOutline(GsonUtils.fromJson(json,ArticleState.OutlineResult.class));store.saveStage(t,s,"OUTLINE_WAIT","OUTLINE_EDITING","OUTLINE_GENERATED");return;
  }
  if("BODY".equals(phase)){
   s.setContent(RuntimeScope.call("BODY","dashscope",GsonUtils.toJson(input),()->content.apply(new OverAllState(input)).get("content").toString()));
   store.saveStage(t,s,"REVIEW","REVIEWING","BODY_SAVED");phase="REVIEW";
  }
  if("REVIEW".equals(phase)){
   if(s.getReviewTrace()==null)review.run(s,p->articles.saveReviewProgress(t.taskId(),p));
   else if(!Set.of("PASS","HUMAN_ACCEPTED","NEEDS_REVIEW").contains(s.getReviewTrace().status()))review.runRound(s,p->articles.saveReviewProgress(t.taskId(),p));
   if("NEEDS_REVIEW".equals(s.getReviewTrace().status()))return;
  }
  if("RETRY_IMAGE".equals(work.action())){
   var request=GsonUtils.fromJson(work.requestJson(),InterventionRequest.class);media.retry(s,request.imageId(),ignored->{});
  }else media.generate(s,ignored->{});
 }
 @PreDestroy public void close(){pool.shutdownNow();}
}
