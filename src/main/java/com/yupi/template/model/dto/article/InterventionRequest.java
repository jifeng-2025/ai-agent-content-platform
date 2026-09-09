package com.yupi.template.model.dto.article;
public record InterventionRequest(String requestId, String action, Integer expectedVersion, Long expectedRevision,
        String content, String imageId, String note, boolean acknowledgeRisks) {}
