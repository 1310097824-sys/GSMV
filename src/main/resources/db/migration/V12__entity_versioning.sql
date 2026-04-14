CREATE TABLE IF NOT EXISTS entity_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    action VARCHAR(32) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    diff_json LONGTEXT NOT NULL,
    changed_by BIGINT NOT NULL,
    rollback_source_version_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_entity_version UNIQUE (entity_type, entity_id, version_no),
    CONSTRAINT fk_entity_version_user FOREIGN KEY (changed_by) REFERENCES sys_user(id),
    CONSTRAINT fk_entity_version_source FOREIGN KEY (rollback_source_version_id) REFERENCES entity_version(id)
);

CREATE INDEX idx_entity_version_lookup
    ON entity_version (entity_type, entity_id, version_no DESC);
