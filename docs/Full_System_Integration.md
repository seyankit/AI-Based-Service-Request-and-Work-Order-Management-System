# Full System Integration Status

## Implemented in this package
- Composite-name account model.
- No user-selectable registration/login role.
- Server-side trusted-role resolution after verified school email.
- Secure random verification tokens with hashed-at-rest storage, expiration, single use, and resend throttling.
- Account states: Pending Email Verification, Active, Pending Approval, with existing review flow compatibility updates.
- Requester draft CRUD backend with ownership and CSRF checks.
- Existing session, service-request, account-review, work-order schema, notification schema, attachment schema, AI schema, and audit schema are preserved.

## Environment configuration
Database: `HTC_DB_URL`, `HTC_DB_USER`, `HTC_DB_PASSWORD`.
Mail: `HTC_MAIL_HOST`, `HTC_MAIL_PORT`, `HTC_MAIL_USER`, `HTC_MAIL_PASSWORD`, `HTC_MAIL_FROM`, `HTC_MAIL_FROM_NAME`, `HTC_APP_BASE_URL`.

## Required local verification before final submission
1. Apply migrations 001–016 to a backup/test database.
2. Run `mvn test` and `mvn clean package`.
3. Deploy `target/HTCServicePortal.war` to Tomcat 10.1.
4. Verify `/api/health` reports MySQL connectivity.
5. Verify a real school-email verification delivery.
6. Verify Requester registration → verification → login → request workflow.
7. Verify mapped privileged account → verification → Pending Approval → administrator decision.
8. Capture the school-required screenshots and create the four requested Git commits.

No live Maven/Tomcat/MySQL/SMTP success is claimed by this document because those services are not available/configured in the build container used to prepare this package.
