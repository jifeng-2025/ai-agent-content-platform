package com.yupi.template.repository;

import com.yupi.template.model.dto.article.ReviewTrace;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewTraceStore {
    private final JdbcTemplate jdbc;

    public void save(String taskId, ReviewTrace trace) {
        jdbc.update("INSERT INTO article_review (taskId, reviewJson) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE reviewJson=VALUES(reviewJson), updatedTime=CURRENT_TIMESTAMP",
                taskId, GsonUtils.toJson(trace));
    }

    public ReviewTrace find(String taskId) {
        var rows = jdbc.query("SELECT reviewJson FROM article_review WHERE taskId=?",
                (rs, row) -> GsonUtils.fromJson(rs.getString(1), ReviewTrace.class), taskId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
