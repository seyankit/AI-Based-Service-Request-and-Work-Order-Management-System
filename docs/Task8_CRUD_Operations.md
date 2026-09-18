# Task 8 – CRUD Operations

## Objective
Demonstrate safe literal CRUD on requester-owned drafts while preserving official/audited service requests.

## Implemented
- Migration `016_requester_drafts_crud.sql` creates `SERVICE_REQUEST_DRAFT`.
- `ServiceRequestDraftDAO` implements Create, Read, Update, and Delete with requester ownership in every SQL predicate.
- `ServiceRequestDraftServlet` exposes `GET`, `POST`, `PUT`, and `DELETE /api/service-request-drafts`.
- POST/PUT/DELETE require authenticated Requester session and CSRF token.
- Draft values use the existing `ServiceRequestValidator` before persistence.
- Official submitted requests are not physically deleted by this implementation.

## Authorization
A requester cannot update or delete another requester's draft because `Requester_ID` is derived from the authenticated session and included in UPDATE/DELETE predicates.

## Screenshot checklist
Draft list, create, edit populated values, invalid edit, updated row, delete confirmation in UI when wired, deleted row, refresh verification, unauthorized request.

## Verification status
Backend CRUD code and SQL migration are included. A complete browser CRUD control layer still requires local end-to-end testing before claiming final completion.

## Git
Commit hash: `TO_BE_RECORDED_AFTER_LOCAL_COMMIT`
