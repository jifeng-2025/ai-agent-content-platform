package com.yupi.template.runtime;
public final class RuntimeScope {
 public record Ticket(String taskId,String requestId,String runId,long fence) {}
 public record Execution(Ticket ticket,RuntimeStore store,RuntimeExternal external) {}
 private static final ThreadLocal<Execution> CURRENT=new ThreadLocal<>();
 public static Execution current(){return CURRENT.get();}
 public static boolean active(){return current()!=null;}
 public static void set(Execution e){CURRENT.set(e);}
 public static void clear(){CURRENT.remove();}
 public static void guard(){if(active())current().store().guard(current().ticket());}
 public static String call(String step,String provider,String payload,java.util.concurrent.Callable<String> action){
  if(active())return current().external().call(current().ticket(),step,provider,payload,action);
  try{return action.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
 }
}
