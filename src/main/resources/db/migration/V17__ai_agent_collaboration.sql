CREATE TABLE IF NOT EXISTS ai_agent_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_type VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
    subject_type VARCHAR(48) NULL,
    subject_id BIGINT NULL,
    user_id BIGINT NULL,
    prompt TEXT NULL,
    summary TEXT NULL,
    verification_status VARCHAR(32) NULL,
    confidence DECIMAL(5, 4) NULL,
    final_output_json JSON NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ai_agent_run_workflow (workflow_type, created_at),
    KEY idx_ai_agent_run_subject (subject_type, subject_id),
    KEY idx_ai_agent_run_user (user_id, created_at),
    CONSTRAINT fk_ai_agent_run_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_agent_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    agent_name VARCHAR(64) NOT NULL,
    agent_role VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SUCCESS',
    summary TEXT NULL,
    input_json JSON NULL,
    output_json JSON NULL,
    evidence_json JSON NULL,
    error_message VARCHAR(1000) NULL,
    confidence DECIMAL(5, 4) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    KEY idx_ai_agent_step_run_order (run_id, step_order),
    CONSTRAINT fk_ai_agent_step_run FOREIGN KEY (run_id) REFERENCES ai_agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_research_report'
       AND COLUMN_NAME = 'agent_run_id') = 0,
    'ALTER TABLE ai_research_report ADD COLUMN agent_run_id BIGINT NULL AFTER evidence_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_review_ticket'
       AND COLUMN_NAME = 'agent_run_id') = 0,
    'ALTER TABLE ai_review_ticket ADD COLUMN agent_run_id BIGINT NULL AFTER review_evidence_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
