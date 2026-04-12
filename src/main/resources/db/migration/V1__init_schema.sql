CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NULL,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_role_perm_perm (permission_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS taxon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NULL,
    `rank` VARCHAR(16) NOT NULL,
    scientific_name VARCHAR(128) NOT NULL,
    chinese_name VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_taxon_unique (parent_id, `rank`, scientific_name),
    KEY idx_taxon_parent (parent_id),
    CONSTRAINT fk_taxon_parent FOREIGN KEY (parent_id) REFERENCES taxon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS species (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    taxon_id BIGINT NOT NULL,
    protection_level VARCHAR(32) NULL,
    iucn_status VARCHAR(16) NULL,
    description TEXT NULL,
    morphology TEXT NULL,
    habitat TEXT NULL,
    distribution TEXT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_species_taxon (taxon_id),
    KEY idx_species_protection (protection_level),
    KEY idx_species_iucn (iucn_status),
    CONSTRAINT fk_species_taxon FOREIGN KEY (taxon_id) REFERENCES taxon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ecosystem (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NULL,
    description TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ecosystem_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS observation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ecosystem_id BIGINT NOT NULL,
    observer_user_id BIGINT NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    location_lat DECIMAL(10, 7) NOT NULL,
    location_lng DECIMAL(10, 7) NOT NULL,
    location_point POINT NOT NULL SRID 4326,
    location_name VARCHAR(128) NULL,
    env_json JSON NULL,
    note TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_obs_time (observed_at),
    KEY idx_obs_ecosystem (ecosystem_id),
    KEY idx_obs_observer (observer_user_id),
    SPATIAL INDEX sp_idx_obs_point (location_point),
    CONSTRAINT fk_obs_ecosystem FOREIGN KEY (ecosystem_id) REFERENCES ecosystem(id),
    CONSTRAINT fk_obs_observer FOREIGN KEY (observer_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS observation_species (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    observation_id BIGINT NOT NULL,
    species_id BIGINT NOT NULL,
    count_estimated INT NULL,
    comment VARCHAR(255) NULL,
    UNIQUE KEY uk_obs_species (observation_id, species_id),
    KEY idx_obs_species_species (species_id),
    CONSTRAINT fk_obs_species_obs FOREIGN KEY (observation_id) REFERENCES observation(id),
    CONSTRAINT fk_obs_species_species FOREIGN KEY (species_id) REFERENCES species(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS media_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NULL,
    uploaded_by BIGINT NULL,
    uploaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_media_business (business_type, business_id),
    KEY idx_media_uploaded_by (uploaded_by),
    CONSTRAINT fk_media_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    module VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NULL,
    entity_id BIGINT NULL,
    request_id VARCHAR(64) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    success TINYINT NOT NULL DEFAULT 1,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_time (created_at),
    KEY idx_audit_user (user_id),
    KEY idx_audit_module_entity (module, entity_type, entity_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
