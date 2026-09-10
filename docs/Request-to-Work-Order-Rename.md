# Request → Work Order rename — impact analysis & specification

**Product:** RepairDesk  
**Document type:** Cross-cutting change spec (domain + API + DB + Admin Panel + Mobile)  
**Status:** Draft — **product decision required before any code**  
**Date:** 2026-08-27  
**Related:** [Database.md](Database.md) · [API.md](API.md) · [features/repair-request.md](features/repair-request.md) · [features/jobs.md](features/jobs.md) · [features/staff-assisted-intake.md](features/staff-assisted-intake.md)

---

# 1. Verdict (read this first)

**You cannot “just update the column name.”** There is no single column called `request`. Intake lives in table **`repair_requests`**. Execution lives in table **`jobs`**, which has FK **`jobs.request_id`**. Discriminator strings (`REPAIR_REQUEST`, audit `REQUEST`), REST `/requests`, JSON `requestId`, Java/Dart/Angular types, routes, and every user-visible label are separate layers.

**Worse: the two entities already mean different things.** In this product (and in FSM):

| Today (shipped) | Role | Typical FSM name |
| --- | --- | --- |
| **Repair Request** | Intake + review (`PENDING_REVIEW` → approve/reject) | Service request / work request |
| **Job** | Work after approval (assign, schedule, start, complete) | **Work order** |

If we rename **Request → Work Order** and leave **Job** as Job, staff will have two objects that both sound like “the work”: a Work Order that is not yet approved, and a Job that is the real work order. That is worse than today’s wording.

**Do not start implementation until §2 is decided.**

---

# 2. Product decision (blocking)

Pick **one**. The rest of this spec is written so either path can be estimated; **Option B is the recommendation.**

### Option A — Rename Request → Work Order (as asked)

- UI: Repair Requests → Work Orders; New Request → New Work Order; Create request → Create work order.
- Data: `repair_requests` → `work_orders`; `jobs.request_id` → `jobs.work_order_id`; JSON `requestId` → `workOrderId`; API `/requests` → `/work-orders` (or keep path with aliases).
- **Job stays Job** unless you also rename it (see collision below).
- Effort: **L / XL** (DB + API break + both apps + docs). Not a column rename.

### Option B — Rename Job → Work Order (recommended, FSM-correct)

- UI: Jobs → Work Orders; job detail → work order; “Approve & create job” → “Approve & create work order”.
- Data (if you want names to match): `jobs` → `work_orders`; JSON `jobId` → `workOrderId`; API `/jobs` → `/work-orders`.
- **Request stays Request** (or display name **Service request** only — no table rename required).
- Matches what we already told operations: the work order is created **on approve**.
- Effort: **M** if **labels only**; **L** if API/DB follow.

### Option C — Labels only (lowest risk, no Flyway)

- Change **user-visible strings** in Admin Panel + Mobile only.
- Keep tables, columns, JSON keys, and URLs as `request` / `job`.
- Effort: **S**. Can ship without a backend release.
- Use this as **Phase 1** even if you later do A or B in the database.

### Option D — Collapse Request + Job into one Work Order table

- One record from intake through complete. Deletes the 1:1 approve-creates-job model.
- **Out of scope for a rename.** That is a domain rewrite (statuses, permissions, AP-04 vs AP-06). Do not mix it into this change.

---

# 3. Why a column rename is not enough

### 3.1 There is no `request` column on the intake row

`repair_requests` columns are `id`, `reference_number`, `customer_id`, device/contact/issue fields, `status`, `priority`, etc. The word “request” is the **table name** and the **FK on jobs**, not a field on the request row.

What *would* have to change for a true data-model rename of Request → Work Order:

| Object | Today | After Option A |
| --- | --- | --- |
| Table | `repair_requests` | `work_orders` |
| PK | `repair_requests.id` | `work_orders.id` (same UUID, renamed table) |
| FK | `jobs.request_id` | `jobs.work_order_id` |
| Unique | `ux_jobs_request` / `ux_jobs_request_id` | matching names |
| CHECK values | `attachments.owner_type` ∈ `REPAIR_REQUEST` \| `JOB` | `WORK_ORDER` \| `JOB` (or `WORK_ORDER` \| `WORK` if Job also renamed) |
| CHECK values | `notifications.related_type` same set | same |
| Audit | `entity_type = 'REQUEST'` | `'WORK_ORDER'` |
| Flyway | New `V11__…sql` | `ALTER TABLE … RENAME`; **never edit V2/V3 in place** |

