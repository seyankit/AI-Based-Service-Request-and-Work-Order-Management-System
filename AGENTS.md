# HTCServicePortal — AGENTS.md

This file is the authoritative operating guide for AI coding agents working on HTCServicePortal.

All coding agents must read and follow this file before inspecting, modifying, testing, committing, or reviewing the project.

---

# 1. Project Identity

Project:

HTCServicePortal

Full title:

AI-Based Service Request and Work Order Management System

Organization:

Holy Trinity College of General Santos City

Authoritative workspace:

C:\HTCServicePortal-CLEAN

GitHub repository:

https://github.com/seyankit/AI-Based-Service-Request-and-Work-Order-Management-System.git

Development branch:

full-system-migration

Current verified checkpoint:

ca80532 Complete technician work order progress workflow

Do not work from obsolete or duplicate project folders.

The only authoritative local workspace is:

C:\HTCServicePortal-CLEAN

---

# 2. Current Project Status

Completed and frozen:

- Phase 1 — Authentication / Registration / Email Verification
- Phase 2 — Requester Workflow
- Phase 3 — Administrator + Department Head Approval
- Phase 4A — Work Order Creation / Administrator Work Order Details
- Phase 4B — Initial Technician Assignment
- Phase 5A — Technician Backend Workflow

Current next phase:

- Phase 5B — Technician Frontend Integration

Remaining:

- Phase 6 — Notifications + History + Audit Integration
- Phase 7 — Python AI Integration
- Phase 8 — Dashboard + Reports
- Phase 9 — Security Review / Hardening
- Phase 10 — Automated Tests / Acceptance
- Final — Full End-to-End Verification

Do not modify completed/frozen modules unless a real dependency defect is demonstrated by:

- failing tests
- runtime evidence
- database evidence
- security evidence

Do not reopen a frozen phase merely to improve style or architecture.

---

# 3. Development Technology Baseline

Backend:

- Java 17
- Jakarta Servlet API
- Tomcat 10.1
- Maven
- JDBC
- Gson
- MySQL

Frontend:

- HTML
- CSS
- Vanilla JavaScript

Active frontend files:

src/main/webapp/index.html

src/main/webapp/js/script.js

Database:

- MySQL
- additive SQL migrations under database/

Development:

- VS Code
- Git
- GitHub

Planned AI component:

- Python AI recommendation service

Do not introduce major replacement frameworks unless explicitly approved.

Do not migrate this project to:

- Spring
- Spring Boot
- Hibernate
- JPA
- React
- Vue
- Angular
- Node backend
- another database

without explicit authorization.

Preserve Java 17 + Jakarta + Tomcat + Maven + MySQL architecture.

---

# 4. Active Frontend Rule

The active application is:

src/main/webapp/index.html

The active JavaScript is:

src/main/webapp/js/script.js

Do not assume another JavaScript file is active.

In particular:

workflows.js

is currently not loaded by the production interface.

Do not modify, activate, or depend on workflows.js unless a future phase explicitly approves it.

Do not create a parallel frontend implementation.

Do not modify unrelated GitHub Pages or old prototype files while implementing backend workflows.

---

# 5. Role Model

Current roles:

Role_ID = 1

Requester

Role_ID = 2

Service Administrator / Coordinator

Role_ID = 3

Department Head / Authorized Approver

Role_ID = 4

Service Personnel / Technician

The authenticated session is authoritative for the acting user.

Never trust client-supplied:

- Personnel_ID of the actor
- Role_ID of the actor
- department authority
- Created_By
- Changed_By
- Assigned_By
- Approved_By
- audit actor
- lifecycle status

A client may submit the ID of a target resource when appropriate, such as:

- target technician
- target service request
- target work order

but the server must independently validate that target.

---

# 6. Master Security Rules

Every new endpoint or mutation must preserve:

- authenticated sessions
- account status checks
- role-based authorization
- CSRF protection
- session-derived actor identity
- ownership boundaries
- department boundaries
- prepared statements
- transaction rollback
- safe external error responses

Never:

- expose password hashes
- return SQL errors to the browser
- return stack traces to the browser
- log passwords
- log SMTP passwords
- log verification tokens
- hardcode secrets
- disable CSRF to make a feature work
- remove authorization checks to make a test pass
- trust browser-provided lifecycle states
- trust browser-provided role information

