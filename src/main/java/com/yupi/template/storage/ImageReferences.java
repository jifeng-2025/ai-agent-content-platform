package com.yupi.template.storage;
public final class ImageReferences {
 public static boolean local(String value){return value!=null&&value.matches("/api/images/[a-f0-9]{32}");}
 public static boolean allowed(String value){if(local(value))return true;try{var u=java.net.URI.create(value);return java.util.Set.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
}
