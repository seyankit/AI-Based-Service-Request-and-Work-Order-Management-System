-- Migration 008: Work-order progress records
-- Progress records are append-only.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS WORK_ORDER_PROGRESS (
    Progress_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Work_Order_ID BIGINT UNSIGNED NOT NULL,

    Assignment_ID BIGINT UNSIGNED NOT NULL,

    Updated_By INT UNSIGNED NOT NULL,

    Update_Type VARCHAR(30) NOT NULL,

    Previous_Work_Status VARCHAR(30) NOT NULL,

    New_Work_Status VARCHAR(30) NOT NULL,

    Progress_Percentage TINYINT UNSIGNED NOT NULL DEFAULT 0,

    Progress_Notes VARCHAR(2000) NOT NULL,

    Is_Requester_Visible BOOLEAN NOT NULL DEFAULT TRUE,

    Recorded_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (Progress_ID),

    KEY IX_WORK_PROGRESS_ORDER_TIME (
        Work_Order_ID,
        Recorded_At,
        Progress_ID
    ),

    KEY IX_WORK_PROGRESS_ASSIGNMENT (
        Assignment_ID,
        Recorded_At
    ),

    KEY IX_WORK_PROGRESS_TECHNICIAN (
        Updated_By,
        Recorded_At
    ),

    KEY IX_WORK_PROGRESS_TYPE (
        Update_Type,
        Recorded_At
    ),

    CONSTRAINT FK_WORK_PROGRESS_ORDER
        FOREIGN KEY (Work_Order_ID)
        REFERENCES WORK_ORDER (Work_Order_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_PROGRESS_ASSIGNMENT
        FOREIGN KEY (Assignment_ID)
        REFERENCES WORK_ORDER_ASSIGNMENT (Assignment_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_PROGRESS_TECHNICIAN
        FOREIGN KEY (Updated_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_WORK_PROGRESS_TYPE
        CHECK (
            Update_Type IN (
                'Acknowledged',
                'Work Started',
                'Progress Update',
                'On Hold',
                'Resumed',
                'Completed'
            )
        ),

    CONSTRAINT CK_WORK_PROGRESS_PREVIOUS_STATUS
        CHECK (
            Previous_Work_Status IN (
                'Created',
                'Assigned',
                'Acknowledged',
                'In Progress',
                'On Hold',
                'Completed',
                'Verified'
            )
        ),

    CONSTRAINT CK_WORK_PROGRESS_NEW_STATUS
        CHECK (
            New_Work_Status IN (
                'Created',
                'Assigned',
                'Acknowledged',
                'In Progress',
                'On Hold',
                'Completed',
                'Verified'
            )
        ),

    CONSTRAINT CK_WORK_PROGRESS_PERCENTAGE
        CHECK (
            Progress_Percentage BETWEEN 0 AND 100
        ),

    CONSTRAINT CK_WORK_PROGRESS_NOTES
        CHECK (
            CHAR_LENGTH(TRIM(Progress_Notes)) >= 5
        ),

    CONSTRAINT CK_WORK_PROGRESS_VISIBILITY
        CHECK (
            Is_Requester_Visible IN (FALSE, TRUE)
        ),

    CONSTRAINT CK_WORK_PROGRESS_TRANSITION
        CHECK (
            (
                Update_Type = 'Acknowledged'
                AND Previous_Work_Status = 'Assigned'
                AND New_Work_Status = 'Acknowledged'
                AND Progress_Percentage = 0
            )
            OR
            (
                Update_Type = 'Work Started'
                AND Previous_Work_Status = 'Acknowledged'
                AND New_Work_Status = 'In Progress'
                AND Progress_Percentage BETWEEN 0 AND 99
            )
            OR
            (
                Update_Type = 'Progress Update'
                AND Previous_Work_Status = New_Work_Status
                AND New_Work_Status IN (
                    'In Progress',
                    'On Hold'
                )
                AND Progress_Percentage BETWEEN 1 AND 99
            )
            OR
            (
                Update_Type = 'On Hold'
                AND Previous_Work_Status = 'In Progress'
                AND New_Work_Status = 'On Hold'
                AND Progress_Percentage BETWEEN 0 AND 99
            )
            OR
            (
                Update_Type = 'Resumed'
                AND Previous_Work_Status = 'On Hold'
                AND New_Work_Status = 'In Progress'
                AND Progress_Percentage BETWEEN 0 AND 99
            )
            OR
            (
                Update_Type = 'Completed'
                AND Previous_Work_Status = 'In Progress'
                AND New_Work_Status = 'Completed'
                AND Progress_Percentage = 100
            )
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE WORK_ORDER_PROGRESS;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'WORK_ORDER_PROGRESS';

SELECT
    COUNT(*) AS Existing_Progress_Count
FROM WORK_ORDER_PROGRESS;