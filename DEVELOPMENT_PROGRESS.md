# Development progress

Audit started 2026-09-18 against the open `HTCServicePortal-New/HTCServicePortal` workspace. The attachment names an older sibling; the open workspace is the working assumption pending clarification. Existing sibling changes and database records are preserved.

## Baseline audit

| Module | Initial classification | Evidence / work required |
|---|---|---|
| Registration, verification, login, sessions | PARTIAL | Real PBKDF2/MySQL/SMTP integration; missing login lockout and global session revocation; verification race and trusted department mapping need repair. |
| Requests and drafts | IMPLEMENTED | Transactional request number, creation, history, audit, pending AI and requester notification; owned draft CRUD. |
| Administrator review | PARTIAL | Real queue, forwarding and duplicate decisions; scoped details, notes, filtering and AI human review need completion. |
| Personnel and account review | PARTIAL | Real account decisions and role/department updates; activation and detail editing missing. |
| Department approval | MISSING | Database and interface exist; no decision API. |
| Work orders and technician workspace | MISSING | Tables and forms exist; no persistence APIs. |
| Notifications | PARTIAL | Some transactional event generation; retrieval/read APIs absent. |
| Python AI | MISSING | Empty service directory; pending records never processed. |
| History and audit | PARTIAL | Request/admin audit writes exist; work lifecycle and staff viewers missing. |
| Dashboards/reports | PARTIAL | Browser-array counts cannot represent institution/department totals. |
| Attachments | PARTIAL | UUID storage, containment, magic bytes and size checks exist; staff access, evidence and file-count enforcement missing. |
| Frontend | PARTIAL | Existing HTC design and real requester integrations; staff forms explicitly disconnected, fresh admin login queue and cross-account cache defects. |
| Configuration/docs | PARTIAL | Environment-based secrets; stale docs, missing AI settings and clean-install migration issue. |
| Tests/deployment | PARTIAL | Baseline `mvn clean test`: 5 passed. Local MySQL service running; Tomcat installed but not running; runtime credentials unavailable in session. |

## Database audit

Existing scripts define departments, roles, personnel/role assignments, categories, requests and number sequences, AI recommendations, approvals, work orders/assignments/progress, attachments, notifications, audit, duplicate links, account reviews/login security, verification tokens/trusted roles and drafts. No existing data is to be dropped. Live schema inspection is pending access to private database configuration. Original migration 015 leaves the original account-status constraint active on a clean install; 015/017 are not rerunnable.

## Work in progress

- Extend the existing approval and work-order tables with transactional APIs and role-scoped resource access.
- Connect existing frontend workspaces, history, notifications, personnel and real dashboard aggregates.
- Add a transparent rule-based Python recommendation service with bounded authenticated Java calls and honest failure states.
- Add database-backed session checks, CSRF enforcement, bounded JSON, account login security and secure uploads.
- Verify meaningful Java/Python tests, final WAR contents and deployment; document exact remaining runtime limitations.

## Verification record

- Baseline Maven: 53 Java source files compile; 5 JUnit tests pass.
- Maven needs the existing cache outside the restricted workspace; approved escalated Maven execution is available.
- Git is rooted above this workspace with pre-existing deletions/untracked siblings. No reset, commit, push or unrelated cleanup has been performed.

## Completion record

Implementation, migrations, API additions, test results, frontend connections and deployment outcomes will be recorded here as they are verified. No end-to-end, SMTP or deployment success is claimed from source inspection alone.
