package com.yupi.template.model.dto.article;

import java.io.Serializable;
import java.util.List;

/** Global versions never reset; roundStartVersion defines this round's two-revision budget. */
public record ReviewTrace(int schemaVersion, String status, int currentVersion, int maxRevisions,
        String stopReason, List<DraftVersion> versions, ReviewResult review,
        int round, int roundStartVersion, List<HumanDecision> humanDecisions) implements Serializable {
    public record DraftVersion(int version, String content, ReviewResult review) implements Serializable {}
    public record HumanDecision(int version, long userId, String decidedAt, String note) implements Serializable {}
    public ReviewTrace {
        versions = List.copyOf(versions);
        humanDecisions = humanDecisions == null ? List.of() : List.copyOf(humanDecisions);
    }
    // A1 source/JSON compatibility: absent round fields deserialize as 0.
    public ReviewTrace(int schemaVersion, String status, int currentVersion, int maxRevisions,
            String stopReason, List<DraftVersion> versions, ReviewResult review) {
        this(schemaVersion,status,currentVersion,maxRevisions,stopReason,versions,review,0,0,List.of());
    }
}
