CREATE TABLE IF NOT EXISTS ai_research_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_type VARCHAR(24) NOT NULL DEFAULT 'MONTHLY',
    days INT NOT NULL DEFAULT 30,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    highlights_json JSON NOT NULL,
    risks_json JSON NOT NULL,
    recommendations_json JSON NOT NULL,
    evidence_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ai_report_created (created_at),
    KEY idx_ai_report_type (report_type),
    CONSTRAINT fk_ai_report_user FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
