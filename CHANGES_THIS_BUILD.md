# HTCServicePortal Updated Build

Prepared from the uploaded `HTCServicePortal-current(1).zip` without recreating the project.

## Implemented in this package
- Composite registration name: First, Middle, Last, Suffix.
- Removed user-selectable System Role from registration and registration JSON.
- Added server-side registration validator.
- Added school-email verification lifecycle and Check Your Email UI.
- Added secure random verification token generation, SHA-256 token-at-rest storage, 60-minute expiration, single-use verification, and resend throttling.
- Added trusted server-side role mapping table. Unmapped verified users become Requesters; mapped privileged users require account approval.
- Updated login messaging for Pending Email Verification / Pending Approval / Rejected states.
- Added SMTP environment-variable configuration and `.env.example` placeholders only.
- Added Task 8 requester-owned draft CRUD backend and migration so literal deletion is demonstrated on safe drafts rather than official audited requests.
- Added JUnit validator tests and Task 6/7/8/full integration documentation.

## Checks run here
- `node --check src/main/webapp/js/script.js` — PASS.
- `pom.xml` and `WEB-INF/web.xml` XML parsing — PASS.
- Core Java utility compile + direct harness — 5/5 PASS.

## Not claimed as verified here
Maven, MySQL, Tomcat, SMTP delivery, browser end-to-end workflow, screenshots, Git commits, and GitHub push require your local Windows environment and credentials/services. See `docs/Full_System_Integration.md`.
