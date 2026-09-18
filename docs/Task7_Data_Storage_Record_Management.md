# Task 7 – Data Storage and Record Management

## Objective
Keep MySQL authoritative while using API responses and frontend state for immediate rendering.

## Implemented
- Migration `015_registration_email_verification.sql` adds composite-name fields and verified-email lifecycle support.
- `EMAIL_VERIFICATION_TOKEN` stores only SHA-256 token hashes; tokens expire and are single use.
- `INSTITUTIONAL_ROLE_ASSIGNMENT` is a trusted server-side mapping. Unmapped verified accounts resolve to Requester (Role 1).
- Registration creates `Pending Email Verification`; it does not assign a role from browser data.
- Email verification assigns the trusted role and activates Requesters or places privileged mappings in `Pending Approval`.
- SMTP settings come only from environment variables.
- Resend verification is rate limited to 60 seconds.

## APIs
- `POST /api/register`
- `GET /api/email-verification/verify?token=...`
- `POST /api/email-verification/resend`
- Existing real service-request retrieval/storage APIs remain in use.

## Verification status
Static JavaScript syntax check passed. Live MySQL, SMTP delivery, and Tomcat integration require the user's configured local environment and were not fabricated here.

## Screenshot checklist
Pending verification row, token hash row, received verification email, verified row, stored service request, refreshed list, MySQL Workbench record.

## Git
Commit hash: `TO_BE_RECORDED_AFTER_LOCAL_COMMIT`
