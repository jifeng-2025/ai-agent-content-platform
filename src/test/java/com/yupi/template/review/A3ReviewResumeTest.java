package com.yupi.template.review;
import com.yupi.template.agent.review.*;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.runtime.*;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.yupi.template.review.A1ReviewLoopTest.*;
import static com.yupi.template.model.dto.article.ReviewResult.*;
class A3ReviewResumeTest {
 @AfterEach void clear(){RuntimeScope.clear();}
 void scope(){RuntimeScope.set(new RuntimeScope.Execution(new RuntimeScope.Ticket("task","request","run",2),mock(RuntimeStore.class),passthrough()));}
 RuntimeExternal passthrough(){var x=mock(RuntimeExternal.class);when(x.call(any(),anyString(),anyString(),anyString(),any())).thenAnswer(c->((java.util.concurrent.Callable<String>)c.getArgument(4)).call());return x;}
 @Test void committedReviewIsReusedBeforeRevision(){
  scope();var s=state();var result=GsonUtils.fromJson(review(Decision.REVISE,Type.AUDIENCE),ReviewResult.class);
  s.setReviewTrace(new ReviewTrace(1,"REVISING",0,2,null,List.of(new ReviewTrace.DraftVersion(0,ORIGINAL,result)),result));
  var model=new Fixed(patch("先选一个任务。"),PASS);new ArticleReviewLoop(model).runRound(s,p->{});
  assertEquals("PASS",s.getReviewTrace().status());assertEquals(1,s.getReviewTrace().currentVersion());assertEquals(2,model.prompts.size());assertTrue(model.prompts.getFirst().contains("OPERATION: REVISION"));
 }
 @Test void lastVersionCannotResetRevisionBudget(){
  scope();var s=state();var result=GsonUtils.fromJson(review(Decision.REVISE,Type.STRUCTURE),ReviewResult.class);
  s.setReviewTrace(new ReviewTrace(1,"REVIEWING",2,2,null,List.of(new ReviewTrace.DraftVersion(0,ORIGINAL,null),new ReviewTrace.DraftVersion(1,ORIGINAL,null),new ReviewTrace.DraftVersion(2,ORIGINAL,result)),result));
  var model=new Fixed();new ArticleReviewLoop(model).runRound(s,p->{});
  assertEquals("REVISION_LIMIT",s.getReviewTrace().stopReason());assertEquals(2,s.getReviewTrace().currentVersion());assertEquals(0,model.prompts.size());
 }
}
