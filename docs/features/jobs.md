# Job Management - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.1
**Status:** Draft

> This document defines *what* the Job Management feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §6 (Requests & Jobs screen), §7 (User Flow), §8 Features 3–5, §9 (Job Statuses), the **Requests & Jobs** and **Dashboard** wireframes, and the upstream [Repair Request](repair-request.md) and [Request Review](request-review.md) specifications. A Job begins **after** a request is approved.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Job Management |
| **Feature ID** | FEAT-JOB-003 |
| **Source Requirement** | PRD §8 Feature 3 (Job Management), Feature 4 (Technician Assignment), Feature 5 (Scheduling); §7 User Flow (Job Created → Technician Assigned → Scheduled → In Progress → Completed → Customer Notified); §9 Job Statuses; Requests & Jobs wireframe (Jobs tab: status control, "Assign Technician → Unassigned", "Filter by Technician"); Dashboard ("manage job status"). |
| **Purpose** | Manage the active repair lifecycle of an approved request: track progress, assign technicians, schedule appointments, record repair actions and outcomes, and complete/close work. The Job is the central operational entity. |
| **Business Value** | Provides operational tracking and control over repairs, improves repair visibility for staff/customers, enables technician management and utilization, and creates an auditable record from approval to closure. Supports PRD goals (optimize assignment, scheduling efficiency, completion visibility). |
| **Related Features** | Request Review (FEAT-RV-002, upstream — creates the job on approval), Repair Request (FEAT-RR-001, source data), Technician Assignment (PRD Feature 4), Scheduling (Feature 5), Customer Management (Feature 6), Product Management (Feature 7), Settings (Feature 8), Authentication & RBAC, Notifications, Repair Status Tracking (customer view). |
| **Dependencies** | A. An approved request exists (FEAT-RV-002). B. Authentication & RBAC (Staff/Technician/Administrator). C. Staff/Technician records for assignment (Staff Management). D. Settings — working hours, slot duration (Feature 8). E. Notification service (in-app, V1). F. Attachment retrieval/storage (deferred to architecture). |

**Scope note:** Technician Assignment (Feature 4) and Scheduling (Feature 5) are listed as separate PRD features but are operated **through the Job** in V1 (the wireframe embeds "Assign Technician" and a Schedule tab alongside Jobs). This spec defines how jobs *use* assignment and scheduling; dedicated availability engines or calendar UIs may be elaborated in their own specs later.

---

# Business Objectives

Jobs exist to provide:

* **Operational tracking** — a single record of each active repair and its current state.
* **Repair lifecycle control** — enforce an orderly progression from creation to closure with valid transitions.
* **Technician management** — assign work, see who is responsible, and track workload/utilization.
* **Repair visibility** — give staff a real-time operational picture and customers a transparent status.
* **Accountability & auditability** — record who did what and when, including outcomes and completion evidence.
* **Throughput & efficiency** — surface bottlenecks (e.g., Awaiting Parts, unassigned, overdue) to keep repairs moving.

---

# User Roles

## Technician
* **Responsibilities**
  * Work assigned jobs; keep status current; record repair notes and evidence; complete jobs.
* **Permissions**
  * View jobs **assigned to them** (with full request context and attachments).
  * Advance status within the technician-permitted set: `Assigned`/`Scheduled` → `In Progress` → `Awaiting Parts` ↔ `In Progress` → `Completed`.
  * Add repair notes (internal); upload repair/completion images.
  * Mark repair work done (move to `Completed`) — requires a resolution summary.
* **Restrictions**
  * Cannot view jobs not assigned to them (V1 default).
  * Cannot (re)assign technicians, cancel, delete, or edit customer/device facts.
  * Cannot change priority (advisory only) or reschedule unilaterally (request via staff) — *V1 simplification*.

## Staff Member
* **Responsibilities**
  * Own the operational queue: assign/reassign technicians, schedule/reschedule, monitor progress, handle exceptions, finalize (close) jobs.