PostgreSQL `ALTER TABLE … RENAME COLUMN` / `RENAME TO` is the right *mechanism* for the physical names. It is **not** sufficient by itself: JPA `@Table` / `@JoinColumn`, every query, every CHECK, and every client JSON key must move in the same release or you break production.

### 3.2 Stored discriminator strings are data, not schema

Existing rows already contain `'REPAIR_REQUEST'` and `'REQUEST'`. A rename requires:

```sql
ALTER TABLE attachments DROP CONSTRAINT attachments_owner_type_check;
UPDATE attachments SET owner_type = 'WORK_ORDER' WHERE owner_type = 'REPAIR_REQUEST';
-- new CHECK
UPDATE audit_events SET entity_type = 'WORK_ORDER' WHERE entity_type = 'REQUEST';
UPDATE notifications SET related_type = 'WORK_ORDER' WHERE related_type = 'REPAIR_REQUEST';
```

Deep links and notification handlers that switch on `relatedType === 'REPAIR_REQUEST'` break until both apps are released together.

### 3.3 HTTP and JSON are a contract

| Layer | Examples (not exhaustive) |
| --- | --- |
| Paths | `POST /requests`, `GET /requests/{id}`, `POST /requests/{id}/approve`, `GET /requests/{id}/attachments`, `GET /jobs/{id}` |
| JSON | `requestId`, `jobId`, `jobStatus` on request summaries |
| Query | `/requests?status=PENDING_REVIEW` |
| Nav | Admin `/requests`, `/jobs`; Flutter named routes / `MaterialPageRoute` |

Renaming JSON `requestId` → `workOrderId` **breaks** any deployed APK and the live Admin Panel until they are rebuilt. If you need zero-downtime: **add** the new key, keep the old key for one release, then delete.

### 3.4 Names that must **not** be globally find-replaced

| Name | Why leave it |
| --- | --- |
| `DeviceCategoryRequest` / `DeviceBrandRequest` | Catalog DTOs, not repair intake |
| `POST /requests/{id}/request-info` | Verb: “ask the customer for information”, not the entity |
| HTTP “request”, `HttpClient`, `CreateRequestDto` as a Spring pattern | Framework language |
| `mustChangePassword` etc. | Unrelated |

A blind rename of `Request` will corrupt catalog APIs and the info-request action.

---

# 4. Inventory (as of 2026-08-27)

### 4.1 Database (Flyway V1–V10)

| Location | Name | Role |
| --- | --- | --- |
| `V2__requests.sql` | table `repair_requests` | Intake |
| `V3__jobs.sql` | table `jobs`, column `request_id` UNIQUE FK | Execution |
| `V5__attachments_notifications_audit.sql` | `owner_type`, `related_type` CHECKs | Polymorphic links |
| `V8__performance_indexes.sql` | `ux_jobs_request_id` | |
| `V9__customer_info_response.sql` | column on `repair_requests` | Not named “request” |

No `work_order` string exists in schema today.

### 4.2 Backend Java (`feature/request` = 11 files, plus consumers)

- Package `…feature.request` (controller, service, repository, domain `RepairRequest`, `RequestStatus`, DTOs).
- `Job.request` / `@JoinColumn(name = "request_id")`.
- `JobResponse.requestId`.
- `RepairRequestResponse.jobId` / `jobStatus` (the *other* direction).
- `AttachmentService` / controller: owner `"REPAIR_REQUEST"`.
- `JobService` audit `"REQUEST"`; notifications `"REPAIR_REQUEST"`, types like `REQUEST_REJECTED`.
- `TrackingService` maps request statuses for the customer app.
- Tests: `RepairRequestServiceTest`, `JobServiceTest`, security tests hitting `/requests`.
- Reference numbers: `RD-{year}{seq}` — **not** `RQ-`. Display copy in specs often says `RQ-1042`; production is `RD-…`. A rename does **not** require changing existing reference strings (they are opaque). Optional later: `WO-` prefix **for new rows only**.

### 4.3 Admin Panel (`admin-web`)

