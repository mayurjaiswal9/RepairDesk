# RepairDesk — Implementation Plan (Version 1)

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Implementation Task Plan
**Version:** 1.0
**Status:** Ready for Execution
**Derived from:** [constitution.md](constitution.md), [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Database.md](Database.md), [API.md](API.md), and the nine feature specs in `features/`.

> This plan breaks V1 into small, independently implementable tasks (each ~1–3 hours). Tasks are grouped into five phases and ordered by dependency. Every task lists an ID, title, description, dependencies, and acceptance criteria. No code is included here — tasks reference the design docs as the source of truth.

## How to read this plan

- **Task ID prefixes:** `PROJ` (project setup), `CORE` (cross-cutting infra), `DB` (database/migrations), `AUTH` (auth), `NAV` (routing), `THEME`/`SHARED` (UI infra), `AUDIT`, `FILE`, `NOTIF`, `REQ` (repair request), `RV` (request review), `JOB`, `SCH` (scheduling), `TRACK` (customer home), `DASH`, `CUST` (customer mgmt), `PROD` (device categories), `STAFF` (staff mgmt), `SET` (settings), `TEST`, `ERR`, `SEC`, `PERF`, `DOC`, `REL`.
- **Layer tag:** `[BE]` backend (Spring Boot), `[FE]` frontend (Flutter), `[INFRA]` tooling/ops, `[QA]`.
- **Effort:** S ≈ 1h, M ≈ 2h, L ≈ 3h.
- **Cross-cutting rule (applies to every BE write task):** all state changes are transactional, write an `audit_events` row (see `AUDIT-001`), enforce RBAC + ownership server-side, use optimistic `version` checks, and emit notifications where the feature spec requires.

---

# Phase 1 — Foundation

Goal: a runnable, secured, migrated skeleton on both tiers with navigation, theming, error handling, and auth infrastructure in place.

## 1A. Project & Tooling Setup

### PROJ-001 · Backend project scaffold `[BE]` (M)
**Description:** Initialize the Spring Boot monolith (build tool, base dependencies for Web, Validation, Data JPA, Security, Flyway, Actuator) and the package-by-feature structure from Architecture §Backend.
**Dependencies:** None
**Acceptance Criteria:**
- Project builds and starts with a health endpoint reachable.
- Package structure (`common`, `config`, `security`, `feature/*`) created per Architecture.
- Dependency versions pinned via the build tool.

### PROJ-002 · Backend configuration & profiles `[BE/INFRA]` (M)
**Description:** Externalize configuration (DB URL/creds, JWT secret, storage path, CORS origins) via env vars and Spring profiles (`local`, `prod`).
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- No secrets committed; all read from environment/profile.
- App starts cleanly under `local` profile; `prod` documented.
- CORS configured for the future Flutter Web origin.

### PROJ-003 · Actuator health & base observability `[BE/INFRA]` (S)
**Description:** Enable Actuator health/readiness and basic metrics for a single-instance deployment.
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- `/health` (or actuator health) returns UP.
- Liveness/readiness suitable for a reverse-proxy/process check.

### PROJ-010 · Flutter project scaffold & folder structure `[FE]` (M)
**Description:** Create the Flutter app with the feature-first structure from Architecture §Frontend (`core/`, `shared/`, `features/`, `router/`).
**Dependencies:** None
**Acceptance Criteria:**
- App builds and runs on a device/emulator showing a placeholder screen.
- Folder structure matches Architecture recommendation.

### PROJ-011 · Flutter dependencies & codegen `[FE]` (S)
**Description:** Add and configure Riverpod, GoRouter, Dio, Freezed, json_serializable, build_runner, secure storage.
**Dependencies:** PROJ-010
**Acceptance Criteria:**
- `build_runner` generates successfully on a sample Freezed model.
- `ProviderScope` wraps the app; dependencies resolve.

## 1B. Database & Migrations

### DB-001 · PostgreSQL + Flyway wiring `[BE/INFRA]` (S)
**Description:** Connect Spring Data/JPA to PostgreSQL with HikariCP; enable Flyway to run on startup; disable Hibernate auto-DDL.
**Dependencies:** PROJ-002
**Acceptance Criteria:**
- App connects to a local PostgreSQL instance.
- Flyway runs and records migrations; auto-DDL is off.

### DB-002 · Migration V1 — core accounts & settings `[BE]` (M)
**Description:** Implement `V1__core_accounts_settings.sql` per Database.md: `users`, `device_categories`, `business_settings`, `working_hours`, indexes/constraints, and `repair_reference_seq`.
**Dependencies:** DB-001
**Acceptance Criteria:**
- Migration applies cleanly; tables/indexes match Database.md.
- Partial unique indexes on `users` (non-deleted) present.

### DB-003 · Migration V2 — repair_requests `[BE]` (M)
**Description:** Implement `V2__requests.sql` per Database.md (columns, FKs, indexes, reference uniqueness).
**Dependencies:** DB-002
**Acceptance Criteria:**
- Migration applies; FKs to `users`/`device_categories` valid; queue indexes present.

### DB-004 · Migration V3 — jobs `[BE]` (M)
**Description:** Implement `V3__jobs.sql` (1:1 `request_id`, technician FK, status/priority, snapshots, warranty-confirmed flag, indexes).
**Dependencies:** DB-003
**Acceptance Criteria:**
- Migration applies; `UNIQUE(request_id)` and `UNIQUE(reference_number)` enforced.

### DB-005 · Migration V4 — appointments `[BE]` (M)
**Description:** Implement `V4__appointments.sql` (child of job, technician FK, reschedule chain) and add `jobs.current_appointment_id` FK.
**Dependencies:** DB-004
**Acceptance Criteria:**
- Migration applies; calendar/conflict index `(technician_id, scheduled_date)` present.

### DB-006 · Migration V5 — attachments, notifications, audit `[BE]` (M)
**Description:** Implement `V5__attachments_notifications_audit.sql` (polymorphic attachments, notifications, append-only audit_events, indexes).
**Dependencies:** DB-005
**Acceptance Criteria:**
- Migration applies; inbox/unread and audit history indexes present.

### DB-007 · Migration V6 — seed reference data `[BE]` (S)
**Description:** Idempotent seed: default `business_settings` row, weekly `working_hours` (Mon–Sat 09:00–18:00, Sun closed), starter `device_categories` (Mobile Phone, Laptop, Tablet, Camera).
**Dependencies:** DB-002
**Acceptance Criteria:**
- Seed applies once and is safe on restart.
- Settings/working-hours/categories present with expected defaults.

### DB-008 · Migration V7 — seed default admin `[BE]` (S)
**Description:** Idempotent seed of the default `admin` account (BCrypt `Admin@1234`, `must_change_password=true`) only if it does not exist (GAP-03).
**Dependencies:** DB-002, AUTH-002
**Acceptance Criteria:**
- Admin seeded once; no duplicate on restart.
- `must_change_password` is true; password is hashed.

## 1C. Cross-Cutting Backend Infrastructure