* **Permissions**
  * View **all** jobs and details.
  * Assign/reassign a technician; set/adjust priority; schedule/reschedule appointments.
  * Change status across the full lifecycle (including `Cancelled`).
  * Add internal notes; upload attachments.
* **Restrictions**
  * Cannot manage users/products/global settings (Administrator scope).
  * Should not silently alter customer-submitted facts without an auditable note.

## Administrator
* **Responsibilities**
  * All Staff capabilities plus configuration (staff/technician records, products, working hours, slot duration) and oversight (audit, reporting, exception overrides).
* **Permissions**
  * Full access to all jobs, audit history, and reporting; corrective overrides (with audit).
* **Restrictions**
  * Configuration changes must not corrupt in-flight jobs.

## Customer
* **Responsibilities**
  * None operationally; receives updates and collects the device.
* **Permissions**
  * View **their own** job's status, appointment information, and completion updates via Repair Status Tracking.
* **Restrictions**
  * No access to internal notes, technician identities beyond what is surfaced, other customers' jobs, or any operational controls. *(V1 decision: customer sees assigned technician name as the PRD's Repair Status Tracking lists "View assigned technician".)*

---

# User Stories

### Technician
1. **See my work queue** — *Goal:* view jobs assigned to me. *Motivation:* know what to work on. *Outcome:* a filtered list of my active jobs with status and priority.
2. **Update repair progress** — *Goal:* move a job to In Progress / Awaiting Parts. *Motivation:* reflect real status. *Outcome:* status updates with audit trail and customer-visible status change.
3. **Record what I did** — *Goal:* add notes and photos. *Motivation:* document the repair and outcome. *Outcome:* internal notes + evidence attached to the job.
4. **Complete a repair** — *Goal:* mark work as done with a resolution summary. *Motivation:* close out the job. *Outcome:* status `Completed`; customer notified.

### Staff Member
5. **Assign a technician** — *Goal:* assign an unassigned job. *Motivation:* get work started. *Outcome:* job `Assigned`; technician sees it; audit recorded.
6. **Schedule an appointment** — *Goal:* set a date/time for the repair. *Motivation:* plan capacity. *Outcome:* job `Scheduled`; appointment visible to customer.
7. **Handle awaiting parts** — *Goal:* mark a job blocked on parts. *Motivation:* track delays. *Outcome:* status `Awaiting Parts`; reflected in reporting.
8. **Reassign / reschedule** — *Goal:* change technician or time. *Motivation:* absences, conflicts. *Outcome:* updated assignment/schedule with audit.
9. **Cancel a job** — *Goal:* stop a job that can no longer proceed. *Motivation:* customer cancels or device unrepairable. *Outcome:* status `Cancelled` with reason; customer notified.

### Administrator
10. **Oversee operations** — *Goal:* see all jobs and metrics. *Motivation:* manage performance. *Outcome:* full visibility, reporting, and override capability.

### Customer
11. **Track my repair** — *Goal:* see status and appointment. *Motivation:* know when it's ready. *Outcome:* current status, appointment info, and completion notification.

---

# Job Creation

* **Automatic on approval (V1 decision):** A job is created **automatically** when a request is `Approved`, **1:1** with the request. There is no separate "Convert to Job" step.
* **Manual creation of a job (no request):** **Not supported.** Staff-assisted work still starts as a request ([FEAT-SAI-001](staff-assisted-intake.md)); **Create and approve** is approval, not a separate job factory. There is no Create job action on the Jobs list.
* **Who can create jobs:** The **system**, triggered by Staff/Administrator approval (from the queue or from Create and approve). No role instantiates a job row directly.
* **Information copied from the request (snapshot at approval):**
  * Customer information (name, mobile, email if present, customer account reference).
  * Device information (category, brand, model, serial/IMEI, purchase date, warranty indication).
  * Issue information (description) and attachments (customer-submitted images).
  * Appointment **preference** (requested date/time) — carried as a starting point, not a confirmed booking.
  * Priority (as set during review; default `Normal`).
  * Linkage: originating Request ID/Reference for traceability.
