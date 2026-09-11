package com.yupi.template.service.image;
/** Immutable non-secret selection captured when the article is created. */
public record ImageProfile(String provider,String model,String endpoint,String size,String aspectRatio,int maxOutputTokens,int timeoutSeconds,String taskId,String configId,int configVersion) implements java.io.Serializable {
 public ImageProfile(String provider,String model,String endpoint,String size,String aspectRatio,int maxOutputTokens,int timeoutSeconds){this(provider,model,endpoint,size,aspectRatio,maxOutputTokens,timeoutSeconds,null,null,0);}
 public ImageProfile(String provider,String model,String endpoint,String size,String aspectRatio,int maxOutputTokens,int timeoutSeconds,String taskId){this(provider,model,endpoint,size,aspectRatio,maxOutputTokens,timeoutSeconds,taskId,null,0);}
 public ImageProfile forTask(String taskId){return new ImageProfile(provider,model,endpoint,size,aspectRatio,maxOutputTokens,timeoutSeconds,taskId,configId,configVersion);}
 public static boolean paid(String method){return "NANO_BANANA".equals(method)||"DOUBAO".equals(method);}
 public void validate(){
  String expected="gemini".equals(provider)?"https://generativelanguage.googleapis.com":"https://ark.cn-beijing.volces.com/api/v3";
  if(configId!=null)com.yupi.template.modelconfig.SafeModelHttp.base(endpoint,provider);
  if((configId==null&&!expected.equals(endpoint))||model==null||!model.matches("[A-Za-z0-9._-]{1,160}")||timeoutSeconds<1||timeoutSeconds>180||maxOutputTokens<1||maxOutputTokens>8192)throw new IllegalArgumentException("图片供应商配置无效");
  if(size==null||!java.util.Set.of("0.5K","1K","2K","4K").contains(size)||aspectRatio==null||!aspectRatio.matches("[1-9][0-9]?:[1-9][0-9]?"))throw new IllegalArgumentException("图片规格无效");
  if(!java.util.Set.of("gemini","doubao").contains(provider))throw new IllegalArgumentException("图片供应商无效");
 }
}
