-- Read-only verification for the HTC Service Portal database.
-- This script does not create, update, or delete data.

USE htc_service_portal;

-- =========================================================
-- 1. VERIFY EACH REQUIRED TABLE
-- =========================================================

SELECT
    expected.Sort_Order,
    expected.Expected_Table,
    CASE
        WHEN actual.TABLE_NAME IS NULL THEN 'MISSING'
        ELSE 'PASS'
    END AS Verification_Result,
    actual.ENGINE,
    actual.TABLE_COLLATION
FROM (
    SELECT 1 AS Sort_Order, 'DEPARTMENT' AS Expected_Table
    UNION ALL SELECT 2, 'PERSONNEL_ROLE'
    UNION ALL SELECT 3, 'SCHOOL_PERSONNEL'
    UNION ALL SELECT 4, 'PERSONNEL_ROLE_ASSIGNMENT'
    UNION ALL SELECT 5, 'SERVICE_CATEGORY'
    UNION ALL SELECT 6, 'REQUEST_NUMBER_SEQUENCE'
    UNION ALL SELECT 7, 'SERVICE_REQUEST'
    UNION ALL SELECT 8, 'REQUEST_STATUS_HISTORY'
    UNION ALL SELECT 9, 'AI_RECOMMENDATION'
    UNION ALL SELECT 10, 'REQUEST_APPROVAL'
    UNION ALL SELECT 11, 'WORK_ORDER_NUMBER_SEQUENCE'
    UNION ALL SELECT 12, 'WORK_ORDER'
    UNION ALL SELECT 13, 'WORK_ORDER_ASSIGNMENT'
    UNION ALL SELECT 14, 'WORK_ORDER_PROGRESS'
    UNION ALL SELECT 15, 'ATTACHMENT'
    UNION ALL SELECT 16, 'NOTIFICATION'
    UNION ALL SELECT 17, 'AUDIT_LOG'
    UNION ALL SELECT 18, 'REQUEST_DUPLICATE_LINK'
    UNION ALL SELECT 19, 'ACCOUNT_REVIEW'
    UNION ALL SELECT 20, 'ACCOUNT_LOGIN_SECURITY'
) AS expected
LEFT JOIN INFORMATION_SCHEMA.TABLES AS actual
    ON actual.TABLE_SCHEMA = DATABASE()
    AND UPPER(actual.TABLE_NAME) = expected.Expected_Table
ORDER BY expected.Sort_Order;

-- =========================================================
-- 2. DISPLAY THE FINAL VERIFICATION SUMMARY
-- =========================================================

SELECT
    20 AS Expected_Table_Count,

    COUNT(DISTINCT actual.TABLE_NAME)
        AS Present_Table_Count,

    20 - COUNT(DISTINCT actual.TABLE_NAME)
        AS Missing_Table_Count,

    COUNT(
        DISTINCT CASE
            WHEN UPPER(actual.ENGINE) <> 'INNODB'
            THEN actual.TABLE_NAME
        END
    ) AS Non_InnoDB_Table_Count,

    COUNT(
        DISTINCT CASE
            WHEN primary_keys.TABLE_NAME IS NULL
            THEN actual.TABLE_NAME
        END
    ) AS Missing_Primary_Key_Count

FROM INFORMATION_SCHEMA.TABLES AS actual

LEFT JOIN (
    SELECT
        TABLE_SCHEMA,
        TABLE_NAME
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_TYPE = 'PRIMARY KEY'
) AS primary_keys
    ON primary_keys.TABLE_SCHEMA = actual.TABLE_SCHEMA
    AND primary_keys.TABLE_NAME = actual.TABLE_NAME

WHERE actual.TABLE_SCHEMA = DATABASE()
  AND actual.TABLE_TYPE = 'BASE TABLE'
  AND UPPER(actual.TABLE_NAME) IN (
        'DEPARTMENT',
        'PERSONNEL_ROLE',
        'SCHOOL_PERSONNEL',
        'PERSONNEL_ROLE_ASSIGNMENT',
        'SERVICE_CATEGORY',
        'REQUEST_NUMBER_SEQUENCE',
        'SERVICE_REQUEST',
        'REQUEST_STATUS_HISTORY',
        'AI_RECOMMENDATION',
        'REQUEST_APPROVAL',
        'WORK_ORDER_NUMBER_SEQUENCE',
        'WORK_ORDER',
        'WORK_ORDER_ASSIGNMENT',
        'WORK_ORDER_PROGRESS',
        'ATTACHMENT',
        'NOTIFICATION',
        'AUDIT_LOG',
        'REQUEST_DUPLICATE_LINK',
        'ACCOUNT_REVIEW',
        'ACCOUNT_LOGIN_SECURITY'
  );

-- =========================================================
-- 3. VERIFY THE FOREIGN-KEY RELATIONSHIPS
-- =========================================================

SELECT
    key_usage.TABLE_NAME,
    key_usage.CONSTRAINT_NAME,
    key_usage.COLUMN_NAME,
    key_usage.REFERENCED_TABLE_NAME,
    key_usage.REFERENCED_COLUMN_NAME,
    rules.UPDATE_RULE,
    rules.DELETE_RULE
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS key_usage
INNER JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS AS rules
    ON rules.CONSTRAINT_SCHEMA = key_usage.CONSTRAINT_SCHEMA
    AND rules.TABLE_NAME = key_usage.TABLE_NAME
    AND rules.CONSTRAINT_NAME = key_usage.CONSTRAINT_NAME
WHERE key_usage.CONSTRAINT_SCHEMA = DATABASE()
  AND key_usage.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY
    key_usage.TABLE_NAME,
    key_usage.CONSTRAINT_NAME,
    key_usage.ORDINAL_POSITION;