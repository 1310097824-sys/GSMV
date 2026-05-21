CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NULL,
    media_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    metadata_json JSON NULL,
    uploaded_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_document_source (source_type, source_id),
    KEY idx_rag_document_status (status),
    KEY idx_rag_document_media (media_id),
    KEY idx_rag_document_uploaded_by (uploaded_by),
    CONSTRAINT fk_rag_document_media FOREIGN KEY (media_id) REFERENCES media_file(id),
    CONSTRAINT fk_rag_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NULL,
    chunk_index INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(512) NULL,
    content MEDIUMTEXT NOT NULL,
    embedding_json MEDIUMTEXT NULL,
    character_count INT NOT NULL DEFAULT 0,
    metadata_json JSON NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'READY',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_chunk_document_index (document_id, chunk_index),
    KEY idx_rag_chunk_source (source_type, source_id),
    KEY idx_rag_chunk_status (status),
    CONSTRAINT fk_rag_chunk_document FOREIGN KEY (document_id) REFERENCES rag_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_index_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
    target_source_type VARCHAR(32) NULL,
    target_source_id BIGINT NULL,
    total_documents INT NOT NULL DEFAULT 0,
    total_chunks INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_rag_job_status (status),
    KEY idx_rag_job_created_at (created_at),
    CONSTRAINT fk_rag_job_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (code, name, description)
SELECT 'RAG_READ', 'RAG知识库查看', '允许查看RAG知识库、分块与检索结果'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'RAG_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'RAG_MANAGE', 'RAG知识库管理', '允许上传资料、重建索引和删除知识库文档'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'RAG_MANAGE');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'ADMIN'
  AND p.code IN ('RAG_READ', 'RAG_MANAGE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'RESEARCHER'
  AND p.code IN ('RAG_READ', 'RAG_MANAGE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