* **Initial state:** New jobs start as `Created` and **Unassigned** (per wireframe "Assign Technician → Unassigned").
* **Assumption (documented):** request data is copied as a **snapshot** so later edits to the request (none expected post-approval) don't mutate the job; the job becomes the operational source of truth.

---

# Job Information

Information stored on a job:

## Customer Information
* Name, Mobile/Phone, Email (if provided), Customer account reference, link to customer profile/history.

## Device Information
* Category, Brand, Model, Serial/IMEI (sensitive), Purchase date, Warranty status (staff-confirmable).

## Repair Information
* Issue description (from request), repair notes (internal), repair outcome/resolution summary, parts-needed indicator (free text in V1), customer-submitted attachments, repair/completion attachments.

## Assignment Information
* Assigned technician (single, V1), assignment history (who/when), unassigned flag.

## Scheduling Information
* Scheduled date/time (confirmed), original requested preference, reschedule history.

## Status Information
* Current status, status history (transitions with actor/time), priority.

## Audit Information
* Created (from request, timestamp), created-from Request ID, all transitions, assignments, reschedules, notes, attachments, completion/closure events, with actor and timestamp.

## Additional fields (recommended)
* **Job Number / Reference** (operational, customer-quotable; distinct from internal ID).
* **Age / time-in-status** indicators (for queues and SLA-style reporting later).
* **Estimated cost / quote** *(deferred — invoicing is Phase 3; not in V1)*.
* **Completion timestamp** (for cycle-time reporting).

---

# Job Lifecycle

```
Approved (request)
      ↓ (auto)
   Created  ──────────────▶ Cancelled  (from any active state, by Staff/Admin)
      ↓ assign technician
   Assigned
      ↓ schedule
   Scheduled
      ↓ work starts
   In Progress ⇄ Awaiting Parts   (toggle while waiting on parts)
      ↓ repair done + resolution summary recorded
   Completed  (terminal)
```

Transition notes:
* `Created → Assigned` requires choosing an active technician.
* `Assigned` and `Scheduled` are both required before `In Progress` (either order is acceptable; assignment may follow scheduling).
* `In Progress ⇄ Awaiting Parts` may repeat any number of times.
* `In Progress → Completed` requires a **resolution summary** and may be performed by the assigned technician or Staff/Admin.
* `Completed` is **terminal**. There is no separate `Closed` or `Ready For Pickup` state in V1.
* `Cancelled` is reachable from any non-terminal state by Staff/Admin with a mandatory reason.

---

# Job Statuses

For each: **Meaning**, **Owner**, **Entry criteria**, **Exit criteria**, **Allowed transitions**.

| Status | Meaning | Owner | Entry Criteria | Exit Criteria | Allowed Transitions |
| --- | --- | --- | --- | --- | --- |
| `Created` | Job created from an approved request; unassigned. | System | Request approved. | Technician assigned. | → `Assigned`, → `Cancelled`. |
| `Assigned` | A technician is responsible; not yet scheduled. | Staff | Valid active technician selected. | Appointment scheduled or work begins. | → `Scheduled`, → `In Progress`*, → `Cancelled`, → `Created` (unassign). |
| `Scheduled` | Appointment date/time confirmed. | Staff | Valid future appointment within working hours. | Work begins or rescheduled. | → `In Progress`, → `Assigned` (reassign/reschedule), → `Cancelled`. |
| `In Progress` | Technician actively performing the repair. | Technician/Staff | Job assigned and scheduled (or assignment-only shortcut*). | Work paused for parts, repair done, or rescheduled. | → `Awaiting Parts`, → `Completed`, → `Scheduled` (reschedule/reassign mid-job), → `Cancelled`. |
| `Awaiting Parts` | Repair blocked pending parts or materials. | Technician/Staff | Parts-needed noted on the job. | Parts obtained; work resumes. | → `In Progress`, → `Cancelled`. |
| `Completed` | Repair finished; resolution summary recorded. **Terminal.** | Technician/Staff | Resolution summary present; job was `In Progress`. | — | Terminal. |
| `Cancelled` | Job stopped before completion; reason required. **Terminal.** | Staff/Admin | Cancellation reason provided. | — | Terminal. |

