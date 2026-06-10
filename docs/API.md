# RepairDesk — REST API Specification (Version 1)

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** API Contract Specification
**Version:** 1.0
**Status:** Final for Implementation
**Derived from:** [Architecture.md](Architecture.md), [Database.md](Database.md), the nine feature specifications, and the approved V1 decisions in [gap-analysis.md](gap-analysis.md).

> This document defines the **REST/JSON API** for V1: conventions, the auth model, the standard error/pagination shapes, and the endpoint contracts per feature (method, path, roles, request/response, status codes, rules). It is implementation-independent (no controller code). Field types reference [Database.md](Database.md) and the [Architecture error model](Architecture.md#error-handling-architecture).

---

# 1. Conventions

| Concern | Convention |
| --- | --- |
| **Base path** | `/api/v1` (Constitution). All paths below are relative to it. |
| **Format** | JSON request/response (`Content-Type: application/json`), except attachment upload (`multipart/form-data`) and download (binary stream). |
| **Auth** | `Authorization: Bearer <JWT>` on all endpoints except those marked **Public**. |
| **IDs** | UUID strings. Customer-facing lookups may use `reference_number`. |
| **Dates/times** | ISO-8601: dates `YYYY-MM-DD`, times `HH:mm`, timestamps `YYYY-MM-DDTHH:mm:ssZ`. |
| **Enums** | Exact values from [Database.md §1](Database.md) (e.g., `PENDING_REVIEW`). |
| **Pagination** | Query params `page` (0-based), `size` (default 20, max 100), `sort` (`field,asc|desc`). List responses use the paged envelope (§4). |
| **Filtering/search** | Documented per endpoint via query params; `q` is free-text search. |
| **Idempotency** | Unsafe retried operations (submit/approve/status change) are idempotent in intent; clients should avoid duplicate side effects. Optimistic concurrency via `version` (409 on conflict). |
| **Concurrency** | Mutations include the entity `version`; stale writes return `409 CONFLICT` (ADR-008). |
| **Security** | All authorization enforced **server-side** by role + ownership/assignment, regardless of client. Sensitive fields (`password_hash`, full `device_serial`) never returned. |
| **CORS** | Configured for the future Flutter Web origin. |

---

# 2. Authentication & Authorization Model

- **Token:** non-expiring JWT carrying user id + single role; validated per request against current account status (ADR-003). A disabled/deleted account is rejected (`401`).
- **Roles:** `CUSTOMER`, `TECHNICIAN`, `STAFF`, `ADMIN` (single role per user).
- **Ownership rules:** customers act only on their own requests; technicians act only on assigned jobs; staff/admin per the permission matrix.
- **Failure codes:** `401 UNAUTHENTICATED` (missing/invalid/revoked token), `403 FORBIDDEN` (authenticated but not permitted).

---

# 3. Standard Error Model

All errors use the shape from [Architecture.md](Architecture.md#error-handling-architecture):

```json
{
  "timestamp": "2026-06-02T17:00:00Z",
  "status": 422,
  "errorCode": "VALIDATION_ERROR",
  "message": "Human-readable, non-sensitive summary",
  "path": "/api/v1/requests",
  "correlationId": "uuid",
  "fieldErrors": [ { "field": "password", "message": "Password must be at least 8 characters" } ]
}
```

**Common `errorCode` values:** `VALIDATION_ERROR` (422), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409, includes optimistic-lock + illegal state transition + duplicate), `RATE_LIMITED` (429, if enabled), `INTERNAL_ERROR` (500). Error messages never leak stack traces, SQL, or sensitive data.

---

# 4. Standard Response Shapes

**Single resource:** the resource object directly (no wrapper).

**Paged list:**

```json
{
  "content": [ { "...": "resource" } ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "sort": "submittedAt,desc"
}
```

**No-content actions:** `204 No Content`.

---

# 5. Endpoint Index

| Group | Base | Roles (primary) |
| --- | --- | --- |
| Authentication | `/auth` | Public + self |
| Account (self) | `/me` | Authenticated (Customer-centric) |
| Users / Staff Management | `/users`, `/technicians` | Admin (technicians: Staff/Admin read) |
| Customer Management | `/customers` | Staff/Admin (read-only) |
| Device Categories | `/device-categories` | Read: authenticated · Write: Admin |
| Settings | `/settings` | Read config: authenticated · Write: Admin |
| Repair Requests | `/requests` | Customer (own) + Staff/Admin |
| Request Review | `/requests/{id}/...` | Staff/Admin |
| Jobs | `/jobs` | Staff/Admin (all) · Technician (assigned) |
| Appointments / Scheduling | `/appointments`, `/jobs/{id}/appointments` | Staff/Admin · Technician (own read) |
| Attachments | `/requests|jobs/{id}/attachments` | Per owner rules |
| Notifications | `/me/notifications`, `/me/alerts` | Self |
| Dashboard counters | `/dashboard/counters` | Staff/Admin |

---

# 6. Authentication API (`/auth`)

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| POST | `/auth/register` | **Public** | Self-register; creates `CUSTOMER`; auto-signs-in. |
| POST | `/auth/login` | **Public** | Authenticate; returns token. |
| POST | `/auth/logout` | Authenticated | Client clears token; server audits (no-op token invalidation). |
| POST | `/auth/reset-password` | **Public** | Set a new password by username/mobile (enumeration-safe). |

**POST `/auth/register`** — body: `{ username, mobile, password }`. → `201` `{ token, user }` where `user = { id, username, mobile, name?, role, status }`. Errors: `422` (missing/short password <8/invalid mobile), `409` (username/mobile in use).

**POST `/auth/login`** — body: `{ identifier (username|mobile), password }`. → `200` `{ token, user }`. Errors: `401` generic invalid credentials (no field disclosure); `403` account disabled.

**POST `/auth/logout`** — → `204`. Always succeeds client-side; server-side is best-effort.

**POST `/auth/reset-password`** — body: `{ identifier, newPassword, confirmPassword }`. → `200` generic success regardless of account existence (prevents enumeration); does not work for disabled/deleted accounts. Errors: `422` (password <8 / mismatch).

---

# 7. Account (Self) API (`/me`)

Covers the customer Account Settings (gap-analysis DEP-02) and self profile.

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/me` | Authenticated | Current account profile `{ id, username, mobile, name?, role, status, mustChangePassword }`. |
| PATCH | `/me` | Authenticated | Update **own mobile** (unique, validated). Username not changeable. |
| POST | `/me/change-password` | Authenticated | `{ currentPassword, newPassword, confirmPassword }`. → `204`. Errors: `422` (short/mismatch), `401` (current wrong). |

Customer Home / repairs and notifications also live under `/me` (§14, §15).

---

# 8. Users / Staff Management API (`/users`) — Admin

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/users` | ADMIN | List/search users. Params: `q`, `role`, `status`, pagination. |
| POST | `/users` | ADMIN | Create user. |
| GET | `/users/{id}` | ADMIN | User detail. |
| PUT | `/users/{id}` | ADMIN | Edit `name`, `mobile`, `role` (+ `version`). |
| POST | `/users/{id}/enable` | ADMIN | Set status `ACTIVE`. |
| POST | `/users/{id}/disable` | ADMIN | Set status `DISABLED`; ends sessions. |
| DELETE | `/users/{id}` | ADMIN | Logical delete (`DELETED`); ends sessions. |
| POST | `/users/{id}/reset-password` | ADMIN | `{ newPassword }` (≥8). |
| GET | `/technicians` | STAFF, ADMIN | Active technician roster (for assignment dropdowns). |

**POST `/users`** — body: `{ username, mobile, name?, password, role }`. → `201` user. Errors: `422` (password <8, invalid mobile, invalid role), `409` (duplicate username/mobile).

**Business rules (return `409`/`422` as appropriate):**
- **Last-admin guard:** disabling, deleting, or demoting the last `ACTIVE ADMIN` is blocked (`409 CONFLICT`, `errorCode: LAST_ADMIN`).
- Created accounts are `ACTIVE` and **not** auto-signed-in (only self-registration auto-signs-in).
- `GET /technicians` returns `[{ id, name, username }]` for `role=TECHNICIAN, status=ACTIVE` only.

---

# 9. Customer Management API (`/customers`) — Staff/Admin, Read-only

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/customers` | STAFF, ADMIN | Search customers (role=CUSTOMER). Params: `q` (name/username/mobile), pagination. |
| GET | `/customers/{id}` | STAFF, ADMIN | Profile: `{ id, username, mobile, status, registeredAt, repairCount }`. |
| GET | `/customers/{id}/history` | STAFF, ADMIN | Combined requests + jobs (most-recently-updated first), each with reference, device category, status, date, and (jobs) technician/appointment. |

**Rules:** strictly read-only (no create/edit/delete/merge here — those are Staff Management / self). Profile shows the **authoritative** account contact (not request-local overrides). Full history regardless of status. `403` for Technician/Customer.

---

# 10. Device Categories API (`/device-categories`)

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/device-categories` | Authenticated | **Active** categories (for the request form). |
| GET | `/device-categories/all` | ADMIN | All incl. archived. |
| POST | `/device-categories` | ADMIN | Create `{ name, description? }`. |
| PUT | `/device-categories/{id}` | ADMIN | Edit `{ name?, description?, version }`. |
| POST | `/device-categories/{id}/archive` | ADMIN | Soft delete. |
| POST | `/device-categories/{id}/unarchive` | ADMIN | Restore. |

**Rules:** active name unique (`409` on duplicate). Archived categories hidden from `GET /device-categories` but retained on existing requests/jobs. (Driven by gap-analysis GAP-01 Product Management; no dedicated spec.)

---

# 11. Settings API (`/settings`) — Admin write, config read

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/settings/scheduling-config` | Authenticated | `{ slotDurationMinutes, bookingHorizonDays, workingHours: [{ dayOfWeek, isClosed, openTime, closeTime }] }` — used by request form & scheduling. |
| GET | `/settings` | ADMIN | Full business settings + working hours. |
| PUT | `/settings` | ADMIN | Update business info, `slotDurationMinutes` (30/60/90/120), `bookingHorizonDays` (+ `version`). |
| PUT | `/settings/working-hours` | ADMIN | Replace weekly hours `[{ dayOfWeek, isClosed, openTime?, closeTime? }]`. |

**Rules:** `slotDurationMinutes` ∈ {30,60,90,120}; `openTime < closeTime` when not closed; changing hours does **not** invalidate existing appointments (those are flagged for staff review). (Gap-analysis GAP-01 Settings.)

---

# 12. Repair Requests API (`/requests`)

## 12.1 Create & customer self-service

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| POST | `/requests` | CUSTOMER, STAFF, ADMIN (as self) | Submit a repair request. |
| GET | `/requests/{id}` | Owner, STAFF, ADMIN | Request detail. |
| PUT | `/requests/{id}` | Owner (editable window) | Edit while `PENDING_REVIEW`/`INFORMATION_REQUIRED`. |
| POST | `/requests/{id}/cancel` | Owner (editable window) | Cancel → `CANCELLED`. |

**POST `/requests`** — body:
```json
{
  "deviceCategoryId": "uuid",
  "contactName": "string", "contactMobile": "string", "contactEmail": "string?",
  "deviceBrand": "string?", "deviceModel": "string?", "deviceSerial": "string?",
  "purchaseDate": "YYYY-MM-DD?", "warrantyStatus": "IN_WARRANTY|OUT_OF_WARRANTY|UNKNOWN?",
  "issueDescription": "string",
  "preferredDate": "YYYY-MM-DD", "preferredTime": "HH:mm"
}
```
→ `201` request `{ id, referenceNumber, status: "PENDING_REVIEW", ... }`. Triggers customer notification + appears in staff queue. Errors: `422` (missing required, past/out-of-hours/out-of-horizon preferred date/time, invalid category), `409` (archived category / duplicate warning is non-blocking — surfaced as a warning field, not a hard error).

**Notes:** attachments optional, added via §16. Contact name/mobile pre-filled from account but request-local. Issue Category/Symptom and Service Mode are **not** in V1.

## 12.2 Staff/Admin queue & review

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/requests` | STAFF, ADMIN | Review queue. Params: `status`, `priority`, `deviceCategoryId`, `q`, `dateFrom`, `dateTo`, pagination, `sort`. |
| GET | `/requests/{id}/history` | STAFF, ADMIN | Audit/decision history (read). |
| POST | `/requests/{id}/approve` | STAFF, ADMIN | Approve → `APPROVED`; **auto-creates Job** (1:1). Body: `{ priority?, note?, version }`. |
| POST | `/requests/{id}/reject` | STAFF, ADMIN | Reject → `REJECTED`. Body: `{ reasonCode, internalReason?, customerMessage, version }` (reason mandatory). |
| POST | `/requests/{id}/request-info` | STAFF, ADMIN | → `INFORMATION_REQUIRED`. Body: `{ message, version }`. |
| POST | `/requests/{id}/priority` | STAFF, ADMIN | Set `{ priority, version }`. |
| POST | `/requests/{id}/notes` | STAFF, ADMIN | Add internal note `{ text }` (append-only, internal). |

**Rules:**
- **Approve** returns the created job reference: `200 { request, jobId, referenceNumber }`. Approval + job creation are atomic (no half-approved state → `409`/`500` rolls back).
- **Reject** is terminal; customer notified with `customerMessage` only (internal reason never exposed).
- Illegal transitions (e.g., approving a `REJECTED` request) → `409 CONFLICT`.
- Stale `version` → `409`.

---

# 13. Jobs API (`/jobs`)

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/jobs` | STAFF/ADMIN (all), TECHNICIAN (assigned only) | List. Params: `status`, `technicianId`, `priority`, `q`, `scheduled` (true/false → unscheduled backlog), `dateFrom`, `dateTo`, pagination, `sort`. |
| GET | `/jobs/{id}` | STAFF/ADMIN, assigned TECHNICIAN | Job detail (full context). |
| GET | `/jobs/{id}/history` | STAFF/ADMIN, assigned TECHNICIAN | Audit/status history. |
| POST | `/jobs/{id}/assign` | STAFF, ADMIN | `{ technicianId, version }` → `ASSIGNED`. |
| POST | `/jobs/{id}/unassign` | STAFF, ADMIN | → back to `CREATED`. |
| POST | `/jobs/{id}/priority` | STAFF, ADMIN | `{ priority, version }`. |
| POST | `/jobs/{id}/warranty` | STAFF, ADMIN | `{ warrantyConfirmedByStaff }` (YES/NO/UNKNOWN). |
| POST | `/jobs/{id}/notes` | assigned TECHNICIAN, STAFF, ADMIN | Internal repair note `{ text }`. |
| GET | `/jobs/{id}/notes` | assigned TECHNICIAN, STAFF, ADMIN | Internal notes (read). |
| POST | `/jobs/{id}/start` | assigned TECHNICIAN, STAFF, ADMIN | → `IN_PROGRESS`. |
| POST | `/jobs/{id}/awaiting-parts` | assigned TECHNICIAN, STAFF, ADMIN | → `AWAITING_PARTS`. Body `{ partsNeededNote? }`. |
| POST | `/jobs/{id}/resume` | assigned TECHNICIAN, STAFF, ADMIN | `AWAITING_PARTS` → `IN_PROGRESS`. |
| POST | `/jobs/{id}/complete` | assigned TECHNICIAN, STAFF, ADMIN | → `COMPLETED`. Body `{ resolutionSummary }` (**required**). |
| POST | `/jobs/{id}/cancel` | STAFF, ADMIN | → `CANCELLED`. Body `{ reason }` (**required**); auto-cancels active appointment atomically. |

**Rules (enforced server-side):**
- Transitions follow the job lifecycle; illegal transitions → `409`. Technicians limited to their subset (`start`/`awaiting-parts`/`resume`/`complete`) on **assigned** jobs only; assign/unassign/priority/cancel are Staff/Admin.
- `complete` requires `resolutionSummary` (`422` if missing).
- `cancel` requires `reason`; produces **one** customer notification (no separate appointment-cancellation notification — CON-03).
- Job manual creation is **not** offered (jobs originate only from approval). `assigned_technician_id` must be an active technician (`409` if disabled/invalid).
- `IN_PROGRESS → SCHEDULED` (reschedule mid-job) happens via the scheduling reschedule endpoint (§14), not here.

---

# 14. Appointments / Scheduling API

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| POST | `/jobs/{id}/appointments` | STAFF, ADMIN | Create appointment for a `CREATED`/`ASSIGNED` job → job `SCHEDULED`. |
| GET | `/appointments` | STAFF, ADMIN | Calendar list. Params: `technicianId`, `status`, `dateFrom`, `dateTo`, `q`, pagination. |
| GET | `/appointments/{id}` | STAFF, ADMIN | Appointment detail. |
| POST | `/appointments/{id}/reschedule` | STAFF, ADMIN | `{ scheduledDate, startTime, technicianId?, version }` → cancels old, creates new `SCHEDULED`; customer notified. |
| POST | `/appointments/{id}/cancel` | STAFF, ADMIN | `{ reason }` → `CANCELLED`; job reverts to `ASSIGNED`/`CREATED` (does not cancel job). |
| GET | `/me/schedule` | TECHNICIAN | Own appointments. Params: `dateFrom`, `dateTo` (default today/this week). |

**POST `/jobs/{id}/appointments`** — body: `{ technicianId, scheduledDate, startTime, appointmentNotes? }`. `endTime` derived from slot duration. → `201` appointment; job → `SCHEDULED`; customer notified.

**Rules:**
- **Hard block (`422`):** outside working hours, past date/time, end exceeds close-of-business, inactive/disabled technician (not selectable).
- **Soft warning (`200` with warning payload):** technician overlap conflict — staff may proceed; acknowledgement audited. Represented as a non-blocking `warnings: [...]` field on the success response (or a `409` only if the client opts into strict mode — V1 uses soft warning, not block).
- One active appointment per job; creating a second requires reschedule/cancel of the first.
- Reschedule retains history (`supersedes_appointment_id`); always notifies the customer.
- Technician `GET /me/schedule` is read-only; technicians cannot create/reschedule/cancel.

---

# 15. Repair Status Tracking / Customer Home API (`/me`)

Customer-facing, own-records-only, customer-friendly labels (mapping in [Repair Status Tracking spec](features/repair-status-tracking.md)).

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/me/repairs` | CUSTOMER | Customer Home list: own requests/jobs, most-recently-updated first. Items: `{ referenceNumber, deviceCategory, friendlyStatus, submittedAt, updatedAt }`. Paginated. |
| GET | `/me/repairs/{referenceNumber}` | CUSTOMER (owner) | Detail: `{ referenceNumber, device, issueDescription, friendlyStatus, appointment?: { date, time, technicianName }, statusHistory: [...], notifications: [...] , rejection?: { message } }`. |

**Rules:**
- Server scopes to the authenticated customer (`403`/`404` for non-owned).
- `friendlyStatus` is the mapped customer label (never raw internal status).
- Appointment block present only once `SCHEDULED`; technician **name only** (no contact).
- No job/appointment mutation here. Request cancel/edit is via `/requests/{id}` (only while pre-review).

## Notifications (`/me/notifications`) — Customer inbox

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/me/notifications` | CUSTOMER | Last 30 notifications (most recent first). |
| GET | `/me/notifications/unread-count` | CUSTOMER | `{ count }` (polled ~30s). |
| POST | `/me/notifications/{id}/read` | CUSTOMER | Mark one read → `204`. |
| POST | `/me/notifications/read-all` | CUSTOMER | Mark all read → `204`. |

## Technician alerts (`/me/alerts`)

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/me/alerts` | TECHNICIAN | Transient alerts (job assigned/reassigned, appointment today). Polled; not a persisted inbox. |

**Polling note:** notification/alert/counter endpoints are lightweight reads polled ~30s while foregrounded; no push/WebSocket in V1 (ADR-005).

---

# 16. Attachments API

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| POST | `/requests/{id}/attachments` | Owner, STAFF, ADMIN | Upload request image (`multipart/form-data`). |
| DELETE | `/requests/{id}/attachments/{attachmentId}` | Owner (pre-review), STAFF, ADMIN | Remove a request attachment. |
| GET | `/requests/{id}/attachments/{attachmentId}` | Owner, STAFF, ADMIN, assigned TECHNICIAN | Stream/download the image. |
| POST | `/jobs/{id}/attachments` | assigned TECHNICIAN, STAFF, ADMIN | Upload job (repair/completion) image. |
| GET | `/jobs/{id}/attachments/{attachmentId}` | assigned TECHNICIAN, STAFF, ADMIN | Stream/download (internal-only; **not** customer-accessible). |

**Rules (server-enforced):**
- Allowed types JPEG/PNG/HEIC/WebP (true content-type sniffing); per-file ≤10 MB; request total ≤25 MB; counts ≤5 (request) / ≤10 (job). Violations → `422` (per-file reason; other files unaffected).
- Files served only via authenticated, authorization-checked streaming (no public URLs).
- Job attachments are internal-only (customers never receive them in V1).

---

# 17. Dashboard / Counters API (`/dashboard`)

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| GET | `/dashboard/counters` | STAFF, ADMIN | `{ pending, approved, activeJobs }` — computed counts for the Requests & Jobs hub (no stored notifications). Polled. |

Landing routing is client-side (role-based, per [Dashboard spec](features/dashboard.md)); the API only supplies the counters and the per-feature data.

---

# 18. HTTP Status Code Usage

| Code | Used for |
| --- | --- |
| 200 OK | Successful GET / action returning a body. |
| 201 Created | Resource created (register, request, user, appointment, attachment). |
| 204 No Content | Successful action with no body (logout, mark-read, change-password, disable/enable). |
| 400 Bad Request | Malformed request (bad JSON/params). |
| 401 Unauthenticated | Missing/invalid/revoked token; bad login (generic). |
| 403 Forbidden | Authenticated but not permitted (role/ownership). |
| 404 Not Found | Resource missing or not owned (no data leak). |
| 409 Conflict | Optimistic-lock conflict, illegal status transition, duplicate (username/mobile/category), last-admin guard. |
| 422 Unprocessable Entity | Validation failures (with `fieldErrors`). |
| 500 Internal Error | Unexpected; correlationId returned, no internals leaked. |

---

# 19. Alignment Notes

- **Constitution:** REST-only, JSON, consistent error structure, versioned `/api/v1`, JWT + RBAC, no sensitive exposure. ✔
- **Architecture.md:** stateless JWT + status check, layered enforcement, polling notifications, file streaming, optimistic concurrency, role-based access. ✔
- **Feature specs:** request lifecycle + review actions, job lifecycle + technician subset, scheduling (soft/hard rules, reschedule/cancel), Customer Home + inbox + friendly labels, Staff Management (last-admin guard), read-only Customer Management, role landing/counters. ✔
- **Gap-analysis V1 decisions:** 1:1 approval→job, single notification on job cancel (CON-03), awaiting-parts customer notification (WORK-02), accepted→preparing single event (WORK-01), rejection guidance (WORK-03), attachments optional/limits, password ≥8, default admin, polling inbox (last 30). ✔

This contract, together with [Database.md](Database.md), is sufficient to derive `Tasks.md` and the implementation.