If a security mechanism appears to block an implementation:

find the root cause.

Do not remove the security mechanism.

---

# 7. Authentication Security — Frozen

Existing authentication behavior is considered stable.

Current security includes:

- PBKDF2WithHmacSHA256
- salted password hashing
- dummy password verification for unknown users
- login attempt tracking
- account lockout
- session creation
- session fixation protection
- active account enforcement
- role session data
- CSRF
- secure logout behavior

Known login policy:

- 5 failed attempts
- approximately 15-minute lockout
- generic invalid-login response
- unknown accounts receive equivalent password-hash work
- correct password does not bypass active lockout
- failure state resets only after successful valid authentication

Do not redesign authentication during later workflow phases.

---

# 8. Registration and Email Verification

Existing components include:

- RegisterServlet
- EmailVerificationDAO
- EmailVerificationServlet
- ResendVerificationServlet
- MailService

School registration email domain:

@online.htcgsc.edu.ph

Email verification endpoints include:

/api/email-verification/verify

/api/email-verification/resend

Verification tokens:

- are generated server-side
- are hashed before storage
- expire
- are single-use
- may be replaced by resend
- resend is rate limited

The SMTP sender account and registered school email are different concepts.

Development may use a dedicated sender account.

Do not expose SMTP credentials.

Known environment variables:

HTC_MAIL_HOST

HTC_MAIL_PORT

HTC_MAIL_USER

HTC_MAIL_PASSWORD

HTC_MAIL_FROM

HTC_APP_BASE_URL

Google App Passwords are 16 characters.

Google may visually display spaces between groups.

The runtime SMTP password must not contain those grouping spaces.

Do not commit mail credentials.

---

# 9. Database Environment

Known database environment variables:

HTC_DB_URL

HTC_DB_USER

HTC_DB_PASSWORD

Never print the password value.

Safe environment checks may confirm only whether variables are present.

Example:

"DB URL set:  " + [bool]$env:HTC_DB_URL

"DB User set: " + [bool]$env:HTC_DB_USER

"DB Pass set: " + [bool]$env:HTC_DB_PASSWORD

Do not commit database credentials.

---

# 10. Runtime Database Verification Rule

Do not assume the local runtime database contains every migration committed to Git.

When a SQL/runtime error occurs:

1. inspect the actual runtime schema
2. use SHOW TABLES
3. use SHOW COLUMNS
4. use DESCRIBE
5. compare runtime schema against committed migration files
6. determine whether a migration simply has not been applied
7. prefer applying an existing committed additive migration over rewriting correct Java code

Do not automatically rewrite application code because a runtime table is missing.

Known example:

database/019_workflow_history.sql

already existed in the repository, but the local database initially did not contain:

WORK_ORDER_HISTORY

Work Order creation correctly rolled back because history insertion failed.

Applying the existing migration fixed the runtime issue without changing Java.

Never:

- DROP tables casually
- TRUNCATE data casually
- recreate the whole database to fix one table
- manually force lifecycle status just to make a test pass
- run destructive migrations without explicit approval

---

# 11. Important Runtime Schema Facts

Use actual runtime column names.

For WORK_ORDER, the status column is:

Work_Status

Do NOT use:

Status

Do NOT use:

Work_Order_Status

Important WORK_ORDER fields include:

- Work_Order_ID
- Work_Order_Number
- Request_ID
- Department_ID
- Created_By
- Work_Description
- Work_Status
- Target_Start_Date
- Target_Completion_Date
- Acknowledged_At
- Actual_Start_At
- Completed_At
- Completion_Summary
- Verified_By
- Verified_At
- Created_At
- Updated_At

Schema-supported Work Order statuses:

- Created
- Assigned
- Acknowledged
- In Progress
- On Hold
- Completed
- Verified

WORK_ORDER.Request_ID is unique.

Therefore:

one Service Request may have at most one Work Order.

---

# 12. Identifier Formats

Service Request format:

SR-YYYY-NNNNNN

Example:

SR-2026-000003

Do not use the obsolete four-digit Service Request validator:

SR-YYYY-NNNN

Work Order format:

WO-YYYY-NNNN

Example:

WO-2026-0001

Frontend and backend validation must preserve actual repository formats.

---

# 13. Change-Control Rules

