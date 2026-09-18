-- Additive lifecycle history for creation, reassignment and verification, which are not progress updates.
USE htc_service_portal;
CREATE TABLE IF NOT EXISTS WORK_ORDER_HISTORY (
    History_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Work_Order_ID BIGINT UNSIGNED NOT NULL,
    Previous_Status VARCHAR(30) NULL,
    New_Status VARCHAR(30) NOT NULL,
    Action_Type VARCHAR(30) NOT NULL,
    Changed_By INT UNSIGNED NOT NULL,
    Remarks VARCHAR(2000) NOT NULL,
    Changed_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX IX_WORK_HISTORY_ORDER_TIME (Work_Order_ID, Changed_At, History_ID),
    CONSTRAINT FK_WORK_HISTORY_ORDER FOREIGN KEY (Work_Order_ID) REFERENCES WORK_ORDER(Work_Order_ID),
    CONSTRAINT FK_WORK_HISTORY_ACTOR FOREIGN KEY (Changed_By) REFERENCES SCHOOL_PERSONNEL(Personnel_ID),
    CONSTRAINT CK_WORK_HISTORY_STATUS CHECK (New_Status IN ('Created','Assigned','Acknowledged','In Progress','On Hold','Completed','Verified')),
    CONSTRAINT CK_WORK_HISTORY_REMARKS CHECK (CHAR_LENGTH(TRIM(Remarks)) BETWEEN 3 AND 2000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
