-- Preserve every returned advisory duplicate candidate while retaining the existing top match.
USE htc_service_portal;
SET @ai_candidates_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AI_RECOMMENDATION'
      AND COLUMN_NAME = 'Duplicate_Candidates_JSON');
SET @ai_candidates_sql = IF(@ai_candidates_exists = 0,
    'ALTER TABLE AI_RECOMMENDATION ADD COLUMN Duplicate_Candidates_JSON JSON NULL',
    'SELECT 1');
PREPARE ai_candidates_statement FROM @ai_candidates_sql;
EXECUTE ai_candidates_statement;
DEALLOCATE PREPARE ai_candidates_statement;