\* `Assigned → In Progress` shortcut is allowed for same-day/immediate repairs where formal scheduling is not needed.

**V1 decisions on additional statuses:**
* `Ready For Pickup` — **removed**. This is a service business; the technician completes the work and marks it `Completed`. No separate collection/handover step in V1.
* `Closed` — **removed**. `Completed` is the single terminal state. Archiving/reporting is handled by filtering on `Completed`/`Cancelled` records.
* `On Hold` — **deferred**; `Awaiting Parts` + notes cover V1 blocking scenarios.
* Cancellation reason — **free text** in V1.

---

# Job Dashboard

Anchored on the **Requests & Jobs** screen, **Jobs** tab (wireframe), with summary counters (Pending / Approved / **Active Jobs**).

* **List view:** Cards/rows showing Job Number, customer name, device — issue summary, current **status** (inline status control per wireframe), assigned technician (or **Unassigned**), and priority. Counter for Active Jobs.
* **Detail view:** Full job (see Job Details Screen).
* **Search:** By Job Number/Reference, customer name, mobile, or device.
* **Filters:**
  * **Status** (Created/Assigned/Scheduled/In Progress/Awaiting Parts/Completed/Cancelled).
  * **Technician** (per wireframe "Filter by Technician", incl. **Unassigned**).
  * **Priority** (Low/Normal/High/Urgent).
  * **Date** (scheduled date and/or created date range).
* **Sorting:** Default by **priority** then **oldest active first**; allow sort by scheduled date, created date, status.
* **Performance:** Must scale to PRD's 10,000+ records with <3s load and <2s search (paging/lazy-load; mechanism deferred to architecture).

---

# Job Details Screen

Information visible:

* **Job Number / Reference** and originating Request reference.
* **Status** (with status history) and **Priority**.
* **Customer** (name, contact; profile link).
* **Device** (category, brand, model, serial/IMEI [least-exposure], warranty).
* **Issue description** and **customer attachments**.
* **Assigned technician** (and assignment control for Staff).
* **Appointment information** (scheduled date/time; requested preference; reschedule control for Staff).
* **Repair notes** (internal thread).
* **Repair/completion attachments** (evidence/photos).
* **Repair outcome / resolution summary**.
* **Audit/history** (transitions, assignments, notes — read).
* **Actions** appropriate to role and current status (assign, schedule, advance status, add note, upload, cancel, close).

---

# Repair Notes

* **Who can add:** Technicians (on their assigned jobs), Staff, and Administrators. Customers cannot add notes.
* **Visibility (V1 decision):** **Internal-only.** Notes are not shown to customers. Customer-facing communication is limited to status, appointment, and completion updates via Repair Status Tracking.
* **Edit permissions:** **Append-only** and attributed/time-stamped. No silent edit/delete; if a correction is needed, add a new note. (Preserves an honest operational record.)
* **Audit requirements:** Note creation is auditable (author, timestamp). Notes must not be used to store data that belongs in structured fields (status, outcome, parts).
* **Recommendation:** A single internal notes thread per job in V1. A separate customer-visible message capability is a future enhancement (kept out to avoid accidental disclosure).

---

# Attachments

* **Types:** Customer-submitted **images** (from the request), plus technician/staff-added **repair evidence** and **completion photos**. Supporting **documents** (e.g., receipts/PDF) and video/audio are **future** (post-V1).
* **Upload permissions:** Technicians (assigned jobs) and Staff/Admin may upload repair/completion images. Customers do not upload to the job (their uploads happen at request time).
* **Visibility (V1 decision):** **Internal-only** for all job attachments. Customers see status and appointment information only, not images, in V1. (Sharing completion photos with customers is a future enhancement.)
* **Limits:** Follow the request attachment rules as a baseline — images JPEG/PNG/HEIC/WebP, 10 MB/file; a reasonable per-job count (recommended ≤10 across the repair). Enforced server-side.
* **Storage assumptions:** Attachments are retrievable for the life of the job and retained per the overall data-retention/backups policy; access restricted to authorized roles; no publicly guessable access. (Storage mechanism deferred to architecture.)

