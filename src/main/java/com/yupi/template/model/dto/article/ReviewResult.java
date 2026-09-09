package com.yupi.template.model.dto.article;

import java.io.Serializable;
import java.util.List;

/** Quality risk review, not a claim of factual verification. */
public record ReviewResult(int schemaVersion, Decision decision, List<Issue> issues) implements Serializable {
    public enum Decision { PASS, REVISE, NEEDS_REVIEW }
    public enum Type {
        AUDIENCE, STRUCTURE, LENGTH, UNSUPPORTED_NUMBER, UNSUPPORTED_ATTRIBUTION,
        EVIDENCE_REQUIRED, OUTPUT_ERROR, MODEL_ERROR, MODEL_TIMEOUT, NO_PROGRESS
    }
    public enum Severity { WARNING, ERROR }
    public record Issue(Type type, Severity severity, String sectionId, String reason,
                        String suggestedAction) implements Serializable {}
    public ReviewResult { issues = List.copyOf(issues); }
    public ReviewResult needsReview() { return new ReviewResult(1, Decision.NEEDS_REVIEW, issues); }
}