### CORE-001 · Standard error model & global exception handler `[BE]` (M)
**Description:** Implement the consistent error response shape (`errorCode`, message, correlationId, fieldErrors) via `@ControllerAdvice` mapping validation/not-found/forbidden/conflict/auth/generic exceptions (Architecture §Error Handling).
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- All error categories return the standard shape and correct HTTP status (per API.md §18).
- No stack traces/SQL/sensitive data leak in responses.

### CORE-002 · Correlation-id filter & logging config `[BE]` (S)
**Description:** Add a servlet filter assigning/propagating a correlation id into MDC; configure Logback structured logging without sensitive data.
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- Every log line includes the correlation id; it is returned in error responses.
- No passwords/tokens/full IMEI in logs.

### CORE-003 · Base entity conventions `[BE]` (S)
**Description:** Establish UUID PK generation (time-ordered), JPA auditing (`created_at`/`updated_at`), and `@Version` optimistic locking as shared base mappings.
**Dependencies:** DB-001
**Acceptance Criteria:**
- Entities inherit consistent id/timestamp/version handling.
- Optimistic-lock conflicts surface as `409 CONFLICT`.

### CORE-004 · Pagination & response conventions `[BE]` (S)
**Description:** Standardize the paged list envelope and pagination/sort params (API.md §4) used by all list endpoints.
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- A sample paged endpoint returns `{content,page,size,totalElements,totalPages,sort}`.
- `size` default/max enforced.

### AUDIT-001 · Audit event entity & AuditService `[BE]` (M)
**Description:** Implement the append-only `audit_events` writer used by all features (actor + username snapshot, before/after JSON, reason, correlation id), invoked within the same transaction as changes.
**Dependencies:** DB-006, CORE-003
**Acceptance Criteria:**
- Services can record audit events; rows are insert-only.
- Actor username snapshot persists attribution after user deletion.

## 1D. Authentication Infrastructure

### AUTH-002 · Password encoder `[BE]` (S)
**Description:** Configure BCrypt password encoding and the 8-char minimum policy (GAP-04) as shared validation.
**Dependencies:** PROJ-001
**Acceptance Criteria:**
- Passwords are hashed with BCrypt; never returned/logged.
- `<8` chars rejected with the standard validation error.

### AUTH-003 · JWT service (mint/validate) `[BE]` (M)
**Description:** Implement signing/verification of non-expiring JWTs carrying user id + single role (ADR-003).
**Dependencies:** PROJ-002
**Acceptance Criteria:**
- Tokens are signed and verifiable; tampered tokens rejected.
- Claims contain id + role only (no sensitive data).

### AUTH-004 · JWT auth filter + account-status check `[BE]` (M)
**Description:** Spring Security filter that validates the bearer token and re-checks current account status, rejecting disabled/deleted accounts (`401`).
**Dependencies:** AUTH-003, CORE-001
**Acceptance Criteria:**
- Valid token + active account authenticates; disabled/deleted rejected even with valid signature.
- Missing/invalid token → `401` standard error.

### AUTH-005 · Security config & current-user accessor `[BE]` (S)
**Description:** Configure the Spring Security filter chain (public vs protected routes, role authorities) and a `CurrentUser` accessor for services.
**Dependencies:** AUTH-004
**Acceptance Criteria:**
- Public auth endpoints reachable; protected endpoints require auth.
- Services can read the authenticated user's id/role.

## 1E. Frontend Infrastructure

### THEME-001 · Theme & responsive foundations `[FE]` (S)
**Description:** Define theme (colors, typography), responsive helpers for mobile/tablet, and ACE Telecom branding tokens.
**Dependencies:** PROJ-010
**Acceptance Criteria:**
- App applies a consistent theme; tablet layout scales.
- Brand name available for the login screen.

### CORE-010 · Dio client & interceptors `[FE]` (M)
**Description:** Configure Dio with base URL, the auth-token interceptor, correlation-id propagation, and timeouts.
**Dependencies:** PROJ-011
**Acceptance Criteria:**
- Requests attach the bearer token when present.
- Base URL configurable per environment.

### CORE-011 · Failure types & error mapping `[FE]` (M)
**Description:** Define the `Failure` union (network/validation/auth/permission/notFound/conflict/server) and a Dio→Failure mapper consuming the standard error body.
**Dependencies:** CORE-010
**Acceptance Criteria:**
- HTTP/transport errors map to typed failures with field errors preserved.
- `401` triggers a session-invalid signal.

### CORE-012 · Secure token storage `[FE]` (S)
**Description:** Wrap platform secure storage for the JWT (Keychain/Keystore); never store in plain prefs/logs.
**Dependencies:** PROJ-011
**Acceptance Criteria:**
- Token persists across restarts; cleared on logout.
- Token never logged.

### CORE-013 · Session/auth state provider `[FE]` (M)
**Description:** Riverpod provider holding `(isAuthenticated, user, role, status)` that the router watches; hydrate from stored token on launch.
**Dependencies:** CORE-012
**Acceptance Criteria:**
- Auth state changes re-route the app.
- Launch reads stored token and resolves session.

### SHARED-001 · Shared UI components `[FE]` (M)
**Description:** Build reusable widgets: loaders, error/retry banners, empty states, status chips, and a customer-friendly status label helper.
**Dependencies:** THEME-001
**Acceptance Criteria:**
- Components render loading/error/empty/data states consistently.
- Status chip maps internal statuses to customer-friendly labels.

### NAV-001 · GoRouter configuration `[FE]` (M)
**Description:** Define the central route table and route names/paths for all features.
**Dependencies:** PROJ-011
**Acceptance Criteria:**
- Named routes resolve; deep-link-friendly paths defined.

### NAV-002 · Route guards (auth + role) `[FE]` (M)
**Description:** Implement the GoRouter redirect guard reading session state: unauthenticated → login; unauthorized-for-role → role landing.
**Dependencies:** NAV-001, CORE-013
**Acceptance Criteria:**
- Protected routes redirect unauthenticated users to login.
- A role accessing a forbidden route is redirected to its landing.

### NAV-003 · Role-based landing resolution `[FE]` (S)
**Description:** Compute landing per role (Customer→Home, Technician→Schedule, Staff/Admin→Requests & Jobs) reflecting current role/status.
**Dependencies:** NAV-002
**Acceptance Criteria:**
- Each role lands on the correct screen after login/auto-login.
- Disabled/deleted session routes to login.

---

# Phase 2 — Core Features

Goal: end-to-end primary workflows — authentication, intake, review, jobs, the customer experience (home/tracking/notifications), and the role landings.

## 2A. Authentication Feature

### AUTH-010 · Register endpoint `[BE]` (M)
**Description:** Implement `POST /auth/register` (creates CUSTOMER, auto-sign-in token) with uniqueness + validation.
**Dependencies:** AUTH-005, AUTH-002, AUDIT-001
**Acceptance Criteria:**
- Valid input creates an active CUSTOMER and returns token + user.
- Duplicate username/mobile → `409`; password `<8` → `422`.