---

# Priority Management

* **Levels:** `Low`, `Normal` (default), `High`, `Urgent`.
* **Source:** Inherited from the request/review decision; **Staff/Admin may change** it on the job. Technicians see but cannot change priority.
* **Assignment rules:** Priority influences **queue ordering** and should guide which jobs staff assign/schedule first; it does not auto-assign technicians in V1 (assignment is manual).
* **Escalation rules (V1 decision):** **Manual only.** Staff may raise priority (e.g., aging or customer-impact). **No automatic escalation** based on age/SLA in V1 (deferred). Reporting exposes aging so staff can act.
* **Rationale:** A lightweight priority model improves triage and utilization without the overhead of an SLA/escalation engine in the first version.

---

# Technician Interaction

Technicians can (on jobs **assigned to them** only):
* **View** the job with full context and attachments.
* **Update status** within: `Assigned`/`Scheduled` → `In Progress`; `In Progress` ⇄ `Awaiting Parts`; `In Progress` → `Completed`.
* **Add internal repair notes.**
* **Upload** repair/completion images.
* **Record repair outcome / resolution summary** (required before `Completed`).

Technicians **cannot**:
* See or act on jobs not assigned to them (V1 default).
* Assign/reassign technicians, schedule/reschedule, cancel, or delete jobs.
* Change priority or edit customer/device facts.
* Add customer-visible content (all technician notes/attachments are internal in V1).

**V1 decision — completion authority:** Technicians may mark a job `Completed` directly (this is a service business with no physical pickup handover). Staff/Admin may also mark `Completed` if needed.

---

# Customer Visibility

Via Repair Status Tracking, a customer can see for **their own** job:
* **Current status** (mapped to customer-friendly labels, e.g., In Progress, Completed).
* **Appointment information** (scheduled date/time, or requested preference until confirmed).
* **Assigned technician** name (per PRD Repair Status Tracking) — *V1 decision: name only, no contact details.*
* **Completion notification** (notified when `Completed`).

Customers **cannot** see: internal notes, job attachments/images (V1), priority, other jobs, audit history, or any operational controls. Purely internal transitions (e.g., `Created`/`Assigned`) may be presented with simplified customer-facing labels.

---

# Scheduling Integration

* **Appointment creation:** Staff set a confirmed appointment (date/time) on the job, moving it to `Scheduled`. The customer's requested preference seeds the suggestion but is not binding.
* **Constraints:** Appointments respect Settings **working hours** and **slot duration**. **No holiday calendar** and **no hard capacity enforcement** in V1 (consistent with intake decisions); staff avoid conflicts manually. The wireframe's **Schedule** tab is the staff view for appointments.
* **Rescheduling:** Staff may reschedule; reschedule history is retained and the customer is notified of the new time.
* **Technician availability (V1 decision):** **No automated availability engine.** Staff use judgment and the Filter-by-Technician view to balance load. Double-booking is not hard-blocked in V1 (a soft warning is a future enhancement).
* **Dependencies:** Scheduling and Technician Assignment (PRD Features 4–5) are operated through the job in V1; richer calendar/availability features may be specified separately later.

---

# Notifications

**Version 1 — in-app.**

| Triggering event | Recipient | Channel |
| --- | --- | --- |
| Job created (from approval) | Staff/Admin (queue counter update) | In-app |
| Technician assigned / reassigned | Assigned technician | In-app |
| Job scheduled / rescheduled | Customer (+ technician) | In-app |
| Status → In Progress | Customer | In-app |
| Status → Awaiting Parts | Staff/Admin (internal flag) | In-app |
| Status → Completed | Customer | In-app |
| Job cancelled | Customer | In-app |

**Future versions** (integrate without redesign): Push (Phase 2), SMS (Phase 2, uses registered mobile), Email (requires email capture), WhatsApp (opt-in + provider).

