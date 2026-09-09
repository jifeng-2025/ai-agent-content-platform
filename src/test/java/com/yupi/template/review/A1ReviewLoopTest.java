package com.yupi.template.review;

import com.yupi.template.agent.review.*;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.yupi.template.model.dto.article.ReviewResult.*;

class A1ReviewLoopTest {
    static final String ORIGINAL = "## 开始行动\n\n选择任务。\n\n记录感受。";
    static final String PASS = "{\"schemaVersion\":1,\"decision\":\"PASS\",\"issues\":[]}";
    static ArticleState state() {
        var s = new ArticleState(); s.setTaskId("a1-fixed"); s.setTopic("任务入门");
        s.setUserDescription("面向非技术新手，补充要求：给出可操作建议");
        var title = new ArticleState.TitleResult(); title.setMainTitle("任务入门"); s.setTitle(title);
        var section = new ArticleState.OutlineSection(); section.setTitle("开始行动"); section.setSection(1); section.setPoints(List.of("选择任务"));
        var outline = new ArticleState.OutlineResult(); outline.setSections(List.of(section)); s.setOutline(outline);
        s.setContent(ORIGINAL); return s;
    }
    static String review(Decision decision, Type... types) {
        return GsonUtils.toJson(new ReviewResult(1, decision, Arrays.stream(types)
                .map(t -> new Issue(t, Severity.ERROR, "p2", "固定问题：" + t, "局部改写")).toList()));
    }
    static String patch(String text) { return patch("p2", text); }
    static String patch(String id, String text) {
        return GsonUtils.toJson(Map.of("replacements", List.of(Map.of("sectionId", id, "text", text))));
    }
    static class Fixed implements ReviewModelGateway {
        final Queue<Object> answers = new ArrayDeque<>(); final List<String> prompts = new ArrayList<>();
        Fixed(Object... answers) { this.answers.addAll(List.of(answers)); }
        public String complete(String prompt) {
            prompts.add(prompt); Object answer = answers.remove();
            if (answer instanceof RuntimeException e) throw e;
            return (String)answer;
        }
    }
    static ReviewTrace run(ArticleState state, Fixed model) {
        new ArticleReviewLoop(model).run(state, ignored -> {}); return state.getReviewTrace();
    }
    @Test void firstPass() {
        var m = new Fixed(PASS); var trace = run(state(),m);
        assertEquals("PASS", trace.status()); assertEquals(0,trace.currentVersion()); assertEquals(1,m.prompts.size());
    }
    @Test void oneRevisionAndReproducibleTrace() throws Exception {
        var s = state(); var m = new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("选择一个日常小任务，先完成第一步。"),PASS);
        var trace = run(s,m);
        assertEquals("PASS",trace.status()); assertEquals(1,trace.currentVersion());
        assertEquals(ORIGINAL,trace.versions().getFirst().content());
        assertEquals("## 开始行动\n\n选择一个日常小任务，先完成第一步。\n\n记录感受。",s.getContent());
        assertEquals(Decision.REVISE,trace.versions().getFirst().review().decision());
        for (String prompt:m.prompts) { assertTrue(prompt.contains(s.getUserDescription())); assertTrue(prompt.contains("开始行动")); }
        assertFalse(m.prompts.get(1).contains("versions"));
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/a1-example.json"),GsonUtils.toJson(trace));
    }
    @Test void twoRevisionsHardLimit() {
        var m = new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("先选择任务。"),
                review(Decision.REVISE,Type.LENGTH),patch("先选择一个日常任务。"),review(Decision.REVISE,Type.STRUCTURE),"must not call");
        var t=run(state(),m); assertEquals("NEEDS_REVIEW",t.status()); assertEquals("REVISION_LIMIT",t.stopReason());
        assertEquals(2,t.currentVersion()); assertEquals(List.of(0,1,2),t.versions().stream().map(ReviewTrace.DraftVersion::version).toList());
        assertEquals(5,m.prompts.size()); assertEquals(2,t.maxRevisions());
    }
    @Test void evidenceRequiresHumanEvenWhenModelRequestsRevision() {
        var m=new Fixed(review(Decision.REVISE,Type.EVIDENCE_REQUIRED)); var t=run(state(),m);
        assertEquals("NEEDS_REVIEW",t.status()); assertEquals(0,t.currentVersion()); assertEquals(1,m.prompts.size());
    }
    @Test void explicitHuman() {
        assertEquals("HUMAN_REQUIRED",run(state(),new Fixed(review(Decision.NEEDS_REVIEW,Type.UNSUPPORTED_ATTRIBUTION))).stopReason());
    }
    @Test void repeatedIssuesStopsAfterOneAttempt() {
        var m=new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("先选任务。"),review(Decision.REVISE,Type.AUDIENCE));
        var t=run(state(),m); assertEquals("REPEATED_ISSUES",t.stopReason()); assertEquals(1,t.currentVersion()); assertEquals(3,m.prompts.size());
    }
    @Test void noOpDoesNotInventVersion() {
        var t=run(state(),new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("选择任务。")));
        assertEquals("NO_PROGRESS",t.stopReason()); assertEquals(0,t.currentVersion());
    }
    @TestFactory Stream<DynamicTest> invalidReviewOutputs() {
        return Stream.of(review(Decision.REVISE,Type.MODEL_ERROR),"broken", "```json\n"+PASS+"\n```", PASS.replace("PASS","UNKNOWN"),
                "{\"decision\":\"PASS\",\"issues\":[]}", PASS.replace("1,","4294967297,"),
                PASS.replace("1,","\"1\","),PASS+" {}",PASS.replace("\"decision\"","\"schemaVersion\":1,\"decision\""),
                PASS.replace("PASS","REVISE"),review(Decision.REVISE,Type.AUDIENCE).replace("p2","p99"),
                review(Decision.REVISE,Type.AUDIENCE).replace("\"suggestedAction\":\"局部改写\"","\"extra\":1"),
                PASS.replace("[]","null"),PASS.replace("1,","1,\"maxRevisions\":99,"))
                .map(raw -> DynamicTest.dynamicTest("invalid " + raw, () -> {
                    var t=run(state(),new Fixed(raw)); assertEquals("NEEDS_REVIEW",t.status());
                    assertEquals(Type.OUTPUT_ERROR,t.review().issues().getFirst().type()); assertEquals(0,t.currentVersion());
                }));
    }
    @TestFactory Stream<DynamicTest> invalidPatchesCannotRewriteUnrelatedParagraphs() {
        return Stream.of(patch("p3","无关重写"),patch("p2","插入\n\n新段"),patch("p2","## 新标题"),
                patch("p2","尾部\n"),"{\"replacements\":[]}","bad")
                .map(raw->DynamicTest.dynamicTest(raw,()->{
                    var s=state();var t=run(s,new Fixed(review(Decision.REVISE,Type.AUDIENCE),raw));
                    assertEquals("REVISION_FAILURE",t.stopReason());assertEquals(ORIGINAL,s.getContent());assertEquals(0,t.currentVersion());
                }));
    }
    @Test void separatorsAndUnrelatedParagraphsRemainExact() {
        String text="## 标题\r\n \r\n旧段\r\n\r\n保持  两空格。\r\n";
        assertEquals(text.replace("旧段","新段"),new ParagraphDraft(text).apply(patch("新段"),Set.of("p2")));
    }
    @TestFactory Stream<DynamicTest> modelFailuresRetainDraft() {
        return Stream.of(false,true).flatMap(timeout -> Stream.of(false,true).map(revision -> DynamicTest.dynamicTest("timeout="+timeout+",revision="+revision,()->{
            var failure=new ReviewModelGateway.CallFailure(timeout);
            var m=revision?new Fixed(review(Decision.REVISE,Type.AUDIENCE),failure):new Fixed(failure);
            var s=state();var t=run(s,m);assertEquals("NEEDS_REVIEW",t.status()); assertEquals(ORIGINAL,s.getContent());
            assertEquals(timeout?Type.MODEL_TIMEOUT:Type.MODEL_ERROR,t.review().issues().getFirst().type());
        })));
    }
    @Test void persistenceFailurePropagatesBeforeAnyModelCall() {
        var m=new Fixed(PASS);
        assertThrows(IllegalStateException.class,()->new ArticleReviewLoop(m).run(state(),s->{throw new IllegalStateException("disk");}));
        assertTrue(m.prompts.isEmpty());
    }
    @TestFactory Stream<DynamicTest> deterministicChecksCannotBeWaivedByModel() {
        return Stream.of(Map.entry("RAG很简单。",Type.AUDIENCE),Map.entry("效率提高80%。",Type.UNSUPPORTED_NUMBER),
                Map.entry("根据某机构报告可以成功。",Type.UNSUPPORTED_ATTRIBUTION))
                .map(c->DynamicTest.dynamicTest(c.getKey(),()->{
                    var s=state();s.setContent(ORIGINAL.replace("选择任务。",c.getKey()));
                    var m=new Fixed(PASS,patch("选择一个任务。"),PASS);var t=run(s,m);
                    assertEquals(c.getValue(),t.versions().getFirst().review().issues().getFirst().type());
                    assertEquals("PASS",t.status());assertEquals(1,t.currentVersion());
                }));
    }
    @Test void lengthAndOutlineMismatchGoToHumanWithoutGlobalRewrite() {
        var s=state();s.setUserDescription("至少1000字"); var m=new Fixed();
        assertEquals(Type.LENGTH,run(s,m).review().issues().getFirst().type());
        s=state();s.setContent("没有章节");
        assertEquals(Type.STRUCTURE,run(s,m).review().issues().getFirst().type());assertTrue(m.prompts.isEmpty());
    }
}
