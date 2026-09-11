package com.yupi.template.service.image;
/** Never includes raw provider bodies, prompts, credentials or transport exception messages. */
public class ImageProviderException extends RuntimeException {
 public enum Category { AUTH, QUOTA, INVALID_REQUEST, NO_IMAGE, INVALID_RESPONSE, TIMEOUT, TRANSPORT, NOT_CONFIGURED }
 private final Category category; private final boolean uncertain; private final String requestId;
 public ImageProviderException(Category category,boolean uncertain){this(category,uncertain,null);}
 public ImageProviderException(Category category,boolean uncertain,String requestId){super("IMAGE_"+category.name()+(uncertain?"：结果不确定，禁止自动重试":"：请求未完成，请检查配置或服务额度"));this.category=category;this.uncertain=uncertain;this.requestId=safeId(requestId);}
 private static String safeId(String id){return id!=null&&id.matches("[A-Za-z0-9._:-]{1,200}")?id:null;}
 public String requestId(){return requestId;}
 public Category category(){return category;} public boolean uncertain(){return uncertain;}
}
