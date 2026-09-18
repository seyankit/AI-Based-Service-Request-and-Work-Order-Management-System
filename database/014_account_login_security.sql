-- Migration 014: Account login security and temporary lockout
-- One security record may exist for each school-personnel account.
-- The Java backend will create the record when needed.
--
-- Planned policy:
-- 1. Record failed login attempts.
-- 2. Temporarily lock after 5 failed attempts.
-- 3. Do not reveal whether an entered email exists.
-- 4. Reset failed-attempt information after successful login.
--
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS ACCOUNT_LOGIN_SECURITY (
    Personnel_ID INT UNSIGNED NOT NULL,

    Failed_Attempt_Count SMALLINT UNSIGNED NOT NULL DEFAULT 0,

    Failure_Window_Started_At TIMESTAMP NULL DEFAULT NULL,
    Last_Failed_Attempt_At TIMESTAMP NULL DEFAULT NULL,
    Locked_Until TIMESTAMP NULL DEFAULT NULL,

    Last_Successful_Login_At TIMESTAMP NULL DEFAULT NULL,

    Created_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Personnel_ID),

    INDEX IX_LOGIN_SECURITY_LOCKED_UNTIL (
        Locked_Until
    ),

    CONSTRAINT FK_LOGIN_SECURITY_PERSONNEL
        FOREIGN KEY (Personnel_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CHK_LOGIN_SECURITY_FAILURE_STATE
        CHECK (
            (
                Failed_Attempt_Count = 0
                AND Failure_Window_Started_At IS NULL
                AND Last_Failed_Attempt_At IS NULL
                AND Locked_Until IS NULL
            )
            OR
            (
                Failed_Attempt_Count > 0
                AND Failure_Window_Started_At IS NOT NULL
                AND Last_Failed_Attempt_At IS NOT NULL
            )
        ),

    CONSTRAINT CHK_LOGIN_SECURITY_FAILURE_ORDER
        CHECK (
            Last_Failed_Attempt_At IS NULL
            OR Failure_Window_Started_At
                <= Last_Failed_Attempt_At
        ),

    CONSTRAINT CHK_LOGIN_SECURITY_LOCK_STATE
        CHECK (
            Locked_Until IS NULL
            OR Failed_Attempt_Count >= 5
        )
) ENGINE = InnoDB;

SHOW CREATE TABLE ACCOUNT_LOGIN_SECURITY;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ACCOUNT_LOGIN_SECURITY';

SELECT
    COUNT(*) AS Existing_Login_Security_Count
FROM ACCOUNT_LOGIN_SECURITY;