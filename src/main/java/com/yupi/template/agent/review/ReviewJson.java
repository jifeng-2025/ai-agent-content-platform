package com.yupi.template.agent.review;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.yupi.template.model.dto.article.ReviewResult;
import java.util.*;

public final class ReviewJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ReviewJson() {}
    public static JsonNode object(String raw) {
        try {
            if (raw == null || raw.length() > 64000) throw new IllegalArgumentException();
            JsonNode root = MAPPER.readTree(raw);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            return root;
        } catch (Exception e) { throw new InvalidOutput(); }
    }
    public static void fields(JsonNode node, String... names) {
        if (!node.isObject()) throw new InvalidOutput();
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(names))) throw new InvalidOutput();
    }
    public static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new InvalidOutput();
        return value.textValue();
    }
    public static ReviewResult review(String raw, Set<String> paragraphIds) {
        try {
            JsonNode root = object(raw);
            fields(root, "schemaVersion", "decision", "issues");
            if (!root.get("schemaVersion").isIntegralNumber() || !root.get("schemaVersion").canConvertToInt() || root.get("schemaVersion").intValue() != 1)
                throw new InvalidOutput();
            var decision = ReviewResult.Decision.valueOf(text(root, "decision"));
            JsonNode array = root.get("issues");
            if (!array.isArray() || array.size() > 32) throw new InvalidOutput();
            List<ReviewResult.Issue> issues = new ArrayList<>();
            for (JsonNode issue : array) {
                fields(issue, "type", "severity", "sectionId", "reason", "suggestedAction");
                String id = text(issue, "sectionId");
                if (!id.equals("article") && !paragraphIds.contains(id)) throw new InvalidOutput();
                var type = ReviewResult.Type.valueOf(text(issue, "type"));
                if (!Set.of(ReviewResult.Type.AUDIENCE, ReviewResult.Type.STRUCTURE, ReviewResult.Type.LENGTH,
                        ReviewResult.Type.UNSUPPORTED_NUMBER, ReviewResult.Type.UNSUPPORTED_ATTRIBUTION,
                        ReviewResult.Type.EVIDENCE_REQUIRED).contains(type)) throw new InvalidOutput();
                issues.add(new ReviewResult.Issue(
                        type,
                        ReviewResult.Severity.valueOf(text(issue, "severity")), id,
                        text(issue, "reason"), text(issue, "suggestedAction")));
            }
            if ((decision == ReviewResult.Decision.PASS) != issues.isEmpty()) throw new InvalidOutput();
            return new ReviewResult(1, decision, issues);
        } catch (RuntimeException e) { throw new InvalidOutput(); }
    }
    public static final class InvalidOutput extends RuntimeException {
        public InvalidOutput() { super("Invalid structured review or revision output"); }
    }
}
