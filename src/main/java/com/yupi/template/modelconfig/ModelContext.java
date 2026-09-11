package com.yupi.template.modelconfig;
@org.aspectj.lang.annotation.Aspect @org.springframework.stereotype.Component
public class ModelContext {
 private static final ThreadLocal<String> TASK=new ThreadLocal<>();
 public static String task(){var r=com.yupi.template.runtime.RuntimeScope.current();return r==null?TASK.get():r.ticket().taskId();}
 @org.aspectj.lang.annotation.Around("execution(* com.yupi.template.agent.agents.*.apply(..)) || execution(* com.yupi.template.agent.review.ArticleReviewLoop.run*(..)) || execution(* com.yupi.template.service.ArticleAgentService.executePhase*(..)) || execution(* com.yupi.template.service.impl.ArticleServiceImpl.aiModifyOutline(..))")
 public Object around(org.aspectj.lang.ProceedingJoinPoint point)throws Throwable{
  String old=TASK.get(),task=old;for(Object a:point.getArgs()){if(a instanceof com.yupi.template.model.dto.article.ArticleState s)task=s.getTaskId();if(a instanceof com.alibaba.cloud.ai.graph.OverAllState s)task=s.value("taskId").map(Object::toString).orElse(task);}
  if(point.getSignature().getName().equals("aiModifyOutline"))task=(String)point.getArgs()[0];if(task!=null)TASK.set(task);
  try{return point.proceed();}finally{if(old==null)TASK.remove();else TASK.set(old);}
 }
}
