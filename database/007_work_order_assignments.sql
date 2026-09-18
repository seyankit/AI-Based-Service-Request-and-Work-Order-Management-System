-- Migration 007: Work-order technician assignments
-- Preserves current and previous technician assignments.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS WORK_ORDER_ASSIGNMENT (
    Assignment_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Work_Order_ID BIGINT UNSIGNED NOT NULL,

    Assignment_Sequence SMALLINT UNSIGNED NOT NULL DEFAULT 1,

    Technician_ID INT UNSIGNED NOT NULL,

    Assigned_By INT UNSIGNED NOT NULL,

    Assignment_Notes VARCHAR(1000) NULL,

    Is_Current BOOLEAN NOT NULL DEFAULT TRUE,

    Assigned_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Acknowledged_At TIMESTAMP NULL,

    Ended_At TIMESTAMP NULL,

    End_Reason VARCHAR(1000) NULL,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Assignment_ID),

    UNIQUE KEY UQ_WORK_ORDER_ASSIGNMENT_SEQUENCE (
        Work_Order_ID,
        Assignment_Sequence
    ),

    KEY IX_WORK_ORDER_ASSIGNMENT_CURRENT (
        Work_Order_ID,
        Is_Current
    ),

    KEY IX_WORK_ORDER_ASSIGNMENT_TECHNICIAN (
        Technician_ID,
        Is_Current,
        Assigned_At
    ),

    KEY IX_WORK_ORDER_ASSIGNMENT_ASSIGNED_BY (
        Assigned_By,
        Assigned_At
    ),

    CONSTRAINT FK_WORK_ORDER_ASSIGNMENT_ORDER
        FOREIGN KEY (Work_Order_ID)
        REFERENCES WORK_ORDER (Work_Order_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_ORDER_ASSIGNMENT_TECHNICIAN
        FOREIGN KEY (Technician_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_ORDER_ASSIGNMENT_ASSIGNED_BY
        FOREIGN KEY (Assigned_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_SEQUENCE
        CHECK (
            Assignment_Sequence >= 1
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_CURRENT
        CHECK (
            Is_Current IN (FALSE, TRUE)
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_NOT_SELF
        CHECK (
            Technician_ID <> Assigned_By
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_NOTES
        CHECK (
            Assignment_Notes IS NULL
            OR CHAR_LENGTH(TRIM(Assignment_Notes)) >= 3
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_END_DATA
        CHECK (
            (
                Is_Current = TRUE
                AND Ended_At IS NULL
                AND End_Reason IS NULL
            )
            OR
            (
                Is_Current = FALSE
                AND Ended_At IS NOT NULL
                AND End_Reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(End_Reason)) >= 3
            )
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_ACK_TIME
        CHECK (
            Acknowledged_At IS NULL
            OR Acknowledged_At >= Assigned_At
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_END_TIME
        CHECK (
            Ended_At IS NULL
            OR Ended_At >= Assigned_At
        ),

    CONSTRAINT CK_WORK_ORDER_ASSIGNMENT_ACK_BEFORE_END
        CHECK (
            Acknowledged_At IS NULL
            OR Ended_At IS NULL
            OR Acknowledged_At <= Ended_At
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE WORK_ORDER_ASSIGNMENT;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'WORK_ORDER_ASSIGNMENT';

SELECT
    COUNT(*) AS Existing_Assignment_Count
FROM WORK_ORDER_ASSIGNMENT;