# API Contract Gaps & Drift Register

**Purpose:** while writing the [Admin Panel UI specs](README.md), every endpoint cited was checked against **both** [API.md](../API.md) and the shipped backend source. This file records where they disagree, and where the panel needs something the API doesn't offer.

**Normative rule:** where [API.md](../API.md) and the deployed backend disagree, **the backend is what the Angular client must be built against**, and API.md is the document that needs correcting. The page specs follow the verified column below.

**Status legend:** `Verified` = read in backend source · `Needs fix in API.md` = docs wrong, code fine · `Needs backend work` = the panel genuinely needs something that doesn't exist · `Product decision` = neither is wrong, someone has to choose.

---

## 1. Documentation drift — API.md is wrong, backend is right

These are **documentation defects**. The endpoints work; API.md describes them incorrectly. Building from API.md alone would produce a client that 404s or 405s.

| # | Area | API.md says | Backend actually does (verified) | Status |
| --- | --- | --- | --- | --- |
| 1.1 | Edit user | `PUT /users/{id}` | **`PATCH /users/{id}`** (`UserManagementController`) | Needs fix in API.md |
| 1.2 | Enable/disable user | `POST /users/{id}/enable` · `POST /users/{id}/disable` | **`POST /users/{id}/activate`** · **`POST /users/{id}/deactivate`** | Needs fix in API.md |
| 1.3 | User list roles | `GET /users` is `ADMIN`-only | `@Secured({"ROLE_STAFF","ROLE_ADMIN"})` — **staff may list users** | Needs fix in API.md |
| 1.4 | Technician roster | `GET /technicians` (Staff/Admin) | **Does not exist.** No `/technicians` route anywhere in the backend. The roster comes from `GET /users?role=TECHNICIAN` | Needs fix in API.md |
| 1.5 | Login request | `{ identifier, password }` | **`{ username, password }`** (`LoginRequest`) — resolves by username only, so a mobile number will not sign anyone in | Needs fix in API.md |
| 1.6 | Login response | `{ token, user }` | Flat `AuthResponse` including **`mustChangePassword`**, which API.md documents nowhere | Needs fix in API.md |
| 1.7 | Reset password body | `{ identifier, newPassword, confirmPassword }` | **`{ username, newPassword }`** — no `confirmPassword` field exists server-side | Needs fix in API.md |
| 1.8 | Dashboard counters | `{ pending, approved, activeJobs }` | **`{ pendingReview, infoRequired, activeJobs, unscheduledJobs }`** (`DashboardController`). There is no `approved` counter | Needs fix in API.md |
| 1.9 | Appointment reschedule | `POST /appointments/{id}/reschedule` | **`PUT /jobs/{jobId}/appointments/{appointmentId}/reschedule`** | Needs fix in API.md |
| 1.10 | Appointment cancel | `POST /appointments/{id}/cancel` | **`DELETE /jobs/{jobId}/appointments/{appointmentId}`** with a required `{ reason }` body | Needs fix in API.md |
| 1.11 | Calendar params | `dateFrom`, `dateTo`, `technicianId`, `status`, `q`, paged | **`from` and `to` only**, both required, returns an **unpaged array** | Needs fix in API.md |
| 1.12 | Jobs list filters | `status`, `scheduled`, `technicianId`, `priority`, `q`, `dateFrom`, `dateTo` | Implemented: `status`, `active`, `scheduled`, `technicianId`, `unassigned`, `q`, pagination/sort. Still no `priority`/`dateFrom`/`dateTo` filters | Partial — update API.md |
| 1.13 | Reschedule payload | Implies the technician can be changed | Body is `{ scheduledDate, startTime, notes? }`; the service **copies the technician from the previous appointment** — it cannot be changed | Needs fix in API.md |

**Impact:** items 1.1, 1.2, 1.4, 1.9, 1.10 are wrong *paths or verbs* — a client built on them fails at runtime with 404/405. Items 1.5–1.8, 1.11–1.13 are wrong *payloads* — the client compiles and then misbehaves. Correcting API.md is a prerequisite for treating it as the contract.

---

## 2. Gaps the panel needs closed

The UI requirement is real and specified in the page specs; the API can't serve it yet.

