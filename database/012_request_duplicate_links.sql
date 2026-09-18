-- Migration 012: Confirmed duplicate-request links
-- AI recommendations remain advisory.
-- A record is inserted only after an authorized administrator
-- confirms that one request duplicates another request.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS REQUEST_DUPLICATE_LINK (
    Duplicate_Link_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- The newer request being marked as Duplicate.
    Duplicate_Request_ID BIGINT UNSIGNED NOT NULL,

    -- The existing request considered the original record.
    Original_Request_ID BIGINT UNSIGNED NOT NULL,

    -- Present when the decision was based on an AI suggestion.
    -- NULL when the duplicate was identified manually.
    Recommendation_ID BIGINT UNSIGNED NULL,

    Confirmed_By_ID INT UNSIGNED NOT NULL,
    Confirmation_Reason VARCHAR(1000) NOT NULL,

    Created_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (Duplicate_Link_ID),

    -- A request may only be marked as a duplicate of one
    -- original request.
    CONSTRAINT UQ_DUPLICATE_REQUEST
        UNIQUE (Duplicate_Request_ID),

    INDEX IX_DUPLICATE_ORIGINAL_REQUEST (
        Original_Request_ID
    ),

    INDEX IX_DUPLICATE_RECOMMENDATION (
        Recommendation_ID
    ),

    INDEX IX_DUPLICATE_CONFIRMED_BY (
        Confirmed_By_ID,
        Created_At
    ),

    CONSTRAINT FK_DUPLICATE_REQUEST
        FOREIGN KEY (Duplicate_Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_DUPLICATE_ORIGINAL
        FOREIGN KEY (Original_Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_DUPLICATE_RECOMMENDATION
        FOREIGN KEY (Recommendation_ID)
        REFERENCES AI_RECOMMENDATION (Recommendation_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_DUPLICATE_CONFIRMED_BY
        FOREIGN KEY (Confirmed_By_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CHK_DUPLICATE_DIFFERENT_REQUESTS
        CHECK (
            Duplicate_Request_ID <> Original_Request_ID
        ),

    CONSTRAINT CHK_DUPLICATE_REASON
        CHECK (
            CHAR_LENGTH(TRIM(Confirmation_Reason))
                BETWEEN 5 AND 1000
        )
) ENGINE = InnoDB;

SHOW CREATE TABLE REQUEST_DUPLICATE_LINK;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'REQUEST_DUPLICATE_LINK';

SELECT
    COUNT(*) AS Existing_Duplicate_Link_Count
FROM REQUEST_DUPLICATE_LINK;