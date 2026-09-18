-- Migration 004: AI recommendation storage
-- AI recommendations remain advisory.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;


CREATE TABLE IF NOT EXISTS AI_RECOMMENDATION (
    Recommendation_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Request_ID BIGINT UNSIGNED NOT NULL,

    Recommendation_Sequence SMALLINT UNSIGNED NOT NULL DEFAULT 1,

    Is_Current BOOLEAN NOT NULL DEFAULT TRUE,

    Analysis_Status VARCHAR(20) NOT NULL DEFAULT 'Pending',

    Recommendation_Method VARCHAR(30) NOT NULL DEFAULT 'None',

    Recommended_Category_ID TINYINT UNSIGNED NULL,

    Category_Confidence DECIMAL(6, 5) NULL,

    Recommended_Priority VARCHAR(20) NULL,

    Priority_Confidence DECIMAL(6, 5) NULL,

    Possible_Duplicate_Request_ID BIGINT UNSIGNED NULL,

    Duplicate_Similarity DECIMAL(6, 5) NULL,

    Duplicate_Threshold DECIMAL(6, 5) NULL,

    Category_Explanation VARCHAR(1000) NULL,

    Priority_Explanation VARCHAR(1000) NULL,

    Duplicate_Explanation VARCHAR(1000) NULL,

    Analysis_Message VARCHAR(255) NULL,

    Model_Name VARCHAR(100) NULL,

    Model_Version VARCHAR(50) NULL,

    Input_Fingerprint CHAR(64) NULL,

    Processing_Time_Ms INT UNSIGNED NULL,

    Review_Decision VARCHAR(20) NULL,

    Reviewed_By INT UNSIGNED NULL,

    Review_Notes VARCHAR(1000) NULL,

    Reviewed_At TIMESTAMP NULL,

    Generated_At TIMESTAMP NULL,

    Created_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (Recommendation_ID),

    UNIQUE KEY UQ_AI_RECOMMENDATION_SEQUENCE (
        Request_ID,
        Recommendation_Sequence
    ),

    KEY IX_AI_RECOMMENDATION_CURRENT (
        Request_ID,
        Is_Current
    ),

    KEY IX_AI_RECOMMENDATION_STATUS (
        Analysis_Status,
        Created_At
    ),

    KEY IX_AI_RECOMMENDATION_CATEGORY (
        Recommended_Category_ID
    ),

    KEY IX_AI_RECOMMENDATION_DUPLICATE (
        Possible_Duplicate_Request_ID
    ),

    KEY IX_AI_RECOMMENDATION_REVIEWER (
        Reviewed_By,
        Reviewed_At
    ),

    CONSTRAINT FK_AI_RECOMMENDATION_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AI_RECOMMENDATION_CATEGORY
        FOREIGN KEY (Recommended_Category_ID)
        REFERENCES SERVICE_CATEGORY (Category_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AI_RECOMMENDATION_DUPLICATE
        FOREIGN KEY (Possible_Duplicate_Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AI_RECOMMENDATION_REVIEWER
        FOREIGN KEY (Reviewed_By)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_AI_RECOMMENDATION_CURRENT
        CHECK (
            Is_Current IN (FALSE, TRUE)
        ),

    CONSTRAINT CK_AI_RECOMMENDATION_SEQUENCE
        CHECK (
            Recommendation_Sequence >= 1
        ),

    CONSTRAINT CK_AI_RECOMMENDATION_STATUS
        CHECK (
            Analysis_Status IN (
                'Pending',
                'Completed',
                'Unavailable',
                'Failed'
            )
        ),

    CONSTRAINT CK_AI_RECOMMENDATION_METHOD
        CHECK (
            Recommendation_Method IN (
                'None',
                'Rule-Based',
                'Machine Learning',
                'Hybrid'
            )
        ),

    CONSTRAINT CK_AI_RECOMMENDATION_PRIORITY
        CHECK (
            Recommended_Priority IS NULL
            OR Recommended_Priority IN (
                'Low',
                'Medium',
                'High',
                'Urgent'
            )
        ),

    CONSTRAINT CK_AI_CATEGORY_CONFIDENCE
        CHECK (
            Category_Confidence IS NULL
            OR Category_Confidence BETWEEN 0.00000 AND 1.00000
        ),

    CONSTRAINT CK_AI_PRIORITY_CONFIDENCE
        CHECK (
            Priority_Confidence IS NULL
            OR Priority_Confidence BETWEEN 0.00000 AND 1.00000
        ),

    CONSTRAINT CK_AI_DUPLICATE_SIMILARITY
        CHECK (
            Duplicate_Similarity IS NULL
            OR Duplicate_Similarity BETWEEN 0.00000 AND 1.00000
        ),

    CONSTRAINT CK_AI_DUPLICATE_THRESHOLD
        CHECK (
            Duplicate_Threshold IS NULL
            OR Duplicate_Threshold BETWEEN 0.00000 AND 1.00000
        ),

    CONSTRAINT CK_AI_DUPLICATE_NOT_SELF
        CHECK (
            Possible_Duplicate_Request_ID IS NULL
            OR Possible_Duplicate_Request_ID <> Request_ID
        ),

    CONSTRAINT CK_AI_PROCESSING_TIME
        CHECK (
            Processing_Time_Ms IS NULL
            OR Processing_Time_Ms >= 0
        ),

    CONSTRAINT CK_AI_REVIEW_DECISION
        CHECK (
            Review_Decision IS NULL
            OR Review_Decision IN (
                'Accepted',
                'Overridden'
            )
        ),

    CONSTRAINT CK_AI_REVIEW_INFORMATION
        CHECK (
            (
                Review_Decision IS NULL
                AND Reviewed_By IS NULL
                AND Reviewed_At IS NULL
            )
            OR
            (
                Review_Decision IS NOT NULL
                AND Reviewed_By IS NOT NULL
                AND Reviewed_At IS NOT NULL
            )
        ),

    CONSTRAINT CK_AI_COMPLETED_RESULT
        CHECK (
            Analysis_Status <> 'Completed'
            OR (
                Recommendation_Method <> 'None'
                AND Recommended_Category_ID IS NOT NULL
                AND Category_Confidence IS NOT NULL
                AND Recommended_Priority IS NOT NULL
                AND Priority_Confidence IS NOT NULL
                AND Model_Name IS NOT NULL
                AND Model_Version IS NOT NULL
                AND Generated_At IS NOT NULL
            )
        )
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Verification

SHOW CREATE TABLE AI_RECOMMENDATION;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'AI_RECOMMENDATION';

SELECT
    COUNT(*) AS Existing_AI_Recommendation_Count
FROM AI_RECOMMENDATION;