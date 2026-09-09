package com.yupi.template.intervention;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.*;
import com.yupi.template.agent.review.ReviewModelGateway;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.service.CosService;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real HTTP/controllers/login/Redis sessions/MySQL transactions; only cloud and object storage are mocked. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT, properties={
    "spring.profiles.active=a2-isolated", "server.port=8567", "article.agent.orchestrator.enabled=true",
    "article.agent.review-loop.enabled=true", "article.agent.intervention.enabled=true"})
@EnabledIfEnvironmentVariable(named="A2_ISOLATED",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class A2HttpIT {
    static final List<String> failures=new java.util.concurrent.CopyOnWriteArrayList<>();
    @org.junit.jupiter.api.extension.RegisterExtension
    static final org.junit.jupiter.api.extension.TestWatcher watcher=new org.junit.jupiter.api.extension.TestWatcher() {
        public void testFailed(org.junit.jupiter.api.extension.ExtensionContext context,Throwable cause) { failures.add(context.getDisplayName()); }
    };
    @Autowired JdbcTemplate jdbc;
    @Autowired com.yupi.template.service.ArticleService articles;
    @MockitoBean(reset=org.springframework.test.context.bean.override.mockito.MockReset.NONE) DashScopeChatModel model;
    @MockitoBean(reset=org.springframework.test.context.bean.override.mockito.MockReset.NONE) ImageGenerationTool imageTool;
    @MockitoBean(reset=org.springframework.test.context.bean.override.mockito.MockReset.NONE) ReviewModelGateway reviewer;
    @MockitoBean(reset=org.springframework.test.context.bean.override.mockito.MockReset.NONE) CosService cos;
    final Map<String,AtomicInteger> imageCalls=new ConcurrentHashMap<>();
    HttpClient owner,other;long ownerId;
    static final String BODY="## 开始行动\n\n选择一个任务，记录自己的感受。\n\n保留这一段，不要重写。";
    static final String PASS="{\"schemaVersion\":1,\"decision\":\"PASS\",\"issues\":[]}";
    static HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();}
    JsonObject call(HttpClient client,String method,String path,Object body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:8567/api"+path)).timeout(Duration.ofSeconds(15));
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(GsonUtils.toJson(body))).header("Content-Type","application/json");
        return JsonParser.parseString(client.send(b.build(),HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }
    JsonObject data(HttpClient c,String path) throws Exception { var response=call(c,"GET",path,null);assertEquals(0,response.get("code").getAsInt(),response.toString());return response.getAsJsonObject("data"); }
    String endpoint(String id){return "/article/"+id+"/interventions";}
    @BeforeAll void setup() throws Exception {
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var p=((Prompt)call.getArgument(0)).getContents();var matcher=java.util.regex.Pattern.compile("A2 ([a-z0-9-]+)").matcher(p);String task=matcher.find()?matcher.group(1):"unknown";
            var requests=new ArrayList<Map<String,Object>>();for(int i=1;i<=2;i++)requests.add(Map.of("position",i,"type","section","sectionTitle","开始行动","keywords",task+(i==1?"-first":"-second"),"imageSource","PEXELS","placeholderId","{{OLD_"+i+"}}"));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(GsonUtils.toJson(Map.of("contentWithPlaceholders","MODEL MUST NOT REWRITE BODY","imageRequirements",requests))))));
        });
        when(imageTool.generateImageDirect(any(),any(),any(),any(),any(),any(),any())).thenAnswer(call->{
            String key=call.getArgument(1);int count=imageCalls.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
            var r=new ImageGenerationTool.ImageGenerationResult();r.setPosition(call.getArgument(3));r.setPlaceholderId(call.getArgument(6));
            r.setSuccess(!(key.endsWith("-first")&&count==1));r.setUrl(System.getenv("A2_IMAGE_ORIGIN")+"/a2-image.svg");r.setMethod(key.endsWith("-second")&&count==1?"PICSUM":"PEXELS");return r;
        });
        when(reviewer.complete(anyString())).thenAnswer(call->{
            String p=call.getArgument(0);Thread.sleep(Long.parseLong(System.getenv().getOrDefault("A2_REVIEW_DELAY_MS","200")));
            if(p.contains("OPERATION: REVISION"))return "{\"replacements\":[{\"sectionId\":\"p2\",\"text\":\"选择一个日常小任务，从第一步开始。\"}]}";
            if(p.contains("EVIDENCE_MARKER"))return review("NEEDS_REVIEW","EVIDENCE_REQUIRED");
            if(p.contains("REVISE_ME"))return review("REVISE","AUDIENCE");return PASS;
        });
        owner=client();other=client();
        for(var entry:Map.of("a2owner",owner,"a2other",other).entrySet()) {
            var registration=call(entry.getValue(),"POST","/user/register",Map.of("userAccount",entry.getKey(),"userPassword","a2-local-only","checkPassword","a2-local-only"));assertEquals(0,registration.get("code").getAsInt(),registration.toString());
            var login=call(entry.getValue(),"POST","/user/login",Map.of("userAccount",entry.getKey(),"userPassword","a2-local-only"));assertEquals(0,login.get("code").getAsInt(),login.toString());
            if(entry.getKey().equals("a2owner"))ownerId=login.getAsJsonObject("data").get("id").getAsLong();
        }
    }
    static String review(String decision,String type){return "{\"schemaVersion\":1,\"decision\":\""+decision+"\",\"issues\":[{\"type\":\""+type+"\",\"severity\":\"ERROR\",\"sectionId\":\"p2\",\"reason\":\"固定评审问题，事实尚未核查\",\"suggestedAction\":\"补充表达或人工判断\"}]}";}
    void seed(String id,boolean old) {
        jdbc.update("INSERT INTO article(taskId,userId,topic,mainTitle,subTitle,outline,content,fullContent,status,phase,userDescription,enabledImageMethods) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            id,ownerId,"任务入门","A2 "+id,"隔离联调固定案例","[{\"section\":1,\"title\":\"开始行动\",\"points\":[\"选择任务\"]}]",BODY,BODY,old?"COMPLETED":"NEEDS_REVIEW",old?"COMPLETED":"NEEDS_REVIEW","面向新手，解释清楚行动步骤","[\"PEXELS\"]");
        if(!old){var r=GsonUtils.fromJson(review("NEEDS_REVIEW","EVIDENCE_REQUIRED"),ReviewResult.class);var t=new ReviewTrace(1,"NEEDS_REVIEW",0,2,"HUMAN_REQUIRED",List.of(new ReviewTrace.DraftVersion(0,BODY,r)),r);jdbc.update("INSERT INTO article_review(taskId,reviewJson) VALUES (?,?)",id,GsonUtils.toJson(t));}
    }
    Map<String,Object> request(String id,String action) throws Exception {
        var v=data(owner,endpoint(id));var body=new HashMap<String,Object>();body.put("requestId",UUID.randomUUID().toString());body.put("action",action);body.put("expectedVersion",v.getAsJsonObject("reviewTrace").get("currentVersion").getAsInt());body.put("expectedRevision",v.get("revision").getAsLong());body.put("acknowledgeRisks",true);return body;
    }
    JsonObject awaitTerminal(String id) throws Exception {
        for(int i=0;i<100;i++){var view=data(owner,endpoint(id));if(!view.get("articleStatus").getAsString().equals("PROCESSING"))return view;Thread.sleep(100);}throw new AssertionError("Task did not terminate: "+id);
    }
    @Test @Order(1) void unauthorizedAndOwnershipAreEnforcedOverHttp() throws Exception {
        seed("auth-case",false);assertEquals(40100,call(client(),"GET",endpoint("auth-case"),null).get("code").getAsInt());
        assertEquals(40101,call(other,"GET",endpoint("auth-case"),null).get("code").getAsInt());
        assertEquals(40101,call(other,"POST",endpoint("auth-case"),request("auth-case","ACCEPT")).get("code").getAsInt());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId='auth-case'",Integer.class));
    }
    @Test @Order(2) void concurrentAcceptIsIdempotentAndDoesNotRunWriter() throws Exception {
        seed("accept-case",false);var body=request("accept-case","ACCEPT");
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->call(owner,"POST",endpoint("accept-case"),body));var b=pool.submit(()->call(owner,"POST",endpoint("accept-case"),body));
            assertEquals(0,a.get().get("code").getAsInt());assertEquals(0,b.get().get("code").getAsInt());
        }
        var v=awaitTerminal("accept-case");assertEquals("IMAGES_FAILED",v.get("articleStatus").getAsString());
        assertEquals("HUMAN_ACCEPTED",v.getAsJsonObject("reviewTrace").get("status").getAsString());assertEquals(1,v.getAsJsonObject("reviewTrace").getAsJsonArray("humanDecisions").size());
        assertEquals("NEEDS_REVIEW",v.getAsJsonObject("reviewTrace").getAsJsonObject("review").get("decision").getAsString());
        assertEquals(BODY,data(owner,"/article/accept-case").get("content").getAsString());assertEquals(1,imageCalls.get("accept-case-first").get());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId='accept-case'",Integer.class));verify(model,never()).stream(any(Prompt.class));
        assertTrue(call(owner,"POST",endpoint("accept-case"),body).getAsJsonObject("data").get("replayed").getAsBoolean());
    }
    @Test @Order(3) void oneImageRetryPreservesOtherImageAndBody() throws Exception {
        var before=data(owner,endpoint("accept-case"));var second=before.getAsJsonObject("media").getAsJsonArray("slots").get(1).deepCopy();
        assertEquals("DEGRADED",second.getAsJsonObject().get("status").getAsString());
        var body=request("accept-case","RETRY_IMAGE");body.put("imageId","image-1");assertEquals(0,call(owner,"POST",endpoint("accept-case"),body).get("code").getAsInt());
        var after=awaitTerminal("accept-case");assertEquals("COMPLETED",after.get("articleStatus").getAsString());assertEquals(second,after.getAsJsonObject("media").getAsJsonArray("slots").get(1));
        assertEquals(BODY,data(owner,"/article/accept-case").get("content").getAsString());assertEquals(2,imageCalls.get("accept-case-first").get());assertEquals(1,imageCalls.get("accept-case-second").get());
        assertTrue(data(owner,"/article/accept-case").get("fullContent").getAsString().contains("占位或降级图片"));
    }
    @Test @Order(4) void conflictingAndChangedDuplicateRequestsCannotDispatch() throws Exception {
        seed("conflict-case",false);var body=request("conflict-case","ACCEPT");var second=new HashMap<>(body);second.put("requestId",UUID.randomUUID().toString());
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->call(owner,"POST",endpoint("conflict-case"),body));var b=pool.submit(()->call(owner,"POST",endpoint("conflict-case"),second));
            var codes=List.of(a.get().get("code").getAsInt(),b.get().get("code").getAsInt());assertTrue(codes.contains(0));assertTrue(codes.contains(40900));}
        awaitTerminal("conflict-case");var stale=request("conflict-case","RETRY_IMAGE");stale.put("imageId","image-1");stale.put("expectedRevision",-1);assertEquals(40900,call(owner,"POST",endpoint("conflict-case"),stale).get("code").getAsInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId='conflict-case'",Integer.class));
    }
    @Test @Order(5) void editedRoundAppendsVersionsAndDuplicateCannotResetBudget() throws Exception {
        seed("edit-case",false);var body=request("edit-case","EDIT_REVIEW");body.put("content",BODY.replace("选择一个任务，记录自己的感受。","REVISE_ME 请解释得更清楚。"));
        assertEquals(0,call(owner,"POST",endpoint("edit-case"),body).get("code").getAsInt());var view=awaitTerminal("edit-case");var trace=view.getAsJsonObject("reviewTrace");
        assertEquals("PASS",trace.get("status").getAsString());assertEquals(2,trace.get("currentVersion").getAsInt());assertEquals(1,trace.get("roundStartVersion").getAsInt());assertEquals(3,trace.getAsJsonArray("versions").size());
        assertEquals(BODY,trace.getAsJsonArray("versions").get(0).getAsJsonObject().get("content").getAsString());
        assertTrue(call(owner,"POST",endpoint("edit-case"),body).getAsJsonObject("data").get("replayed").getAsBoolean());assertEquals(2,data(owner,endpoint("edit-case")).getAsJsonObject("reviewTrace").get("currentVersion").getAsInt());
        var changed=new HashMap<>(body);changed.put("content",BODY+" changed");assertEquals(40900,call(owner,"POST",endpoint("edit-case"),changed).get("code").getAsInt());
    }
    @Test @Order(6) void noOpAndMissingAcknowledgementAreRejected() throws Exception {
        seed("validation-case",false);var body=request("validation-case","EDIT_REVIEW");body.put("content",BODY+"  \n");assertEquals(40900,call(owner,"POST",endpoint("validation-case"),body).get("code").getAsInt());
        body=request("validation-case","ACCEPT");body.put("acknowledgeRisks",false);assertEquals(40900,call(owner,"POST",endpoint("validation-case"),body).get("code").getAsInt());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_operation WHERE taskId='validation-case'",Integer.class));
    }
    @Test @Order(7) void oldArticleAndRefreshUseDurableInterfaces() throws Exception {
        seed("old-case",true);var view=data(owner,endpoint("old-case"));assertFalse(view.has("reviewTrace")&&!view.get("reviewTrace").isJsonNull());assertTrue(view.getAsJsonArray("allowedActions").isEmpty());
        var refreshed=data(owner,endpoint("edit-case"));assertEquals(3,refreshed.getAsJsonObject("reviewTrace").getAsJsonArray("versions").size());
        assertEquals(BODY,data(owner,"/article/old-case").get("content").getAsString());
    }
    @Test @Order(8) void schemaMigrationRetainsSyntheticExistingRow() {
        assertEquals("preserved synthetic data",jdbc.queryForObject("SELECT content FROM article WHERE taskId='migration-preserve'",String.class));
        assertEquals("mediumtext",jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article' AND COLUMN_NAME='content'",String.class));
    }
    @Test @Order(9) void jdbcAndMapperRollbackTogether() {
        var before=articles.getByTaskId("migration-preserve");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new com.mybatisflex.spring.FlexTransactionManager());
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            jdbc.update("UPDATE article SET content='temporary JDBC write' WHERE taskId='migration-preserve'");
            var patch=new com.yupi.template.model.entity.Article();patch.setId(before.getId());patch.setTopic("temporary mapper write");articles.updateById(patch);
            throw new IllegalStateException("rollback both stores");
        }));
        var after=articles.getByTaskId("migration-preserve");assertEquals(before.getContent(),after.getContent());assertEquals(before.getTopic(),after.getTopic());
    }
    @AfterAll void browserFixtures() throws Exception {
        for(String id:List.of("browser-human","browser-edit","browser-long","browser-old"))seed(id,id.equals("browser-old"));
        String longText=BODY+"\n\n"+"这是较长的正文，保持可读的段落间距和清晰的任务边界。\n\n".repeat(60);
        jdbc.update("UPDATE article SET content=?,fullContent=? WHERE taskId='browser-long'",longText,longText);
        var r=GsonUtils.fromJson(review("NEEDS_REVIEW","EVIDENCE_REQUIRED"),ReviewResult.class);
        var trace=new ReviewTrace(1,"NEEDS_REVIEW",1,2,"HUMAN_REQUIRED",List.of(new ReviewTrace.DraftVersion(0,BODY,null),new ReviewTrace.DraftVersion(1,longText,r)),r);
        jdbc.update("UPDATE article_review SET reviewJson=? WHERE taskId='browser-long'",GsonUtils.toJson(trace));
        Files.writeString(Path.of("target/a2-http-ready.json"),GsonUtils.toJson(Map.of("status","HTTP_TESTS_FINISHED","failedTests",failures,"login","a2owner","password","a2-local-only","fixtures",List.of("browser-human","browser-edit","browser-long","browser-old"))));
        if("true".equals(System.getenv("A2_BROWSER_HOLD"))) {
            long deadline=System.currentTimeMillis()+1800000;
            while(!Files.exists(Path.of("target/a2-browser-stop")) && System.currentTimeMillis()<deadline)Thread.sleep(500);
        }
    }
}