| Surface | Today |
| --- | --- |
| Nav | “Repair Requests” → `/requests`; “Jobs” → `/jobs` |
| Feature folder | `features/requests/`, `core/api/requests.api.ts`, `request.models.ts` |
| Routes | `/requests`, `/requests/:id`, `/requests/new` (AP-18 spec) |
| Copy | Approve & create job; originating request; Repair requests (N) on customer detail |
| JSON | `requestId` on `JobDetail` |

### 4.4 Mobile (`frontend`)

| Surface | Today |
| --- | --- |
| Customer FAB | “New Request”; app bar “New Repair Request” |
| Customer home | “No repairs yet” / repair cards (already mixed “repair” vs “request”) |
| Admin (same APK) | App bar “Repair Requests”; `RequestsRepository` → `/requests` |
| Jobs | `JobsRepository` → `/jobs`; “My jobs” for technicians |
| History | Customer detail section “Repair Requests” |

### 4.5 Docs (will lag unless updated in the same PR)

PRD, Database.md, API.md, Architecture.md, gap-analysis, all `features/*.md`, AP-01…AP-18, mockups, Tasks.md, User-Guide-Admin.md. Specs currently say “Approve & create job” and “Repair Requests”.

---

# 5. Recommended glossary (if Option B)

Use this in **both apps** so customers and staff share one language.

| Audience | Intake record | Execution record |
| --- | --- | --- |
| Customer (mobile) | Request (or “Repair request”) | They already see **status of the repair**; after approve, tracking can say **Work order** `{reference}` |
| Staff (admin + mobile) | **Requests** (queue to accept) | **Work orders** (assign / schedule / do the work) |
| API / DB (Phase 1) | `repair_requests`, `/requests` | `jobs`, `/jobs` |
| API / DB (Phase 2, optional) | unchanged | `work_orders`, `/work-orders`, `workOrderId` |

**Create and approve** (AP-18): button becomes **Create and approve work order** (still creates a Job row).

---

# 6. If Option A is chosen anyway (Request → Work Order)

Do it in **layers**. Do not rename the table in the same hour as a find-replace in Flutter without a compatibility window.

### Phase A1 — Labels only (both apps)

| Today | New |
| --- | --- |
| Repair Requests | Work Orders |
| New Request / New Repair Request | New Work Order |
| Create request | Create work order |
| Originating request | Originating work order |
| Approve & create job | Approve & create job *(keep Job until you also rename Job)* |

Collision copy to avoid: sidebar showing **Work Orders** and **Jobs** next to each other with no explanation. If A proceeds, **Jobs must be renamed in the same UI release** (e.g. **Jobs** → **Active work** / **Assignments**) or users will not know which list to open.

### Phase A2 — API additive

- Accept `/work-orders` as an **alias** of `/requests` (same controller).
- JSON: emit both `requestId` and `workOrderId` (same UUID).
- Keep `/requests` until every APK is retired.

### Phase A3 — Database

New Flyway migration only (never rewrite V2/V3):

1. `ALTER TABLE repair_requests RENAME TO work_orders;`
2. Rename constraints/indexes that contain `requests`.
3. `ALTER TABLE jobs RENAME COLUMN request_id TO work_order_id;`
4. Update CHECKs and stored `REPAIR_REQUEST` / `REQUEST` values (§3.2).
5. JPA: `@Table(name = "work_orders")`, `@JoinColumn(name = "work_order_id")`.

Rollback is painful once clients write `workOrderId` only. Take a DB backup; ship backend **before** or **with** clients that require the new names.

### Phase A4 — Remove aliases

After all clients are on the new contract: drop `/requests`, drop `requestId`.

---

# 7. If Option B is chosen (Job → Work Order) — implementation spec

### 7.1 Phase 1 — UI labels (no API/DB) — **do this first**

**Admin Panel**

| Location | Change |
| --- | --- |
| `nav.config.ts` | Jobs → **Work orders**; description “Track and manage work orders” |
| AP-06 title | Jobs / My jobs → Work orders / My work orders |
| AP-07 | Job detail headings, toasts (“started”, “completed”) |
| AP-04/AP-05 | “Approve & create job” → **Approve & create work order**; Job column header → Work order |
| AP-08/AP-09 | “Schedule appointment” copy that says job → work order |
| AP-11 | Jobs (n) → Work orders (n) |
| AP-18 | Create and approve → **Create and approve work order** |
| Routes | **Keep `/jobs`** in Phase 1 (bookmarks). Optional later: `/work-orders` redirect. |

**Mobile**