### AUTH-011 · Login endpoint `[BE]` (S)
**Description:** Implement `POST /auth/login` returning a token for active accounts; generic failure messaging.
**Dependencies:** AUTH-005, AUTH-002
**Acceptance Criteria:**
- Valid credentials → token; invalid → generic `401` (no field disclosure).
- Disabled account → `403`.

### AUTH-012 · Logout & reset-password endpoints `[BE]` (S)
**Description:** Implement `POST /auth/logout` (audit/no-op) and `POST /auth/reset-password` (enumeration-safe; blocked for disabled/deleted).
**Dependencies:** AUTH-005
**Acceptance Criteria:**
- Logout returns `204`.
- Reset returns generic success regardless of account existence; password rules enforced.

### AUTH-013 · Self profile & account settings endpoints `[BE]` (M)
**Description:** Implement `GET /me`, `PATCH /me` (update own mobile), `POST /me/change-password` (DEP-02).
**Dependencies:** AUTH-005, AUDIT-001
**Acceptance Criteria:**
- `/me` returns profile (no sensitive fields).
- Mobile update enforces uniqueness; change-password verifies current + strength.

### AUTH-020 · Login screen `[FE]` (M)
**Description:** Build the login UI with ACE Telecom branding, validation, and error states; wire to login + session.
**Dependencies:** AUTH-011, CORE-013, SHARED-001, NAV-003
**Acceptance Criteria:**
- Valid login routes to role landing; invalid shows generic error.
- ACE Telecom name displayed.

### AUTH-021 · Register & reset-password screens `[FE]` (M)
**Description:** Build registration (auto-sign-in) and reset-password screens with validation.
**Dependencies:** AUTH-010, AUTH-012, SHARED-001
**Acceptance Criteria:**
- Successful registration signs in and lands on Customer Home.
- Reset flow shows generic success and returns to login.

### AUTH-022 · Auto-login & logout wiring `[FE]` (S)
**Description:** Implement launch auto-login from stored token and a logout action that clears local state.
**Dependencies:** AUTH-020, CORE-012
**Acceptance Criteria:**
- Returning user bypasses login when token valid/active.
- Logout always returns to login (even offline).

### AUTH-023 · Account settings screen `[FE]` (M)
**Description:** Build the customer Account Settings UI (view username/mobile, change password, update mobile).
**Dependencies:** AUTH-013, SHARED-001
**Acceptance Criteria:**
- Mobile/password changes succeed with validation; username read-only.

## 2B. Shared Read Dependencies (Catalog & Config)

### PROD-001 · Device category entity, repo & active-list endpoint `[BE]` (S)
**Description:** Implement the `DeviceCategory` entity/repository and `GET /device-categories` (active only) used by the request form.
**Dependencies:** DB-002, AUTH-005
**Acceptance Criteria:**
- Authenticated users get the active category list; archived hidden.

### SET-001 · Settings entity, repo & scheduling-config endpoint `[BE]` (S)
**Description:** Implement settings/working-hours read and `GET /settings/scheduling-config` (slot duration, horizon, working hours) for forms/scheduling.
**Dependencies:** DB-007, AUTH-005
**Acceptance Criteria:**
- Endpoint returns seeded config; available to authenticated users.

## 2C. Notifications Core

### NOTIF-001 · Notification entity, repo & service `[BE]` (M)
**Description:** Implement `Notification` persistence and a `NotificationService` used by request/job/scheduling to create customer notifications (single-source for messages).
**Dependencies:** DB-006, AUDIT-001
**Acceptance Criteria:**
- Services can create notifications for a recipient with related entity + reference.
- Messages follow the consolidated notification set.

### NOTIF-002 · Customer inbox endpoints `[BE]` (S)
**Description:** Implement `GET /me/notifications` (last 30), `unread-count`, `mark read`, `read-all`.
**Dependencies:** NOTIF-001
**Acceptance Criteria:**
- Inbox returns last 30 most-recent; unread count accurate; mark-read works.

### NOTIF-003 · Technician alerts & dashboard counters endpoints `[BE]` (S)
**Description:** Implement `GET /me/alerts` (technician transient) and `GET /dashboard/counters` (Pending/Approved/Active Jobs computed).
**Dependencies:** NOTIF-001, AUTH-005
**Acceptance Criteria:**
- Technician sees assignment/appointment alerts; staff/admin get accurate counts.

### NOTIF-020 · Notification inbox UI & bell badge `[FE]` (M)
**Description:** Build the customer inbox screen and the Home bell with unread badge; tapping routes to the related repair.
**Dependencies:** NOTIF-002, SHARED-001
**Acceptance Criteria:**
- Inbox lists last 30; unread badge updates; tap deep-links to detail.

### NOTIF-021 · Polling controller `[FE]` (M)
**Description:** Implement a foreground ~30s polling controller for unread count / alerts / counters; no polling when backgrounded.
**Dependencies:** NOTIF-002, NOTIF-003
**Acceptance Criteria:**
- Counts/alerts refresh ~30s in foreground; polling stops in background.
- Polling failures retry silently without UI spam.

## 2D. Repair Request (Intake)

### FILE-001 · Storage service abstraction `[BE]` (M)
**Description:** Implement the `StorageService` over the server file system (write/read/delete by non-guessable key) per Architecture §File Storage.
**Dependencies:** PROJ-002
**Acceptance Criteria:**
- Files persist to the configured volume; keys are non-guessable.
- Interface allows a future object-storage swap.

### FILE-002 · Request attachment upload & validation `[BE]` (M)
**Description:** Implement `POST /requests/{id}/attachments` (multipart) with server-side type sniffing, size/count/total limits (≤5, 10MB, 25MB), and metadata persistence.
**Dependencies:** FILE-001, REQ-001
**Acceptance Criteria:**
- Valid images stored + linked; invalid type/oversize/over-count → `422` (others unaffected).

### FILE-003 · Authenticated attachment streaming `[BE]` (S)
**Description:** Implement download/stream endpoints with ownership/role checks (request attachments; job attachments internal-only).
**Dependencies:** FILE-002
**Acceptance Criteria:**
- Only authorized roles/owners can stream; no public URLs.

### REQ-001 · RepairRequest entity & repository `[BE]` (M)
**Description:** Implement the `RepairRequest` entity/repository with status/priority and search-supporting queries.
**Dependencies:** DB-003, CORE-003
**Acceptance Criteria:**
- Persistence + queries match Database.md; paginated list supported.

### REQ-002 · Create request + reference generation `[BE]` (L)
**Description:** Implement `POST /requests` with reference-number generation, full validation (required fields, preferred date/time within hours/horizon, active category), duplicate warning (non-blocking), and submission notification.
**Dependencies:** REQ-001, PROD-001, SET-001, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Valid submission creates `PENDING_REVIEW` with a reference and notifies the customer.
- Validation failures return field-level `422`; archived category blocked.

### REQ-003 · Get/list own requests `[BE]` (S)
**Description:** Implement `GET /requests/{id}` (owner/staff/admin) and customer access to own requests.
**Dependencies:** REQ-001, AUTH-005
**Acceptance Criteria:**
- Owner can read own request; cross-customer access denied (`403/404`).

