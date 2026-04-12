SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'habit') = 0,
    'ALTER TABLE species ADD COLUMN habit TEXT NULL AFTER morphology',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'distribution_lat') = 0,
    'ALTER TABLE species ADD COLUMN distribution_lat DECIMAL(10, 7) NULL AFTER distribution',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'distribution_lng') = 0,
    'ALTER TABLE species ADD COLUMN distribution_lng DECIMAL(10, 7) NULL AFTER distribution_lat',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'geo_range_text') = 0,
    'ALTER TABLE species ADD COLUMN geo_range_text TEXT NULL AFTER distribution_lng',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'video_url') = 0,
    'ALTER TABLE species ADD COLUMN video_url VARCHAR(512) NULL AFTER geo_range_text',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'species'
       AND COLUMN_NAME = 'reference_text') = 0,
    'ALTER TABLE species ADD COLUMN reference_text TEXT NULL AFTER video_url',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
