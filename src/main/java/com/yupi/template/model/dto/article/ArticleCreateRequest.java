package com.yupi.template.model.dto.article;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建文章请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleCreateRequest implements Serializable {

    /**
     * 选题
     */
    private String topic;

    /** Optional network idempotency key for A3. */
    private String requestId;

    /**
     * 文章风格：tech/emotional/educational/humorous，可为空
     */
    private String style;

    /**
     * 允许的配图方式列表（为空或 null 表示默认DEMO演示占位）
     * 可选值：DEMO, DOUBAO, PEXELS, NANO_BANANA, MERMAID, ICONIFY, EMOJI_PACK, SVG_DIAGRAM
     */
    private List<String> enabledImageMethods;

    private static final long serialVersionUID = 1L;
}