### REQ-004 · Edit/cancel request (editable window) `[BE]` (M)
**Description:** Implement `PUT /requests/{id}` and `POST /requests/{id}/cancel` allowed only while `PENDING_REVIEW`/`INFORMATION_REQUIRED`.
**Dependencies:** REQ-001, AUDIT-001
**Acceptance Criteria:**
- Edit/cancel succeed in-window; blocked otherwise with explanation; audited.

### REQ-020 · Repair request form UI `[FE]` (L)
**Description:** Build the mobile-first intake form (customer/device/issue/appointment sections) with pre-filled contact, category dropdown, and inline validation.
**Dependencies:** REQ-002, PROD-001, SET-001, SHARED-001
**Acceptance Criteria:**
- Form enforces required fields and appointment constraints client-side.
- Categories load from active catalog; contact pre-filled and editable.

### REQ-021 · Attachment picker & upload UI `[FE]` (M)
**Description:** Build image add/remove with count indicator (x/5) and per-file feedback wired to upload.
**Dependencies:** REQ-020, FILE-002
**Acceptance Criteria:**
- Up to 5 images add/upload; invalid/oversize files rejected with reasons.

### REQ-022 · Submit, confirmation & error handling `[FE]` (M)
**Description:** Wire submission with idempotent intent, confirmation (reference + status), and recoverable error handling preserving input.
**Dependencies:** REQ-020, REQ-021, CORE-011
**Acceptance Criteria:**
- Successful submit shows reference and routes to tracking detail.
- Network/server errors preserve data; no false success.

## 2E. Request Review

### RV-001 · Review queue list & filters `[BE]` (M)
**Description:** Implement `GET /requests` for staff/admin with status/priority/category/date filters, search, pagination, and sort.
**Dependencies:** REQ-001, AUTH-005
**Acceptance Criteria:**
- Queue returns paginated results within performance targets; filters/search work.

### RV-002 · Approve → auto-create job `[BE]` (L)
**Description:** Implement `POST /requests/{id}/approve` that atomically sets `APPROVED`, creates the 1:1 job (CREATED, reference reused), copies snapshot data, sets priority, and sends the accepted→preparing notification.
**Dependencies:** RV-001, JOB-001, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Approval creates exactly one job; failure rolls back (no half-approved state).
- Customer receives a single accepted/preparing notification.

### RV-003 · Reject workflow `[BE]` (M)
**Description:** Implement `POST /requests/{id}/reject` with mandatory reason code + customer-facing message; terminal; rejection-guidance notification (WORK-03).
**Dependencies:** RV-001, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Reject requires reason (`422` if missing); customer notified with customer-message only; internal reason never exposed.

### RV-004 · Request-info workflow `[BE]` (S)
**Description:** Implement `POST /requests/{id}/request-info` → `INFORMATION_REQUIRED` with message + notification; resume to `PENDING_REVIEW` on customer update.
**Dependencies:** RV-001, NOTIF-001, REQ-004
**Acceptance Criteria:**
- Status/notification set; customer can update; request returns to queue.

### RV-005 · Priority, internal notes & history `[BE]` (S)
**Description:** Implement `POST /requests/{id}/priority`, `POST /requests/{id}/notes` (append-only internal), and `GET /requests/{id}/history`.
**Dependencies:** RV-001, AUDIT-001
**Acceptance Criteria:**
- Priority updates; notes are internal append-only; history returns audit trail.

### RV-020 · Requests queue UI `[FE]` (M)
**Description:** Build the Requests tab with counters, filters/search, and list items linking to detail.
**Dependencies:** RV-001, DASH-001, SHARED-001
**Acceptance Criteria:**
- Queue displays/filters/searches; counters reflect state.

### RV-021 · Request detail + attachment review UI `[FE]` (M)
**Description:** Build the request detail view (all sections, sensitive-field least-exposure) with attachment viewer.
**Dependencies:** RV-020, FILE-003
**Acceptance Criteria:**
- Full request visible; attachments open/zoom; internal data hidden from non-permitted views.

### RV-022 · Decision actions UI `[FE]` (M)
**Description:** Build approve/reject/request-info/priority/notes actions with confirmation and conflict handling.
**Dependencies:** RV-021, RV-002, RV-003, RV-004, RV-005
**Acceptance Criteria:**
- Approve creates a job and updates counters; reject requires reason; stale state shows conflict, no overwrite.

## 2F. Job Management

### JOB-001 · Job entity & repository `[BE]` (M)
**Description:** Implement the `Job` entity/repository (1:1 request, technician, status/priority, snapshots, warranty flag) with role-scoped queries.
**Dependencies:** DB-004, CORE-003
**Acceptance Criteria:**
- Persistence matches Database.md; technician-scoped and all-jobs queries supported.

### JOB-002 · Job list & detail (role-scoped) `[BE]` (M)
**Description:** Implement `GET /jobs` (staff/admin all; technician assigned-only; filters incl. unscheduled) and `GET /jobs/{id}` + `GET /jobs/{id}/history`.
**Dependencies:** JOB-001, AUTH-005
**Acceptance Criteria:**
- Technicians see only assigned jobs; filters/search/pagination work; history returns audit.

### JOB-003 · Assign / unassign technician `[BE]` (M)
**Description:** Implement `POST /jobs/{id}/assign` and `/unassign` with active-technician validation and notifications.
**Dependencies:** JOB-001, STAFF-006, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Only active technicians assignable; assignment audited + technician notified; unassign returns to `CREATED`.

### JOB-004 · Status transition actions `[BE]` (L)
**Description:** Implement `start`, `awaiting-parts`, `resume`, `complete` (resolution required) with transition validation and customer notifications (in-progress, awaiting-parts, completed); technician subset enforced on assigned jobs.
**Dependencies:** JOB-001, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Only valid transitions allowed (`409` otherwise); complete requires resolution; correct customer notifications fire.

### JOB-005 · Cancel job (atomic appointment cancel) `[BE]` (M)
**Description:** Implement `POST /jobs/{id}/cancel` (reason required, staff/admin) that atomically cancels any active appointment and sends one customer notification (CON-03).
**Dependencies:** JOB-001, SCH-001, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Reason required; active appointment cancelled atomically; exactly one customer notification.

### JOB-006 · Priority, warranty, notes & attachments `[BE]` (M)
**Description:** Implement `priority`, `warranty` (staff confirm), `notes` (technician-on-assigned/staff/admin, append-only), and job attachment upload/stream (≤10, internal-only).
**Dependencies:** JOB-001, FILE-001, AUDIT-001
**Acceptance Criteria:**
- Priority/warranty/notes update with audit; job attachments internal-only and limited to 10.

### JOB-020 · Jobs list UI `[FE]` (M)
**Description:** Build the Jobs tab (cards with reference/customer/device/status/technician/priority), filters (status/technician/priority/date), inline status control, and Active Jobs counter.
**Dependencies:** JOB-002, DASH-001, SHARED-001
**Acceptance Criteria:**
- List filters/sorts; technician filter incl. Unassigned; matches wireframe behavior.