| # | Need | Where | Detail | Status |
| --- | --- | --- | --- | --- |
| 2.1 | Technician filter + "Unassigned" on the jobs list | [AP-06](pages/AP-06-jobs.md) | `GET /jobs` now accepts `technicianId` and `unassigned=true` (plus `q`). | ✅ Closed 2026-08-11 |
| 2.2 | Multi-status / active-work filtering | [AP-03](pages/AP-03-dashboard.md), [AP-06](pages/AP-06-jobs.md) | `active=true` and `scheduled=false` shorthands ship on `GET /jobs` (APB-008). | ✅ Closed earlier |
| 2.3 | Multi-role user filtering | [AP-12](pages/AP-12-staff.md) | `GET /users?role=` takes one role, so "all internal staff" needs three parallel calls merged and paged client-side, which breaks true server-side pagination. Needs repeated `role` params or `excludeRole=CUSTOMER`. | Needs backend work |
| 2.4 | Notification deep links | [AP-16](pages/AP-16-notifications.md) | `NotificationResponse` carries `relatedEntityId` but **no `relatedType`** (verified), so the client holds a record id with no way to know whether it's a request, job, or appointment. Blocks all deep linking. Needs `relatedType` added. | Needs backend work |
| 2.5 | Request attachment listing | [AP-05](pages/AP-05-request-detail.md) | API.md documents upload, delete, and single-file download but no endpoint to enumerate a request's attachments, which every gallery needs. (The mobile app calls one, so this may be another doc gap — verify.) | Needs backend work or doc fix |
| 2.6 | Job attachment delete | [AP-07](pages/AP-07-job-detail.md) | Delete is documented for request attachments only, yet the mobile app shows a delete control on job attachments. | Needs backend work or doc fix |
| 2.7 | Brand/category `version` in responses | [AP-13](pages/AP-13-device-categories.md), [AP-14](pages/AP-14-brands.md) | Edits require `version` for optimistic concurrency, but the responses aren't documented as returning it. Same question for user payloads ([AP-12](pages/AP-12-staff.md)). | Needs backend work or doc fix |
| 2.8 | Category brand counts | [AP-13](pages/AP-13-device-categories.md) | The categories list needs an active-brand count per row; without an `activeBrandCount` field the only alternative is an N+1 call per row. | Needs backend work |
| 2.9 | `GET /device-categories/{id}` | [AP-14](pages/AP-14-brands.md) | The brands page has to fetch the whole catalog and match on id just to show the category's name. | Needs backend work |
| 2.10 | Search on requests / categories | [AP-04](pages/AP-04-repair-requests.md), [AP-13](pages/AP-13-device-categories.md) | `q` exists on `/customers` but not on `/requests` or `/device-categories`. PRD §10 sets a <2s search target across 10k records, which client-side filtering can't meet once paged. | Needs backend work |
| 2.11 | Working-hours validation | [AP-08](pages/AP-08-schedule-appointment.md) | The server validates only past-date, booking horizon, and end-after-start. **Closed days, out-of-hours times, and non-slot-aligned times are silently accepted**, leaving the client as the only enforcement — a rule enforced only in a client isn't enforced. | Needs backend work |
| 2.12 | Overlap conflict not returned | [AP-08](pages/AP-08-schedule-appointment.md) | The server detects double-booking and writes it to the audit log only; the client never learns. The panel currently re-derives it from the calendar range endpoint. | Needs backend work |
| 2.13 | Reschedule history unreadable | [AP-08](pages/AP-08-schedule-appointment.md) | Rescheduling cancels the prior appointment, but `GET /jobs/{id}/appointments` excludes cancelled rows — so the retained history is unreachable, defeating its purpose. | Needs backend work |
| 2.14 | Walk-in customer create | [AP-18](pages/AP-18-create-request.md) | No `POST /customers`. `POST /users` is admin-only and aimed at internal roles. Staff cannot register a walk-in. | Needs backend work — APB-SAI-001 |
| 2.15 | Assisted `POST /requests` | [AP-18](pages/AP-18-create-request.md) | `RepairRequestService.create` always sets owner = **caller**. No `customerId`, `approveOnCreate`, or `origin`. Walk-in would create a request owned by staff. Device type / brand / model / issue stay the same as customer create. | Needs backend work — APB-SAI-002 |

---

## 3. Security findings

Found while verifying auth endpoints. **These are not UI issues** — they are noted here because the panel is the client that will expose them.

