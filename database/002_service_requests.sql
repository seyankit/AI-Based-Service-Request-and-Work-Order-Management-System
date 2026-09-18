-- Migration 002: Service requests
-- Safe to rerun.
-- Does not delete or modify existing authentication records.

USE htc_service_portal;


-- Maintains the next request number for each calendar year.
-- Java will update this table inside the same transaction used
-- to create a service request.

CREATE TABLE IF NOT EXISTS REQUEST_NUMBER_SEQUENCE (
    Sequence_Year SMALLINT UNSIGNED NOT NULL,
    Last_Number INT UNSIGNED NOT NULL DEFAULT 0,
    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Sequence_Year),

    CONSTRAINT CK_REQUEST_SEQUENCE_YEAR
        CHECK (
            Sequence_Year BETWEEN 2020 AND 9999
        ),

    CONSTRAINT CK_REQUEST_SEQUENCE_NUMBER
        CHECK (
            Last_Number >= 0
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS SERVICE_REQUEST (
    Request_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Request_Number VARCHAR(24) NOT NULL,

    Requester_ID INT UNSIGNED NOT NULL,

    Requester_Department_ID INT UNSIGNED NOT NULL,

    Requested_Category_ID TINYINT UNSIGNED NULL,

    Final_Category_ID TINYINT UNSIGNED NULL,

    Routed_Department_ID INT UNSIGNED NULL,

    Preferred_Priority VARCHAR(20) NOT NULL,

    Final_Priority VARCHAR(20) NULL,

    Request_Title VARCHAR(150) NOT NULL,

    Request_Description TEXT NOT NULL,

    Request_Location VARCHAR(120) NOT NULL,

    Date_Reported DATE NOT NULL,

    Current_Status VARCHAR(30) NOT NULL
        DEFAULT 'Submitted',

    Completed_At TIMESTAMP NULL,

    Closed_At TIMESTAMP NULL,

    Created_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Request_ID),

    UNIQUE KEY UQ_SERVICE_REQUEST_NUMBER (
        Request_Number
    ),

    KEY IX_SERVICE_REQUEST_REQUESTER (
        Requester_ID,
        Created_At
    ),

    KEY IX_SERVICE_REQUEST_REQUESTER_STATUS (
        Requester_ID,
        Current_Status
    ),

    KEY IX_SERVICE_REQUEST_STATUS_CREATED (
        Current_Status,
        Created_At
    ),

    KEY IX_SERVICE_REQUEST_ROUTED_DEPARTMENT (
        Routed_Department_ID,
        Current_Status
    ),

    KEY IX_SERVICE_REQUEST_REQUESTED_CATEGORY (
        Requested_Category_ID
    ),

    KEY IX_SERVICE_REQUEST_FINAL_CATEGORY (
        Final_Category_ID
    ),

    KEY IX_SERVICE_REQUEST_FINAL_PRIORITY (
        Final_Priority,
        Current_Status
    ),

    CONSTRAINT FK_SERVICE_REQUEST_REQUESTER
        FOREIGN KEY (Requester_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_SERVICE_REQUEST_REQUESTER_DEPARTMENT
        FOREIGN KEY (Requester_Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_SERVICE_REQUEST_REQUESTED_CATEGORY
        FOREIGN KEY (Requested_Category_ID)
        REFERENCES SERVICE_CATEGORY (Category_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_SERVICE_REQUEST_FINAL_CATEGORY
        FOREIGN KEY (Final_Category_ID)
        REFERENCES SERVICE_CATEGORY (Category_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_SERVICE_REQUEST_ROUTED_DEPARTMENT
        FOREIGN KEY (Routed_Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_SERVICE_REQUEST_PREFERRED_PRIORITY
        CHECK (
            Preferred_Priority IN (
                'Low',
                'Medium',
                'High',
                'Urgent'
            )
        ),

    CONSTRAINT CK_SERVICE_REQUEST_FINAL_PRIORITY
        CHECK (
            Final_Priority IS NULL
            OR Final_Priority IN (
                'Low',
                'Medium',
                'High',
                'Urgent'
            )
        ),

    CONSTRAINT CK_SERVICE_REQUEST_STATUS
        CHECK (
            Current_Status IN (
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

    CONSTRAINT CK_SERVICE_REQUEST_TITLE_NOT_EMPTY
        CHECK (
            CHAR_LENGTH(TRIM(Request_Title)) >= 5
        ),

    CONSTRAINT CK_SERVICE_REQUEST_DESCRIPTION_NOT_EMPTY
        CHECK (
            CHAR_LENGTH(TRIM(Request_Description)) >= 15
        ),

    CONSTRAINT CK_SERVICE_REQUEST_LOCATION_NOT_EMPTY
        CHECK (
            CHAR_LENGTH(TRIM(Request_Location)) >= 2
        ),

    CONSTRAINT CK_SERVICE_REQUEST_COMPLETION_TIME
        CHECK (
            Completed_At IS NULL
            OR Completed_At >= Created_At
        ),

    CONSTRAINT CK_SERVICE_REQUEST_CLOSURE_TIME
        CHECK (
            Closed_At IS NULL
            OR (
                Completed_At IS NOT NULL
                AND Closed_At >= Completed_At
            )
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE REQUEST_NUMBER_SEQUENCE;

SHOW CREATE TABLE SERVICE_REQUEST;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'REQUEST_NUMBER_SEQUENCE',
      'SERVICE_REQUEST'
  )
ORDER BY TABLE_NAME;

SELECT
    COUNT(*) AS Existing_Request_Count
FROM SERVICE_REQUEST;