Do not reformat, prettify, normalize, or rewrite entire existing files when making a localized change.

Preserve existing formatting and line structure.

Modify only the minimum lines and files required for the current task.

Do not modify unrelated modules.

Do not create:

- backup source files
- temporary source copies
- corrupted copies
- step copies
- `file_old.java`
- `file_backup.java`
- `script2.js`
- parallel implementation files

inside src/.

Do not make speculative improvements.

Do not perform unrelated refactors.

Fix only confirmed problems required by the current task.

Do not bulk-fix SonarQube warnings.

Do not reorganize unrelated classes.

Do not rename unrelated methods.

Do not update dependencies merely because newer versions exist.

---

# 14. Required Pre-Edit Check

Before modifying anything:

1. Verify workspace:

C:\HTCServicePortal-CLEAN

2. Verify branch:

full-system-migration

3. Run:

git status --short

4. Run:

git branch --show-current

5. Run:

git log -1 --oneline

The working tree should normally be clean before a new phase begins.

If unexpected modifications exist:

STOP.

Report them.

Do not automatically:

- reset
- restore
- clean
- delete
- checkout over them

---

# 15. Development Workflow

Required workflow:

inspect
→ identify smallest missing capability
→ minimal implementation
→ focused tests
→ full tests
→ package
→ review diff
→ runtime verification
→ database verification
→ authorization verification
→ commit
→ push
→ clean status
→ freeze phase

Short form:

One small feature
→ minimal change
→ test
→ package
→ runtime verify
→ commit
→ stop

Do not continue into another module until the current change is verified and committed.

---

# 16. Root-Cause-First Debugging

When something fails, do not immediately change code.

Investigate in this order:

1. browser Network request
2. HTTP method
3. HTTP status
4. response body
5. Tomcat/backend logs
6. runtime database schema
7. runtime database data
8. environment variables
9. stale frontend assets
10. application code

Known examples:

SMTP failure:
Google App Password contained grouping spaces.

Work Order failure:
runtime database lacked WORK_ORDER_HISTORY.

Frontend issue:
stale JavaScript after redeployment.

SQL verification issue:
manual query used an incorrect column name.

Do not change correct code to compensate for an incorrect manual query or incomplete local database.

---

# 17. Transaction Rules

Use one database transaction when a workflow operation updates multiple authoritative records.

Examples:

Approval:

approval
+ request status
+ request history
+ notifications
+ audit

Work Order creation:

work order
+ work order history
+ audit

Technician assignment:

assignment
+ work order status
+ history
+ audit
+ notification if required

Technician progress:

work order status
+ progress
+ history
+ audit
+ notification if required

On failure:

ROLLBACK.

Never leave partially completed workflow state.

Follow existing JDBC patterns:

- setAutoCommit(false)
- prepared statements
- try-with-resources
- commit
- rollback
- restore connection state where appropriate

---

# 18. API Conventions

Follow existing project API conventions.

Typical responses:

200
successful read/update

201
successful creation

400
invalid request

401
not authenticated

403
authenticated but unauthorized

404
resource not found or intentionally non-disclosed

409
workflow conflict / duplicate / invalid current state

413
payload too large

415
unsupported media type

429
rate limiting / resend cooldown

500
unexpected internal error

Do not expose:

- SQL text
- stack traces
- filesystem internals
- passwords
- tokens
- credential values

to the client.

---

# 19. Input Validation

State-changing JSON APIs should normally verify:

- authentication
- authorization
- CSRF
- Content-Type
- request size
- valid JSON
- required fields
- positive IDs
- schema-compatible text lengths
- authoritative database state
- valid lifecycle transition

Do not rely only on frontend validation.

Frontend validation improves usability.

Backend validation provides security and integrity.

---

# 20. Frontend Implementation Rules

Use the existing:

- state object
- fetch conventions
- CSRF helpers
- toast helpers
- modal helpers
- render functions

Do not create another networking framework unless necessary.

After a successful backend mutation:

reload authoritative data from the backend.

Do not manufacture final authoritative objects only in JavaScript state.

Forms should show only functionality implemented in the current backend phase.

Example:

Phase 4A Create Work Order form:

- Service Request ID
- Work Description

Technician selection belongs to Phase 4B.