### JOB-021 · Job detail UI `[FE]` (M)
**Description:** Build the job detail view (status/priority, customer/device, issue/attachments, technician, appointment, notes, resolution, history, role-appropriate actions).
**Dependencies:** JOB-020, JOB-002, FILE-003
**Acceptance Criteria:**
- Full job visible per role; sensitive fields least-exposure; actions match role/status.

### JOB-022 · Assign & status action UI `[FE]` (M)
**Description:** Build assign/unassign and status-transition controls with validation messaging and conflict handling.
**Dependencies:** JOB-021, JOB-003, JOB-004, JOB-005
**Acceptance Criteria:**
- Assign from active technicians; transitions reflect lifecycle; completion requires resolution; cancel requires reason.

### JOB-023 · Notes & attachments UI (job) `[FE]` (S)
**Description:** Build internal notes thread and job image upload/view (internal-only).
**Dependencies:** JOB-021, JOB-006
**Acceptance Criteria:**
- Notes append-only; technician limited to assigned jobs; images upload within limits.

## 2G. Repair Status Tracking (Customer Home)

### TRACK-001 · Customer Home list endpoint `[BE]` (M)
**Description:** Implement `GET /me/repairs` (own requests/jobs, most-recently-updated first) returning reference, device category, friendly status, dates.
**Dependencies:** REQ-001, JOB-001, AUTH-005
**Acceptance Criteria:**
- Returns only own records, correctly ordered/paginated; friendly status mapped.

### TRACK-002 · Repair detail endpoint `[BE]` (M)
**Description:** Implement `GET /me/repairs/{referenceNumber}` (friendly status, appointment once scheduled, technician name, issue, status history, per-record notifications, rejection guidance).
**Dependencies:** TRACK-001, SCH-001, NOTIF-001
**Acceptance Criteria:**
- Owner-only; raw internal status never exposed; appointment/technician shown only when scheduled.

### TRACK-020 · Customer Home UI `[FE]` (M)
**Description:** Build the Customer Home list (repairs, bell, "+ New Request", Account access) with empty state.
**Dependencies:** TRACK-001, NOTIF-020, SHARED-001, NAV-003
**Acceptance Criteria:**
- Lists repairs most-recent-first; empty state shown; "+" opens the request form.

### TRACK-021 · Repair detail UI `[FE]` (M)
**Description:** Build the customer detail view (friendly status, appointment/technician, status history, notification history, rejection guidance, pre-review cancel/edit entry).
**Dependencies:** TRACK-020, TRACK-002
**Acceptance Criteria:**
- Detail shows friendly labels and history; no job/appointment controls; cancel/edit only pre-review.

## 2H. Dashboard & Landings

### DASH-001 · Counters endpoint integration `[BE]` (S)
**Description:** Ensure `GET /dashboard/counters` returns Pending/Approved/Active Jobs (reuse NOTIF-003 implementation).
**Dependencies:** NOTIF-003
**Acceptance Criteria:**
- Counts accurate within refresh cadence; staff/admin only.

### DASH-020 · Staff/Admin dashboard hub UI `[FE]` (M)
**Description:** Build the navigation hub with role-based tiles (Requests & Jobs, Customers; +Staff/Products/Settings for Admin) and counters/badges.
**Dependencies:** DASH-001, NAV-003, NOTIF-021, SHARED-001
**Acceptance Criteria:**
- Tiles render per role; counters show; tiles route correctly; admin-only tiles hidden for staff.

### DASH-021 · Technician landing (schedule) UI `[FE]` (M)
**Description:** Build the technician landing showing today's appointments with a path to assigned jobs and transient alerts.
**Dependencies:** SCH-006, JOB-020, NOTIF-021
**Acceptance Criteria:**
- Technician lands on today's schedule; can open assigned jobs; alerts shown.

---

# Phase 3 — Operational Features

Goal: scheduling, customer management, product management, staff management, and settings administration.

## 3A. Scheduling

### SCH-001 · Appointment entity & repository `[BE]` (M)
**Description:** Implement the `Appointment` entity/repository (child of job, reschedule chain) and conflict/calendar queries.
**Dependencies:** DB-005, CORE-003
**Acceptance Criteria:**
- Persistence matches Database.md; overlap query for a technician supported.

### SCH-002 · Create appointment + validation `[BE]` (L)
**Description:** Implement `POST /jobs/{id}/appointments` with hard blocks (outside-hours/past/end-overflow/inactive technician), soft overlap warning, end-time derivation, job→`SCHEDULED`, and customer notification.
**Dependencies:** SCH-001, SET-001, STAFF-006, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Hard rules block (`422`); overlap returns non-blocking warning; job moves to `SCHEDULED`; customer notified.

### SCH-003 · Reschedule appointment `[BE]` (M)
**Description:** Implement reschedule (cancel old, create new, retain history, keep job `SCHEDULED` or return `IN_PROGRESS`→`SCHEDULED`), always notify customer.
**Dependencies:** SCH-002, JOB-004, NOTIF-001
**Acceptance Criteria:**
- New appointment created with history link; customer notified; `IN_PROGRESS → SCHEDULED` supported.

### SCH-004 · Cancel appointment `[BE]` (S)
**Description:** Implement appointment cancel (reason required) that reverts job to `ASSIGNED`/`CREATED` (does not cancel job) and notifies the customer.
**Dependencies:** SCH-002, NOTIF-001, AUDIT-001
**Acceptance Criteria:**
- Reason required; job reverts (not cancelled); customer notified; completed appointments not cancelable.

### SCH-005 · Calendar list & technician schedule `[BE]` (M)
**Description:** Implement `GET /appointments` (filters: technician/status/date range/search) and `GET /me/schedule` (technician own).
**Dependencies:** SCH-001, AUTH-005
**Acceptance Criteria:**
- Calendar filters work within load target; technician sees only own appointments.

### SCH-020 · Calendar view UI (weekly/daily) `[FE]` (L)
**Description:** Build the Schedule tab with weekly default + daily views, technician/status filters, appointment cards, and navigation.
**Dependencies:** SCH-005, SHARED-001
**Acceptance Criteria:**
- Weekly/daily views render; filter by technician/status; cards show key fields.

### SCH-021 · Create/reschedule/cancel UI `[FE]` (M)
**Description:** Build scheduling forms with preference pre-fill, slot picker (from settings), conflict-warning acknowledgement, and reschedule/cancel actions.
**Dependencies:** SCH-020, SCH-002, SCH-003, SCH-004
**Acceptance Criteria:**
- Slots constrained to valid times; soft conflict prompts acknowledgement; reschedule/cancel notify customer.

### SCH-022 · Technician schedule view & start-work `[FE]` (M)
**Description:** Build the technician schedule (read-only) with the ability to start work (→ `IN_PROGRESS`) from an appointment.
**Dependencies:** SCH-005, JOB-004
**Acceptance Criteria:**
- Technician views own schedule; starting work transitions the job; no scheduling controls.