| # | Finding | Detail | Severity |
| --- | --- | --- | --- |
| 3.1 | **`POST /auth/reset-password` allows account takeover** | The endpoint is **public** and its entire body is `{ username, newPassword }` (verified in `ResetPasswordRequest`). There is no token, emailed link, OTP, security question, or current-password check — **no proof of account ownership of any kind**. Anyone who can reach the API and knows a username can set that account's password. The seeded administrator's username is `admin` and is published in [User-Guide-Admin.md](../User-Guide-Admin.md). The code comment describes it as "enumeration-safe" (it always returns 204), which is true and beside the point: the vulnerability isn't enumeration, it's missing authorization. | **Critical** |
| 3.2 | No rate limiting anywhere | No throttling on login or password reset, so 3.1 is also trivially scriptable and login is open to unlimited credential stuffing. API.md mentions a `RATE_LIMITED` (429) code that nothing emits. | High |
| 3.3 | Non-expiring JWTs + browser storage | Tokens never expire by design (ADR-003). In a web client, any XSS yields a permanently valid credential. [README §6.1](README.md) recommends in-memory + `sessionStorage`; this needs ratifying before build. | High |
| 3.4 | Wrong current password returns `403` | `POST /me/change-password` answers a mistyped current password with `403`, which under the standard error mapping renders a page-level "forbidden" state and ejects the user from the form. [AP-17](pages/AP-17-account.md) special-cases it; the status code should be `401`/`422` instead. | Low (UX), Medium (as a signal) |

**Recommendation:** 3.1 should be treated as a release blocker for exposing this API to the internet, independently of the Admin Panel. The minimum fix is to require proof of ownership (a time-limited token delivered out-of-band) or, if no delivery channel exists yet, to remove the public endpoint and leave password resets to the admin-only `POST /users/{id}/reset-password`, which is already properly secured.

---

## 4. Product decisions needed

Neither the docs nor the code are wrong — a person has to choose.

| # | Question | Context |
| --- | --- | --- |
| 4.1 | **May `STAFF` review repair requests?** | [Architecture.md](../Architecture.md) and the shipped app say admin-only; [PRD.md](../PRD.md) §4, [features/authentication.md](../features/authentication.md) §11, [features/request-review.md](../features/request-review.md), and [gap-analysis.md](../gap-analysis.md) all say staff may approve/reject. Affects the route guard and nav visibility on [AP-04](pages/AP-04-repair-requests.md)/[AP-05](pages/AP-05-request-detail.md) only — no page content changes either way. |
| 4.2 | **Do staff and admins receive notifications at all?** | Verified: every notification the backend creates targets a customer, except `JOB_ASSIGNED` which targets a technician. So the inbox and the top-bar bell are decorative for admins and staff. Either add staff/admin events or scope [AP-16](pages/AP-16-notifications.md) accordingly. |
| 4.3 | **Where do technicians land, and do they get a dashboard?** | PRD says personal schedule, the Flutter app shows a jobs list, and the panel's own README §3/§4 aren't fully consistent. |
| 4.4 | **Are customer accounts manageable from the panel?** | [features/customer-management.md](../features/customer-management.md) and API.md §9 call it strictly read-only, while [Bugs.md](../Bugs.md) #12 deliberately added admin reset-password and delete. [AP-10](pages/AP-10-customers.md)/[AP-11](pages/AP-11-customer-detail.md) follow shipped behavior; confirm or reverse for both. |
| 4.5 | **Canonical rejection reason codes.** | The reject dialog needs the real list; the mobile app only ever sends `UNABLE_TO_SERVICE`. |
| 4.6 | **Business info fields in Settings.** | PRD §6 lists business info under Settings; API.md §11 says "business info" without naming fields. The columns exist in [Database.md](../Database.md) §3.9, so [AP-15](pages/AP-15-business-settings.md) specs them against the implementation rather than a contract. |
| 4.7 | **Seeded admin's forced password change.** | Database.md §9 specifies `must_change_password = true`, but the seed reportedly inserts `false`, so the forced-change flow may never fire. Confirm which is intended. |

---

## 5. Recommended sequencing

1. **Correct API.md** against §1. Cheap, unblocks treating it as the contract, and prevents building the wrong paths.
2. **Decide §3.1 and §3.3.** Security posture shouldn't be discovered during implementation.
3. **Resolve §4.1, §4.2, §4.4.** They change route guards and nav, which is the first thing built.
4. **Schedule §2 work by page order.** Items 2.1–2.4 block whole page elements; the rest degrade gracefully and are marked as such in their specs.
5. **Then build**, page by page, in the AP-01 → AP-17 order of [README §4](README.md).

---

*Compiled 2026-08-06 while authoring the Admin Panel UI specs. Verified against backend source: `UserManagementController`, `AuthController`, `LoginRequest`, `ResetPasswordRequest`, `AppointmentController`, `DashboardController`, `NotificationResponse`, and the device-category/brand controllers.*
