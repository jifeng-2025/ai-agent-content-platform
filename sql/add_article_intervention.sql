-- A2 additive tables. Run after add_article_review.sql in an explicitly selected schema.
CREATE TABLE IF NOT EXISTS article_intervention (
 taskId VARCHAR(64) NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0,
 mediaJson LONGTEXT NULL,
 updatedTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS article_operation (
 taskId VARCHAR(64) NOT NULL,
 requestId VARCHAR(64) NOT NULL,
 payloadHash CHAR(64) NOT NULL,
 action VARCHAR(32) NOT NULL,
 requestJson LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL,
 errorMessage VARCHAR(500) NULL,
 createdTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updatedTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(taskId,requestId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Widen only: retain existing text while allowing bounded Unicode drafts and image markup.
ALTER TABLE article MODIFY COLUMN content MEDIUMTEXT NULL, MODIFY COLUMN fullContent MEDIUMTEXT NULL;
