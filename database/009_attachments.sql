-- Migration 009: Secure attachment metadata
-- Physical files must be stored outside the deployed web application.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS ATTACHMENT (
    Attachment_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Request_ID BIGINT UNSIGNED NULL,

    Progress_ID BIGINT UNSIGNED NULL,

    Uploaded_By INT UNSIGNED NOT NULL,

    Attachment_Purpose VARCHAR(30) NOT NULL,

    Original_File_Name VARCHAR(255) NOT NULL,

    Stored_File_Name VARCHAR(100) NOT NULL,

    File_Extension VARCHAR(10) NOT NULL,

    Content_Type VARCHAR(100) NOT NULL,

    File_Size_Bytes BIGINT UNSIGNED NOT NULL,

    File_SHA256 CHAR(64) NOT NULL,

    Attachment_Description VARCHAR(255) NULL,

    Is_Requester_Visible BOOLEAN NOT NULL DEFAULT TRUE,

    Attachment_Status VARCHAR(20) NOT NULL DEFAULT 'Active',

    Uploaded_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    Removed_By INT UNSIGNED NULL,

    Removed_At TIMESTAMP NULL,

    Removal_Reason VARCHAR(1000) NULL,

    PRIMARY KEY (Attachment_ID),

    UNIQUE KEY UQ_ATTACHMENT_STORED_FILE (
        Stored_File_Name
    ),

    KEY IX_ATTACHMENT_REQUEST (
        Request_ID,
        Attachment_Status,
        Uploaded_At
    ),

    KEY IX_ATTACHMENT_PROGRESS (
        Progress_ID,
        Attachment_Status,
        Uploaded_At
    ),

    KEY IX_ATTACHMENT_UPLOADER (
        Uploaded_By,
        Uploaded_At
    ),

    KEY IX_ATTACHMENT_HASH (
        File_SHA256
    ),

    CONSTRAINT FK_ATTACHMENT_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ATTACHMENT_PROGRESS
        FOREIGN KEY (Progress_ID)
        REFERENCES WORK_ORDER_PROGRESS (Progress_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ATTACHMENT_UPLOADER
        FOREIGN KEY (Uploaded_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_ATTACHMENT_REMOVED_BY
        FOREIGN KEY (Removed_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_ATTACHMENT_SINGLE_OWNER
        CHECK (
            (
                Request_ID IS NOT NULL
                AND Progress_ID IS NULL
            )
            OR
            (
                Request_ID IS NULL
                AND Progress_ID IS NOT NULL
            )
        ),

    CONSTRAINT CK_ATTACHMENT_PURPOSE
        CHECK (
            Attachment_Purpose IN (
                'Request Evidence',
                'Progress Evidence',
                'Completion Evidence'
            )
        ),

    CONSTRAINT CK_ATTACHMENT_PURPOSE_OWNER
        CHECK (
            (
                Request_ID IS NOT NULL
                AND Attachment_Purpose = 'Request Evidence'
            )
            OR
            (
                Progress_ID IS NOT NULL
                AND Attachment_Purpose IN (
                    'Progress Evidence',
                    'Completion Evidence'
                )
            )
        ),

    CONSTRAINT CK_ATTACHMENT_ORIGINAL_NAME
        CHECK (
            CHAR_LENGTH(TRIM(Original_File_Name)) >= 1
        ),

    CONSTRAINT CK_ATTACHMENT_STORED_NAME
        CHECK (
            CHAR_LENGTH(TRIM(Stored_File_Name)) >= 20
        ),

    CONSTRAINT CK_ATTACHMENT_EXTENSION
        CHECK (
            File_Extension IN (
                'png',
                'jpg',
                'jpeg',
                'webp',
                'pdf'
            )
        ),

    CONSTRAINT CK_ATTACHMENT_CONTENT_TYPE
        CHECK (
            Content_Type IN (
                'image/png',
                'image/jpeg',
                'image/webp',
                'application/pdf'
            )
        ),

    CONSTRAINT CK_ATTACHMENT_SIZE
        CHECK (
            File_Size_Bytes BETWEEN 1 AND 5242880
        ),

    CONSTRAINT CK_ATTACHMENT_HASH
        CHECK (
            CHAR_LENGTH(File_SHA256) = 64
        ),

    CONSTRAINT CK_ATTACHMENT_VISIBILITY
        CHECK (
            Is_Requester_Visible IN (FALSE, TRUE)
        ),

    CONSTRAINT CK_ATTACHMENT_STATUS
        CHECK (
            Attachment_Status IN (
                'Active',
                'Removed'
            )
        ),

    CONSTRAINT CK_ATTACHMENT_REMOVAL_DATA
        CHECK (
            (
                Attachment_Status = 'Active'
                AND Removed_By IS NULL
                AND Removed_At IS NULL
                AND Removal_Reason IS NULL
            )
            OR
            (
                Attachment_Status = 'Removed'
                AND Removed_By IS NOT NULL
                AND Removed_At IS NOT NULL
                AND Removal_Reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(Removal_Reason)) >= 3
            )
        ),

    CONSTRAINT CK_ATTACHMENT_REMOVAL_TIME
        CHECK (
            Removed_At IS NULL
            OR Removed_At >= Uploaded_At
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE ATTACHMENT;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ATTACHMENT';

SELECT
    COUNT(*) AS Existing_Attachment_Count
FROM ATTACHMENT;