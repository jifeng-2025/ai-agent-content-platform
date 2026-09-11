package com.yupi.template.modelconfig;
import java.util.*;
public record ModelSpec(String id,int version,String name,String kind,String protocol,String endpoint,String model,int timeoutSeconds,int maxOutputTokens,String imageSize,String aspectRatio) {
 public ModelSpec normalized(){
  if(name==null||name.isBlank()||name.length()>80||!Set.of("TEXT","IMAGE").contains(kind))throw bad("名称或类型无效");
  if(!("TEXT".equals(kind)?Set.of("dashscope","compatible"):Set.of("demo","gemini","doubao")).contains(protocol))throw bad("不支持的协议");
  if(timeoutSeconds<5||timeoutSeconds>180||maxOutputTokens<1||maxOutputTokens>8192)throw bad("超时或输出限制无效");
  if(!"demo".equals(protocol)&&(model==null||!model.matches("[A-Za-z0-9._:/-]{1,160}")))throw bad("请填写账户可用的模型ID");
  if("gemini".equals(protocol)&&!model.matches("[A-Za-z0-9._-]{1,160}"))throw bad("Gemini模型ID无效");
  if(!Set.of("0.5K","1K","2K","4K").contains(imageSize)||aspectRatio==null||!aspectRatio.matches("[1-9][0-9]?:[1-9][0-9]?"))throw bad("图片规格无效");
  String url="demo".equals(protocol)?"":SafeModelHttp.base(endpoint,protocol);
  return new ModelSpec(id,version,name.trim(),kind,protocol,url,"demo".equals(protocol)?"demo":model,timeoutSeconds,maxOutputTokens,imageSize,aspectRatio);
 }
 public static com.yupi.template.exception.BusinessException bad(String text){return new com.yupi.template.exception.BusinessException(com.yupi.template.exception.ErrorCode.PARAMS_ERROR,text);}
}