*V1 note:* staff alerts via badges/counters to avoid notification fatigue; customer notifications limited to meaningful milestones above.

---

# Validation Rules

* **Required data on creation:** A job must reference a valid approved request and carry the copied customer/device/issue data.
* **Assignment validation:** Only an **active** technician (Technician role, not disabled) may be assigned; assigning requires the job be in an assignable state (`Created`/`Assigned`/`Scheduled`).
* **Scheduling validation:** Appointment date/time must be **now or future**, within **working hours**, and aligned to **slot duration**; reschedule follows the same rules.
* **Status transition validation:** Only transitions defined in **Job Statuses** are allowed; illegal transitions are blocked. Role-permitted transition sets are enforced (technicians limited to their subset).
* **Work-start precondition (V1):** A job should be **assigned and scheduled** before `In Progress` (the `Assigned → In Progress` shortcut is allowed only when scheduling is not applicable).
* **Completion validation:** Moving to `Completed` requires a **repair outcome/resolution summary**. `Completed` may be set by the assigned technician or Staff/Admin. The job must be `In Progress` to transition to `Completed`.
* **Cancellation validation:** `Cancelled` requires a **reason** (free text, V1) and is **Staff/Admin-only**.
* **Priority validation:** Must be one of Low/Normal/High/Urgent.
* **Authorization:** All actions enforced server-side against role and (for technicians) assignment ownership.

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **Invalid status change** (illegal transition / stale state) | Clear message that the action isn't allowed from the current status; show current status. | Refresh to latest state; offer only valid actions. |
| **Missing technician assignment** (e.g., trying to start work unassigned) | Block with guidance to assign first. | Prompt staff to assign; technician informed work can't start yet. |
| **Concurrent updates** (two actors update the same job) | Inform that the job changed since loaded; never silently overwrite. | Reload latest; require re-confirmation; **optimistic concurrency** (no locking in V1). |
| **Assignment to invalid/disabled technician** | Block with message; technician not selectable. | Refresh technician list; choose an active technician. |
| **Scheduling conflict / outside hours** | Block with explanation (outside working hours / past time). | Offer valid times; staff adjust. (Double-booking not hard-blocked in V1 — soft-warning is future.) |
| **Attachment/upload failure** | Mark the specific file failed with reason; other content unaffected; no false success. | Retry or remove; allow the action to proceed if attachment is non-blocking. |
| **Network failure** | Non-blocking error; entered data (notes/outcome) retained; no false confirmation. | Offer retry; confirm final state on reconnect before re-applying. |
| **Save failure** | No false confirmation; correlation id if available; data retained. | Idempotent retry so a transition/note isn't double-applied; log for support. |
| **Permission denied** | Inform user lacks permission. | Hide/disable unauthorized actions; log attempt. |
| **Session expired** | Prompt re-login. | Route to login; return to the job; preserve unsaved input where feasible. |

**Cross-cutting:** Never show a status/action as applied unless persisted. Error messages must not expose sensitive/system internals (Constitution).

---

# Reporting Requirements

Data captured must enable at least:
* **Jobs created** (per day/week/month) and current **active jobs** count.
* **Jobs completed** and **completion rate**.
* **Average completion time** (created → completed) and **cycle time** per status.
* **Jobs by technician** (workload, throughput, utilization).
* **Jobs by device category** (and brand if captured).
* **Jobs awaiting parts** (count and aging) — bottleneck visibility.
* **Jobs by status** (pipeline snapshot) and **aging/overdue** items.
* **Cancellations** (count and reasons).
* **Reschedule frequency** and **preferred-vs-scheduled** variance (scheduling effectiveness).
* **Priority distribution** of active/completed jobs.

*Note:* defines data-capture obligations; report visualization is out of scope.

---

# Audit Requirements

