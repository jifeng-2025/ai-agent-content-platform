package com.yupi.template.agent.agents;

import org.springframework.ai.chat.model.ChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.reflect.TypeToken;
import com.yupi.template.constant.PromptConstant;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.enums.ArticleStyleEnum;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 标题生成 Agent
 * 根据选题生成 3-5 个爆款标题方案
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TitleGeneratorAgent implements NodeAction {

    private final ChatModel chatModel;

    public static final String INPUT_TOPIC = "topic";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_TITLE_OPTIONS = "titleOptions";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic = state.value(INPUT_TOPIC)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少选题参数"));
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("TitleGeneratorAgent 开始执行: topic={}, style={}", topic, style);
        
        // 构建 prompt
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", topic)
                + getStylePrompt(style);
        
        // 调用 LLM
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        String content = response.getResult().getOutput().getText();
        
        // 解析结果
        List<ArticleState.TitleOption> titleOptions = GsonUtils.fromJson(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){}
        );
        
        log.info("TitleGeneratorAgent 执行完成: 生成了 {} 个标题方案", titleOptions.size());
        
        return Map.of(OUTPUT_TITLE_OPTIONS, titleOptions);
    }

    /**
     * 根据风格获取对应的 Prompt 附加内容
     */
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        
        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }
        
        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }
}
