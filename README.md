# HTC Service Portal

This is the Maven WAR project for the **AI-Based Service Request and Work Order
Management System** of Holy Trinity College of General Santos City. The active
application is packaged from `src/main/webapp/` and served by Tomcat.

## Current application

- Java 17, Jakarta Servlets, JDBC, MySQL, Maven, and Tomcat 10.1.
- Role-scoped requester, administrator, department-head, and technician workflows.
- Notifications, history, audit viewing, and advisory rule-based AI integration.
- PBKDF2-HMAC-SHA-256 password hashing and session/CSRF protections.

The root `index.html`, `css/`, `js/`, and `images/` directory are a separate
static prototype published through GitHub Pages. They are not packaged into the
WAR and do not replace the Tomcat application.

## Required setup

1. Install JDK 17, Maven, MySQL 8.x, and Tomcat 10.1.x.
2. Apply `database/database.sql` and committed additive migrations in numeric
   order to a suitable database.
3. Set `HTC_DB_PASSWORD` for the Tomcat process. Optional overrides are
   `HTC_DB_USER` and `HTC_DB_URL`.
4. Follow `docs/deployment-guide.txt`, adapting its example paths to this
   workspace and the configured local environment.

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

Run the Maven checks and deployment steps in the local development environment
before reporting runtime verification as successful.
