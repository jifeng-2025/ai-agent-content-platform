package com.yupi.template.runtime;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against a dedicated MySQL. The harness kills the first JVM at the on-disk barrier. */
@EnabledIfEnvironmentVariable(named="A3_STORE_ISOLATED",matches="true")
class A3StoreIT {
 @Configuration @EnableTransactionManagement static class Beans {
  @Bean DataSource dataSource(){return new DriverManagerDataSource(System.getenv("A3_DB_URL"),"root","a3-test-only");}
  @Bean JdbcTemplate jdbc(DataSource d){return new JdbcTemplate(d);}
  @Bean DataSourceTransactionManager tx(DataSource d){return new DataSourceTransactionManager(d);}
  @Bean RuntimeConfig config(){return new RuntimeConfig();}
  @Bean RuntimeStore store(JdbcTemplate j,RuntimeConfig c){return new RuntimeStore(j,c);}
  @Bean RuntimeLedger ledger(JdbcTemplate j,RuntimeStore s,RuntimeConfig c){return new RuntimeLedger(j,s,c);}
 }
 @Test void persistentCheckpointSurvivesKilledJvmAndRejectsOldFence() throws Exception {
  try(var context=new AnnotationConfigApplicationContext(Beans.class)) {
   var store=context.getBean(RuntimeStore.class);var jdbc=context.getBean(JdbcTemplate.class);
   var tx=new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
   if("prepare".equals(System.getenv("A3_PROCESS_STAGE"))) {
    for(String id:List.of("queued","checkpoint","budget","deadline"))jdbc.update("INSERT INTO article(taskId,userId,topic,content) VALUES (?,1,'isolated','saved draft')",id);
    store.enqueue("queued","create-request","CREATE","TITLE","{}");
    store.enqueue("checkpoint","edit-request","EDIT_REVIEW","REVIEW","{}");
    store.enqueue("budget","budget-request","CREATE","TITLE","{}");
    jdbc.update("UPDATE article_runtime SET callsUsed=maxCalls WHERE taskId='budget'");
    store.enqueue("deadline","deadline-request","CREATE","TITLE","{}");store.claim("deadline","deadline-request","first-process");
    jdbc.update("UPDATE article_runtime SET remainingMs=1 WHERE taskId='deadline'");
    var w=store.claim("checkpoint","edit-request","first-process");assertNotNull(w);
    tx.executeWithoutResult(s->{jdbc.update("UPDATE article_runtime SET callsUsed=4,remainingMs=123456 WHERE taskId='checkpoint'");jdbc.update("INSERT INTO article_review(taskId,reviewJson) VALUES ('checkpoint','{\"currentVersion\":2,\"revisionCount\":2}')");store.checkpoint(w.ticket(),"DONE","TEST_CHECKPOINT");});
    RuntimeScope.set(new RuntimeScope.Execution(w.ticket(),store,null));
    try {
     var pixels=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(pixels,"png",buffer);
     var generated=new com.yupi.template.storage.GeneratedImageStore(jdbc);var request=com.yupi.template.model.dto.image.ImageRequest.builder().prompt("persistent generated image").position(1).build();
     generated.save(generated.key("NANO_BANANA:"+com.yupi.template.utils.GsonUtils.toJson(request)),com.yupi.template.model.dto.image.ImageData.fromBytes(buffer.toByteArray(),"image/png"));
     Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/storage-blocked"),"not a directory");
     assertThrows(com.yupi.template.storage.ImageStorageException.class,()->new com.yupi.template.storage.LocalImageStorage("target/storage-blocked/images",10000).save(com.yupi.template.model.dto.image.ImageData.fromBytes(buffer.toByteArray(),"image/png"),"x"));
    } finally {RuntimeScope.clear();}
    Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/a3-kill-ready"),"checkpoint committed; process must be killed before finish");
    Thread.sleep(120000);fail("Harness failed to interrupt process at checkpoint");
   } else {
    assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM article_operation WHERE taskId='queued'",String.class));
    try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
     var one=pool.submit(()->store.claim("queued","create-request","worker-one"));var two=pool.submit(()->store.claim("queued","create-request","worker-two"));
     var results=Arrays.asList(one.get(),two.get());assertEquals(1,results.stream().filter(Objects::nonNull).count());assertEquals("TITLE",results.stream().filter(Objects::nonNull).findFirst().orElseThrow().phase());
    }
    var recovered=store.claim("checkpoint","edit-request","restarted-process");assertNotNull(recovered);assertEquals("DONE",recovered.phase());assertEquals(2,recovered.ticket().fence());
    RuntimeScope.set(new RuntimeScope.Execution(recovered.ticket(),store,null));
    try{
     var strategy=new com.yupi.template.service.ImageServiceStrategy();var imageModel=org.mockito.Mockito.mock(com.yupi.template.service.ImageSearchService.class);org.mockito.Mockito.when(imageModel.getMethod()).thenReturn(com.yupi.template.model.enums.ImageMethodEnum.NANO_BANANA);org.mockito.Mockito.when(imageModel.isAvailable()).thenReturn(true);
     var disk=new com.yupi.template.storage.LocalImageStorage("target/recovered-images",10000);
     org.springframework.test.util.ReflectionTestUtils.setField(strategy,"imageSearchServices",List.of(imageModel));org.springframework.test.util.ReflectionTestUtils.setField(strategy,"generated",new com.yupi.template.storage.GeneratedImageStore(jdbc));org.springframework.test.util.ReflectionTestUtils.setField(strategy,"imageStorage",disk);strategy.init();
     var result=strategy.getImageAndUpload("NANO_BANANA",com.yupi.template.model.dto.image.ImageRequest.builder().prompt("persistent generated image").position(1).build());assertTrue(result.isSuccess());assertTrue(disk.read(result.getUrl().substring(12)).length>0);org.mockito.Mockito.verify(imageModel,org.mockito.Mockito.never()).getImageData(org.mockito.ArgumentMatchers.any());
    }finally{RuntimeScope.clear();}
    var old=new RuntimeScope.Ticket("checkpoint","edit-request",recovered.ticket().runId(),1);
    assertEquals("FENCED",assertThrows(RuntimeStop.class,()->store.guard(old)).status);
    var staleDraft=new com.yupi.template.model.dto.article.ArticleState();staleDraft.setContent("STALE WORKER MUST NOT WRITE");
    assertEquals("FENCED",assertThrows(RuntimeStop.class,()->store.saveStage(old,staleDraft,"REVIEW","REVIEWING","BODY_SAVED")).status);
    store.stop(old,"FAILED");assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM article_operation WHERE taskId='checkpoint'",String.class));
    assertEquals(4,RuntimeStore.number(store.runtime("checkpoint"),"callsUsed"));
    assertTrue(RuntimeStore.number(store.runtime("checkpoint"),"remainingMs")<=123456);
    assertEquals("{\"currentVersion\":2,\"revisionCount\":2}",jdbc.queryForObject("SELECT reviewJson FROM article_review WHERE taskId='checkpoint'",String.class));
    var budget=store.claim("budget","budget-request","restarted-process");
    assertEquals("BUDGET_EXHAUSTED",assertThrows(RuntimeStop.class,()->context.getBean(RuntimeLedger.class).prepare(budget.ticket(),"TITLE","dashscope","{}")).status);
    assertEquals(12,RuntimeStore.number(store.runtime("budget"),"callsUsed"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_call WHERE taskId='budget'",Integer.class));store.stop(budget.ticket(),"BUDGET_EXHAUSTED");
    var deadline=store.claim("deadline","deadline-request","restarted-process");assertEquals("TIMED_OUT",assertThrows(RuntimeStop.class,()->store.guard(deadline.ticket())).status);store.stop(deadline.ticket(),"TIMED_OUT");
    assertEquals(0,RuntimeStore.number(store.runtime("deadline"),"remainingMs"));
    var articles=org.mockito.Mockito.mock(com.yupi.template.service.ArticleService.class);
    var article=new com.yupi.template.model.entity.Article();article.setTaskId("checkpoint");article.setTopic("isolated");article.setContent("saved draft");org.mockito.Mockito.when(articles.getByTaskId("checkpoint")).thenReturn(article);
    var titles=org.mockito.Mockito.mock(com.yupi.template.agent.agents.TitleGeneratorAgent.class);var outlines=org.mockito.Mockito.mock(com.yupi.template.agent.agents.OutlineGeneratorAgent.class);var body=org.mockito.Mockito.mock(com.yupi.template.agent.agents.ContentGeneratorAgent.class);var reviewer=org.mockito.Mockito.mock(com.yupi.template.agent.review.ArticleReviewLoop.class);var media=org.mockito.Mockito.mock(com.yupi.template.service.ArticleMediaProcessor.class);
    var worker=new RuntimeWorker(store,context.getBean(RuntimeConfig.class),new RuntimeExternal(context.getBean(RuntimeLedger.class),store,List.of()),articles,org.mockito.Mockito.mock(com.yupi.template.repository.ReviewTraceStore.class),titles,outlines,body,reviewer,media);
    try{worker.execute(recovered);}finally{worker.close();}
    org.mockito.Mockito.verifyNoInteractions(titles,outlines,body,reviewer,media);
assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM article_operation WHERE taskId='checkpoint'",String.class));
    assertEquals("saved draft",jdbc.queryForObject("SELECT content FROM article WHERE taskId='checkpoint'",String.class));
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM article_event WHERE taskId='checkpoint' AND type='RECOVERING'",Integer.class)>0);
   }
  }
 }
}
