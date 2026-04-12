CREATE TABLE IF NOT EXISTS ai_review_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL DEFAULT 'SPECIES_IDENTIFY',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    resolution_code VARCHAR(24) NULL,
    submitted_by BIGINT NOT NULL,
    reviewer_user_id BIGINT NULL,
    image_media_id BIGINT NULL,
    likely_chinese_name VARCHAR(128) NULL,
    likely_scientific_name VARCHAR(128) NULL,
    confidence DECIMAL(5, 4) NOT NULL DEFAULT 0,
    needs_human_review TINYINT NOT NULL DEFAULT 1,
    reasoning TEXT NULL,
    candidate_json JSON NULL,
    related_species_json JSON NULL,
    submit_note TEXT NULL,
    final_species_id BIGINT NULL,
    final_chinese_name VARCHAR(128) NULL,
    final_scientific_name VARCHAR(128) NULL,
    review_note TEXT NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_ai_review_status (status),
    KEY idx_ai_review_submitter (submitted_by),
    KEY idx_ai_review_reviewer (reviewer_user_id),
    KEY idx_ai_review_created (created_at),
    CONSTRAINT fk_ai_review_submitter FOREIGN KEY (submitted_by) REFERENCES sys_user(id),
    CONSTRAINT fk_ai_review_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_ai_review_image FOREIGN KEY (image_media_id) REFERENCES media_file(id),
    CONSTRAINT fk_ai_review_species FOREIGN KEY (final_species_id) REFERENCES species(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (code, name, description)
SELECT 'AI_REVIEW_READ', '查看AI复核工单', '允许查看AI识图人工复核工单'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'AI_REVIEW_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'AI_REVIEW_WRITE', '处理AI复核工单', '允许开始复核并提交AI人工复核结论'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'AI_REVIEW_WRITE');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'ADMIN'
  AND p.code IN ('AI_REVIEW_READ', 'AI_REVIEW_WRITE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'RESEARCHER'
  AND p.code IN ('AI_REVIEW_READ', 'AI_REVIEW_WRITE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
