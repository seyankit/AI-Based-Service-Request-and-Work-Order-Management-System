-- Migration 013: Privileged account review records
-- Requester accounts using Role_ID 1 do not require this review.
-- Roles 2, 3, and 4 remain Pending until an authorized
-- Service Administrator reviews them.
--
-- Approved decision: SCHOOL_PERSONNEL.Account_Status becomes Active.
-- Rejected decision: SCHOOL_PERSONNEL.Account_Status becomes Inactive.
-- These changes will later occur in one Java database transaction.
--
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS ACCOUNT_REVIEW (
    Account_Review_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Personnel_ID INT UNSIGNED NOT NULL,
    Requested_Role_ID TINYINT UNSIGNED NOT NULL,
    Requested_Department_ID INT UNSIGNED NOT NULL,

    Review_Status VARCHAR(20) NOT NULL DEFAULT 'Pending',

    Reviewed_By_ID INT UNSIGNED NULL,
    Decision_Remarks VARCHAR(1000) NULL,

    Submitted_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Reviewed_At TIMESTAMP NULL DEFAULT NULL,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Account_Review_ID),

    -- Prevents the same registration from being reviewed twice.
    CONSTRAINT UQ_ACCOUNT_REVIEW_PERSONNEL
        UNIQUE (Personnel_ID),

    INDEX IX_ACCOUNT_REVIEW_QUEUE (
        Review_Status,
        Submitted_At
    ),

    INDEX IX_ACCOUNT_REVIEW_ROLE (
        Requested_Role_ID,
        Review_Status
    ),

    INDEX IX_ACCOUNT_REVIEW_DEPARTMENT (
        Requested_Department_ID,
        Review_Status
    ),

    INDEX IX_ACCOUNT_REVIEW_REVIEWER (
        Reviewed_By_ID,
        Reviewed_At
    ),

    CONSTRAINT FK_ACCOUNT_REVIEW_PERSONNEL
        FOREIGN KEY (Personnel_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ACCOUNT_REVIEW_ROLE
        FOREIGN KEY (Requested_Role_ID)
        REFERENCES PERSONNEL_ROLE (Role_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ACCOUNT_REVIEW_DEPARTMENT
        FOREIGN KEY (Requested_Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ACCOUNT_REVIEW_REVIEWER
        FOREIGN KEY (Reviewed_By_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CHK_ACCOUNT_REVIEW_PRIVILEGED_ROLE
        CHECK (
            Requested_Role_ID IN (2, 3, 4)
        ),

    CONSTRAINT CHK_ACCOUNT_REVIEW_STATUS
        CHECK (
            Review_Status IN (
                'Pending',
                'Approved',
                'Rejected'
            )
        ),

    CONSTRAINT CHK_ACCOUNT_REVIEW_DECISION_STATE
        CHECK (
            (
                Review_Status = 'Pending'
                AND Reviewed_By_ID IS NULL
                AND Decision_Remarks IS NULL
                AND Reviewed_At IS NULL
            )
            OR
            (
                Review_Status IN ('Approved', 'Rejected')
                AND Reviewed_By_ID IS NOT NULL
                AND CHAR_LENGTH(TRIM(Decision_Remarks))
                    BETWEEN 5 AND 1000
                AND Reviewed_At IS NOT NULL
            )
        ),

    CONSTRAINT CHK_ACCOUNT_REVIEW_DIFFERENT_REVIEWER
        CHECK (
            Reviewed_By_ID IS NULL
            OR Reviewed_By_ID <> Personnel_ID
        )
) ENGINE = InnoDB;

SHOW CREATE TABLE ACCOUNT_REVIEW;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ACCOUNT_REVIEW';

SELECT
    COUNT(*) AS Existing_Account_Review_Count
FROM ACCOUNT_REVIEW;