Progress fields belong to Phase 5B frontend integration; the Phase 5A backend is complete/frozen.

Do not expose future workflow controls prematurely.

---

# 21. Testing Rules

Prefer deterministic unit and servlet tests.

Servlet tests should avoid requiring a live MySQL database when practical.

Use injectable DAO/function boundaries where existing repository patterns support them.

Do not introduce a new testing framework without approval.

Do not introduce:

- Docker
- Testcontainers
- embedded MySQL alternatives
- another build system

merely for convenience.

Tests should cover, when relevant:

- authentication
- authorization
- ownership
- department scope
- malformed input
- invalid IDs
- duplicate state
- invalid state transitions
- CSRF
- success behavior
- failure mapping

Full required Java verification:

mvn clean test

then:

mvn clean package

If JavaScript changed:

node --check src/main/webapp/js/script.js

---

# 22. Definition of Phase Complete

A phase is NOT complete merely because code compiles.

A phase is complete only after:

1. required code implemented
2. focused tests pass
3. full Maven tests pass
4. Maven package passes
5. JavaScript syntax check passes when JS changed
6. WAR deployed
7. runtime workflow succeeds
8. expected database records verified
9. authorization boundaries verified
10. duplicate/invalid behavior verified
11. rollback behavior verified where practical
12. git diff reviewed
13. no unrelated files changed
14. commit created
15. commit pushed
16. branch matches origin
17. git status is clean

After that:

mark the phase frozen.

---

# 23. Git Rules

Before editing:

git status --short

git branch --show-current

git log -1 --oneline

After editing:

git status --short

git diff --stat

git diff --check

git diff

Before commit:

stage only expected files.

Then:

git diff --cached --stat

git diff --cached --check

After push:

git status --short

git log -3 --oneline

Do not automatically:

- git reset --hard
- git clean -fd
- force push
- rewrite Git history
- switch branches
- merge into main

Do not merge:

full-system-migration

into:

main

until all required project phases and final acceptance tests are complete.

---

# 24. AI Coding Agent Rules

Use only one autonomous coding agent on the working tree at a time.

Do not run multiple editors/agents concurrently against the same files.

Possible project agents include:

- Cline
- Codex
- Kilo
- Claude
- Copilot
- Gemini
- Amazon Q

Agent names in the roadmap represent preferred responsibilities, not permission to widen scope.

Recommended pattern:

architecture / inspection
→ implementation
→ manual verification
→ commit

Inspection-only prompts:

must not modify files.

Implementation prompts:

must remain inside explicitly approved scope.

Do not let an agent automatically commit or push unless explicitly instructed.

Prefer narrow inspection over repeated full repository scans.

---

# 25. Windows Path Safety

The Windows user profile path contains a space:

C:\Users\Sean Keith

Never pass a path containing spaces without proper PowerShell quoting.

Prefer:

- $env:USERPROFILE
- Join-Path
- Set-Location -LiteralPath
- Get-Content -LiteralPath

Correct:

Set-Location -LiteralPath "C:\Users\Sean Keith\Tools"

Correct:

$path = Join-Path $env:USERPROFILE "Tools\Maven"

Wrong:

cd C:\Users\Sean Keith\Tools

Never use these merely to inspect project files:

- start
- Start-Process
- Invoke-Item
- ii
- explorer
- cmd /c start

Do not launch/open a target named:

Sean

Use agent file-reading tools or:

Get-Content -LiteralPath

instead.

Do not paste PowerShell prompt markers as commands.

These are not commands:

PS C:\HTCServicePortal-CLEAN>

>>

---

# 26. Java / Maven Environment

Required Java:

Java 17

Known correct JDK:

C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot

Known Maven:

C:\Users\Sean Keith\Tools\Maven\apache-maven-3.9.16

Before important builds verify:

java -version

mvn -version

where.exe java

Do not intentionally compile this project using Oracle JDK 26.

Project target remains Java 17.

---

# 27. Tomcat Runtime

Tomcat:

C:\apache-tomcat-10.1.54\apache-tomcat-10.1.54

Generated WAR:

C:\HTCServicePortal-CLEAN\target\HTCServicePortal.war

Restarting Tomcat invalidates existing web sessions.

After restart:

log in again.

When JavaScript or HTML changes:

- redeploy current WAR
- use Ctrl + Shift + R
- disable browser cache during debugging when necessary

