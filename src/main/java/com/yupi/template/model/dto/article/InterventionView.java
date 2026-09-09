package com.yupi.template.model.dto.article;
import java.util.List;
public record InterventionView(boolean enabled, String taskId, String articleStatus, String phase,
        long revision, ReviewTrace reviewTrace, MediaState media, List<String> allowedActions, String errorMessage) {}
