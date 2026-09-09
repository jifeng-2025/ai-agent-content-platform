package com.yupi.template.agent.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;

/** Patch only named paragraphs; separators and every other paragraph stay byte-for-byte unchanged. */
public final class ParagraphDraft {
    public record Paragraph(String sectionId, String text, String separator) {}
    private final List<Paragraph> paragraphs;
    public ParagraphDraft(String content) {
        var matcher = Pattern.compile("\\r?\\n[\\t ]*\\r?\\n").matcher(content);
        List<Paragraph> parts = new ArrayList<>();
        int start = 0;
        while (matcher.find()) {
            parts.add(new Paragraph("p" + (parts.size() + 1), content.substring(start, matcher.start()), matcher.group()));
            start = matcher.end();
        }
        if (start < content.length() || parts.isEmpty())
            parts.add(new Paragraph("p" + (parts.size() + 1), content.substring(start), ""));
        paragraphs = List.copyOf(parts);
    }
    public List<Paragraph> paragraphs() { return paragraphs; }
    public Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        paragraphs.forEach(p -> ids.add(p.sectionId()));
        return ids;
    }
    public String apply(String raw, Set<String> allowed) {
        JsonNode root = ReviewJson.object(raw);
        ReviewJson.fields(root, "replacements");
        JsonNode replacements = root.get("replacements");
        if (!replacements.isArray() || replacements.isEmpty() || replacements.size() > allowed.size())
            throw new ReviewJson.InvalidOutput();
        Map<String, String> patch = new HashMap<>();
        for (JsonNode replacement : replacements) {
            ReviewJson.fields(replacement, "sectionId", "text");
            String id = ReviewJson.text(replacement, "sectionId");
            String text = ReviewJson.text(replacement, "text");
            if (!allowed.contains(id) || !ids().contains(id) || patch.putIfAbsent(id, text) != null ||
                    Pattern.compile("\\r?\\n[\\t ]*\\r?\\n").matcher(text).find()) throw new ReviewJson.InvalidOutput();
            String original = paragraphs.stream().filter(p -> p.sectionId().equals(id)).findFirst().orElseThrow().text();
            if (text.startsWith("\n") || text.startsWith("\r") || text.endsWith("\n") || text.endsWith("\r")) throw new ReviewJson.InvalidOutput();
            if (!headings(original).equals(headings(text))) throw new ReviewJson.InvalidOutput();
        }
        StringBuilder result = new StringBuilder();
        for (Paragraph p : paragraphs) result.append(patch.getOrDefault(p.sectionId(), p.text())).append(p.separator());
        return result.toString();
    }
    private static List<String> headings(String text) {
        return Pattern.compile("(?m)^#{1,6}[\\t ]+.*$").matcher(text).results().map(m -> m.group()).toList();
    }
}
