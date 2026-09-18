-- Migration 003: Request status history
-- Safe to rerun.
-- Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS REQUEST_STATUS_HISTORY (
    History_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Request_ID BIGINT UNSIGNED NOT NULL,

    Previous_Status VARCHAR(30) NULL,

    New_Status VARCHAR(30) NOT NULL,

    Changed_By INT UNSIGNED NOT NULL,

    Changed_By_Role_ID TINYINT UNSIGNED NOT NULL,

    Change_Reason VARCHAR(1000) NULL,

    Changed_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (History_ID),

    KEY IX_REQUEST_HISTORY_REQUEST_TIME (
        Request_ID,
        Changed_At,
        History_ID
    ),

    KEY IX_REQUEST_HISTORY_NEW_STATUS (
        New_Status,
        Changed_At
    ),

    KEY IX_REQUEST_HISTORY_ACTOR (
        Changed_By,
        Changed_At
    ),

    CONSTRAINT FK_REQUEST_HISTORY_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_REQUEST_HISTORY_PERSONNEL
        FOREIGN KEY (Changed_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_REQUEST_HISTORY_ROLE
        FOREIGN KEY (Changed_By_Role_ID)
        REFERENCES PERSONNEL_ROLE (Role_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_REQUEST_HISTORY_PREVIOUS_STATUS
        CHECK (
            Previous_Status IS NULL
            OR Previous_Status IN (
                'Submitted',
                'Under Review',
                'Awaiting Approval',
                'Approved',
                'Rejected',
                'Assigned',
                'In Progress',
                'Completed',
                'Closed',
                'Cancelled',
                'Duplicate'
            )
        ),

    CONSTRAINT CK_REQUEST_HISTORY_NEW_STATUS
        CHECK (
            New_Status IN (
                'Submitted',
                'Under Review',
                'Awaiting Approval',
                'Approved',
                'Rejected',
                'Assigned',
                'In Progress',
                'Completed',
                'Closed',
                'Cancelled',
                'Duplicate'
            )
        ),

    CONSTRAINT CK_REQUEST_HISTORY_STATUS_CHANGED
        CHECK (
            Previous_Status IS NULL
            OR Previous_Status <> New_Status
        ),

    CONSTRAINT CK_REQUEST_HISTORY_REASON_NOT_EMPTY
        CHECK (
            Change_Reason IS NULL
            OR CHAR_LENGTH(TRIM(Change_Reason)) >= 3
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE REQUEST_STATUS_HISTORY;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'REQUEST_STATUS_HISTORY';

SELECT
    COUNT(*) AS Existing_History_Count
FROM REQUEST_STATUS_HISTORY;