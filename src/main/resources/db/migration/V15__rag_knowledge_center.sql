SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'rag_chunk'
       AND COLUMN_NAME = 'vector_point_id') = 0,
    'ALTER TABLE rag_chunk ADD COLUMN vector_point_id VARCHAR(128) NULL AFTER embedding_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'rag_chunk'
       AND COLUMN_NAME = 'embedding_model') = 0,
    'ALTER TABLE rag_chunk ADD COLUMN embedding_model VARCHAR(64) NULL AFTER vector_point_id',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'rag_chunk'
       AND COLUMN_NAME = 'embedding_dimension') = 0,
    'ALTER TABLE rag_chunk ADD COLUMN embedding_dimension INT NULL AFTER embedding_model',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'rag_chunk'
       AND COLUMN_NAME = 'embedding_status') = 0,
    'ALTER TABLE rag_chunk ADD COLUMN embedding_status VARCHAR(24) NOT NULL DEFAULT ''PENDING'' AFTER embedding_dimension',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'rag_chunk'
       AND COLUMN_NAME = 'embedding_error') = 0,
    'ALTER TABLE rag_chunk ADD COLUMN embedding_error VARCHAR(1000) NULL AFTER embedding_status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS rag_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_type VARCHAR(48) NOT NULL,
    base_url VARCHAR(512) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    config_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_source_code (code),
    KEY idx_rag_source_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_ingest_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
    source_code VARCHAR(48) NULL,
    title VARCHAR(255) NULL,
    total_items INT NOT NULL DEFAULT 0,
    processed_items INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_rag_ingest_job_status (status),
    KEY idx_rag_ingest_job_created (created_at),
    KEY idx_rag_ingest_job_source (source_code),
    CONSTRAINT fk_rag_ingest_job_user FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_ingest_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    source_type VARCHAR(48) NOT NULL,
    source_code VARCHAR(48) NULL,
    external_id VARCHAR(255) NULL,
    source_url VARCHAR(1000) NULL,
    local_path VARCHAR(1000) NULL,
    media_id BIGINT NULL,
    rag_document_id BIGINT NULL,
    title VARCHAR(255) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000) NULL,
    metadata_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_rag_ingest_item_job (job_id),
    KEY idx_rag_ingest_item_status (status),
    KEY idx_rag_ingest_item_source (source_code, external_id),
    CONSTRAINT fk_rag_ingest_item_job FOREIGN KEY (job_id) REFERENCES rag_ingest_job(id),
    CONSTRAINT fk_rag_ingest_item_media FOREIGN KEY (media_id) REFERENCES media_file(id),
    CONSTRAINT fk_rag_ingest_item_document FOREIGN KEY (rag_document_id) REFERENCES rag_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_review_ticket'
       AND COLUMN_NAME = 'initial_recognition_json') = 0,
    'ALTER TABLE ai_review_ticket ADD COLUMN initial_recognition_json JSON NULL AFTER related_species_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_review_ticket'
       AND COLUMN_NAME = 'rag_evidence_json') = 0,
    'ALTER TABLE ai_review_ticket ADD COLUMN rag_evidence_json JSON NULL AFTER initial_recognition_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_review_ticket'
       AND COLUMN_NAME = 'review_evidence_json') = 0,
    'ALTER TABLE ai_review_ticket ADD COLUMN review_evidence_json JSON NULL AFTER rag_evidence_json',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'LOCAL_FOLDER', 'Local folder corpus', 'LOCAL_FOLDER', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'LOCAL_FOLDER');

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'OBIS', 'Ocean Biodiversity Information System', 'EXTERNAL_API', 'https://api.obis.org', 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'OBIS');

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'GBIF', 'Global Biodiversity Information Facility', 'EXTERNAL_API', 'https://api.gbif.org', 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'GBIF');

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'WORMS', 'World Register of Marine Species', 'EXTERNAL_API', 'https://www.marinespecies.org/rest', 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'WORMS');

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'IUCN', 'IUCN Red List', 'EXTERNAL_API', 'https://api.iucnredlist.org', 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'IUCN');

INSERT INTO rag_source (code, name, source_type, base_url, enabled)
SELECT 'WEB_PDF', 'Whitelist web documents', 'WEB_DOCUMENT', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM rag_source WHERE code = 'WEB_PDF');
