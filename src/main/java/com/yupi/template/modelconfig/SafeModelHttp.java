package com.yupi.template.modelconfig;
import okhttp3.*;import java.net.*;import java.time.Duration;import java.util.*;import java.io.*;
/** Public HTTPS only. DNS addresses validated inside the connection resolver; no proxy, retry or redirects. */
@org.springframework.stereotype.Component
public class SafeModelHttp {
 public static String base(String value,String protocol){
  if(value==null)throw ModelSpec.bad("请输入HTTPS Base URL");String s=value.trim().replaceAll("/+$","");
  String tail=switch(protocol){case "compatible"->"/chat/completions";case "doubao"->"/images/generations";case "dashscope"->"/api/v1/services/aigc/text-generation/generation";default->"";};
  if(!tail.isEmpty()&&s.endsWith(tail))s=s.substring(0,s.length()-tail.length());
  if("gemini".equals(protocol)&&s.endsWith("/v1beta"))s=s.substring(0,s.length()-7);
  if(s.length()>500||s.contains("/chat/completions")||s.contains("/images/generations")||s.contains(":generateContent"))throw ModelSpec.bad("请填写对应协议的Base URL，不要混用接口路径");check(s);return s;
 }
 static URI check(String s){try{URI u=URI.create(s);String h=u.getHost();if(!"https".equals(u.getScheme())||h==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!h.matches("[A-Za-z0-9.-]+")||!h.contains(".")||h.endsWith(".")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||u.getRawPath().contains("%")||u.getPath().contains("..")||u.getPath().contains("//"))throw new Exception();return u;}catch(Exception e){throw ModelSpec.bad("仅允许公开HTTPS地址（443端口，无凭据、查询或路径转义）");}}
 static boolean publicIp(InetAddress a){if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;byte[] b=a.getAddress();if(b.length==4){int x=b[0]&255,y=b[1]&255;return x!=0&&x!=10&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==172&&y>=16&&y<=31)&&!(x==192&&(y==168||y==0||y==2))&&!(x==198&&(y==18||y==19||y==51))&&!(x==203&&y==0);}return (b[0]&0xe0)==0x20&&!(b[0]==0x20&&b[1]==1&&(b[2]==0||b[2]==0x0d));}
 public Call call(String url,String header,String key,String json,int seconds,boolean stream){
  check(url);var client=new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).protocols(List.of(Protocol.HTTP_1_1)).connectionPool(new ConnectionPool(0,1,java.util.concurrent.TimeUnit.SECONDS)).dns(host->{var addresses=Arrays.asList(InetAddress.getAllByName(host));if(addresses.isEmpty()||addresses.stream().anyMatch(a->!publicIp(a)))throw new UnknownHostException("Blocked destination");return addresses;}).connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(seconds)).callTimeout(Duration.ofSeconds(seconds)).build();
  var req=new Request.Builder().url(url).header("Content-Type","application/json").header(header,key).post(RequestBody.create(json,MediaType.get("application/json")));if(stream)req.header("X-DashScope-SSE","enable");return client.newCall(req.build());
 }
 public com.yupi.template.service.image.ImageHttpTransport.Response post(String url,String header,String key,String json,int seconds){
  try(var response=call(url,header,key,json,seconds,false).execute()){byte[] b=response.body().byteStream().readNBytes(8*1024*1024+1);if(b.length>8*1024*1024)throw new IOException();return new com.yupi.template.service.image.ImageHttpTransport.Response(response.code(),new String(b,java.nio.charset.StandardCharsets.UTF_8),requestId(response));}catch(Exception e){throw ModelSpec.bad("MODEL_TRANSPORT_UNCERTAIN：未重试，结果可能已产生费用");}
 }
 public static String requestId(Response r){String s=r.header("x-request-id",r.header("x-tt-logid",""));return s.matches("[A-Za-z0-9._:-]{1,200}")?s:"";}
}
