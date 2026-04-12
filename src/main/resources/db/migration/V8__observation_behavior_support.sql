SET @behavior_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'observation_species'
      AND COLUMN_NAME = 'behavior'
);

SET @behavior_column_sql = IF(
    @behavior_column_exists = 0,
    'ALTER TABLE observation_species ADD COLUMN behavior VARCHAR(255) NULL AFTER count_estimated',
    'SELECT 1'
);

PREPARE behavior_stmt FROM @behavior_column_sql;
EXECUTE behavior_stmt;
DEALLOCATE PREPARE behavior_stmt;