### SCH-023 · Unscheduled-jobs indicator `[FE]` (S)
**Description:** Surface approved jobs without an appointment as a backlog indicator/filter.
**Dependencies:** SCH-020, JOB-002
**Acceptance Criteria:**
- Unscheduled jobs are discoverable from the schedule/jobs view.

## 3B. Customer Management (read-only)

### CUST-001 · Customer search & list `[BE]` (S)
**Description:** Implement `GET /customers` (role=CUSTOMER) with search by name/username/mobile + pagination.
**Dependencies:** AUTH-005, REQ-001
**Acceptance Criteria:**
- Staff/Admin search returns matches within `<2s`; technician/customer denied.

### CUST-002 · Customer profile & history `[BE]` (M)
**Description:** Implement `GET /customers/{id}` (authoritative profile) and `GET /customers/{id}/history` (requests + jobs, all statuses).
**Dependencies:** CUST-001, JOB-001
**Acceptance Criteria:**
- Profile shows account contact; history includes all records; read-only.

### CUST-020 · Customer search & profile UI `[FE]` (M)
**Description:** Build the customer list/search and profile + history screens with links into requests/jobs.
**Dependencies:** CUST-001, CUST-002, SHARED-001
**Acceptance Criteria:**
- Search/select works; profile + history render; no edit controls; links open records.

## 3C. Product Management (Device Categories — Admin)

### PROD-002 · Category management endpoints `[BE]` (M)
**Description:** Implement admin `POST`/`PUT`/`archive`/`unarchive` and `GET /device-categories/all` with active-name uniqueness.
**Dependencies:** PROD-001, AUTH-005, AUDIT-001
**Acceptance Criteria:**
- Create/edit/archive/unarchive work; duplicate active name `409`; archived hidden from active list.

### PROD-020 · Product management UI `[FE]` (M)
**Description:** Build the admin category management screen (list incl. archived, add/edit/archive).
**Dependencies:** PROD-002, SHARED-001
**Acceptance Criteria:**
- Admin manages categories; archive toggles visibility; validation surfaced.

## 3D. Staff Management (Admin)

### STAFF-001 · User list & search `[BE]` (S)
**Description:** Implement `GET /users` (filters: q/role/status, pagination) — admin only.
**Dependencies:** AUTH-005
**Acceptance Criteria:**
- Admin lists/searches users; non-admin denied.

### STAFF-002 · Create user `[BE]` (M)
**Description:** Implement `POST /users` (username/mobile/name/password/role) with uniqueness + strength; active by default.
**Dependencies:** STAFF-001, AUTH-002, AUDIT-001
**Acceptance Criteria:**
- Creates active account with single role; duplicates `409`; password `<8` `422`.

### STAFF-003 · Edit user & assign role `[BE]` (M)
**Description:** Implement `PUT /users/{id}` (name/mobile/role) with last-admin demotion guard.
**Dependencies:** STAFF-001, AUDIT-001
**Acceptance Criteria:**
- Edits/role-change succeed; demoting last active admin blocked (`409`).

### STAFF-004 · Enable/disable & delete `[BE]` (M)
**Description:** Implement enable/disable and logical delete with session termination and last-admin guard.
**Dependencies:** STAFF-001, AUTH-004, AUDIT-001
**Acceptance Criteria:**
- Disable/delete revoke access immediately; last active admin protected; data retained/attributable.

### STAFF-005 · Reset password & technician roster `[BE]` (S)
**Description:** Implement `POST /users/{id}/reset-password` and `GET /technicians` (active technicians).
**Dependencies:** STAFF-002, AUTH-002
**Acceptance Criteria:**
- Admin sets passwords (≥8); roster returns only active technicians (consumed by JOB/SCH).

### STAFF-006 · Technician roster service hook `[BE]` (S)
**Description:** Expose the active-technician lookup as a service used by Jobs/Scheduling assignment validation.
**Dependencies:** STAFF-005
**Acceptance Criteria:**
- Assign/scheduling reject disabled/deleted/non-technician users.

### STAFF-020 · User list UI `[FE]` (M)
**Description:** Build the admin user list with role/status badges, search/filter, and create entry.
**Dependencies:** STAFF-001, SHARED-001
**Acceptance Criteria:**
- List/search/filter work; admin-only access.

### STAFF-021 · Create/edit user UI `[FE]` (M)
**Description:** Build create/edit user forms (role select, password set/reset) with validation.
**Dependencies:** STAFF-020, STAFF-002, STAFF-003, STAFF-005
**Acceptance Criteria:**
- Create/edit/role/reset work with validation; duplicates surfaced.

### STAFF-022 · Enable/disable/delete UI `[FE]` (S)
**Description:** Build status actions with confirmation and last-admin guard messaging.
**Dependencies:** STAFF-021, STAFF-004
**Acceptance Criteria:**
- Enable/disable/delete with confirmation; last-admin block explained.

## 3E. Settings (Admin)

### SET-002 · Settings & working-hours update `[BE]` (M)
**Description:** Implement `GET /settings` (admin), `PUT /settings` (business info + slot duration + horizon), `PUT /settings/working-hours` with validation and existing-appointment flagging note.
**Dependencies:** SET-001, AUDIT-001
**Acceptance Criteria:**
- Updates persist; slot duration ∈ {30,60,90,120}; open<close; hours change does not invalidate existing appointments.

### SET-020 · Settings UI (business + slots) `[FE]` (M)
**Description:** Build the admin settings screen (business info, slot duration, horizon).
**Dependencies:** SET-002, SHARED-001
**Acceptance Criteria:**
- Admin edits settings with validation; changes reflected in scheduling-config.

### SET-021 · Working-hours editor UI `[FE]` (M)
**Description:** Build the per-weekday working-hours editor (open/close/closed).
**Dependencies:** SET-020
**Acceptance Criteria:**
- Per-day hours/closed editable and saved; validation enforced.

---

# Phase 4 — Quality

Goal: automated tests for critical logic and contracts (Constitution: features require tests; critical logic requires unit tests), plus error-handling hardening.

## 4A. Backend Tests

### TEST-001 · Auth & JWT unit tests `[QA/BE]` (M)
**Description:** Unit-test JWT mint/validate, account-status rejection, password rules, login/register/reset logic.
**Dependencies:** AUTH-010, AUTH-011, AUTH-012, AUTH-013
**Acceptance Criteria:**
- Valid/invalid/tampered/disabled scenarios covered and passing.

### TEST-002 · Request lifecycle unit tests `[QA/BE]` (M)
**Description:** Unit-test request transitions, editable window, duplicate warning, approve→job 1:1, reject reason requirement.
**Dependencies:** RV-002, RV-003, RV-004, REQ-004
**Acceptance Criteria:**
- Illegal transitions blocked; approval creates exactly one job; reject requires reason.

