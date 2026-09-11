package com.yupi.template.service.image;
import com.google.gson.*;import com.yupi.template.model.dto.image.ImageData;import java.util.*;
/** Native synchronous protocols; one HTTP submission, no cross-provider fallback. */
public final class ImageProviderProtocol {
 public static ImageData generate(ImageHttpTransport http,ImageProfile p,String key,String prompt){
  p.validate();if(key==null||key.isBlank())throw new ImageProviderException(ImageProviderException.Category.NOT_CONFIGURED,false);
  if(prompt==null||prompt.isBlank()||prompt.length()>12000)throw new ImageProviderException(ImageProviderException.Category.INVALID_REQUEST,false);
  boolean gemini="gemini".equals(p.provider());
  Map<String,String> imageConfig=new HashMap<>();imageConfig.put("aspectRatio",p.aspectRatio());if(!p.model().startsWith("gemini-2.5-"))imageConfig.put("imageSize",p.size());
  Object body=gemini?Map.of("contents",List.of(Map.of("parts",List.of(Map.of("text",prompt)))),"generationConfig",Map.of("responseModalities",List.of("TEXT","IMAGE"),"maxOutputTokens",p.maxOutputTokens(),"imageConfig",imageConfig)):Map.of("model",p.model(),"prompt",prompt,"size",p.size(),"response_format","b64_json","sequential_image_generation","disabled","stream",false,"watermark",true);
  var response=http.post(p.endpoint()+(gemini?"/v1beta/models/"+p.model()+":generateContent":"/images/generations"),gemini?"x-goog-api-key":"Authorization",gemini?key:"Bearer "+key,new Gson().toJson(body),p.timeoutSeconds());
  int status=response.status();String requestId=response.requestId();
  if(status==401||status==403)throw new ImageProviderException(ImageProviderException.Category.AUTH,false,requestId);
  if(status==429)throw new ImageProviderException(ImageProviderException.Category.QUOTA,false,requestId);
  if(status>=400&&status<500&&status!=408)throw new ImageProviderException(ImageProviderException.Category.INVALID_REQUEST,false,requestId);
  if(status!=200)throw new ImageProviderException(ImageProviderException.Category.TRANSPORT,true,requestId);
  try{
   JsonObject root=JsonParser.parseString(response.body()).getAsJsonObject();String encoded=null,mime="image/png";
   if(gemini){
    if(root.has("responseId")){String id=root.get("responseId").getAsString();if(id.matches("[A-Za-z0-9._:-]{1,200}"))requestId=id;}
    if(root.has("candidates"))for(var candidate:root.getAsJsonArray("candidates")){
     var c=candidate.getAsJsonObject();if(!c.has("content"))continue;var content=c.getAsJsonObject("content");if(!content.has("parts"))continue;
     for(var part:content.getAsJsonArray("parts")){var v=part.getAsJsonObject();if(v.has("inlineData")){var inline=v.getAsJsonObject("inlineData");encoded=inline.get("data").getAsString();mime=inline.get("mimeType").getAsString();break;}}
     if(encoded!=null)break;
    }
   }else if(root.has("data")&&!root.getAsJsonArray("data").isEmpty()){
    var first=root.getAsJsonArray("data").get(0).getAsJsonObject();if(first.has("b64_json"))encoded=first.get("b64_json").getAsString();
   }
   if(encoded==null||encoded.isBlank())throw new ImageProviderException(ImageProviderException.Category.NO_IMAGE,false,requestId);
   byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length>5242880||bytes.length<8)throw new IllegalArgumentException();
   // Ark b64 has no MIME: detect the raster signature; LocalImageStorage performs full validation.
   if(!gemini)mime=(bytes[0]==(byte)0xff&&bytes[1]==(byte)0xd8)?"image/jpeg":"image/png";
   if(!Set.of("image/png","image/jpeg","image/webp").contains(mime))throw new IllegalArgumentException();
   Map<String,Long> usage=new TreeMap<>();String usageKey=gemini?"usageMetadata":"usage";
   if(root.has(usageKey)&&root.get(usageKey).isJsonObject())for(var e:root.getAsJsonObject(usageKey).entrySet())if(e.getKey().matches("[A-Za-z_]{1,64}")&&e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isNumber()){long n=e.getValue().getAsLong();if(n>=0)usage.put(e.getKey(),n);}
   var result=ImageData.fromBytes(bytes,mime);result.setMetadata(new ImageMetadata(p.provider(),p.model(),requestId,null,usage,null));return result;
  }catch(ImageProviderException e){throw e;}catch(Exception e){throw new ImageProviderException(ImageProviderException.Category.INVALID_RESPONSE,true,requestId);}
 }
}
