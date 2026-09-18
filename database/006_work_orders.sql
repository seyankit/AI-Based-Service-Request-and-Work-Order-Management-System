--- Migration 006: Work orders
-- A work order can only be created for an approved service request.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS WORK_ORDER_NUMBER_SEQUENCE (
    Sequence_Year SMALLINT UNSIGNED NOT NULL,

    Last_Number INT UNSIGNED NOT NULL DEFAULT 0,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Sequence_Year),

    CONSTRAINT CK_WORK_ORDER_SEQUENCE_YEAR
        CHECK (
            Sequence_Year BETWEEN 2020 AND 9999
        ),

    CONSTRAINT CK_WORK_ORDER_SEQUENCE_NUMBER
        CHECK (
            Last_Number >= 0
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS WORK_ORDER (
    Work_Order_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Work_Order_Number VARCHAR(24) NOT NULL,

    Request_ID BIGINT UNSIGNED NOT NULL,

    Department_ID INT UNSIGNED NOT NULL,

    Created_By INT UNSIGNED NOT NULL,

    Work_Description TEXT NOT NULL,

    Work_Status VARCHAR(30) NOT NULL
        DEFAULT 'Created',

    Target_Start_Date DATE NULL,

    Target_Completion_Date DATE NULL,

    Acknowledged_At TIMESTAMP NULL,

    Actual_Start_At TIMESTAMP NULL,

    Completion_Summary VARCHAR(2000) NULL,

    Completed_At TIMESTAMP NULL,

    Verified_By INT UNSIGNED NULL,

    Verified_At TIMESTAMP NULL,

    Created_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Work_Order_ID),

    UNIQUE KEY UQ_WORK_ORDER_NUMBER (
        Work_Order_Number
    ),

    UNIQUE KEY UQ_WORK_ORDER_REQUEST (
        Request_ID
    ),

    KEY IX_WORK_ORDER_DEPARTMENT_STATUS (
        Department_ID,
        Work_Status
    ),

    KEY IX_WORK_ORDER_STATUS_CREATED (
        Work_Status,
        Created_At
    ),

    KEY IX_WORK_ORDER_CREATED_BY (
        Created_By,
        Created_At
    ),

    KEY IX_WORK_ORDER_VERIFIED_BY (
        Verified_By,
        Verified_At
    ),

    CONSTRAINT FK_WORK_ORDER_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_ORDER_DEPARTMENT
        FOREIGN KEY (Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_ORDER_CREATED_BY
        FOREIGN KEY (Created_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_WORK_ORDER_VERIFIED_BY
        FOREIGN KEY (Verified_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_WORK_ORDER_STATUS
        CHECK (
            Work_Status IN (
                'Created',
                'Assigned',
                'Acknowledged',
                'In Progress',
                'On Hold',
                'Completed',
                'Verified'
            )
        ),

    CONSTRAINT CK_WORK_ORDER_DESCRIPTION
        CHECK (
            CHAR_LENGTH(TRIM(Work_Description)) >= 10
        ),

    CONSTRAINT CK_WORK_ORDER_TARGET_DATES
        CHECK (
            Target_Start_Date IS NULL
            OR Target_Completion_Date IS NULL
            OR Target_Completion_Date >= Target_Start_Date
        ),

    CONSTRAINT CK_WORK_ORDER_ACKNOWLEDGEMENT
        CHECK (
            (
                Work_Status IN (
                    'Created',
                    'Assigned'
                )
                AND Acknowledged_At IS NULL
            )
            OR
            (
                Work_Status IN (
                    'Acknowledged',
                    'In Progress',
                    'On Hold',
                    'Completed',
                    'Verified'
                )
                AND Acknowledged_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_WORK_ORDER_START_DATA
        CHECK (
            (
                Work_Status IN (
                    'Created',
                    'Assigned',
                    'Acknowledged'
                )
                AND Actual_Start_At IS NULL
            )
            OR
            (
                Work_Status IN (
                    'In Progress',
                    'On Hold',
                    'Completed',
                    'Verified'
                )
                AND Actual_Start_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_WORK_ORDER_COMPLETION_DATA
        CHECK (
            (
                Work_Status IN (
                    'Created',
                    'Assigned',
                    'Acknowledged',
                    'In Progress',
                    'On Hold'
                )
                AND Completion_Summary IS NULL
                AND Completed_At IS NULL
            )
            OR
            (
                Work_Status IN (
                    'Completed',
                    'Verified'
                )
                AND Completion_Summary IS NOT NULL
                AND CHAR_LENGTH(TRIM(Completion_Summary)) >= 10
                AND Completed_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_WORK_ORDER_VERIFICATION_DATA
        CHECK (
            (
                Work_Status <> 'Verified'
                AND Verified_By IS NULL
                AND Verified_At IS NULL
            )
            OR
            (
                Work_Status = 'Verified'
                AND Verified_By IS NOT NULL
                AND Verified_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_WORK_ORDER_ACKNOWLEDGED_TIME
        CHECK (
            Acknowledged_At IS NULL
            OR Acknowledged_At >= Created_At
        ),

    CONSTRAINT CK_WORK_ORDER_START_TIME
        CHECK (
            Actual_Start_At IS NULL
            OR (
                Acknowledged_At IS NOT NULL
                AND Actual_Start_At >= Acknowledged_At
            )
        ),

    CONSTRAINT CK_WORK_ORDER_COMPLETED_TIME
        CHECK (
            Completed_At IS NULL
            OR (
                Actual_Start_At IS NOT NULL
                AND Completed_At >= Actual_Start_At
            )
        ),

    CONSTRAINT CK_WORK_ORDER_VERIFIED_TIME
        CHECK (
            Verified_At IS NULL
            OR (
                Completed_At IS NOT NULL
                AND Verified_At >= Completed_At
            )
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE WORK_ORDER_NUMBER_SEQUENCE;

SHOW CREATE TABLE WORK_ORDER;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'WORK_ORDER',
      'WORK_ORDER_NUMBER_SEQUENCE'
  )
ORDER BY TABLE_NAME;

SELECT
    COUNT(*) AS Existing_Work_Order_Count
FROM WORK_ORDER;