### TEST-003 · Job lifecycle unit tests `[QA/BE]` (M)
**Description:** Unit-test job transitions, technician subset, completion-requires-resolution, cancel atomic appointment + single notification.
**Dependencies:** JOB-004, JOB-005
**Acceptance Criteria:**
- Valid/invalid transitions enforced; cancel produces one notification.

### TEST-004 · Scheduling validation unit tests `[QA/BE]` (M)
**Description:** Unit-test hours/slot/past/end-overflow hard blocks, soft overlap warning, single-active-appointment, reschedule history, cancel reverts job.
**Dependencies:** SCH-002, SCH-003, SCH-004
**Acceptance Criteria:**
- Hard/soft rules behave per spec; history retained.

### TEST-005 · Staff management unit tests `[QA/BE]` (S)
**Description:** Unit-test uniqueness, role assignment, enable/disable/delete, and the last-active-admin guard.
**Dependencies:** STAFF-002, STAFF-003, STAFF-004
**Acceptance Criteria:**
- Last-admin guard blocks disable/delete/demote; duplicates rejected.

### TEST-006 · Notification rules unit tests `[QA/BE]` (S)
**Description:** Unit-test notification creation for key events (submitted/accepted/rejected/info/scheduled/awaiting-parts/completed/cancelled) and inbox last-30/unread logic.
**Dependencies:** NOTIF-001, NOTIF-002
**Acceptance Criteria:**
- Correct messages/recipients; inbox cap and unread count correct.

### TEST-007 · Security integration tests `[QA/BE]` (M)
**Description:** Integration-test the security filter (valid/invalid/disabled token) and RBAC/ownership on representative endpoints using Testcontainers PostgreSQL.
**Dependencies:** AUTH-004, JOB-002, REQ-003
**Acceptance Criteria:**
- Unauthorized/forbidden return correct codes; ownership enforced.

### TEST-008 · Endpoint integration tests `[QA/BE]` (L)
**Description:** Integration-test the core flows end-to-end (register→submit→approve→assign→schedule→complete) against PostgreSQL.
**Dependencies:** RV-002, JOB-004, SCH-002
**Acceptance Criteria:**
- Happy-path flow passes; error contract matches API.md.

### TEST-009 · Migration & repository tests `[QA/BE]` (M)
**Description:** Verify Flyway migrations apply cleanly on a fresh DB; test repository pagination/search/soft-delete filtering.
**Dependencies:** DB-008
**Acceptance Criteria:**
- Migrations apply from scratch; soft-deleted/archived excluded by default; pagination correct.

## 4B. Frontend Tests

### TEST-020 · Provider/repository/mapper unit tests `[QA/FE]` (M)
**Description:** Unit-test notifiers/repositories (mocked Dio), DTO (de)serialization, and the customer-friendly status mapping.
**Dependencies:** CORE-011, TRACK-020
**Acceptance Criteria:**
- State transitions and mapping covered; serialization round-trips.

### TEST-021 · Error-mapping & validation unit tests `[QA/FE]` (S)
**Description:** Unit-test Dio→Failure mapping and form validators (password, mobile, required fields).
**Dependencies:** CORE-011, REQ-020
**Acceptance Criteria:**
- Each failure type maps correctly; validators enforce rules.

### TEST-022 · Auth & routing widget tests `[QA/FE]` (M)
**Description:** Widget-test login/register/reset and the role-based redirect guard (each role lands correctly; forbidden routes redirect).
**Dependencies:** AUTH-020, NAV-002, NAV-003
**Acceptance Criteria:**
- Login flows and route protection behave per spec.

### TEST-023 · Customer experience widget tests `[QA/FE]` (M)
**Description:** Widget-test Customer Home (list/empty), detail (friendly labels), and notification badge/inbox.
**Dependencies:** TRACK-020, TRACK-021, NOTIF-020
**Acceptance Criteria:**
- Empty/list/detail states render; labels are customer-friendly; badge updates.

### TEST-024 · Operational widget tests `[QA/FE]` (M)
**Description:** Widget-test request form validation, requests/jobs lists, and key staff/admin actions.
**Dependencies:** REQ-020, RV-020, JOB-020
**Acceptance Criteria:**
- Form blocks invalid input; lists filter; actions reflect role/status.

## 4C. Error Handling Hardening

### ERR-001 · Frontend resilience polish `[FE]` (M)
**Description:** Audit connectivity/timeout/conflict handling across screens: preserve input, retry, no false success, optimistic-conflict reload.
**Dependencies:** CORE-011, REQ-022, JOB-022
**Acceptance Criteria:**
- Offline/timeout/conflict handled consistently per spec error tables.

### ERR-002 · Backend error-contract coverage `[BE]` (S)
**Description:** Verify every error category returns the standard model with correct codes; ensure idempotent retries don't double-apply.
**Dependencies:** CORE-001, TEST-008
**Acceptance Criteria:**
- Error responses consistent; safe retries verified for submit/approve/status.

---

# Phase 5 — Release Preparation

Goal: security/performance review, documentation, deployment, and a release checklist.

### SEC-001 · RBAC & ownership review `[QA/BE]` (M)
**Description:** Review every endpoint against the permission matrix; confirm server-side role + ownership enforcement and last-admin guard.
**Dependencies:** Phase 2 & 3 complete
**Acceptance Criteria:**
- No endpoint relies on UI-only checks; matrix fully enforced; documented sign-off.

### SEC-002 · File upload security review `[BE]` (S)
**Description:** Verify type sniffing, size/count/total limits, non-guessable keys, authenticated streaming, and internal-only job attachments.
**Dependencies:** FILE-002, FILE-003, JOB-006
**Acceptance Criteria:**
- Malicious/oversize/wrong-type uploads rejected; no public access; review documented.

### SEC-003 · Sensitive-data & logging review `[BE]` (S)
**Description:** Confirm passwords/tokens/IMEI never appear in responses or logs; sensitive fields least-exposure by role.
**Dependencies:** CORE-002, SEC-001
**Acceptance Criteria:**
- No sensitive data in logs/responses; least-exposure verified.

### SEC-004 · Default-admin & credential handover `[INFRA]` (S)
**Description:** Verify idempotent admin seed, forced password change on first login, and prepare the out-of-band handover note.
**Dependencies:** DB-008, AUTH-013
**Acceptance Criteria:**
- Admin must change password on first login; credentials documented for handover, not in UI.

### PERF-001 · Performance review (targets) `[QA]` (M)
**Description:** Validate `<3s` load, `<2s` search, and 10k-record behavior; confirm indexes and pagination on all lists.
**Dependencies:** Phase 2 & 3 complete
**Acceptance Criteria:**
- Targets met with seeded ~10k records; slow queries indexed; documented results.

### PERF-002 · Polling efficiency review `[QA]` (S)
**Description:** Confirm polling endpoints are lightweight, foreground-only, and don't degrade under repeated calls.
**Dependencies:** NOTIF-021
**Acceptance Criteria:**
- Polling cadence correct; endpoints efficient; no background polling.

