package com.yupi.template.service;
import com.yupi.template.agent.review.ArticleReviewLoop;
import com.yupi.template.manager.SseEmitterManager;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleInterventionWorker {
    private final ArticleInterventionService interventions;
    private final ArticleService articles;
    private final ArticleReviewLoop review;
    private final ArticleMediaProcessor media;
    private final SseEmitterManager sse;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.yupi.template.runtime.RuntimeConfig runtimeConfig;
    @Async("articleExecutor")
    public void execute(String taskId,String requestId) {
        if (runtimeConfig != null && runtimeConfig.isEnabled()) return; // durable dispatcher owns A3 operations
        var request=interventions.claim(taskId,requestId);
        if(request==null)return;
        try {
            var state=interventions.state(taskId);
            if("EDIT_REVIEW".equals(request.action())) {
                review.runRound(state,progress->{articles.saveReviewProgress(taskId,progress);send(taskId,"REVIEW_UPDATED");});
                if("NEEDS_REVIEW".equals(state.getReviewTrace().status())) {
                    interventions.finish(taskId,requestId);send(taskId,"NEEDS_REVIEW");return;
                }
            }
            if("RETRY_IMAGE".equals(request.action()))media.retry(state,request.imageId(),type->send(taskId,type));
            else media.generate(state,type->send(taskId,type));
            interventions.finish(taskId,requestId);
            send(taskId,"IMAGES_FAILED".equals(state.getPhase())?"IMAGES_FAILED":"ALL_COMPLETE");
        } catch(Exception e) {
            log.warn("人工操作未完成: taskId={}, requestId={}",taskId,requestId,e);
            interventions.fail(taskId,requestId);send(taskId,"ERROR");
        } finally { sse.complete(taskId); }
    }
    private void send(String taskId,String type) {
        try { sse.send(taskId,GsonUtils.toJson(Map.of("type",type,"taskId",taskId))); } catch(RuntimeException ignored) { }
    }
}
