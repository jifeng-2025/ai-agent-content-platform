package com.yupi.template.repository;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.service.ArticleInterventionService;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ArticleMediaStore {
    private final JdbcTemplate jdbc;
    private final ArticleInterventionService interventions;
    @Transactional
    public void save(ArticleState state, MediaState media, String status) {
        interventions.lock(state.getTaskId());
        jdbc.update("INSERT INTO article_intervention(taskId,mediaJson,revision) VALUES (?,?,1) ON DUPLICATE KEY UPDATE mediaJson=VALUES(mediaJson),revision=revision+1",state.getTaskId(),GsonUtils.toJson(media));
        jdbc.update("UPDATE article SET content=?,fullContent=?,images=?,coverImage=?,status=?,phase=?,errorMessage=?,completedTime=CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE taskId=?",
                state.getContent(),state.getFullContent(),GsonUtils.toJson(state.getImages()),state.getCoverImage(),status,
                "PROCESSING".equals(status)?"IMAGE_GENERATING":status,"IMAGES_FAILED".equals(status)?"部分配图失败，可单张重试":null,status,state.getTaskId());
    }
}