Do not interpret stale browser assets as a server-side defect until deployment/cache state is verified.

---

# 28. PHASE ROADMAP

# PHASE 1 — Authentication Runtime Verification

Preferred tools:

Codex / Cline

Status:

COMPLETE / FROZEN

Implemented and verified:

- login
- logout
- session handling
- role session state
- active-account validation
- password hashing
- PBKDF2
- login failure tracking
- lockout
- dummy password verification
- CSRF
- session fixation protection
- registration validation
- email verification
- verification resend
- SMTP runtime configuration

Do not redesign this phase unless a verified security defect exists.

---

# PHASE 2 — Requester Workflow

Preferred tools:

Codex / Cline

Status:

COMPLETE / FROZEN

Implemented:

- requester drafts
- draft update
- final submission
- validation
- body size protection
- Content-Type enforcement
- requester ownership
- request details
- tracking/history
- foreign request non-disclosure
- attachment metadata ownership boundary

Requester identity must always come from the authenticated session.

Do not trust browser-submitted Requester_ID.

---

# PHASE 3 — Administrator + Department Head Approval

Preferred review:

Claude

Preferred implementation:

Codex / Cline

Status:

COMPLETE / FROZEN

Frozen checkpoint:

41c0999 Complete department head approval workflow

Implemented:

GET /api/approvals

PUT /api/approvals

Role behavior:

Role 2:
may list approvals.

Role 3:
may list department-scoped approvals and approve/reject.

Rules include:

- request must be Awaiting Approval
- approval must be Pending
- Department Head must match department
- self-approval denied
- actor from session
- transaction used
- request status history written
- audit written
- notifications written

Transitions:

Awaiting Approval
→ Approved

Awaiting Approval
→ Rejected

Do not change ApprovalDAO or ApprovalServlet without demonstrated need.

---

# PHASE 4 — Work Orders

Preferred architecture:

Kilo Architect

Preferred implementation:

Codex / Cline

Phase 4 is split into smaller controlled sub-phases.

---

## PHASE 4A — Work Order Creation / Administrator Details

Status:

COMPLETE / FROZEN

Frozen checkpoint:

19d83a9 Complete work order creation workflow

Backend:

WorkOrderDAO.java

WorkOrderServlet.java

API:

GET /api/work-orders

GET /api/work-orders?workOrderId=<id>

POST /api/work-orders

Authorization:

Role 2 Service Administrator only.

Creation rules:

- Service Request must exist
- Service Request must be Approved
- only one Work Order per Service Request
- Department_ID derived from Routed_Department_ID
- Created_By derived from authenticated administrator
- initial Work_Status = Created
- Work_Order_Number generated server-side
- browser cannot choose actor
- browser cannot choose role
- browser cannot choose department
- browser cannot choose initial status
- browser cannot choose Work Order number

Creation transaction writes:

- WORK_ORDER
- WORK_ORDER_HISTORY
- AUDIT_LOG

Creation notification currently deferred.

SERVICE_REQUEST remains:

Approved

after Work Order creation.

Do NOT change the Service Request to Assigned merely because the Work Order exists.

Verified runtime test example:

Service Request:

SR-2026-000003

Request_ID:

3

Department_ID:

1

Generated Work Order:

WO-2026-0001

Work_Order_ID:

5

Work_Status:

Created

Duplicate Work Order creation correctly rejected.

These IDs are local test examples only.

Never hardcode them.

---

## PHASE 4B — Technician / Service Personnel Assignment

Status:

COMPLETE / FROZEN

Frozen checkpoint:

cf8e39e Complete initial technician assignment workflow

Verified implementation:

Created Work Order
- Administrator selects eligible Service Personnel
- Assignment persisted
- Work Order becomes Assigned
- history/audit written
- technician notification written

Implementation was validated against:

- WORK_ORDER_ASSIGNMENT
- WORK_ORDER
- SCHOOL_PERSONNEL
- PERSONNEL_ROLE_ASSIGNMENT
- PERSONNEL_ROLE
- DEPARTMENT
- WORK_ORDER_HISTORY
- AUDIT_LOG
- NOTIFICATION
- WorkflowPolicy
- WorkflowStore
- ResourceAccess
- WorkOrderDAO
- WorkOrderServlet
- index.html
- script.js

