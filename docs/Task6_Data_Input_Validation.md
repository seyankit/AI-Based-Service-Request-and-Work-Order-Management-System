# Task 6 – Data Input Validation

## Objective
Implement accessible client-side validation that mirrors authoritative Java validation.

## Implemented
- Registration uses First Name, Middle Name, Last Name, and Suffix.
- System Role selection was removed from registration and login.
- HTC email domain, contact number, personnel type, department, password strength, and password confirmation are validated.
- Service-request validation already present in `ServiceRequestValidator` remains authoritative.
- Inline errors use the existing `.is-invalid`, `.is-valid`, validation message, `aria-invalid`, and `aria-describedby` behavior.
- Browser registration JSON no longer contains `roleId`.

## Files changed
- `src/main/webapp/index.html`
- `src/main/webapp/js/script.js`
- `src/main/java/.../util/RegistrationValidator.java`
- `src/main/java/.../servlet/RegisterServlet.java`
- `src/test/java/.../RegistrationValidatorTest.java`

## Tests
| Test | Result |
|---|---|
| JavaScript syntax (`node --check`) | PASS |
| JUnit suite | NOT RUN in this container because Maven is unavailable |
| Live browser/API validation | Requires local Tomcat + MySQL |

## Screenshot checklist
1. Empty registration errors
2. Invalid HTC email
3. Invalid name/contact
4. Weak password
5. Password mismatch
6. Valid registration without role selector
7. Check Your Email screen
8. Invalid/valid service-request validation

## Git
Commit hash: `TO_BE_RECORDED_AFTER_LOCAL_COMMIT`
Repository: `YOUR_GITHUB_REPOSITORY_URL`
