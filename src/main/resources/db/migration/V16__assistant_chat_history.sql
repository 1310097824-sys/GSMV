CREATE TABLE IF NOT EXISTS ai_assistant_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    structured_query_json JSON NULL,
    highlights_json JSON NULL,
    evidence_json JSON NULL,
    cache_hit TINYINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ai_assistant_message_user_time (user_id, created_at, id),
    CONSTRAINT fk_ai_assistant_message_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
