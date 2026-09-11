package com.yupi.template.runtime;

import com.yupi.template.utils.GsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Test-classpath-only local HTTP adapter. Never packaged in the application JAR. */
@Configuration
@Profile("a4-isolated")
public class A4MockProviderConfig {
 @Bean RuntimeProvider a4Provider(com.yupi.template.storage.ImageStorage images) {
  if (!"true".equals(System.getenv("A4_ISOLATED"))) throw new IllegalStateException("Dedicated isolated environment required");
  return new RuntimeProvider() {
   final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
   public boolean supports(String provider){return !"image:DEMO".equals(provider);}
   public boolean idempotentSubmit(){return true;}
   public Result submit(String id,String payload)throws Exception{return send("POST","/jobs",GsonUtils.toJson(Map.of("id",id,"payload",payload)));}
   public Result query(String id,String job)throws Exception{return send("GET","/jobs/"+id,null);}
   Result send(String method,String path,String body)throws Exception {
    var request=HttpRequest.newBuilder(URI.create(System.getenv("A3_PROVIDER_URL")+path)).timeout(Duration.ofSeconds(65)).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
    var response=client.send(request,HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()!=200)throw new IllegalStateException("Isolated provider HTTP failure");
    var result=GsonUtils.fromJson(response.body(),Result.class);
    if(result.result()!=null && result.result().contains("\"url\"") && result.result().contains("\"success\"")){
     var json=com.google.gson.JsonParser.parseString(result.result()).getAsJsonObject();
     if(json.has("success")&&json.get("success").getAsBoolean()){
      var picture=new java.awt.image.BufferedImage(640,360,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=picture.createGraphics();g.setColor(new java.awt.Color(212,237,220));g.fillRect(0,0,640,360);g.setColor(java.awt.Color.BLACK);g.drawString("A4 MOCK - local persistent image",80,180);g.dispose();var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(picture,"png",bytes);
      json.addProperty("url",images.save(com.yupi.template.model.dto.image.ImageData.fromBytes(bytes.toByteArray(),"image/png"),"mock"));result=new Result(result.status(),result.jobId(),json.toString(),result.actualCostMicros());
     }
    }
    return result;
   }
  };
 }
}
