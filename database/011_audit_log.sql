-- Migration 011: Permanent audit trail
-- Audit records are append-only.
-- Safe to rerun. Existing records are not deleted or modified.

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    Audit_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    Actor_Type VARCHAR(20) NOT NULL,
    Actor_ID INT UNSIGNED NULL,
    Actor_Role_ID TINYINT UNSIGNED NULL,

    Request_ID BIGINT UNSIGNED NULL,
    Work_Order_ID BIGINT UNSIGNED NULL,

    Action_Type VARCHAR(60) NOT NULL,
    Entity_Type VARCHAR(50) NOT NULL,
    Entity_ID BIGINT UNSIGNED NULL,

    Action_Outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    Action_Summary VARCHAR(500) NOT NULL,

    -- Never store passwords, password hashes, session IDs,
    -- database credentials, or other secrets here.
    Details_JSON JSON NULL,

    Client_IP_Address VARCHAR(45) NULL,
    Client_User_Agent VARCHAR(500) NULL,
    Correlation_ID CHAR(36) NULL,

    Created_At TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (Audit_ID),

    INDEX IX_AUDIT_ACTOR (
        Actor_ID,
        Created_At
    ),

    INDEX IX_AUDIT_REQUEST (
        Request_ID,
        Created_At
    ),

    INDEX IX_AUDIT_WORK_ORDER (
        Work_Order_ID,
        Created_At
    ),

    INDEX IX_AUDIT_ACTION (
        Action_Type,
        Created_At
    ),

    INDEX IX_AUDIT_OUTCOME (
        Action_Outcome,
        Created_At
    ),

    INDEX IX_AUDIT_CORRELATION (
        Correlation_ID
    ),

    CONSTRAINT FK_AUDIT_ACTOR
        FOREIGN KEY (Actor_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AUDIT_ACTOR_ROLE
        FOREIGN KEY (Actor_Role_ID)
        REFERENCES PERSONNEL_ROLE (Role_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AUDIT_REQUEST
        FOREIGN KEY (Request_ID)
        REFERENCES SERVICE_REQUEST (Request_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT FK_AUDIT_WORK_ORDER
        FOREIGN KEY (Work_Order_ID)
        REFERENCES WORK_ORDER (Work_Order_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CHK_AUDIT_ACTOR_TYPE
        CHECK (
            Actor_Type IN (
                'PERSONNEL',
                'SYSTEM',
                'ANONYMOUS'
            )
        ),

    CONSTRAINT CHK_AUDIT_ACTOR_ID
        CHECK (
            (
                Actor_Type = 'PERSONNEL'
                AND Actor_ID IS NOT NULL
            )
            OR
            (
                Actor_Type IN ('SYSTEM', 'ANONYMOUS')
                AND Actor_ID IS NULL
                AND Actor_Role_ID IS NULL
            )
        ),

    CONSTRAINT CHK_AUDIT_ACTOR_ROLE
        CHECK (
            Actor_Role_ID IS NULL
            OR Actor_Type = 'PERSONNEL'
        ),

    CONSTRAINT CHK_AUDIT_ACTION_TYPE
        CHECK (
            CHAR_LENGTH(TRIM(Action_Type))
                BETWEEN 3 AND 60
            AND Action_Type = UPPER(Action_Type)
            AND Action_Type NOT LIKE '% %'
        ),

    CONSTRAINT CHK_AUDIT_ENTITY_TYPE
        CHECK (
            CHAR_LENGTH(TRIM(Entity_Type))
                BETWEEN 3 AND 50
            AND Entity_Type = UPPER(Entity_Type)
            AND Entity_Type NOT LIKE '% %'
        ),

    CONSTRAINT CHK_AUDIT_OUTCOME
        CHECK (
            Action_Outcome IN (
                'SUCCESS',
                'FAILURE',
                'DENIED'
            )
        ),

    CONSTRAINT CHK_AUDIT_SUMMARY
        CHECK (
            CHAR_LENGTH(TRIM(Action_Summary))
                BETWEEN 3 AND 500
        ),

    CONSTRAINT CHK_AUDIT_CLIENT_IP
        CHECK (
            Client_IP_Address IS NULL
            OR CHAR_LENGTH(TRIM(Client_IP_Address))
                BETWEEN 3 AND 45
        ),

    CONSTRAINT CHK_AUDIT_CORRELATION
        CHECK (
            Correlation_ID IS NULL
            OR CHAR_LENGTH(TRIM(Correlation_ID)) = 36
        )
) ENGINE = InnoDB;

SHOW CREATE TABLE AUDIT_LOG;

SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'AUDIT_LOG';

SELECT
    COUNT(*) AS Existing_Audit_Log_Count
FROM AUDIT_LOG;