package com.yupi.template.agent.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.yupi.template.agent.context.StreamHandlerContext;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.enums.SseMessageTypeEnum;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 并行图片生成器
 * 根据 imageSource 分组，并行执行不同类型的图片生成任务
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParallelImageGenerator implements NodeAction {

    private final ImageGenerationTool imageGenerationTool;
    @jakarta.annotation.Resource private com.yupi.template.service.image.ImageProfileStore profiles;

    public static final String INPUT_IMAGE_REQUIREMENTS = "imageRequirements";
    public static final String OUTPUT_IMAGES = "images";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        @SuppressWarnings("unchecked")
        List<ArticleState.ImageRequirement> imageRequirements = state.value(INPUT_IMAGE_REQUIREMENTS)
                .map(v -> {
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        if (list.isEmpty()) {
                            return new ArrayList<ArticleState.ImageRequirement>();
                        }
                        if (list.get(0) instanceof ArticleState.ImageRequirement) {
                            return (List<ArticleState.ImageRequirement>) v;
                        }
                        // 尝试转换
                        return convertToImageRequirements(list);
                    }
                    return new ArrayList<ArticleState.ImageRequirement>();
                })
                .orElse(new ArrayList<>());
        
        // 从 ThreadLocal 获取流式处理器
        Consumer<String> streamHandler = StreamHandlerContext.get();
        
        log.info("ParallelImageGenerator 开始执行: 配图需求数量={}", imageRequirements.size());
        
        if (imageRequirements.isEmpty()) {
            log.info("没有配图需求，跳过图片生成");
            return Map.of(OUTPUT_IMAGES, new ArrayList<>());
        }
        
        for(var req:imageRequirements)if(com.yupi.template.service.image.ImageProfile.paid(req.getImageSource()))req.setProfile(profiles.find(state.value("taskId",String.class).orElse(null),req.getImageSource()));
        // 按 imageSource 分组
        Map<String, List<ArticleState.ImageRequirement>> groupedBySource = imageRequirements.stream()
                .collect(Collectors.groupingBy(ArticleState.ImageRequirement::getImageSource));
        
        log.info("配图需求按类型分组: {}", 
                groupedBySource.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().size()
                        )));
        
        // 并行执行不同类型的图片生成
        List<ArticleState.ImageResult> allImages = executeParallel(groupedBySource, streamHandler);
        
        // 按 position 排序
        allImages.sort((a, b) -> {
            Integer posA = a.getPosition() != null ? a.getPosition() : 0;
            Integer posB = b.getPosition() != null ? b.getPosition() : 0;
            return posA.compareTo(posB);
        });
        
        log.info("ParallelImageGenerator 执行完成: 成功生成 {} 张图片", allImages.size());
        
        return Map.of(OUTPUT_IMAGES, allImages);
    }

    /**
     * 并行执行图片生成任务
     * 不同 imageSource 类型并行执行，同一类型内部串行执行
     */
    private List<ArticleState.ImageResult> executeParallel(
            Map<String, List<ArticleState.ImageRequirement>> groupedBySource,
            Consumer<String> streamHandler) {
        
        // 使用线程安全的列表收集结果
        CopyOnWriteArrayList<ArticleState.ImageResult> allImages = new CopyOnWriteArrayList<>();
        
        // 为每种 imageSource 创建异步任务
        List<CompletableFuture<Void>> futures = groupedBySource.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    String imageSource = entry.getKey();
                    List<ArticleState.ImageRequirement> requirements = entry.getValue();
                    
                    log.info("开始处理 {} 类型的图片，数量: {}", imageSource, requirements.size());
                    
                    // 同一类型内部串行执行
                    for (ArticleState.ImageRequirement req : requirements) {
                        try {
                            ImageGenerationTool.ImageGenerationResult result = 
                                    generateTool(req);
                            
                            if (result.isSuccess()) {
                                ArticleState.ImageResult imageResult = convertToImageResult(result);
                                allImages.add(imageResult);
                                
                                // 推送单张配图完成消息
                                if (streamHandler != null) {
                                    String message = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() 
                                            + GsonUtils.toJson(imageResult);
                                    streamHandler.accept(message);
                                }
                                
                                log.info("图片生成成功: imageSource={}, position={}", 
                                        imageSource, req.getPosition());
                            } else {
                                log.warn("图片生成失败: imageSource={}, position={}, error={}", 
                                        imageSource, req.getPosition(), result.getError());
                            }
                        } catch (Exception e) {
                            if(com.yupi.template.service.image.ImageProfile.paid(req.getImageSource()))throw new java.util.concurrent.CompletionException(e);
                            log.error("图片生成异常: imageSource={}, position={}", 
                                    imageSource, req.getPosition(), e);
                        }
                    }
                    
                    log.info("完成处理 {} 类型的图片", imageSource);
                }))
                .toList();
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return new ArrayList<>(allImages);
    }

    /**
     * 转换 ImageGenerationResult 为 ArticleState.ImageResult
     */
    private ImageGenerationTool.ImageGenerationResult generateTool(ArticleState.ImageRequirement req){
      if(com.yupi.template.service.image.ImageProfile.paid(req.getImageSource()))return imageGenerationTool.generateImageDirect(req.getImageSource(),req.getKeywords(),req.getPrompt(),req.getPosition(),req.getType(),req.getSectionTitle(),req.getPlaceholderId(),req.getProfile());
      return imageGenerationTool.generateImageDirect(req.getImageSource(),req.getKeywords(),req.getPrompt(),req.getPosition(),req.getType(),req.getSectionTitle(),req.getPlaceholderId());
    }
    private ArticleState.ImageResult convertToImageResult(ImageGenerationTool.ImageGenerationResult genResult) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(genResult.getPosition());
        imageResult.setUrl(genResult.getUrl());
        imageResult.setMetadata(genResult.getMetadata());
        imageResult.setMethod(genResult.getMethod());
        imageResult.setKeywords(genResult.getKeywords());
        imageResult.setSectionTitle(genResult.getSectionTitle());
        imageResult.setDescription(genResult.getDescription());
        imageResult.setPlaceholderId(genResult.getPlaceholderId());
        return imageResult;
    }

    /**
     * 转换列表为 ImageRequirement 列表
     */
    private List<ArticleState.ImageRequirement> convertToImageRequirements(List<?> list) {
        List<ArticleState.ImageRequirement> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof ArticleState.ImageRequirement) {
                results.add((ArticleState.ImageRequirement) item);
            } else if (item instanceof Map) {
                String json = GsonUtils.toJson(item);
                ArticleState.ImageRequirement req = GsonUtils.fromJson(json, ArticleState.ImageRequirement.class);
                results.add(req);
            }
        }
        return results;
    }
}