Verified rules:

- Role 4 technician eligibility
- account must be Active
- department relationship
- current assignment semantics
- Assignment_Sequence
- Is_Current behavior
- assignment end behavior
- reassignment remains deferred
- Created → Assigned transition
- whether SERVICE_REQUEST also changes status

Do not guess.

Use repository evidence.

Implemented transaction:

1. authenticate Role 2 administrator
2. lock Work Order
3. verify current Work_Status
4. load target technician
5. verify technician active
6. verify technician Role 4
7. enforce department rule if supported
8. ensure assignment consistency
9. create WORK_ORDER_ASSIGNMENT
10. change Work_Status to Assigned
11. write WORK_ORDER_HISTORY
12. write AUDIT_LOG
13. create assignment notification if semantics are clear
14. commit

On any failure:

rollback.

Phase 4B must NOT implement:

- technician acknowledgement
- start work
- progress updates
- On Hold
- completion
- verification

Reassignment remains deferred and may be addressed in a separately approved Phase 4B.2.

---

# PHASE 5 — Technician Workflow

Preferred architecture:

Kilo Architect

Preferred implementation:

Codex / Cline

Status:

PARTIALLY COMPLETE — Phase 5A is frozen; Phase 5B frontend integration is pending.

Objective:

assigned technician executes the Work Order lifecycle.

Schema/policy lifecycle (verification remains outside Phase 5A):

Assigned
→ Acknowledged
→ In Progress
→ On Hold
→ In Progress
→ Completed
→ Verified

Do not assume every user can perform every transition.

Technician must only access Work Orders currently assigned to their authenticated Personnel_ID.

Never accept the acting Technician_ID from the browser.

Phase 5 capabilities (backend complete in Phase 5A; frontend pending in Phase 5B):

- technician queue
- assigned Work Order details
- acknowledge
- start work
- progress entries
- On Hold reason
- resume
- completion
- completion summary

Verification actor must be determined from actual project rules before implementation.

Every mutation must verify:

- authenticated actor
- Role 4 where required
- current assignment
- valid current status
- valid next transition
- transaction integrity
- history for real status changes only
- audit
- notifications to WORK_ORDER.Created_By for Phase 5A actions

Do not permit arbitrary status jumps.

---

## PHASE 5A — Technician Backend Workflow

Status:

COMPLETE / FROZEN

Frozen checkpoint:

ca80532 Complete technician work order progress workflow

Implemented API:

/api/work-order-progress

Verified behavior:

- Role 4 technician-scoped queue and Work Order details
- authenticated actor derived from the session
- Active account and Role 4 enforcement
- current-assignment ownership enforcement
- completion summary required
- completion percentage = 100
- technician cannot verify
- post-completion technician progress rejected
- unauthenticated mutation rejected

Implemented transitions:

- Assigned → Acknowledged
- Acknowledged → In Progress
- In Progress → In Progress (progress update)
- In Progress → On Hold
- On Hold → On Hold (progress update)
- On Hold → In Progress (resume)
- In Progress → Completed

Transactional records:

- WORK_ORDER status and lifecycle fields
- WORK_ORDER_PROGRESS append-only records
- WORK_ORDER_HISTORY for real status changes only
- AUDIT_LOG writes
- notifications to WORK_ORDER.Created_By

Requester visibility is false for Phase 5A progress rows.

SERVICE_REQUEST remains Approved throughout the Phase 5A technician workflow, including Work Order completion.

Phase 5A does not implement:

- reassignment (deferred)
- verification
- closure
- requester-facing progress
- Phase 5 frontend integration
- SMTP changes

Verified local runtime fixture:

- Work Order: WO-2026-0001
- Work_Order_ID = 5
- Technician_ID = 15

Verified lifecycle:

Assigned
→ Acknowledged
→ In Progress
→ 40% Progress
→ On Hold
→ 60% Progress
→ Resumed
→ 80% Progress
→ Completed

Final runtime snapshot for this fixture:

- WORK_ORDER_PROGRESS rows = 8
- WORK_ORDER_HISTORY rows = 7 total, including earlier lifecycle history
- WORK_ORDER.Work_Status = Completed
- SERVICE_REQUEST status = Approved

These IDs and row counts are local verification examples only.

