package com.yupi.template.review;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.service.*;
import com.yupi.template.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class A4DemoMediaTest {
 @TempDir Path root;
 @Test void explicitDemoUsesRealToolPreservesTextAndMarksDegradedOnRetry() throws Exception {
  var h=new A2MediaTest.Harness(1);var local=new LocalImageStorage(root.toString(),5242880);
  var strategy=new ImageServiceStrategy();ReflectionTestUtils.setField(strategy,"demo",new DemoImageService(local,local,false));
  var tool=new ImageGenerationTool();ReflectionTestUtils.setField(tool,"imageServiceStrategy",strategy);
  var processor=new ArticleMediaProcessor(h.analyzer,tool,h.store,h.interventions);
  var state=A1ReviewLoopTest.state();state.setEnabledImageMethods(List.of("DEMO"));String original=state.getContent();
  processor.generate(state,e->{});assertEquals("COMPLETED",state.getPhase());assertEquals("DEGRADED",h.saved.get().slots().getFirst().status());assertEquals("DEMO_PNG",state.getImages().getFirst().getMethod());assertEquals(original,state.getContent());assertTrue(state.getFullContent().contains("非AI生图"));
  processor.retry(state,"image-1",e->{});assertEquals(2,h.saved.get().slots().getFirst().attempts());assertEquals(original,state.getContent());verifyNoInteractions(h.analyzer);
 }
 @Test void modelCannotInventDemoForLegacyUnspecifiedTask() throws Exception {
  var h=new A2MediaTest.Harness(1);when(h.analyzer.apply(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Map.of("imageRequirements",DemoImageService.plan()));
  var state=A1ReviewLoopTest.state();state.setEnabledImageMethods(null);assertThrows(IllegalStateException.class,()->h.processor.generate(state,e->{}));verifyNoInteractions(h.tool);
 }
 @Test void fallbackCannotBeSelectedAsModelPlan() throws Exception {
  var h=new A2MediaTest.Harness(1);var state=A1ReviewLoopTest.state();state.setEnabledImageMethods(List.of("PICSUM"));assertThrows(IllegalStateException.class,()->h.processor.generate(state,e->{}));verifyNoInteractions(h.tool);
 }
}
