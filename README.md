# HTC Service Portal

This is the corrected Maven WAR project for the **AI-Based Service Request and
Work Order Management System** of Holy Trinity College of General Santos City.
The original interface, dashboards, role workspaces, modals, styling hooks, and
front-end service-request demonstrations are retained.

## What is connected

- MySQL-backed department choices: `GET api/departments`
- Registration: `POST api/register`
- Login and Java `HttpSession`: `POST api/login`
- Refresh/session restoration: `GET api/session`
- Server-side logout: `POST api/logout`
- Database health check: `GET api/health`
- PBKDF2-HMAC-SHA-256 password hashing on the Java server

Requester accounts are activated after registration. Privileged role requests
(administrator, approver, and technician) are stored as `Pending` until an
authorized person verifies them in MySQL. This preserves the registration role
choice without allowing a visitor to grant themselves administrative access.

## Current scope boundary

Authentication and department lookup are connected to Java/MySQL. The supplied
service-request, approval, work-order, progress, and personnel-management
screens still use the original in-memory JavaScript demonstration data because
their Java classes, API contract, and complete approved SQL definitions were not
included. Their interface was preserved, but those records are not yet database
persistent.

## Required setup

1. Install JDK 17, Maven, MySQL 8.x, and Tomcat 10.1.x.
2. Run `database/database.sql` in MySQL Workbench and inspect every `SHOW CREATE
   TABLE` result if the tables already existed.
3. Rotate the previously exposed `htc_app` password.
4. Set `HTC_DB_PASSWORD` for the Tomcat process. Optional overrides are
   `HTC_DB_USER` and `HTC_DB_URL`.
5. Copy the eight original image assets listed in
   `src/main/webapp/images/README.txt` into that directory.
6. Follow `docs/build_and_deploy_commands.txt`.

## Expected Maven layout

```text
HTCServicePortal
├── pom.xml
├── database/database.sql
├── docs/
├── src/main/java/ph/edu/htcgsc/serviceportal/
│   ├── config/
│   ├── dao/
│   ├── model/
│   ├── servlet/
│   └── util/
└── src/main/webapp/
    ├── WEB-INF/web.xml
    ├── css/style.css
    ├── images/
    ├── js/script.js
    └── index.html
```

The Maven build produces `target/HTCServicePortal.war`, which deploys at the
context path `/HTCServicePortal` when copied to Tomcat's `webapps` directory.

## Important verification boundary

The source, XML, JavaScript syntax, role/department mappings, password utility,
and Java package structure were statically verified in the repair environment.
That environment did not contain Maven, Tomcat, or MySQL, so the final Maven
dependency resolution, live JDBC connection, servlet deployment, and browser
flows must be run on the supplied Windows development machine using the command
file above. Do not report those live tests as passed until their actual output
is successful.
