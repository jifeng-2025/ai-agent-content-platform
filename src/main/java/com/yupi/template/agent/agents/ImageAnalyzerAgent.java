package com.yupi.template.agent.agents;

import org.springframework.ai.chat.model.ChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.reflect.TypeToken;
import com.yupi.template.constant.PromptConstant;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.enums.ImageMethodEnum;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配图需求分析 Agent
 * 分析文章内容，生成配图需求列表
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImageAnalyzerAgent implements NodeAction {

    private final ChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_CONTENT = "content";
    public static final String INPUT_ENABLED_IMAGE_METHODS = "enabledImageMethods";
    public static final String OUTPUT_CONTENT_WITH_PLACEHOLDERS = "contentWithPlaceholders";
    public static final String OUTPUT_IMAGE_REQUIREMENTS = "imageRequirements";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));
        
        String content = state.value(INPUT_CONTENT)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少正文内容参数"));
        
        @SuppressWarnings("unchecked")
        List<String> enabledMethods = state.value(INPUT_ENABLED_IMAGE_METHODS)
                .map(v -> {
                    if (v instanceof List) {
                        return (List<String>) v;
                    }
                    return null;
                })
                .orElse(null);
        
        log.info("ImageAnalyzerAgent 开始执行: mainTitle={}, enabledMethods={}", mainTitle, enabledMethods);
        
        if (com.yupi.template.service.DemoImageService.selected(enabledMethods)) {
            return Map.of(OUTPUT_CONTENT_WITH_PLACEHOLDERS, content+"\n\n{{DEMO_IMAGE}}", INPUT_CONTENT, content+"\n\n{{DEMO_IMAGE}}", OUTPUT_IMAGE_REQUIREMENTS, com.yupi.template.service.DemoImageService.plan());
        }

        // 构建可用配图方式说明
        String availableMethods = buildAvailableMethodsDescription(enabledMethods);
        // 构建各配图方式的详细使用指南（只包含允许的方式）
        String methodUsageGuide = buildMethodUsageGuide(enabledMethods);
        
        // 构建 prompt
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{content}", content)
                .replace("{availableMethods}", availableMethods)
                .replace("{methodUsageGuide}", methodUsageGuide);
        
        // 调用 LLM
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        String responseContent = response.getResult().getOutput().getText();
        
        // 解析结果（新格式：包含 contentWithPlaceholders 和 imageRequirements）
        ArticleState.Agent4Result agent4Result = GsonUtils.fromJson(
                responseContent,
                ArticleState.Agent4Result.class
        );
        
        // 验证并过滤配图需求
        List<ArticleState.ImageRequirement> validatedRequirements = validateAndFilterImageRequirements(
                agent4Result.getImageRequirements(),
                enabledMethods
        );
        
        log.info("ImageAnalyzerAgent 执行完成: 配图需求数量={}, 验证后数量={}, 已在正文中插入占位符", 
                agent4Result.getImageRequirements().size(), validatedRequirements.size());
        
        // 返回结果：contentWithPlaceholders、content（更新为包含占位符）、imageRequirements
        return Map.of(
                OUTPUT_CONTENT_WITH_PLACEHOLDERS, agent4Result.getContentWithPlaceholders(),
                INPUT_CONTENT, agent4Result.getContentWithPlaceholders(), // 更新 content 为包含占位符的版本，传给下游节点
                OUTPUT_IMAGE_REQUIREMENTS, validatedRequirements
        );
    }

    /**
     * 构建可用配图方式说明
     */
    private String buildAvailableMethodsDescription(List<String> enabledMethods) {
        if (enabledMethods == null || enabledMethods.isEmpty()) {
            return getAllMethodsDescription();
        }

        StringBuilder sb = new StringBuilder();
        for (String method : enabledMethods) {
            ImageMethodEnum methodEnum = ImageMethodEnum.getByValue(method);
            if (methodEnum != null && !methodEnum.isFallback()) {
                sb.append("   - ").append(methodEnum.getValue())
                        .append(": ").append(getMethodUsageDescription(methodEnum))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取所有配图方式的完整描述
     */
    private String getAllMethodsDescription() {
        return """
               - PEXELS: 适合真实场景、产品照片、人物照片、自然风景等写实图片
               - NANO_BANANA: 适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片
               - MERMAID: 适合流程图、架构图、时序图、关系图、甘特图等结构化图表
               - ICONIFY: 适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）
               - EMOJI_PACK: 适合表情包、搞笑图片、轻松幽默的配图
               - SVG_DIAGRAM: 适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）
               """;
    }

    /**
     * 获取配图方式的使用说明
     */
    private String getMethodUsageDescription(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "适合真实场景、产品照片、人物照片、自然风景等写实图片";
            case NANO_BANANA -> "适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片";
            case MERMAID -> "适合流程图、架构图、时序图、关系图、甘特图等结构化图表";
            case ICONIFY -> "适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）";
            case EMOJI_PACK -> "适合表情包、搞笑图片、轻松幽默的配图";
            case SVG_DIAGRAM -> "适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）";
            default -> method.getDescription();
        };
    }

    /**
     * 构建配图方式的详细使用指南（只包含允许的方式）
     */
    private String buildMethodUsageGuide(List<String> enabledMethods) {
        // 如果没有限制，返回所有方式的使用指南
        List<String> methodsToInclude = (enabledMethods == null || enabledMethods.isEmpty())
                ? List.of("PEXELS", "NANO_BANANA", "MERMAID", "ICONIFY", "EMOJI_PACK", "SVG_DIAGRAM")
                : enabledMethods;

        StringBuilder sb = new StringBuilder();
        
        for (String method : methodsToInclude) {
            String guide = getMethodDetailedGuide(method);
            if (guide != null && !guide.isEmpty()) {
                sb.append(guide).append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * 获取单个配图方式的详细使用指南
     */
    private String getMethodDetailedGuide(String method) {
        return switch (method) {
            case "PEXELS" -> """
                    - PEXELS: 提供英文搜索关键词(keywords)，要准确、具体。prompt 留空。""";
            case "DOUBAO" -> "- DOUBAO: 提供详细图片提示词(prompt)，仅生成一张封面；不指定模型或接口。keywords 留空。";
            case "NANO_BANANA" -> """
                    - NANO_BANANA: 提供详细的英文生图提示词(prompt)，描述场景、风格、细节。keywords 留空。""";
            case "MERMAID" -> """
                    - MERMAID: 在 prompt 字段生成完整的 Mermaid 代码（如流程图、架构图）。keywords 留空。""";
            case "ICONIFY" -> """
                    - ICONIFY: 提供英文图标关键词(keywords)，如：check、arrow、star、heart。prompt 留空。""";
            case "EMOJI_PACK" -> """
                    - EMOJI_PACK: 提供中文或英文关键词(keywords)描述表情内容。prompt 留空。系统会自动添加"表情包"搜索。""";
            case "SVG_DIAGRAM" -> """
                    - SVG_DIAGRAM: 在 prompt 字段描述示意图需求（中文），说明要表达的概念和关系。keywords 留空。
                      示例：绘制思维导图样式的图，中心是"自律"，周围4个分支：习惯、环境、反馈、系统""";
            default -> null;
        };
    }

    /**
     * 验证并过滤配图需求
     */
    private List<ArticleState.ImageRequirement> validateAndFilterImageRequirements(
            List<ArticleState.ImageRequirement> requirements,
            List<String> enabledMethods) {
        
        if (enabledMethods == null || enabledMethods.isEmpty()) {
            return requirements;
        }
        
        List<ArticleState.ImageRequirement> validatedRequirements = new ArrayList<>();
        
        for (ArticleState.ImageRequirement req : requirements) {
            String imageSource = req.getImageSource();
            
            if (enabledMethods.contains(imageSource)) {
                validatedRequirements.add(req);
                log.debug("配图需求验证通过, position={}, imageSource={}", req.getPosition(), imageSource);
            } else {
                log.warn("配图需求不符合限制被过滤, position={}, imageSource={}, enabledMethods={}", 
                        req.getPosition(), imageSource, enabledMethods);
                
                // 尝试替换为允许的方式
                if (!enabledMethods.isEmpty()) {
                    String fallbackSource = enabledMethods.get(0);
                    req.setImageSource(fallbackSource);
                    validatedRequirements.add(req);
                    log.info("配图需求已替换为允许的方式, position={}, fallback={}", 
                            req.getPosition(), fallbackSource);
                }
            }
        }
        
        return validatedRequirements;
    }
}
