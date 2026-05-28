package com.gsmv.bootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class RagSchemaBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSchemaBootstrapRunner.class);

    private final DataSource dataSource;

    public RagSchemaBootstrapRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            ensureRagSchema(connection);
            ensureAssistantSchema(connection);
            ensureRagPermissions(connection);
        } catch (SQLException exception) {
            log.warn("RAG schema bootstrap failed: {}", exception.getMessage(), exception);
        }
    }

    private void ensureRagSchema(Connection connection) throws SQLException {
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
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
                    vector_point_id VARCHAR(128) NULL,
                    embedding_model VARCHAR(64) NULL,
                    embedding_dimension INT NULL,
                    embedding_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                    embedding_error VARCHAR(1000) NULL,
                    character_count INT NOT NULL DEFAULT 0,
                    metadata_json JSON NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'READY',
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    UNIQUE KEY uk_rag_chunk_document_index (document_id, chunk_index),
                    KEY idx_rag_chunk_source (source_type, source_id),
                    KEY idx_rag_chunk_status (status),
                    CONSTRAINT fk_rag_chunk_document FOREIGN KEY (document_id) REFERENCES rag_document(id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        ensureColumn(connection, "rag_chunk", "vector_point_id",
                "ALTER TABLE rag_chunk ADD COLUMN vector_point_id VARCHAR(128) NULL AFTER embedding_json");
        ensureColumn(connection, "rag_chunk", "embedding_model",
                "ALTER TABLE rag_chunk ADD COLUMN embedding_model VARCHAR(64) NULL AFTER vector_point_id");
        ensureColumn(connection, "rag_chunk", "embedding_dimension",
                "ALTER TABLE rag_chunk ADD COLUMN embedding_dimension INT NULL AFTER embedding_model");
        ensureColumn(connection, "rag_chunk", "embedding_status",
                "ALTER TABLE rag_chunk ADD COLUMN embedding_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER embedding_dimension");
        ensureColumn(connection, "rag_chunk", "embedding_error",
                "ALTER TABLE rag_chunk ADD COLUMN embedding_error VARCHAR(1000) NULL AFTER embedding_status");
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        ensureColumn(connection, "ai_review_ticket", "initial_recognition_json",
                "ALTER TABLE ai_review_ticket ADD COLUMN initial_recognition_json JSON NULL AFTER related_species_json");
        ensureColumn(connection, "ai_review_ticket", "rag_evidence_json",
                "ALTER TABLE ai_review_ticket ADD COLUMN rag_evidence_json JSON NULL AFTER initial_recognition_json");
        ensureColumn(connection, "ai_review_ticket", "review_evidence_json",
                "ALTER TABLE ai_review_ticket ADD COLUMN review_evidence_json JSON NULL AFTER rag_evidence_json");
        seedSources(connection);
    }

    private void ensureAssistantSchema(Connection connection) throws SQLException {
        execute(connection, """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureRagPermissions(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO sys_permission (code, name, description)
                SELECT 'RAG_READ', 'RAG知识库查看', '允许查看RAG知识库、分块与检索结果'
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'RAG_READ')
                """);
        execute(connection, """
                INSERT INTO sys_permission (code, name, description)
                SELECT 'RAG_MANAGE', 'RAG知识库管理', '允许上传资料、重建索引和删除知识库文档'
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'RAG_MANAGE')
                """);
        execute(connection, """
                INSERT INTO sys_role_permission (role_id, permission_id)
                SELECT r.id, p.id
                FROM sys_role r
                JOIN sys_permission p ON 1 = 1
                WHERE r.code = 'ADMIN'
                  AND p.code IN ('RAG_READ', 'RAG_MANAGE')
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
        execute(connection, """
                INSERT INTO sys_role_permission (role_id, permission_id)
                SELECT r.id, p.id
                FROM sys_role r
                JOIN sys_permission p ON 1 = 1
                WHERE r.code = 'RESEARCHER'
                  AND p.code IN ('RAG_READ', 'RAG_MANAGE')
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String ddl) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    execute(connection, ddl);
                }
            }
        }
    }

    private void seedSources(Connection connection) throws SQLException {
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('LOCAL_FOLDER', 'Local folder corpus', 'LOCAL_FOLDER', NULL, 1)");
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('OBIS', 'Ocean Biodiversity Information System', 'EXTERNAL_API', 'https://api.obis.org', 1)");
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('GBIF', 'Global Biodiversity Information Facility', 'EXTERNAL_API', 'https://api.gbif.org', 1)");
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('WORMS', 'World Register of Marine Species', 'EXTERNAL_API', 'https://www.marinespecies.org/rest', 1)");
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('IUCN', 'IUCN Red List', 'EXTERNAL_API', 'https://api.iucnredlist.org', 1)");
        execute(connection, "INSERT IGNORE INTO rag_source (code, name, source_type, base_url, enabled) VALUES ('WEB_PDF', 'Whitelist web documents', 'WEB_DOCUMENT', NULL, 1)");
    }
}