### DOC-001 · Documentation consistency pass `[INFRA]` (S)
**Description:** Reconcile PRD/Architecture/API/Database/feature specs with the final implementation; update where drift occurred (Constitution doc rule).
**Dependencies:** Phase 2 & 3 complete
**Acceptance Criteria:**
- Docs match shipped behavior; discrepancies resolved or noted.

### DOC-002 · API documentation (OpenAPI) `[BE]` (M)
**Description:** Generate/curate OpenAPI/Swagger from the API surface matching API.md.
**Dependencies:** Phase 2 & 3 complete
**Acceptance Criteria:**
- Browsable API docs reflect endpoints, auth, and error model.

### DOC-003 · Deployment & operations runbook `[INFRA]` (M)
**Description:** Document deployment (single env, reverse proxy/TLS), configuration, backups/restore, and recovery procedures.
**Dependencies:** REL-001, REL-002
**Acceptance Criteria:**
- A new operator can deploy, configure, back up, and restore from the runbook.

### REL-001 · Deployment setup (single environment) `[INFRA]` (L)
**Description:** Stand up the single Spring Boot instance + PostgreSQL + reverse proxy (TLS) with env-based config; Flyway on startup; Docker recommended.
**Dependencies:** PROJ-002, DB-001
**Acceptance Criteria:**
- App reachable over HTTPS; migrations apply on deploy; config via env.

### REL-002 · Backup & restore verification `[INFRA]` (M)
**Description:** Configure daily DB (`pg_dump`) + attachment-volume backups and verify a restore.
**Dependencies:** REL-001
**Acceptance Criteria:**
- Backups run daily; a test restore reproduces DB + files consistently.

### REL-003 · Build artifacts (clients + server) `[INFRA]` (M)
**Description:** Produce release builds: Android/iOS Flutter artifacts and the backend JAR/image with environment config.
**Dependencies:** Phase 4 complete
**Acceptance Criteria:**
- Signed/installable client builds and a deployable server artifact exist.

### REL-004 · Release checklist & smoke tests `[QA]` (M)
**Description:** Execute an end-to-end smoke test across roles (register→submit→approve→assign→schedule→complete→notify) and a go/no-go checklist.
**Dependencies:** REL-001, REL-003, SEC-001, PERF-001
**Acceptance Criteria:**
- Full happy-path passes in the deployed environment; checklist signed off.

---

# Dependency Map (high level)

```
PROJ/CONFIG → DB migrations → CORE/AUDIT/AUTH infra → (FE) THEME/CORE/NAV
        ↓
AUTH feature ── PROD-001/SET-001 ── NOTIF core
        ↓
REQUEST (REQ) → REVIEW (RV) → JOBS (JOB) → TRACKING (TRACK)/DASH
        ↓
SCHEDULING (SCH) ── CUSTOMER (CUST) ── PRODUCT (PROD) ── STAFF ── SETTINGS (SET)
        ↓
TESTS (Phase 4) → SECURITY/PERF/DOC/RELEASE (Phase 5)
```

**Critical path:** DB migrations → Auth infra → Notification core → Request → Review (approve) → Jobs → Scheduling. Customer Home/Tracking and Dashboard depend on Request/Job data. Staff Management's technician roster is a dependency of Job assignment and Scheduling (build STAFF-005/006 before, or stub the roster from seeded users and revisit).

---

# Resolved Decisions (formerly Open Questions)

The previously open questions are **resolved below with simple V1 decisions** (kept intentionally minimal — each can be enhanced later). These are now authoritative for the affected tasks; none block Phase 1.

1. **Reference-number format → `RD-YYYYNNNNNN`.** Year prefix + 6-digit zero-padded sequence (e.g., `RD-2026000123`). Generated at request creation, reused as the Job Number. *(REQ-002, DB-006/Database.md.)*
2. **Default admin seed mobile → fixed placeholder.** Seed `mobile = '0000000000'` for the `admin` account; the admin updates it after first login (alongside the forced password change). Mobile stays required/unique. *(DB-008.)*
3. **Customer "name" capture → request-local in V1.** Registration stays username/mobile/password only. The contact name is entered on the request form (pre-filled from the optional account name if present). No name field added to registration in V1. *(AUTH-010, REQ-020.)*
4. **Mobile-number format → permissive single-country rule.** Accept 8–15 digits, optional leading `+`/country code, spaces/dashes stripped before validation. No strict per-country rules in V1. *(AUTH/STAFF/REQ validation.)*
5. **Search implementation → simple `ILIKE` prefix/contains.** Use case-insensitive `ILIKE '%term%'` with the indexes already defined; **no `pg_trgm`** in V1. Revisit only if the `<2s` target is missed at ~10k records. *(CUST-001, RV-001, JOB-002, PERF-001.)*
6. **Notification retention → retain all; serve last 30.** No pruning job in V1; the inbox query simply returns the most-recent 30. *(NOTIF-002.)*
7. **Technician alerts → reuse `notifications` rows.** Persist technician alert events as `notifications` rows (recipient = technician) and display them transiently (no separate table, no badge inbox). *(NOTIF-001/003.)*
8. **Polling cadence → single 30s interval.** Staff counters, technician alerts, and the customer unread count all poll at the same **30s** foreground interval (one shared polling controller). *(NOTIF-021.)*
9. **HEIC → accept on upload; display via standard image widget.** Keep HEIC in the allowed upload set (server-validated). On screens, render with the default Flutter image widget; **no server-side conversion/thumbnailing in V1**. If a device cannot display HEIC, show a graceful "preview unavailable — download to view" fallback. *(FILE-002, RV-021, JOB-021.)*
10. **Time zone → single fixed business time zone.** All working hours and appointment times use one configured business time zone (default `Asia/Kolkata`; settable via backend config, not in the UI). Store timestamps as `timestamptz`; render in the business TZ. *(SCH, SET, PROJ-002.)*
11. **Rejection reason codes → fixed V1 list.** `DUPLICATE`, `UNSUPPORTED_DEVICE`, `INSUFFICIENT_INFORMATION`, `SPAM_INVALID`, `NON_REPAIRABLE`, plus optional free-text detail. *(RV-003.)*
12. **Hosting/TLS → single VM + Nginx + Let's Encrypt.** V1 deploys to one Linux VM (or a single container host) behind Nginx terminating TLS via a free Let's Encrypt certificate; Docker recommended for reproducibility. Final provider/domain chosen at deploy time. *(REL-001, DOC-003.)*
13. **Localization → English only.** V1 ships English-only; no localization framework. *(All UI tasks.)*
14. **Staff/Admin personal requests in Customer Management → not listed.** Customer Management lists only Customer-role accounts. A staff/admin who submitted a personal request will not appear in customer search; their request still exists and is reviewable in the normal queue. *(CUST-001.)*

> **Action:** Database.md and the relevant feature notes should be updated to reflect items 1, 2, 5, 7, 10, and 11 during `DOC-001` (documentation consistency pass). The decisions above are authoritative in the meantime.

---

*This plan is complete enough to begin execution immediately. Tasks are sized for 1–3 hours, ordered by dependency, and traceable to the Constitution, PRD, Architecture, Database, API, and feature specifications.*
