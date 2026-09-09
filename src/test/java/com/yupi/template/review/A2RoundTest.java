package com.yupi.template.review;
import com.yupi.template.agent.review.*;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.yupi.template.review.A1ReviewLoopTest.*;
import static com.yupi.template.model.dto.article.ReviewResult.*;
class A2RoundTest {
    @Test void oldJsonAndNoReviewVersionRemainReadable() {
        var old="{\"schemaVersion\":1,\"status\":\"REVIEWING\",\"currentVersion\":0,\"maxRevisions\":2,\"versions\":[{\"version\":0,\"content\":\"text\"}]}";
        var trace=GsonUtils.fromJson(old,ReviewTrace.class);
        assertEquals(0,trace.round());assertEquals(0,trace.roundStartVersion());assertNull(trace.versions().getFirst().review());assertTrue(trace.humanDecisions().isEmpty());
    }
    @Test void globalVersionsAndRoundBudgetDoNotOverwriteHistory() {
        var s=state();run(s,new Fixed(review(Decision.NEEDS_REVIEW,Type.EVIDENCE_REQUIRED)));
        var original=s.getReviewTrace().versions().getFirst();
        var versions=new ArrayList<>(s.getReviewTrace().versions());versions.add(new ReviewTrace.DraftVersion(1,ORIGINAL,null));
        s.setReviewTrace(new ReviewTrace(1,"REVIEWING",1,2,null,versions,null,1,1,List.of()));
        var model=new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("先选任务。"),review(Decision.REVISE,Type.LENGTH),patch("先选一个任务。"),review(Decision.REVISE,Type.STRUCTURE));
        new ArticleReviewLoop(model).runRound(s,p->{});
        var trace=s.getReviewTrace();assertEquals("NEEDS_REVIEW",trace.status());assertEquals("REVISION_LIMIT",trace.stopReason());
        assertEquals(3,trace.currentVersion());assertEquals(2,trace.currentVersion()-trace.roundStartVersion());assertEquals(original,trace.versions().getFirst());assertEquals(5,model.prompts.size());
        assertThrows(IllegalStateException.class,()->new ArticleReviewLoop(model).runRound(s,p->{}));assertEquals(5,model.prompts.size());
    }
    @Test void humanDecisionAndOriginalIssuesSurviveNextRound() {
        var s=state();run(s,new Fixed(review(Decision.NEEDS_REVIEW,Type.EVIDENCE_REQUIRED)));
        var decision=new ReviewTrace.HumanDecision(0,1,"2026-09-09T00:00:00Z","接受风险");
        var versions=new ArrayList<>(s.getReviewTrace().versions());versions.add(new ReviewTrace.DraftVersion(1,ORIGINAL,null));
        s.setReviewTrace(new ReviewTrace(1,"REVIEWING",1,2,null,versions,null,1,1,List.of(decision)));
        new ArticleReviewLoop(new Fixed(PASS)).runRound(s,p->{});
        assertEquals("PASS",s.getReviewTrace().status());assertEquals(List.of(decision),s.getReviewTrace().humanDecisions());
        assertEquals(Decision.NEEDS_REVIEW,s.getReviewTrace().versions().getFirst().review().decision());
    }
}
