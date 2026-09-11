package com.yupi.template.runtime;
import com.yupi.template.MainApplication;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.yupi.template.agent.review.ReviewModelGateway;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.service.CosService;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Real application process controlled externally by a fault-injection harness. Cloud adapters use real local HTTP. */
@SpringBootTest(classes={MainApplication.class,A3ProcessIT.Provider.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"spring.profiles.active=a2-isolated","server.port=8567","article.runtime.enabled=true"})
@EnabledIfEnvironmentVariable(named="A3_PROCESS_ISOLATED",matches="true")
class A3ProcessIT {
 @MockitoBean DashScopeChatModel cloud;
 @MockitoBean ReviewModelGateway reviewer;
 @MockitoBean ImageGenerationTool images;
 @MockitoBean CosService cos;
 @Autowired RuntimeWorker worker;
 @TestConfiguration static class Provider {
  @Bean RuntimeProvider fakeProvider(){return new RuntimeProvider(){
   final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
   public boolean supports(String provider){return true;}
   public boolean idempotentSubmit(){return true;}
   public Result submit(String id,String payload)throws Exception {return send("POST","/jobs",GsonUtils.toJson(Map.of("id",id,"payload",payload)));}
   public Result query(String id,String job)throws Exception{return send("GET","/jobs/"+id,null);}
   Result send(String method,String path,String body)throws Exception {
    var b=HttpRequest.newBuilder(URI.create(System.getenv("A3_PROVIDER_URL")+path)).timeout(Duration.ofSeconds(65)).header("Content-Type","application/json");
    var response=client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()!=200)throw new IllegalStateException("Mock provider HTTP failure");return GsonUtils.fromJson(response.body(),Result.class);
   }
  };}
 }
 @Test void serveIsolatedApplicationUntilHarnessFinishes()throws Exception {
  String role=System.getenv().getOrDefault("A3_WORKER_ROLE","backend");Files.createDirectories(Path.of("target"));
  Files.writeString(Path.of("target/a3-process-ready-"+role+".json"),GsonUtils.toJson(Map.of("ready",true,"owner",Objects.requireNonNull(org.springframework.test.util.ReflectionTestUtils.getField(worker,"owner")),"pid",ProcessHandle.current().pid())));
  long deadline=System.currentTimeMillis()+1800000;
  while(!Files.exists(Path.of("target/a3-process-stop"))&&System.currentTimeMillis()<deadline)Thread.sleep(500);
  Assertions.assertTrue(Files.exists(Path.of("target/a3-process-stop")),"Harness did not finish within 30 minutes");
 }
}