Auditable events (actor, timestamp; before/after where applicable):
* **Job created** (from which request, initial status, copied data snapshot reference).
* **Technician assigned / reassigned / unassigned.**
* **Status changed** (from → to, by whom; reason for cancellation).
* **Scheduled / rescheduled** (old → new date/time).
* **Priority changed** (old → new).
* **Repair note added** (author, timestamp).
* **Attachment uploaded / (removed if ever permitted).**
* **Repair outcome recorded.**
* **Job completed** (by whom, timestamp, resolution summary reference).
* **Permission-denied attempts** on job actions (security signal).

**Retention:** Audit records are tamper-evident, attributable, retained for the life of the job and per the PRD's backup/retention posture; sensitive values protected within logs (Constitution: never expose sensitive info).

---

# Security Requirements

* **Access control / Authentication:** All job actions require an authenticated session (Authentication spec; JWT, non-expiring until logout/disable/delete).
* **Role permissions (RBAC):**
  * **Technicians:** access **only assigned jobs**, with the limited action set above.
  * **Staff:** full operational access to all jobs (assign, schedule, status, cancel, close, notes, attachments).
  * **Administrators:** Staff access plus configuration, reporting, and overrides.
  * **Customers:** read-only status/appointment/completion for **their own** job only.
* **Data visibility:**
  * Sensitive fields (Serial/IMEI, full contact) visible to **internal roles only**, least-exposure.
  * Internal notes and job attachments are **never** exposed to customers in V1.
  * Attachment retrieval restricted to authorized roles; no public/guessable access.
* **Audit:** All operational actions and sensitive-field access (where policy requires) are logged and attributable.
* **Challenged assumptions:**
  * **Technician scoping:** Default is assigned-only visibility. If the shop is small and technicians need to see the shared board, this may need broadening — but V1 stays least-privilege.
  * **No availability/double-booking enforcement** in V1 is an operational risk; mitigated by staff oversight and the technician filter. Flagged as a future enhancement, not a V1 blocker.
  * **Completion authority shared:** technicians may mark `Completed` directly (no physical handover needed for service). Staff may override if needed.

---

# Acceptance Criteria

Given/When/Then. Covers creation, transitions, assignment, completion, and error scenarios.

### Job creation
**AC-1 — Auto-create on approval**
* **Given** a request is approved
* **When** the approval completes
* **Then** exactly one job is created in status `Created`, Unassigned, with customer/device/issue/attachment/priority data copied and the originating request linked, and it appears in the Jobs list.

**AC-2 — No manual creation**
* **Given** any user in V1
* **When** they look for a "create job" action outside approval
* **Then** none is available (jobs originate only from approvals).

### Status transitions
**AC-3 — Valid progression**
* **Given** an `Assigned` and `Scheduled` job
* **When** the technician starts work
* **Then** status becomes `In Progress`, recorded with actor/time, and the customer sees an updated status.

**AC-4 — Awaiting parts toggle**
* **Given** an `In Progress` job
* **When** the technician marks it awaiting parts and later resumes
* **Then** the job moves `In Progress → Awaiting Parts → In Progress`, each transition audited.

**AC-5 — Illegal transition blocked**
* **Given** a `Created` (unassigned) job
* **When** someone attempts to set it `In Progress`
* **Then** the action is blocked with guidance to assign (and schedule) first.

### Assignment workflows
**AC-6 — Assign a technician**
* **Given** a `Created` job and an active technician
* **When** a Staff member assigns the technician
* **Then** status becomes `Assigned`, the technician sees the job, and the assignment is audited and notified.

**AC-7 — Reassign**
* **Given** an assigned job
* **When** Staff reassign to another active technician
* **Then** the new technician gains access, the previous loses it, and both assignment events are audited.

**AC-8 — Cannot assign a disabled technician**
* **Given** a disabled technician account
* **When** Staff attempt to assign them
* **Then** the assignment is blocked.

**AC-9 — Technician scope enforced**
* **Given** a technician
* **When** they attempt to open a job not assigned to them
* **Then** access is denied.

### Scheduling
**AC-10 — Schedule within hours**
* **Given** an assigned job
* **When** Staff schedule a future time within working hours aligned to slot duration
* **Then** status becomes `Scheduled`, the appointment is stored, and the customer is notified of the date/time.

