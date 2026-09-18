-- HTC Service Portal authentication schema
-- MySQL 8.x
-- This script is non-destructive: it does not DROP existing tables or data.

CREATE DATABASE IF NOT EXISTS htc_service_portal
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE htc_service_portal;

CREATE TABLE IF NOT EXISTS DEPARTMENT (
    Department_ID INT UNSIGNED NOT NULL AUTO_INCREMENT,
    Department_Name VARCHAR(120) NOT NULL,
    Department_Description VARCHAR(255) NULL,
    PRIMARY KEY (Department_ID),
    UNIQUE KEY UQ_DEPARTMENT_NAME (Department_Name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS PERSONNEL_ROLE (
    Role_ID TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
    Role_Name VARCHAR(80) NOT NULL,
    Role_Description VARCHAR(255) NULL,
    PRIMARY KEY (Role_ID),
    UNIQUE KEY UQ_PERSONNEL_ROLE_NAME (Role_Name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS SCHOOL_PERSONNEL (
    Personnel_ID INT UNSIGNED NOT NULL AUTO_INCREMENT,
    First_Name VARCHAR(70) NOT NULL,
    Last_Name VARCHAR(70) NOT NULL,
    Email VARCHAR(120) NOT NULL,
    Contact_Number VARCHAR(20) NULL,
    Personnel_Type VARCHAR(50) NOT NULL,
    Password_Hash VARCHAR(255) NOT NULL,
    Account_Status VARCHAR(20) NOT NULL DEFAULT 'Pending',
    Department_ID INT UNSIGNED NOT NULL,
    Created_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Updated_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (Personnel_ID),
    UNIQUE KEY UQ_SCHOOL_PERSONNEL_EMAIL (Email),
    KEY IX_SCHOOL_PERSONNEL_DEPARTMENT (Department_ID),
    CONSTRAINT FK_SCHOOL_PERSONNEL_DEPARTMENT
        FOREIGN KEY (Department_ID)
        REFERENCES DEPARTMENT (Department_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT CK_SCHOOL_PERSONNEL_STATUS
        CHECK (Account_Status IN ('Pending', 'Active', 'Inactive')),
    CONSTRAINT CK_SCHOOL_PERSONNEL_TYPE
        CHECK (Personnel_Type IN (
            'Faculty',
            'Staff',
            'Administrative Personnel',
            'Department Personnel',
            'Office Personnel',
            'Service Personnel',
            'Authorized School Personnel'
        ))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS PERSONNEL_ROLE_ASSIGNMENT (
    Personnel_ID INT UNSIGNED NOT NULL,
    Role_ID TINYINT UNSIGNED NOT NULL,
    Assigned_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (Personnel_ID),
    KEY IX_PERSONNEL_ROLE_ASSIGNMENT_ROLE (Role_ID),
    CONSTRAINT FK_ROLE_ASSIGNMENT_PERSONNEL
        FOREIGN KEY (Personnel_ID)
        REFERENCES SCHOOL_PERSONNEL (Personnel_ID)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    CONSTRAINT FK_ROLE_ASSIGNMENT_ROLE
        FOREIGN KEY (Role_ID)
        REFERENCES PERSONNEL_ROLE (Role_ID)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- These IDs are part of the application contract used for role workspaces.
INSERT IGNORE INTO PERSONNEL_ROLE
    (Role_ID, Role_Name, Role_Description)
VALUES
    (1, 'Requester', 'Submits and tracks service requests.'),
    (2, 'Service Administrator / Coordinator', 'Reviews requests and coordinates work orders.'),
    (3, 'Department Head / Authorized Approver', 'Records authorization decisions.'),
    (4, 'Service Personnel / Technician', 'Completes assigned work and records progress.');

UPDATE PERSONNEL_ROLE
SET Role_Name = 'Requester',
    Role_Description = 'Submits and tracks service requests.'
WHERE Role_ID = 1;

UPDATE PERSONNEL_ROLE
SET Role_Name = 'Service Administrator / Coordinator',
    Role_Description = 'Reviews requests and coordinates work orders.'
WHERE Role_ID = 2;

UPDATE PERSONNEL_ROLE
SET Role_Name = 'Department Head / Authorized Approver',
    Role_Description = 'Records authorization decisions.'
WHERE Role_ID = 3;

UPDATE PERSONNEL_ROLE
SET Role_Name = 'Service Personnel / Technician',
    Role_Description = 'Completes assigned work and records progress.'
WHERE Role_ID = 4;

-- IDs 1-5 were visible in the supplied Workbench evidence. The same insert
-- operation reported ten returned rows; IDs 6-10 complete the supplied list.
INSERT IGNORE INTO DEPARTMENT
    (Department_ID, Department_Name, Department_Description)
VALUES
    (1, 'Information Technology Office', 'Campus information technology services.'),
    (2, 'Facilities Management', 'Campus buildings, rooms, and facilities services.'),
    (3, 'Electrical Services', 'Electrical maintenance and safety services.'),
    (4, 'Maintenance Services', 'General maintenance and repair services.'),
    (5, 'Administration Office', 'School administration services.'),
    (6, 'College of Engineering and Technology Education', 'Engineering and technology academic unit.'),
    (7, 'College Department', 'College-level academic department.'),
    (8, 'Basic Education Department', 'Basic education academic department.'),
    (9, 'Registrar''s Office', 'Student records and registration services.'),
    (10, 'Other School Office', 'Other authorized school office.');

ALTER TABLE DEPARTMENT AUTO_INCREMENT = 11;
ALTER TABLE PERSONNEL_ROLE AUTO_INCREMENT = 5;

-- Run these inspection statements after the setup. If a table already existed,
-- CREATE TABLE IF NOT EXISTS does not repair its old definition; compare the
-- output with the definitions above before applying any ALTER TABLE migration.
SHOW CREATE TABLE DEPARTMENT;
SHOW CREATE TABLE PERSONNEL_ROLE;
SHOW CREATE TABLE SCHOOL_PERSONNEL;
SHOW CREATE TABLE PERSONNEL_ROLE_ASSIGNMENT;

SELECT Department_ID, Department_Name
FROM DEPARTMENT
ORDER BY Department_ID;

SELECT Role_ID, Role_Name
FROM PERSONNEL_ROLE
ORDER BY Role_ID;

-- This must return zero rows. The Java application intentionally supports one
-- system role per personnel account.
SELECT Personnel_ID, COUNT(*) AS Role_Count
FROM PERSONNEL_ROLE_ASSIGNMENT
GROUP BY Personnel_ID
HAVING COUNT(*) > 1;

-- This must return zero rows before adding or repairing a UNIQUE email key.
SELECT LOWER(Email) AS Normalized_Email, COUNT(*) AS Email_Count
FROM SCHOOL_PERSONNEL
GROUP BY LOWER(Email)
HAVING COUNT(*) > 1;

-- If PERSONNEL_ROLE_ASSIGNMENT already existed with a composite primary key,
-- first resolve every duplicate reported above. Then an administrator may run:
-- ALTER TABLE PERSONNEL_ROLE_ASSIGNMENT
--     ADD UNIQUE KEY UQ_ROLE_ASSIGNMENT_PERSONNEL (Personnel_ID);

-- If SCHOOL_PERSONNEL already existed without a unique email key, first resolve
-- every duplicate reported above. Then an administrator may run:
-- ALTER TABLE SCHOOL_PERSONNEL
--     ADD UNIQUE KEY UQ_SCHOOL_PERSONNEL_EMAIL (Email);

-- Create or rotate the local application account separately as a MySQL
-- administrator. Never commit the real password into this file.
--
-- CREATE USER IF NOT EXISTS 'htc_app'@'localhost'
--     IDENTIFIED BY 'YOUR_MYSQL_PASSWORD';
-- ALTER USER 'htc_app'@'localhost'
--     IDENTIFIED BY 'YOUR_MYSQL_PASSWORD';
-- GRANT SELECT, INSERT, UPDATE, DELETE
--     ON htc_service_portal.*
--     TO 'htc_app'@'localhost';
-- FLUSH PRIVILEGES;

-- Requester registrations (Role_ID 1) are activated automatically. Roles 2-4
-- are created as Pending to prevent self-service privilege escalation. After an
-- authorized person verifies an account, activate only that exact record:
--
-- UPDATE SCHOOL_PERSONNEL
-- SET Account_Status = 'Active'
-- WHERE Personnel_ID = 123
--   AND Account_Status = 'Pending';
