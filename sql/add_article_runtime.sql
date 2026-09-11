-- A3 additive migration. Select the isolated/approved schema explicitly first.
-- Re-runnable without dropping historical rows.
DROP PROCEDURE IF EXISTS a3_add_column;
DELIMITER $$
CREATE PROCEDURE a3_add_column(IN col VARCHAR(64), IN definition TEXT)
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article_operation' AND COLUMN_NAME=col) THEN
  SET @a3_ddl=CONCAT('ALTER TABLE article_operation ADD COLUMN ',col,' ',definition);
  PREPARE a3_stmt FROM @a3_ddl; EXECUTE a3_stmt; DEALLOCATE PREPARE a3_stmt;
 END IF;
END$$
DELIMITER ;
CALL a3_add_column('runId','VARCHAR(64) NULL');
CALL a3_add_column('phase','VARCHAR(32) NULL');
CALL a3_add_column('checkpointJson','LONGTEXT NULL');
CALL a3_add_column('owner','VARCHAR(100) NULL');
CALL a3_add_column('fence','BIGINT NOT NULL DEFAULT 0');
CALL a3_add_column('leaseUntil','DATETIME(3) NULL');
CALL a3_add_column('heartbeatAt','DATETIME(3) NULL');
CALL a3_add_column('stateVersion','BIGINT NOT NULL DEFAULT 0');
DROP PROCEDURE a3_add_column;
CREATE TABLE IF NOT EXISTS article_runtime (
 taskId VARCHAR(64) PRIMARY KEY, runId VARCHAR(64) NOT NULL UNIQUE, userId BIGINT NOT NULL,
 createRequestId VARCHAR(64) NULL, createPayloadHash CHAR(64) NULL, UNIQUE(userId,createRequestId),
 stateVersion BIGINT NOT NULL DEFAULT 0, lastEventId BIGINT NOT NULL DEFAULT 0,
 remainingMs BIGINT NOT NULL, maxCalls INT NOT NULL, callsUsed INT NOT NULL DEFAULT 0,
 maxImageRetries INT NOT NULL, imageRetriesUsed INT NOT NULL DEFAULT 0,
 maxEstimatedCostMicros BIGINT NOT NULL, reservedCostMicros BIGINT NOT NULL DEFAULT 0,
 actualCostMicros BIGINT NULL, createdTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS article_call (
 callId CHAR(64) PRIMARY KEY, taskId VARCHAR(64) NOT NULL, requestId VARCHAR(64) NOT NULL,
 stepId VARCHAR(100) NOT NULL, provider VARCHAR(64) NOT NULL, payloadHash CHAR(64) NOT NULL,
 status VARCHAR(32) NOT NULL, attempts INT NOT NULL DEFAULT 0, queryAttempts INT NOT NULL DEFAULT 0,
 providerJobId VARCHAR(200) NULL, resultJson LONGTEXT NULL, reservedCostMicros BIGINT NOT NULL,
 actualCostMicros BIGINT NULL, deadlineAt DATETIME(3) NOT NULL,
 createdTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updatedTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 INDEX(taskId,requestId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS article_event (
 taskId VARCHAR(64) NOT NULL, seq BIGINT NOT NULL, runId VARCHAR(64) NOT NULL,
 type VARCHAR(40) NOT NULL, payloadJson LONGTEXT NOT NULL,
 createdTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY(taskId,seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
