-- ============================================================
-- Migration 017
-- Profile Photo Support
-- HTC Service Portal
--
-- The database stores only a safe server-generated filename.
-- Actual image files are stored outside the deployed WAR.
-- ============================================================

USE htc_service_portal;

ALTER TABLE SCHOOL_PERSONNEL
    ADD COLUMN Profile_Image_File_Name VARCHAR(255)
        COLLATE utf8mb4_unicode_ci
        NULL
        AFTER Department_ID;