Never hardcode them.

Negative runtime checks verified:

- unsupported verify action → HTTP 400
- progress after Completed → HTTP 409
- unauthenticated mutation → HTTP 401

Verified test/build baseline at ca80532:

- Java 17
- Maven 3.9.16
- 86 tests passed; 0 failures, 0 errors, 0 skipped
- mvn clean package: BUILD SUCCESS
- node --check src/main/webapp/js/script.js: PASS
- Git clean after the ca80532 implementation commit/push

Do not reopen the frozen backend without a demonstrated dependency defect.

---

## PHASE 5B — Technician Frontend Integration

Status:

PLANNED — CURRENT NEXT SUB-PHASE

Objective:

integrate the frozen Phase 5A technician queue, details, and lifecycle actions into the active frontend.

Active files:

- src/main/webapp/index.html
- src/main/webapp/js/script.js

Use the existing state, fetch, CSRF, toast, modal, and render helpers.

Reload authoritative backend data after successful mutations.

Do not activate workflows.js or create a parallel frontend.

Reassignment remains deferred. Verification remains outside Phase 5A and requires an authorized actor/policy to be established before implementation.

Phase 5 as a whole is not complete while Phase 5B frontend integration is pending.

---

# PHASE 6 — Notifications + History + Audit

Preferred tools:

Copilot / Cline

Status:

PLANNED

Objective:

complete lifecycle traceability.

Review:

- REQUEST_STATUS_HISTORY
- WORK_ORDER_HISTORY
- WORK_ORDER_PROGRESS
- AUDIT_LOG
- NOTIFICATION

Possible events include:

- request submitted
- approval required
- request approved
- request rejected
- Work Order created
- technician assigned
- technician reassigned
- technician acknowledged assignment
- work started
- progress updated
- work put On Hold
- work resumed
- work completed
- verification
- request/work closure if supported

Do not duplicate records already generated correctly by previous phases.

Notification recipients must be supported by real workflow requirements.

Do not guess recipients.

Database notifications and email notifications are separate concerns.

Do not automatically add SMTP mail for every database notification unless explicitly required.

---

# PHASE 7 — Python AI Integration

Preferred architecture:

Claude / Kilo

Preferred implementation:

Codex / Cline

Status:

PLANNED

Expected AI capabilities:

- Request Classification
- Priority Recommendation
- Duplicate Detection
- Service Insights

AI is advisory.

AI must NOT directly:

- approve requests
- reject requests
- assign technicians without authorized confirmation
- complete Work Orders
- verify Work Orders
- bypass authorization
- override human workflow decisions

Authorized personnel review recommendations.

Preferred architecture:

Java HTCServicePortal
↔ HTTP API
↔ Python AI service

The Java backend remains the authoritative system-of-record interface.

AI service failure must not prevent normal manual service-request operations.

Handle:

- timeout
- service unavailable
- invalid response
- malformed JSON
- unsafe output
- fallback behavior

Never send:

- DB credentials
- passwords
- SMTP secrets
- authentication tokens

to the AI service.

---

# PHASE 8 — Dashboard + Reports

Preferred tools:

Copilot / Cline

Status:

PLANNED

Replace placeholder/mock dashboard information with real database-backed information.

Potential Requester metrics:

- total requests
- pending requests
- completed requests
- recent requests

Administrator metrics:

- requests by status
- approval state
- open Work Orders
- assignments
- completion
- department workload
- service categories

Department Head:

- pending approvals
- approval history
- department requests

Technician:

- assigned work
- active work
- completed work

Rules:

- use real database data
- respect role scope
- respect department boundaries
- avoid unbounded queries
- use SQL aggregation appropriately
- do not use fake production statistics
- clearly identify AI insights separately from actual metrics

---

# PHASE 9 — Security

Preferred review:

Claude / Amazon Q

Preferred fixes:

Codex / Cline

Status:

PLANNED

Perform final security review.

Authentication:

- login
- lockout
- logout
- session invalidation
- inactive account
- session fixation
- CSRF

Authorization:

- requester ownership
- Department Head department scope
- administrator actions
- technician assignment ownership
- cross-department access
- direct URL/API attacks

Input:

- malformed JSON
- invalid IDs
- wrong content types
- oversized payloads
- duplicate actions
- invalid transitions

Database:

