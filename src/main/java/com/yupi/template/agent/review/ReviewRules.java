package com.yupi.template.agent.review;

import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.dto.article.ReviewResult;
import java.util.*;
import java.util.regex.Pattern;
import static com.yupi.template.model.dto.article.ReviewResult.*;

public final class ReviewRules {
    private ReviewRules() {}
    public static Issue issue(Type type, String id, String reason, String action) {
        return new Issue(type, Severity.ERROR, id, reason, action);
    }
    public static List<Issue> check(ArticleState state, ParagraphDraft draft) {
        List<Issue> issues = new ArrayList<>();
        String content = state.getContent();
        String goal = Objects.toString(state.getUserDescription(), "");
        boolean novice = Pattern.compile("小白|新手|非技术|普通人").matcher(goal).find();
        for (var p : draft.paragraphs()) {
            String text = p.text();
            if (novice && Pattern.compile("\\b(RAG|RLHF|Transformer|Embedding)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()
                    && !Pattern.compile("即|也就是|指的是|可以理解为|检索增强").matcher(text).find())
                issues.add(issue(Type.AUDIENCE, p.sectionId(), "面向新手却使用未解释术语", "用普通读者可理解的表达解释或替换术语"));
            if (Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:%|％|倍|万人|亿元)").matcher(text).find()
                    && !Pattern.compile("假设|假定|虚构示例").matcher(text).find())
                issues.add(issue(Type.UNSUPPORTED_NUMBER, p.sectionId(), "精确数字缺少经核验依据；A1不具备事实核查能力", "删除数字、明确限定为假设，或交人工核实"));
            if (Pattern.compile("(?:据|根据|来自).{0,30}(?:研究|大学|机构|报告|实验室|研究院|研究所|调查)|(?:哈佛|斯坦福|麦肯锡|MIT|清华).{0,20}(?:研究|报告|发现|表明)").matcher(text).find())
                issues.add(issue(Type.UNSUPPORTED_ATTRIBUTION, p.sectionId(), "机构归因未获得外部证据核验", "删除归因或交人工核实，不得编造来源"));
        }
        if (state.getOutline() == null || state.getOutline().getSections() == null || state.getOutline().getSections().isEmpty()) {
            issues.add(issue(Type.STRUCTURE, "article", "缺少确认的大纲", "人工确认大纲"));
        } else {
            for (var section : state.getOutline().getSections()) {
                if (section.getTitle() == null || section.getTitle().isBlank() ||
                        Pattern.compile("(?m)^#{1,6}\\s+.*" + Pattern.quote(section.getTitle()) + ".*$").matcher(content).results().findAny().isEmpty()) {
                    issues.add(issue(Type.STRUCTURE, "article", "正文未包含确认大纲的章节标题", "人工核对章节结构，避免整篇重写"));
                    break;
                }
            }
        }
        int min = 0, max = 64000;
        var range = Pattern.compile("(\\d{2,5})\\s*[-~到至]\\s*(\\d{2,5})\\s*字").matcher(goal);
        var upper = Pattern.compile("(?:不超过|最多|至多)\\s*(\\d{2,5})\\s*字").matcher(goal);
        var lower = Pattern.compile("(?:至少|不少于)\\s*(\\d{2,5})\\s*字").matcher(goal);
        if (range.find()) { min = Integer.parseInt(range.group(1)); max = Math.min(max, Integer.parseInt(range.group(2))); }
        if (upper.find()) max = Math.min(max, Integer.parseInt(upper.group(1)));
        if (lower.find()) min = Math.max(min, Integer.parseInt(lower.group(1)));
        int length = content.replaceAll("\\s", "").codePointCount(0, content.replaceAll("\\s", "").length());
        if (length < min || length > max)
            issues.add(issue(Type.LENGTH, "article", "非空白字符数不满足明确篇幅范围或超过评审安全上限", "人工调整篇幅；没有明确数字的篇幅要求仍由Reviewer判断"));
        return issues;
    }
}
