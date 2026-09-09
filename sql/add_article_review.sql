-- A1 additive migration. Apply deliberately to the target schema BEFORE enabling the loop.
-- No existing article rows/columns are changed. Do not run against a user database in tests.
CREATE TABLE IF NOT EXISTS article_review (
    taskId VARCHAR(64) NOT NULL PRIMARY KEY,
    reviewJson LONGTEXT NOT NULL,
    updatedTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