| Location | Change |
| --- | --- |
| Technician/staff jobs list | My jobs → My work orders |
| Job detail | Title/toasts |
| Dashboard tiles | Jobs → Work orders |
| Approve dialog | create job → create work order |
| Customer | Prefer “repair” / status phrases; only say Work order **after** approved if you want FSM purity. Do not call a pending submission a work order. |

**Do not rename** `features/jobs/` folders or `JobsApi` in Phase 1.

### 7.2 Phase 2 — API/DB (optional)

| Today | New |
| --- | --- |
| table `jobs` | `work_orders` |
| `appointments.job_id` | `appointments.work_order_id` |
| JSON `jobId` | `workOrderId` (+ alias) |
| path `/jobs` | `/work-orders` (+ alias) |
| `owner_type` `'JOB'` | `'WORK_ORDER'` |
| Java `Job` | `WorkOrder` (or keep class `Job` mapped to table `work_orders` — less churn) |

`jobs.request_id` **stays** `request_id` (still points at `repair_requests`). That is correct: a work order originates from a request.

### 7.3 Acceptance criteria (Phase 1)

**AC-WO-01** — Given I am an admin, When I open the sidebar, Then I see **Work orders** not **Jobs**, and the URL may still be `/jobs`.

**AC-WO-02** — Given I approve a pending request, When the toast appears, Then it says a **work order** was created, and the execution record is still the `jobs` row.

**AC-WO-03** — Given I am a customer with a pending submission, When I view home, Then I do **not** see the word Work order on that card (it is still a request/repair under review).

**AC-WO-04** — Given existing APKs, When they call `/jobs` and `/requests`, Then the backend behaves as today (Phase 1 has no API change).

**AC-WO-05** — Given a technician, When they open their list, Then the title is **My work orders** and assigned rows are unchanged.

---

# 8. Impact matrix (rough)

| Area | Option C labels | Option B Phase 2 (Job DB/API) | Option A (Request DB/API) |
| --- | --- | --- | --- |
| User confusion | Low if glossary is B | Low | **High** unless Job is also renamed |
| Flyway | None | 1 migration, UPDATE discriminators | 1 migration, more FKs/CHECKs |
| Backend Java | None | Package `job` + DTOs + tests | Package `request` + Job FK + attachments/audit/notif |
| Admin Panel | Strings + maybe titles | Routes aliases, models `jobId` | Routes `/requests`, models, AP-18 |
| Mobile APK | Strings; **forced store release** if API breaks | Forced release if JSON/path change | Forced release |
| Live Admin SWA | Redeploy HTML | Redeploy + API together | Redeploy + API together |
| Docs / mockups | Sweep | Sweep | Sweep |
| Downtime | None | Brief if aliases used | Brief if aliases used |
| Estimate | 1–2 days UI | +3–5 days BE+FE | +5–8 days BE+FE+data |

---

# 9. Coordination / rollout

1. Freeze the **decision** (A / B / C) in this doc’s header Status line.
2. If C or B Phase 1: one PR per client (admin-web, frontend) + docs; **no** backend.
3. If A or B Phase 2: backend Flyway + aliases **first** (deploy API), then clients, then remove aliases.
4. Do not run a repo-wide replace of `Request` or `request`.
5. QA: approve flow, attachments on request vs job, notification tap targets, customer tracking, technician list, AP-18 create-and-approve (when built).

---

# 10. Open questions

1. **Confirm Option A vs B vs C.** Recommendation: **C then B Phase 1** (labels: Job → Work order; Request stays Request).
2. Customer-facing word: “Repair”, “Request”, or “Work order” after approval only?
3. Keep URLs `/jobs` and `/requests` forever (stable bookmarks) vs vanity `/work-orders`?
4. Change new reference prefix from `RD-` to `WO-`? (Cosmetic; old numbers stay `RD-`.)
5. AP-18 is specified but not implemented — apply the chosen glossary in AP-18 copy **now** so it does not ship as “Create request”.

---

# 11. Out of scope

- Merging request and job into one table (Option D).
- Changing status enums (`PENDING_REVIEW`, `CREATED`, …).
- Staff-assisted intake behaviour (FEAT-SAI-001), except button labels.
- Tezvo marketing site.

---

*Until §2 is answered, no schema or JSON rename should be implemented. A column rename alone will not achieve a product rename and will break the 1:1 request↔job model if done blindly.*