**AC-11 — Reject invalid schedule**
* **Given** Staff scheduling a past time or a time outside working hours
* **When** they attempt to save
* **Then** it is blocked with guidance.

### Completion workflows
**AC-12 — Technician completes work**
* **Given** an `In Progress` job with a recorded resolution summary
* **When** the technician marks it completed
* **Then** status becomes `Completed`, the customer is notified in-app, and the job leaves active queues.

**AC-13 — Resolution summary required**
* **Given** an `In Progress` job with no resolution summary
* **When** the technician or staff attempts `Completed`
* **Then** the action is blocked with a message to record the outcome first.

**AC-14 — Staff can also complete**
* **Given** an `In Progress` job
* **When** a Staff member marks it `Completed` with a resolution summary
* **Then** the transition is recorded with the actor's identity and the customer is notified.

### Error scenarios
**AC-16 — Concurrent update**
* **Given** two staff editing the same job
* **When** one changes status and the other then saves a stale change
* **Then** the second is told the job changed, shown the current state, and no silent overwrite occurs.

**AC-17 — Network failure on status change**
* **Given** a status change that fails to reach the server
* **When** the failure occurs
* **Then** no false success is shown, entered notes/outcome are preserved, and a safe retry is offered that does not double-apply.

**AC-18 — Cancellation requires reason**
* **Given** Staff cancelling a job
* **When** they confirm without a reason
* **Then** the action is blocked until a reason is provided; on success status becomes `Cancelled` and the customer is notified.

### Permission/visibility
**AC-19 — Customer visibility**
* **Given** a customer viewing their job
* **When** they open status tracking
* **Then** they see status, appointment, assigned technician name, and completion updates — but no internal notes, attachments, or controls.

**AC-20 — Customer cannot see others' jobs**
* **Given** a customer
* **When** they attempt to access another customer's job
* **Then** access is denied with no data exposed.

---

# V1 Decisions (Resolved)

All open questions are resolved below, optimized for a simple first version that can serve multiple customers and be enhanced later.

1. **Job vs. Request numbering:** The customer-facing **Job Number reuses the Request reference** for continuity — one reference number follows the repair from submission to completion.
2. **Assignment and scheduling order:** Either order is acceptable. Both must be set before `In Progress` (except the immediate-repair shortcut).
3. **Single technician per job:** **One technician per job** in V1. Multi-technician/team assignments are a future enhancement.
4. **Ready For Pickup / Closed removed:** `Completed` is the **single terminal status**. No `Ready For Pickup` (no physical handover step — this is a service business) and no separate `Closed` state.
5. **Double-booking:** **No hard enforcement** in V1. Staff manage conflicts manually using the Technician filter. Soft-warning is a future enhancement.
6. **Parts handling:** Free-text notes + `Awaiting Parts` status only. No structured parts/inventory in V1 (Phase 3).
7. **Cost/quote:** **Out of V1.** Invoicing is Phase 3.
8. **Completion evidence to customer:** **Internal only** in V1. Completion photos are not shared with customers (future enhancement).
9. **Technician status subset:** Technicians may perform: `Assigned`/`Scheduled` → `In Progress`; `In Progress` ⇄ `Awaiting Parts`; `In Progress` → `Completed`. Cancellation and rescheduling remain Staff/Admin-only.
10. **Reopen after Completed:** `Completed` is **strictly terminal** in V1. Rework/returns are a future enhancement.
11. **Customer job cancellation:** **Staff/Admin-only** after a job is created. Customers cannot cancel a job; they may request cancellation through communication channels.
12. **Retention:** Completed/cancelled job records and attachments are **retained indefinitely** in V1 and covered by the PRD's daily-backup policy. A formal retention/purge policy is a future governance decision.

*Future enhancements (deferred):* physical handover/pickup status, multi-technician jobs, automated double-booking warnings, structured parts management, invoicing, completion photo sharing with customers, rework/reopen flow, formal retention policy.
