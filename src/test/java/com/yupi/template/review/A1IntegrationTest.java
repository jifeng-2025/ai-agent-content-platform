package com.yupi.template.review;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.yupi.template.agent.ArticleAgentOrchestrator;
import com.yupi.template.agent.agents.*;
import com.yupi.template.agent.config.AgentConfig;
import com.yupi.template.agent.parallel.ParallelImageGenerator;
import com.yupi.template.agent.review.*;
import com.yupi.template.manager.SseEmitterManager;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.entity.Article;
import com.yupi.template.model.enums.ArticleStatusEnum;
import com.yupi.template.service.*;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.yupi.template.review.A1ReviewLoopTest.*;
import static com.yupi.template.model.dto.article.ReviewResult.*;

class A1IntegrationTest {
    static ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    static class Harness {
        final DashScopeChatModel model=mock(DashScopeChatModel.class);
        final ArticleService store=mock(ArticleService.class);
        final SseEmitterManager sse=mock(SseEmitterManager.class);
        final ParallelImageGenerator images=mock(ParallelImageGenerator.class);
        final ArticleAgentService legacy=mock(ArticleAgentService.class);
        final ArticleAsyncService async=new ArticleAsyncService();
        final List<ReviewTrace> saved=new ArrayList<>();
        Harness(boolean loop, boolean orchestrator, Fixed gateway) throws Exception {
            var config=mock(AgentConfig.class);when(config.isReviewLoopEnabled()).thenReturn(loop);when(config.isOrchestratorEnabled()).thenReturn(orchestrator);
            var graph=new ArticleAgentOrchestrator();
            ReflectionTestUtils.setField(graph,"agentConfig",config);
            ReflectionTestUtils.setField(graph,"contentGeneratorAgent",new ContentGeneratorAgent(model));
            ReflectionTestUtils.setField(graph,"imageAnalyzerAgent",new ImageAnalyzerAgent(model));
            ReflectionTestUtils.setField(graph,"parallelImageGenerator",images);
            ReflectionTestUtils.setField(graph,"contentMergerAgent",new ContentMergerAgent());
            ReflectionTestUtils.setField(graph,"articleReviewLoop",new ArticleReviewLoop(gateway));
            ReflectionTestUtils.setField(async,"articleAgentOrchestrator",graph);
            ReflectionTestUtils.setField(async,"articleAgentService",legacy);
            ReflectionTestUtils.setField(async,"articleService",store);
            ReflectionTestUtils.setField(async,"sseEmitterManager",sse);
            ReflectionTestUtils.setField(async,"agentConfig",config);
            var s=state();var a=new Article();a.setTaskId("a1-fixed");a.setTopic(s.getTopic());a.setMainTitle(s.getTitle().getMainTitle());
            a.setUserDescription(s.getUserDescription());a.setOutline(GsonUtils.toJson(s.getOutline().getSections()));
            when(store.getByTaskId("a1-fixed")).thenReturn(a);
            doAnswer(call->{saved.add(((ArticleState)call.getArgument(1)).getReviewTrace());return null;}).when(store).saveReviewProgress(anyString(),any());
            when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response(ORIGINAL)));
            when(model.call(any(Prompt.class))).thenAnswer(call->{
                // Image analysis must receive the final reviewed text, and preserves it in this fixed response.
                String prompt=((Prompt)call.getArgument(0)).getContents();
                String text=prompt.contains("选择一个日常小任务。")?ORIGINAL.replace("选择任务。","选择一个日常小任务。"):ORIGINAL;
                return response(GsonUtils.toJson(Map.of("contentWithPlaceholders",text,"imageRequirements",List.of())));
            });
            when(images.apply(any())).thenReturn(Map.of("images",List.of()));
        }
        List<String> events() {
            var captor=ArgumentCaptor.forClass(String.class);verify(sse,atLeastOnce()).send(eq("a1-fixed"),captor.capture());return captor.getAllValues();
        }
    }
    @Test void approvedRevisionReachesImagesAndMergeAndWriterReceivesGoal() throws Exception {
        var m=new Fixed(review(Decision.REVISE,Type.AUDIENCE),patch("选择一个日常小任务。"),PASS);
        var h=new Harness(true,true,m);h.async.executePhase3("a1-fixed");
        var saved=ArgumentCaptor.forClass(ArticleState.class);verify(h.store).saveArticleContent(eq("a1-fixed"),saved.capture());
        assertEquals(ORIGINAL.replace("选择任务。","选择一个日常小任务。"),saved.getValue().getFullContent());
        var imagePrompt=ArgumentCaptor.forClass(Prompt.class);verify(h.model).call(imagePrompt.capture());
        assertTrue(imagePrompt.getValue().getContents().contains("选择一个日常小任务。"));
        var writerPrompt=ArgumentCaptor.forClass(Prompt.class);verify(h.model).stream(writerPrompt.capture());
        assertTrue(writerPrompt.getValue().getContents().contains(state().getUserDescription()));
        assertTrue(writerPrompt.getValue().getContents().contains(state().getTopic()));
        assertEquals("PASS",h.saved.getLast().status());
        verify(h.store).updateArticleStatus("a1-fixed",ArticleStatusEnum.COMPLETED,null);verify(h.sse).complete("a1-fixed");
        assertTrue(h.events().stream().anyMatch(e->e.contains("\"type\":\"ALL_COMPLETE\"")));
    }
    @Test void humanTerminalPersistsBeforeEventAndNeverGeneratesImagesOrCompletes() throws Exception {
        var h=new Harness(true,true,new Fixed(review(Decision.NEEDS_REVIEW,Type.EVIDENCE_REQUIRED)));h.async.executePhase3("a1-fixed");
        verifyNoInteractions(h.images);verify(h.model,never()).call(any(Prompt.class));
        verify(h.store,never()).saveArticleContent(anyString(),any());
        verify(h.store,never()).updateArticleStatus(anyString(),eq(ArticleStatusEnum.COMPLETED),any());
        assertEquals("NEEDS_REVIEW",h.saved.getLast().status());assertEquals(ORIGINAL,h.saved.getLast().versions().getFirst().content());
        var ordered=inOrder(h.store,h.sse);
        ordered.verify(h.store,atLeastOnce()).saveReviewProgress(eq("a1-fixed"),any());
        ordered.verify(h.sse).send(eq("a1-fixed"),contains("\"type\":\"NEEDS_REVIEW\""));ordered.verify(h.sse).complete("a1-fixed");
        assertFalse(h.events().stream().anyMatch(e->e.contains("ALL_COMPLETE")));
    }
    @Test void flagOffUsesOriginalGraphWithoutReviewStorage() throws Exception {
        var m=new Fixed();var h=new Harness(false,true,m);h.async.executePhase3("a1-fixed");
        assertTrue(m.prompts.isEmpty());verify(h.store,never()).saveReviewProgress(anyString(),any());
        verify(h.images).apply(any());verify(h.store).saveArticleContent(anyString(),any());
        verify(h.store).updateArticleStatus("a1-fixed",ArticleStatusEnum.COMPLETED,null);
    }
    @Test void legacySwitchStillRoutesToLegacyEvenWithLoopFlag() throws Exception {
        var m=new Fixed();var h=new Harness(true,false,m);h.async.executePhase3("a1-fixed");
        verify(h.legacy).executePhase3_GenerateContent(any(),any());verifyNoInteractions(h.model,h.images);
        assertTrue(m.prompts.isEmpty());verify(h.store,never()).saveReviewProgress(anyString(),any());
    }
    @Test void storageFailureCannotAnnounceSuccess() throws Exception {
        var h=new Harness(true,true,new Fixed(PASS));
        doThrow(new IllegalStateException("save failed")).when(h.store).saveReviewProgress(anyString(),any());
        h.async.executePhase3("a1-fixed");verifyNoInteractions(h.images);
        verify(h.store).updateArticleStatus(eq("a1-fixed"),eq(ArticleStatusEnum.FAILED),anyString());
        assertFalse(h.events().stream().anyMatch(e->e.contains("ALL_COMPLETE")));verify(h.sse).complete("a1-fixed");
    }
    @Test void gatewayTimeoutIsBoundedAndClassified() {
        var model=mock(DashScopeChatModel.class);when(model.stream(any(Prompt.class))).thenReturn(Flux.never());
        var gateway=new DashScopeReviewModelGateway(model,20);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->{
            var failure=assertThrows(ReviewModelGateway.CallFailure.class,()->gateway.complete("test"));assertTrue(failure.isTimeout());
        });
    }
    @Test void gatewayModelExceptionDoesNotExposeProviderMessage() {
        var model=mock(DashScopeChatModel.class);when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("secret-provider-detail")));
        var failure=assertThrows(ReviewModelGateway.CallFailure.class,()->new DashScopeReviewModelGateway(model,100).complete("test"));
        assertFalse(failure.isTimeout());assertFalse(failure.getMessage().contains("secret"));
    }
    @Test void disconnectedSseCannotOverwriteDurableHumanStatus() throws Exception {
        var h=new Harness(true,true,new Fixed(review(Decision.NEEDS_REVIEW,Type.EVIDENCE_REQUIRED)));
        doAnswer(call->{
            String event=call.getArgument(1);
            if (event.contains("REVIEW_UPDATED") || event.contains("NEEDS_REVIEW")) throw new IllegalStateException("closed transport");
            return null;
        }).when(h.sse).send(anyString(),anyString());
        h.async.executePhase3("a1-fixed");
        assertEquals("NEEDS_REVIEW",h.saved.getLast().status());
        verify(h.store,never()).updateArticleStatus(anyString(),eq(ArticleStatusEnum.FAILED),any());
        verify(h.store,never()).saveArticleContent(anyString(),any());verifyNoInteractions(h.images);verify(h.sse).complete("a1-fixed");
    }
    @Test void defaultFlagIsExplicitlyOff() throws Exception {
        var annotation=AgentConfig.class.getDeclaredField("reviewLoopEnabled").getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        assertEquals("${article.agent.review-loop.enabled:false}",annotation.value());
    }
}
