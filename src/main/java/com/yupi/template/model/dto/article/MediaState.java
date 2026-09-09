package com.yupi.template.model.dto.article;
import java.util.List;
public record MediaState(String template, List<Slot> slots) {
    public MediaState { slots = List.copyOf(slots); }
    public record Slot(String id, ArticleState.ImageRequirement requirement, ArticleState.ImageResult result,
            String status, int attempts, String error) {}
}