- rollback
- duplicate constraints
- foreign keys
- missing migration detection
- assignment consistency

Workflow attacks:

- bypass approval
- create duplicate Work Order
- assign invalid personnel
- access another technician's work
- skip statuses
- restart completed/verified work

Attachments:

- ownership
- file authorization
- traversal prevention
- file size/type limits

Do not perform large unrelated refactors during security hardening.

Fix confirmed vulnerabilities only.

---

# PHASE 10 — Automated Tests

Preferred tools:

Copilot / Gemini

Status:

PLANNED

Expand automated regression coverage after core workflows stabilize.

Cover:

Authentication

Registration

Email verification

Requester workflow

Approval workflow

Work Order creation

Technician assignment

Technician workflow

Notifications

History

Audit

AI fallback behavior

Reports

Security boundaries

Important test categories:

- happy path
- unauthenticated
- unauthorized
- wrong role
- wrong department
- wrong owner
- malformed request
- missing record
- duplicate
- invalid state
- CSRF
- rollback
- boundary values

Do not change production behavior merely to make weak tests easier.

Tests must validate intended behavior.

---

# FINAL — Full End-to-End Test

The system is ready for final acceptance only after all planned phases are complete.

Required complete workflow:

Requester

Register
→ Verify email
→ Login
→ Submit request
→ Track request

Administrator

Review service workflow
→ Work with approved request
→ Create Work Order
→ Assign Service Personnel

Department Head

Review approval
→ Approve or Reject

Technician

View assigned work
→ Acknowledge
→ Start
→ Update progress
→ Complete

Authorized verifier

Verify completion if required by project rules

System

→ histories
→ audit
→ notifications
→ dashboards
→ reports
→ AI recommendations

Final acceptance requires:

- clean Git working tree
- all expected migrations applied
- full Maven tests passing
- Maven WAR package passing
- JavaScript checks passing
- health endpoint working
- database connection working
- SMTP/email verification working
- all role workflows verified
- authorization tested
- no hardcoded secrets
- deployment documentation
- environment-variable documentation
- known limitations documented

Final deployment artifact:

target/HTCServicePortal.war

Do not commit:

- target/
- logs/
- passwords
- App Passwords
- .env files containing secrets
- local machine-specific credentials

---

# 29. Agent Responsibility Map

Current preferred workflow:

PHASE 1

Authentication runtime verification

Codex / Cline

        ↓

PHASE 2

Requester workflow

Codex / Cline

        ↓

PHASE 3

Admin + Department Head approval

Claude review

        ↓

Codex/Cline implementation

        ↓

PHASE 4

Work Orders

Kilo Architect

        ↓

Codex/Cline implementation

        ↓

PHASE 5

Phase 5A — Technician Backend Workflow: COMPLETE / FROZEN (ca80532)

Phase 5B — Technician Frontend Integration: current next sub-phase

Kilo Architect

        ↓

Codex/Cline implementation

        ↓

PHASE 6

Notifications + History + Audit

Copilot/Cline

        ↓

PHASE 7

Python AI Integration

Claude/Kilo architecture

        ↓

Codex/Cline implementation

        ↓

PHASE 8

Dashboard + Reports

Copilot/Cline

        ↓

PHASE 9

Security

Claude/Amazon Q review

        ↓

Codex/Cline fixes

        ↓

PHASE 10

Automated Tests

Copilot/Gemini

        ↓

FINAL

Full end-to-end test

This agent map is guidance.

It does not override:

- scope rules
- security requirements
- change control
- phase boundaries
- frozen modules
- runtime verification requirements

---

# 30. Final Agent Rule

Before doing anything:

understand the current phase.

Do not solve a future phase early.

Do not modify a frozen module without evidence.

Do not make speculative improvements.

Do not create architecture that conflicts with the existing system.

Always prefer:

existing implementation
+ smallest safe extension

over:

replacement implementation.

The expected operating pattern is:

INSPECT
→ REPORT
→ APPROVE SCOPE
→ IMPLEMENT MINIMUM CHANGE
→ TEST
→ PACKAGE
→ RUNTIME VERIFY
→ DATABASE VERIFY
→ SECURITY VERIFY
→ REVIEW DIFF
→ COMMIT
→ PUSH
→ STOP

Never continue automatically into the next phase.
