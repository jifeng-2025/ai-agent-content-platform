package com.yupi.template.model.enums;

import lombok.Getter;

/**
 * 文章状态枚举
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Getter
public enum ArticleStatusEnum {

    IMAGES_FAILED("IMAGES_FAILED", "配图待重试"),
    NEEDS_REVIEW("NEEDS_REVIEW", "待人工评审"),
    PENDING("PENDING", "等待处理"),
    PROCESSING("PROCESSING", "处理中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    /**
     * 状态值
     */
    private final String value;

    /**
     * 状态描述
     */
    private final String description;

    ArticleStatusEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 状态值
     * @return 枚举实例
     */
    public static ArticleStatusEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ArticleStatusEnum statusEnum : values()) {
            if (statusEnum.getValue().equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}
