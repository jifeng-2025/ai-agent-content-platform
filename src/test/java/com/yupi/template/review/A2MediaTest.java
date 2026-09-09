package com.yupi.template.review;
import com.yupi.template.agent.agents.ImageAnalyzerAgent;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.repository.ArticleMediaStore;
import com.yupi.template.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.yupi.template.review.A1ReviewLoopTest.*;
class A2MediaTest {
    static class Harness {
        ImageAnalyzerAgent analyzer=mock(ImageAnalyzerAgent.class);
        ImageGenerationTool tool=mock(ImageGenerationTool.class);
        ArticleMediaStore store=mock(ArticleMediaStore.class);
        ArticleInterventionService interventions=mock(ArticleInterventionService.class);
        AtomicReference<MediaState> saved=new AtomicReference<>();
        ArticleMediaProcessor processor=new ArticleMediaProcessor(analyzer,tool,store,interventions);
        Harness(int count) throws Exception {
            var requirements=new ArrayList<ArticleState.ImageRequirement>();
            for(int i=1;i<=count;i++){var r=new ArticleState.ImageRequirement();r.setImageSource("PEXELS");r.setPosition(i);r.setKeywords("fixed");r.setSectionTitle("开始行动");requirements.add(r);}
            when(analyzer.apply(any())).thenReturn(Map.of("imageRequirements",requirements,"contentWithPlaceholders","UNWANTED REWRITE"));
            when(interventions.media(any())).thenAnswer(c->saved.get());
            doAnswer(c->{saved.set(c.getArgument(1));return null;}).when(store).save(any(),any(),anyString());
        }
    }
    static ImageGenerationTool.ImageGenerationResult image(boolean success,String method) {
        var r=new ImageGenerationTool.ImageGenerationResult();r.setSuccess(success);r.setMethod(method);r.setUrl("https://example.invalid/fixed.png");return r;
    }
    @Test void retryOnlyTargetsOneImageAndDoesNotRewriteLiteralMarkers() throws Exception {
        var h=new Harness(2);var n=new AtomicInteger();when(h.tool.generateImageDirect(any(),any(),any(),any(),any(),any(),any())).thenAnswer(c->{int i=n.incrementAndGet();return image(i!=1,i==2?"PICSUM":"PEXELS");});
        var s=state();s.setContent(ORIGINAL+"\n字面值 {{A2_IMAGE_1}} 不应被替换。");String body=s.getContent();
        h.processor.generate(s,e->{});assertEquals("IMAGES_FAILED",s.getPhase());assertEquals(body,h.saved.get().template());
        var other=h.saved.get().slots().get(1);h.processor.retry(s,"image-1",e->{});
        assertEquals(3,n.get());assertSame(other,h.saved.get().slots().get(1));assertEquals(body,s.getContent());assertTrue(s.getFullContent().startsWith(body));
        assertEquals("COMPLETED",s.getPhase());assertTrue(s.getFullContent().contains("占位或降级图片"));verify(h.analyzer,times(1)).apply(any());
    }
    @Test void failedRetryRetainsPriorImageAndDegradationLabel() throws Exception {
        var h=new Harness(1);when(h.tool.generateImageDirect(any(),any(),any(),any(),any(),any(),any())).thenReturn(image(true,"PICSUM"),image(false,"PEXELS"));
        var s=state();h.processor.generate(s,e->{});String url=h.saved.get().slots().getFirst().result().getUrl();h.processor.retry(s,"image-1",e->{});
        assertEquals("IMAGES_FAILED",s.getPhase());assertEquals(url,h.saved.get().slots().getFirst().result().getUrl());assertTrue(s.getFullContent().contains("占位或降级"));assertTrue(s.getFullContent().contains("保留上次图片"));
    }
    @Test void excessivePlanCannotCallImageProvider() throws Exception {
        var h=new Harness(9);assertThrows(IllegalStateException.class,()->h.processor.generate(state(),e->{}));verifyNoInteractions(h.tool,h.store);
    }
    @Test void disallowedMethodCannotCallProvider() throws Exception {
        var h=new Harness(1);var s=state();s.setEnabledImageMethods(List.of("ICONIFY"));assertThrows(IllegalStateException.class,()->h.processor.generate(s,e->{}));verifyNoInteractions(h.tool,h.store);
    }
}
