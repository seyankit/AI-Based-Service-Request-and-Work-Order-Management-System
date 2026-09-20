USE htc_service_portal;
CREATE INDEX IX_AUDIT_CREATED
ON AUDIT_LOG (Created_At, Audit_ID);