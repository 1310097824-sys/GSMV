SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio') = 0,
  'ALTER TABLE sys_user ADD COLUMN bio TEXT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'approval_status') = 0,
  'ALTER TABLE sys_user ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT ''APPROVED''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'approval_remark') = 0,
  'ALTER TABLE sys_user ADD COLUMN approval_remark VARCHAR(255) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'reviewed_by') = 0,
  'ALTER TABLE sys_user ADD COLUMN reviewed_by BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'reviewed_at') = 0,
  'ALTER TABLE sys_user ADD COLUMN reviewed_at DATETIME(3) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_media_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar_media_id BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET approval_status = 'APPROVED'
WHERE approval_status IS NULL OR approval_status = '';

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_approval_status') = 0,
  'CREATE INDEX idx_sys_user_approval_status ON sys_user (approval_status)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_role (code, name, description)
SELECT 'STUDENT', '学生', '已审核通过的学生用户'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'STUDENT');

INSERT INTO sys_role (code, name, description)
SELECT 'PUBLIC', '公众', '已审核通过的公众用户'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'PUBLIC');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'STUDENT'
  AND p.code IN ('SPECIES_READ', 'OBS_READ', 'OBS_WRITE', 'ECOSYSTEM_READ', 'REPORT_READ', 'MEDIA_READ', 'MEDIA_WRITE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'PUBLIC'
  AND p.code IN ('SPECIES_READ', 'OBS_READ', 'ECOSYSTEM_READ', 'REPORT_READ', 'MEDIA_READ')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
