-- Migration 001: Service categories
-- Safe to rerun. Does not delete or replace existing records.

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS SERVICE_CATEGORY (
    Category_ID TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
    Category_Code VARCHAR(40) NOT NULL,
    Category_Name VARCHAR(100) NOT NULL,
    Category_Description VARCHAR(255) NULL,
    Default_Department_ID INT UNSIGNED NOT NULL,
    Display_Order TINYINT UNSIGNED NOT NULL DEFAULT 1,
    Is_Active BOOLEAN NOT NULL DEFAULT TRUE,
    Created_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Updated_At TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (Category_ID),

    UNIQUE KEY UQ_SERVICE_CATEGORY_CODE (
        Category_Code
    ),

    UNIQUE KEY UQ_SERVICE_CATEGORY_NAME (
        Category_Name
    ),

    KEY IX_SERVICE_CATEGORY_DEPARTMENT (
        Default_Department_ID
    ),

    KEY IX_SERVICE_CATEGORY_ACTIVE_ORDER (
        Is_Active,
        Display_Order
    ),

    CONSTRAINT FK_SERVICE_CATEGORY_DEPARTMENT
        FOREIGN KEY (Default_Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT CK_SERVICE_CATEGORY_ACTIVE
        CHECK (Is_Active IN (FALSE, TRUE))
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    1,
    'IT_COMPUTER',
    'IT & Computer',
    'Computers, software, accounts, printers, and related IT concerns.',
    1,
    1,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'IT_COMPUTER'
);


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    2,
    'INTERNET_NETWORK',
    'Internet & Network',
    'Internet connectivity, Wi-Fi, network access, and network equipment.',
    1,
    2,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'INTERNET_NETWORK'
);


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    3,
    'ELECTRICAL',
    'Electrical',
    'Power supply, wiring, lighting, outlets, and other electrical concerns.',
    3,
    3,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'ELECTRICAL'
);


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    4,
    'FACILITIES',
    'Facilities',
    'Rooms, buildings, plumbing, furniture, and other campus facilities.',
    2,
    4,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'FACILITIES'
);


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    5,
    'EQUIPMENT',
    'Equipment',
    'School equipment inspection, repair, replacement, and servicing.',
    2,
    5,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'EQUIPMENT'
);


INSERT INTO SERVICE_CATEGORY (
    Category_ID,
    Category_Code,
    Category_Name,
    Category_Description,
    Default_Department_ID,
    Display_Order,
    Is_Active
)
SELECT
    6,
    'MAINTENANCE',
    'Maintenance',
    'General repair and preventive maintenance concerns.',
    4,
    6,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM SERVICE_CATEGORY
    WHERE Category_Code = 'MAINTENANCE'
);


-- Keep the predefined category values synchronized when this migration reruns.

UPDATE SERVICE_CATEGORY
SET Category_Name = 'IT & Computer',
    Category_Description =
        'Computers, software, accounts, printers, and related IT concerns.',
    Default_Department_ID = 1,
    Display_Order = 1,
    Is_Active = TRUE
WHERE Category_Code = 'IT_COMPUTER';

UPDATE SERVICE_CATEGORY
SET Category_Name = 'Internet & Network',
    Category_Description =
        'Internet connectivity, Wi-Fi, network access, and network equipment.',
    Default_Department_ID = 1,
    Display_Order = 2,
    Is_Active = TRUE
WHERE Category_Code = 'INTERNET_NETWORK';

UPDATE SERVICE_CATEGORY
SET Category_Name = 'Electrical',
    Category_Description =
        'Power supply, wiring, lighting, outlets, and other electrical concerns.',
    Default_Department_ID = 3,
    Display_Order = 3,
    Is_Active = TRUE
WHERE Category_Code = 'ELECTRICAL';

UPDATE SERVICE_CATEGORY
SET Category_Name = 'Facilities',
    Category_Description =
        'Rooms, buildings, plumbing, furniture, and other campus facilities.',
    Default_Department_ID = 2,
    Display_Order = 4,
    Is_Active = TRUE
WHERE Category_Code = 'FACILITIES';

UPDATE SERVICE_CATEGORY
SET Category_Name = 'Equipment',
    Category_Description =
        'School equipment inspection, repair, replacement, and servicing.',
    Default_Department_ID = 2,
    Display_Order = 5,
    Is_Active = TRUE
WHERE Category_Code = 'EQUIPMENT';

UPDATE SERVICE_CATEGORY
SET Category_Name = 'Maintenance',
    Category_Description =
        'General repair and preventive maintenance concerns.',
    Default_Department_ID = 4,
    Display_Order = 6,
    Is_Active = TRUE
WHERE Category_Code = 'MAINTENANCE';

ALTER TABLE SERVICE_CATEGORY AUTO_INCREMENT = 7;


-- Verification

SHOW CREATE TABLE SERVICE_CATEGORY;

SELECT
    sc.Category_ID,
    sc.Category_Code,
    sc.Category_Name,
    d.Department_Name AS Default_Department,
    sc.Display_Order,
    sc.Is_Active
FROM SERVICE_CATEGORY sc
INNER JOIN DEPARTMENT d
    ON d.Department_ID = sc.Default_Department_ID
ORDER BY sc.Display_Order;