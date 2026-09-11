-- Additive; repeatable; never copies or removes existing images/articles.
CREATE TABLE IF NOT EXISTS article_image_profile (
 taskId VARCHAR(64) NOT NULL, method VARCHAR(32) NOT NULL, profileJson JSON NOT NULL,
 createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(taskId,method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS article_image_metadata (
 cacheKey CHAR(64) PRIMARY KEY, metadataJson JSON NOT NULL,
 createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
