-- Migration 005: Request approvals
-- Every valid request requires Department Head approval.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS REQUEST_APPROVAL (
    Approval_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Request_ID BIGINT UNSIGNED NOT NULL,

    Approval_Sequence SMALLINT UNSIGNED NOT NULL DEFAULT 1,

    Is_Current BOOLEAN NOT NULL DEFAULT TRUE,

    Department_ID INT UNSIGNED NOT NULL,

    Requested_By INT UNSIGNED NOT NULL,

    Requested_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Decision VARCHAR(20) NOT NULL
        DEFAULT 'Pending',

    Approver_ID INT UNSIGNED NULL,

    Decision_Remarks VARCHAR(1000) NULL,

    Decided_At TIMESTAMP NULL,

    PRIMARY KEY (Approval_ID),

    UNIQUE KEY UQ_REQUEST_APPROVAL_SEQUENCE (
        Request_ID,
        Approval_Sequence
    ),

    KEY IX_REQUEST_APPROVAL_CURRENT (
        Request_ID,
        Is_Current
    ),

    KEY IX_REQUEST_APPROVAL_DEPARTMENT_QUEUE (
        Department_ID,
        Decision,
        Requested_At
    ),

    KEY IX_REQUEST_APPROVAL_REQUESTED_BY (
        Requested_By,
        Requested_At
    ),

    KEY IX_REQUEST_APPROVAL_APPROVER (
        Approver_ID,
        Decided_At
    ),

    CONSTRAINT FK_REQUEST_APPROVAL_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_REQUEST_APPROVAL_DEPARTMENT
        FOREIGN KEY (Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_REQUEST_APPROVAL_REQUESTED_BY
        FOREIGN KEY (Requested_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_REQUEST_APPROVAL_APPROVER
        FOREIGN KEY (Approver_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_REQUEST_APPROVAL_SEQUENCE
        CHECK (
            Approval_Sequence >= 1
        ),

    CONSTRAINT CK_REQUEST_APPROVAL_CURRENT
        CHECK (
            Is_Current IN (FALSE, TRUE)
        ),

    CONSTRAINT CK_REQUEST_APPROVAL_DECISION
        CHECK (
            Decision IN (
                'Pending',
                'Approved',
                'Rejected'
            )
        ),

    CONSTRAINT CK_REQUEST_APPROVAL_DECISION_DATA
        CHECK (
            (
                Decision = 'Pending'
                AND Approver_ID IS NULL
                AND Decision_Remarks IS NULL
                AND Decided_At IS NULL
            )
            OR
            (
                Decision IN ('Approved', 'Rejected')
                AND Approver_ID IS NOT NULL
                AND Decision_Remarks IS NOT NULL
                AND CHAR_LENGTH(TRIM(Decision_Remarks)) >= 3
                AND Decided_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_REQUEST_APPROVAL_DECISION_TIME
        CHECK (
            Decided_At IS NULL
            OR Decided_At >= Requested_At
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE REQUEST_APPROVAL;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'REQUEST_APPROVAL';

SELECT
    COUNT(*) AS Existing_Approval_Count
FROM REQUEST_APPROVAL;