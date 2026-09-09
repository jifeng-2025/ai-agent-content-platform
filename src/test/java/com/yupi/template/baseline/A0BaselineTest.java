package com.yupi.template.baseline;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.yupi.template.agent.ArticleAgentOrchestrator;
import com.yupi.template.agent.agents.*;
import com.yupi.template.agent.config.AgentConfig;
import com.yupi.template.agent.parallel.ParallelImageGenerator;
import com.yupi.template.controller.ArticleController;
import com.yupi.template.manager.SseEmitterManager;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.entity.Article;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.ArticleStatusEnum;
import com.yupi.template.model.enums.ArticlePhaseEnum;
import com.yupi.template.service.*;
import com.yupi.template.utils.GsonUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A0 characterization: real controller/service/graphs/nodes; mock persistence, model and image I/O.
 * No Spring context, database, Redis, credentials, HTTP server, or application configuration is loaded.
 * Async proxy and actual SSE transport are outside this test's scope.
 */
class A0BaselineTest {
    record Case(String id, String topic, String style, String audience, String outcome, String reviewFocus) {}
    @TestFactory
    Stream<DynamicTest> fixedCases() throws Exception {
        try (var input = getClass().getResourceAsStream("/a0/cases.json")) {
            assertNotNull(input);
            var cases = GsonUtils.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), Case[].class);
            assertEquals(5, cases.length);
            assertEquals(5, Arrays.stream(cases).map(Case::id).distinct().count());
            return Arrays.stream(cases).map(c -> DynamicTest.dynamicTest(c.id + " " + c.topic, () -> runCase(c)));
        }
    }
    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
    private void runCase(Case c) throws Exception {
        var model = mock(DashScopeChatModel.class);
        var persistence = mock(ArticleService.class);
        var users = mock(UserService.class);
        var sse = mock(SseEmitterManager.class);
        var image = mock(ParallelImageGenerator.class);
        var config = mock(AgentConfig.class);
        when(config.isOrchestratorEnabled()).thenReturn(true);
        var legacy = mock(ArticleAgentService.class);
        var graph = new ArticleAgentOrchestrator();
        ReflectionTestUtils.setField(graph, "agentConfig", config);
        ReflectionTestUtils.setField(graph, "titleGeneratorAgent", new TitleGeneratorAgent(model));
        ReflectionTestUtils.setField(graph, "outlineGeneratorAgent", new OutlineGeneratorAgent(model));
        ReflectionTestUtils.setField(graph, "contentGeneratorAgent", new ContentGeneratorAgent(model));
        ReflectionTestUtils.setField(graph, "imageAnalyzerAgent", new ImageAnalyzerAgent(model));
        ReflectionTestUtils.setField(graph, "parallelImageGenerator", image);
        ReflectionTestUtils.setField(graph, "contentMergerAgent", new ContentMergerAgent());
        var async = new ArticleAsyncService();
        ReflectionTestUtils.setField(async, "agentConfig", config);
        ReflectionTestUtils.setField(async, "articleAgentOrchestrator", graph);
        ReflectionTestUtils.setField(async, "articleAgentService", legacy);
        ReflectionTestUtils.setField(async, "articleService", persistence);
        ReflectionTestUtils.setField(async, "sseEmitterManager", sse);
        var controller = new ArticleController();
        ReflectionTestUtils.setField(controller, "articleService", persistence);
        ReflectionTestUtils.setField(controller, "articleAsyncService", async);
        ReflectionTestUtils.setField(controller, "userService", users);
        var request = new MockHttpServletRequest();
        var user = new User(); user.setId(-1L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(persistence.createArticleTaskWithQuotaCheck(eq(c.topic), eq(c.style), anyList(), same(user))).thenReturn(c.id);
        var article = new Article();
        article.setTaskId(c.id); article.setStyle(c.style);
        article.setMainTitle(c.topic); article.setSubTitle("从一个小步骤开始");
        article.setUserDescription(c.audience); article.setEnabledImageMethods("[\"PEXELS\"]");
        when(persistence.getByTaskId(c.id)).thenReturn(article);
        var titleJson = GsonUtils.toJson(List.of(Map.of("mainTitle", c.topic, "subTitle", article.getSubTitle())));
        if (c.outcome.equals("model-error")) when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("A0 simulated provider unavailable"));
        else when(model.call(any(Prompt.class))).thenReturn(response(c.outcome.equals("malformed-json") ? "{invalid-json" : titleJson));
        var create = new ArticleCreateRequest(); create.setTopic(c.topic); create.setStyle(c.style); create.setEnabledImageMethods(List.of("PEXELS"));
        assertEquals(c.id, controller.createArticle(create, request).getData());
        verifyNoInteractions(legacy);
        var events = ArgumentCaptor.forClass(String.class);
        if (!c.outcome.equals("success")) {
            verify(persistence).updateArticleStatus(eq(c.id), eq(ArticleStatusEnum.FAILED), anyString());
            verify(persistence, never()).saveTitleOptions(anyString(), any());
            verify(persistence, never()).saveArticleContent(anyString(), any());
            verify(sse).complete(c.id);
            verify(sse, atLeastOnce()).send(eq(c.id), events.capture());
            assertTrue(events.getAllValues().stream().anyMatch(e -> e.contains("\"type\":\"ERROR\"")));
            verify(model, times(1)).call(any(Prompt.class));
            return;
        }
        var titles = ArgumentCaptor.forClass(List.class);
        verify(persistence).saveTitleOptions(eq(c.id), titles.capture());
        assertEquals(c.topic, ((ArticleState.TitleOption) titles.getValue().getFirst()).getMainTitle());
        verify(persistence).updatePhase(c.id, ArticlePhaseEnum.TITLE_SELECTING);
        var section = new ArticleState.OutlineSection(); section.setSection(1); section.setTitle("开始行动"); section.setPoints(List.of("选择一个小任务"));
        var outline = new ArticleState.OutlineResult(); outline.setSections(List.of(section));
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response(GsonUtils.toJson(outline))));
        var titleRequest = new ArticleConfirmTitleRequest(); titleRequest.setTaskId(c.id); titleRequest.setSelectedMainTitle(c.topic);
        titleRequest.setSelectedSubTitle(article.getSubTitle()); titleRequest.setUserDescription(c.audience);
        controller.confirmTitle(titleRequest, request);
        verify(persistence).confirmTitle(c.id, c.topic, article.getSubTitle(), c.audience, user);
        verify(persistence).updatePhase(c.id, ArticlePhaseEnum.OUTLINE_EDITING);
        assertEquals(1, GsonUtils.fromJson(article.getOutline(), ArticleState.OutlineSection[].class).length);
        String content = "## 开始行动\n选择一个小任务，完成后记录感受。";
        String placeholderContent = content + "\n{{IMAGE_PLACEHOLDER_1}}";
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response(content)));
        when(model.call(any(Prompt.class))).thenReturn(response(GsonUtils.toJson(Map.of(
            "contentWithPlaceholders", placeholderContent,
            "imageRequirements", List.of(Map.of("position", 1, "type", "illustration", "sectionTitle", "开始行动", "keywords", "desk", "imageSource", "PEXELS", "placeholderId", "{{IMAGE_PLACEHOLDER_1}}"))))));
        var picture = new ArticleState.ImageResult(); picture.setPosition(1); picture.setUrl("https://example.invalid/a0.png");
        picture.setMethod("PEXELS"); picture.setDescription("固定测试配图"); picture.setPlaceholderId("{{IMAGE_PLACEHOLDER_1}}");
        when(image.apply(any())).thenReturn(Map.of("images", List.of(picture)));
        var outlineRequest = new ArticleConfirmOutlineRequest(); outlineRequest.setTaskId(c.id); outlineRequest.setOutline(List.of(section));
        controller.confirmOutline(outlineRequest, request);
        verify(persistence).confirmOutline(c.id, List.of(section), user);
        var finalState = ArgumentCaptor.forClass(ArticleState.class);
        verify(persistence).saveArticleContent(eq(c.id), finalState.capture());
        assertEquals(content + "\n![固定测试配图](https://example.invalid/a0.png)", finalState.getValue().getFullContent());
        verify(persistence).updateArticleStatus(c.id, ArticleStatusEnum.COMPLETED, null);
        verify(persistence, never()).updateArticleStatus(eq(c.id), eq(ArticleStatusEnum.FAILED), any());
        verify(sse).complete(c.id);
        verify(sse, atLeastOnce()).send(eq(c.id), events.capture());
        for (String event : List.of("TITLES_GENERATED", "OUTLINE_GENERATED", "ALL_COMPLETE")) {
            assertTrue(events.getAllValues().stream().anyMatch(e -> e.contains("\"type\":\"" + event + "\"")), event);
        }
        verify(model, times(2)).call(any(Prompt.class));
        verify(model, times(2)).stream(any(Prompt.class));
        verifyNoInteractions(legacy);
    }
}