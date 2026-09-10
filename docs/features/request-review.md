# Request Review - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Request Review feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Those artifacts are to be derived from this specification in later phases. Source requirements: PRD §6 (Requests & Jobs screen), §8 Feature 2 (Request Review), §9 (Repair Statuses), the **Requests & Jobs** and **Dashboard** wireframes, and the upstream [Repair Request specification](repair-request.md).

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Request Review |
| **Feature ID** | FEAT-RV-002 |
| **Source Requirement** | PRD §8 Feature 2; PRD §6 "Requests & Jobs"; PRD §7 User Flow (Staff Review → Approved/Rejected → Job Created); Requests & Jobs wireframe; Dashboard wireframe ("approve/reject, assign technicians"); Shop Settings ("approval settings"). |
| **Purpose** | Provide a quality-control and approval stage where internal staff evaluate submitted repair requests before they become active repair jobs. Staff review details and attachments, validate information, then approve, reject, or request additional information, and convert approved requests into jobs. |
| **Business Value** | Prevents invalid or incomplete requests from becoming jobs, protects technician utilization and scheduling accuracy, improves data quality, and provides an auditable decision point. Supports PRD success metrics (reduced processing time, reduced scheduling conflicts, improved technician utilization). |
| **Related Features** | Repair Request (FEAT-RR-001, upstream), Job Management (PRD Feature 3, downstream), Technician Assignment (Feature 4), Scheduling (Feature 5), Customer Management (Feature 6), Product Management (Feature 7), Settings Management (Feature 8, incl. "approval settings"), Authentication & Registration, Notifications, Repair Status Tracking. |
| **Dependencies** | A. Submitted repair requests exist (FEAT-RR-001). B. Authentication & RBAC (Staff/Administrator roles). C. Settings — "approval settings", working hours, slot durations (Feature 8). D. Notification service (in-app for V1). E. Attachment retrieval (storage deferred to architecture). F. Job creation capability (downstream) for the "convert to job" outcome. |

**Scope boundary:** This feature owns the lifecycle from `Pending Review` through `Approved`/`Rejected`/`Information Required` and the *trigger* to create a job on approval. The actual job execution, technician assignment, and scheduling are downstream features and are referenced only for continuity. The wireframe shows assignment/scheduling on adjacent tabs (Jobs, Schedule); those are out of scope here.

---

# User Roles

## Staff Member

* **Responsibilities**
  * Monitor the review queue and open submitted requests promptly.
  * Validate completeness and plausibility of customer-, device-, issue-, and appointment information.
  * Review attachments.
  * Make a decision: approve, reject, or request additional information.
  * Record internal notes and (if enabled) set priority.
  * Convert approved requests into jobs.
* **Permissions**
  * View all repair requests and their attachments.
  * Transition `Pending Review` → `Approved`, `Rejected`, or `Information Required`.
  * Resume review of `Information Required` requests after customer update.
  * Add internal notes; set/adjust priority (if enabled).
  * Trigger job creation on approval.
* **Restrictions**
  * Cannot manage users, products, or global settings (Administrator scope).
  * Should not silently alter customer-entered factual content without an auditable note/correction trail.
  * Cannot delete requests (only transition statuses; deletion, if any, is an Administrator/policy concern).
  * Cannot bypass mandatory reason capture on rejection.

## Administrator

* **Responsibilities**
  * All Staff review responsibilities, plus configuration of "approval settings", device catalog, and business settings that influence review.
  * Oversee audit integrity and access policy; handle escalations and exceptions.
* **Permissions**
  * All Staff permissions.
  * Configure approval-related settings (e.g., whether priority is enabled, rejection reason list, SLA targets — see Open Questions).
  * View full audit history and reporting.
  * Override/correct decisions per policy (with audit trail).
* **Restrictions**
  * Configuration changes must not corrupt or retroactively invalidate in-flight requests.

## Technician

* **Responsibilities**
  * None within the review decision itself; technicians are consumers of approved work.
* **Permissions**
  * Read-only access to request context (device, issue, attachments) for requests/jobs assigned to them after approval.
  * *Assumption (documented):* Technicians may be permitted to view the review queue read-only for planning. Default in V1: no queue access unless granted. Flagged in Open Questions.
* **Restrictions**
  * Cannot approve, reject, request information, set priority, or add review/internal notes (unless policy explicitly grants note-adding on assigned jobs).

## Customer

* **Responsibilities**
  * Respond to "request additional information" prompts by updating their request.
* **Permissions**
  * View the *status* of their own request (e.g., Pending Review, Information Required, Approved, Rejected) and any customer-facing reason via Repair Status Tracking.
  * Provide additional information when asked.
* **Restrictions**
  * Cannot view the review queue, internal notes, other customers' requests, or staff identities (unless explicitly surfaced by policy).
  * Cannot self-approve or change review status.

---

# Business Objectives

The review stage exists to:

* **Improve request quality** — catch incomplete, ambiguous, or implausible submissions before they consume operational capacity.
* **Reduce invalid jobs** — ensure only legitimate, actionable requests become jobs, reducing wasted technician time and rework.
* **Ensure scheduling accuracy** — validate device/issue/appointment data so downstream scheduling and assignment are based on trustworthy information.
* **Improve technician utilization** — by filtering and prioritizing work, technicians focus on real, well-specified repairs.
* **Create an accountable decision point** — every approve/reject/info-request is attributable and auditable, supporting dispute resolution and process improvement.
* **Enable triage/prioritization** — surface urgent work and manage the queue against business SLAs.
* **Provide operational visibility** — the queue counters (Pending/Approved/Active Jobs in the wireframe) give staff and management at-a-glance workload insight.

---

# User Stories

### Staff Member

1. **Triage the review queue**
   * *Goal:* See all requests awaiting review, most relevant first.
   * *Motivation:* Work efficiently and meet response targets.
   * *Expected outcome:* A filterable, sortable queue of `Pending Review` (and `Information Required`) requests with key summary info.

2. **Review a request in detail**
   * *Goal:* Open a request to inspect customer, device, issue, appointment data, and attachments.
   * *Motivation:* Make an informed decision.
   * *Expected outcome:* A complete read view; attachments can be opened/zoomed; a decision can be recorded.

3. **Approve a valid request**
   * *Goal:* Approve and move the request toward becoming a job.
   * *Motivation:* The request is complete and legitimate.
   * *Expected outcome:* Status becomes `Approved`; a job is created (or made ready for assignment); customer is notified in-app.

4. **Reject an invalid request with a reason**
   * *Goal:* Reject a request that cannot proceed.
   * *Motivation:* Duplicate, unsupported device, spam, or non-repairable issue.
   * *Expected outcome:* Status becomes `Rejected`; a mandatory reason is recorded; customer is notified with an appropriate message.

5. **Request additional information**
   * *Goal:* Ask the customer to clarify or complete the request.
   * *Motivation:* The submission is missing details needed to decide.
   * *Expected outcome:* Status becomes `Information Required`; customer is notified and prompted; review resumes after they respond.

6. **Add internal notes**
   * *Goal:* Record review observations not visible to the customer.
   * *Motivation:* Coordinate with colleagues and preserve rationale.
   * *Expected outcome:* Time-stamped, attributed notes attached to the request, visible to internal roles only.

7. **Set priority**
   * *Goal:* Flag urgency on a request.
   * *Motivation:* Ensure urgent repairs are handled first.
   * *Expected outcome:* Priority recorded and reflected in queue sorting/filtering (if enabled).

### Administrator

8. **Configure approval behavior**
   * *Goal:* Adjust approval settings (e.g., rejection reasons, priority enablement, SLA targets).
   * *Motivation:* Match the business's operating policy.
   * *Expected outcome:* Review behavior reflects configuration without code changes.

9. **Audit a decision**
   * *Goal:* See who approved/rejected a request and why.
   * *Motivation:* Resolve disputes and improve process.
   * *Expected outcome:* A complete, attributable audit trail.

### Technician

10. **Read approved request context**
    * *Goal:* Understand the issue before working.
    * *Motivation:* Prepare correctly.
    * *Expected outcome:* Read-only access to issue details and attachments for assigned work.

### Customer

11. **Understand my request's review outcome**
    * *Goal:* Know whether my request was approved, rejected, or needs more info.
    * *Motivation:* Decide my next step.
    * *Expected outcome:* Clear in-app status and any customer-facing message/reason.

---

# Screen Overview

The review experience is anchored on the **Requests & Jobs** screen (wireframe), specifically its **Requests** tab. The screen presents summary counters (e.g., Pending, Approved, Active Jobs) and tabs (Requests | Jobs | Schedule).

## 1. Request Review Screen (Requests queue)

* **Purpose:** Present the queue of requests awaiting/under review and allow staff to triage and open them.
* **Available actions**
  * View summary counters (Pending / Approved / Active Jobs).
  * Switch tabs (Requests | Jobs | Schedule).
  * Sort, filter, and search the queue (see Queue Management).
  * Open a request to its details view.
  * *(Wireframe shows an inline status control and a filter; inline quick-status change is a candidate — see Open Questions.)*
* **Navigation entry points**
  * Dashboard → "Requests & Jobs" card.
  * Direct deep link from an in-app notification ("new request submitted").
* **Navigation exit points**
  * Into a request's details view.
  * To the Jobs tab (after approval/conversion) or Schedule tab.
  * Back to Dashboard.

## 2. Request Details View

* **Purpose:** Show the full request for evaluation and host the decision actions.
* **Available actions**
  * Read all request information (see Request Information Display).
  * Open attachment review.
  * Approve / Reject / Request Additional Information.
  * Add internal note; set priority (if enabled).
  * View decision history / audit trail (read).
* **Navigation entry points**
  * From the Requests queue (selecting a request).
  * From a notification deep link.
* **Navigation exit points**
  * Back to the queue (with updated status reflected).
  * To the created job (on approval) where applicable.

## 3. Attachment Review

* **Purpose:** Let staff inspect supporting images submitted with the request.
* **Available actions**
  * View thumbnails; open full-size; zoom/pan; navigate between attachments.
  * See attachment metadata (type, upload time) as permitted.
* **Navigation entry points**
  * From the Request Details View.
* **Navigation exit points**
  * Back to Request Details View.
* **Notes:** Read-only for review. No editing/deletion of customer attachments by staff in V1 (corrections go through "request additional information").

## 4. Approval Workflow (screen behavior)

* **Purpose:** Capture an approve decision and initiate job creation.
* **Available actions**
  * Confirm approval; optionally set priority and add an internal note; optionally capture readiness data needed for job creation *(see Approval Workflow section / Open Questions)*.
* **Navigation entry points**
  * From Request Details View → Approve.
* **Navigation exit points**
  * On success → confirmation, status reflects `Approved`, and the created job becomes available (Jobs tab).

## 5. Rejection Workflow (screen behavior)

* **Purpose:** Capture a reject decision with a mandatory reason.
* **Available actions**
  * Select/enter a rejection reason (mandatory); optionally add an internal note; choose customer-facing message.
* **Navigation entry points**
  * From Request Details View → Reject.
* **Navigation exit points**
  * On success → confirmation, status reflects `Rejected`, customer notified.

---

# Request Information Display

All information below must be visible (read) to reviewing staff. Sensitive fields follow Security Requirements.

## Customer Information
* Name
* Phone / Mobile number
* Email (if captured)
* Contact details / preferred contact channel (if captured)
* Link to customer profile & repair history (Customer Management) — *recommended for context*

## Device Information
* Category / Type
* Brand
* Model
* Serial Number / IMEI (sensitive — masked/least-exposure per Security)
* Purchase Date (if provided)
* Warranty Status (customer-indicated; staff-confirmable)

## Issue Information
* Issue Description (full text)
* Issue Category / Symptom (if captured)
* Customer-indicated Severity/Urgency (if captured)
* Supporting attachments (images; thumbnails + full view)

## Appointment Information
* Requested (preferred) date
* Requested (preferred) time / slot
* Service mode (if captured)
* *Reminder:* this is a customer **preference**, not a confirmed booking.

## Request Metadata
* Request ID
* Reference Number (customer-facing)
* Submission Date/Time
* Current Status
* Priority (if set)
* Created-by (customer account)
* Last updated date/time and by whom

## Additional useful information (recommended)
* **Decision history / audit summary** (prior status changes, info requests, notes).
* **Duplicate indicators** — links to similar/related requests by the same customer or device.
* **Customer history snapshot** — count of prior requests, prior rejections, repeat-issue flag.
* **SLA/age indicator** — time in `Pending Review` (supports prioritization and reporting).
* **Internal notes thread** (internal-only).
* **Catalog status flag** — if the selected device category was archived after submission.

---

# Review Workflow

Complete review process with all paths:

1. **Request submitted** (upstream) — a request reaches `Pending Review` and appears in the queue; queue counters update; an in-app notification may alert staff.
2. **Request appears in review queue** — visible on the Requests tab, sortable/filterable.
3. **Staff opens request** — transitions to a "being reviewed" context; opening is auditable. *(Optional: soft-lock/claim to reduce concurrent review — see Open Questions.)*
4. **Staff reviews information** — reads all sections and attachments; may consult customer history and duplicate indicators; may add internal notes; may set priority.
5. **Decision made** — exactly one of:
   * **Approve** → status `Approved`; job creation triggered; customer notified. (See Approval Workflow.)
   * **Reject** → mandatory reason captured; status `Rejected`; customer notified. (See Rejection Workflow.)
   * **Request additional information** → status `Information Required`; customer notified/prompted. (See Additional Information Workflow.)
   * **Defer / leave pending** → no status change; request remains in queue (optionally re-prioritized).
6. **Post-decision** — queue counters and lists update; audit entries written; downstream features pick up `Approved` requests.

### Information-required sub-loop
`Information Required` → customer updates → request returns to `Pending Review` (or a "Resubmitted/Updated" indicator) → staff resumes review → decision.

```
Request Submitted
        ↓
Appears in Review Queue (Pending Review)
        ↓
Staff Opens Request
        ↓
Staff Reviews Information (+ notes / priority)
        ↓
        ├── Approve  → Approved → Job created → Customer notified
        ├── Reject   → (reason required) → Rejected → Customer notified
        ├── Request Info → Information Required → Customer notified
        │                         ↓
        │                 Customer updates request
        │                         ↓
        │                 Back to Pending Review (resume)
        └── Defer → remains Pending Review
```

---

# Review Actions

For each action: **Business rules**, **Preconditions**, **Postconditions**.

## Approve
* **Business rules:** Allowed only from `Pending Review` (or resumed-from-`Information Required`). Required information must be present and valid. May require priority if priority is mandatory (config). Approval triggers job creation (see assumptions).
* **Preconditions:** Request is in a reviewable status; reviewer has Staff/Admin role; mandatory data complete.
* **Postconditions:** Status `Approved`; job created or made ready; customer notified (in-app); audit entry written; queue counters updated.

## Reject
* **Business rules:** Allowed from `Pending Review`/`Information Required`. **Reason is mandatory** (from a configurable list and/or free text). Optionally distinguish customer-facing reason vs. internal reason.
* **Preconditions:** Reviewable status; Staff/Admin role; reason provided.
* **Postconditions:** Status `Rejected`; reason stored; customer notified; audit entry written. *Re-open policy:* whether a rejected request can be reopened/appealed is an Open Question.

## Request Additional Information
* **Business rules:** Allowed from `Pending Review`. Staff specify what is needed (structured prompt and/or message). Moves request to `Information Required`.
* **Preconditions:** Reviewable status; Staff/Admin role; an information request message/criteria provided.
* **Postconditions:** Status `Information Required`; customer notified/prompted; request awaits customer update; audit entry written.

## Add Internal Notes
* **Business rules:** Internal-only visibility; notes are append-only and attributed/time-stamped. Does not change status.
* **Preconditions:** Staff/Admin role; request exists.
* **Postconditions:** Note stored and visible to internal roles; audit entry written. (Edit/delete restrictions in Internal Notes section.)

## Assign Priority
* **Business rules:** Priority chosen from a defined set (see Priority Management). Enablement and whether it is mandatory are config-driven ("approval settings"). Changing priority does not change status.
* **Preconditions:** Staff/Admin role; priority feature enabled; valid priority value.
* **Postconditions:** Priority stored; queue ordering/filtering reflects it; audit entry written.

> **Note on "Convert approved request into a job" (V1 decision):** Job creation is **automatic on approval** (1:1) — there is no separate "Convert to Job" step. The new job appears on the Jobs tab as **Unassigned**, ready for downstream Technician Assignment.

---

# Approval Workflow

When a request is approved:

* **Status changes:** `Pending Review` → `Approved`.
* **Job creation (V1 decision):** Approval **automatically** creates one repair job (1:1) carrying over device, issue, attachments, customer, requested appointment preference, and priority as context. No separate confirmation step.
* **Assignment readiness:** The new job becomes available in the Jobs view as **Unassigned** (per wireframe's "Assign Technician → Unassigned"), ready for Technician Assignment (downstream Feature 4). Review does **not** assign technicians or confirm schedules.
* **Notification generation:** Customer receives an in-app notification that the request was approved and is being processed.
* **Counters:** "Pending" decreases, "Approved" (and subsequently "Active Jobs") increases per wireframe counters.
* **Audit:** Approval recorded with reviewer identity, timestamp, and any priority/notes.

**Documented assumptions:**
* The mapping from approved request → job is 1:1 in V1 (one request becomes one job). Splitting/merging is out of scope.
* "Approval settings" (Shop Settings) may govern behavior such as whether approval auto-creates a job, default priority, or required fields at approval. Exact settings TBD (Open Questions).
* Approval does not guarantee the customer's preferred slot; scheduling remains downstream.

---

# Rejection Workflow

When a request is rejected:

* **Reason required:** A rejection reason is mandatory. Recommended: a configurable reason list (e.g., Duplicate, Unsupported Device, Insufficient Information, Out of Service Area, Spam/Invalid, Non-repairable) plus optional free-text detail.
* **Customer-facing vs. internal:** Allow a customer-appropriate message distinct from internal notes; never expose internal-only commentary to customers.
* **Status changes:** `Pending Review`/`Information Required` → `Rejected`.
* **Customer notification:** Customer is notified in-app with the outcome and the customer-facing reason/message.
* **Counters/queue:** Request leaves the active review queue (Pending decreases).
* **Audit requirements:** Record reviewer identity, timestamp, reason code, internal/customer-facing text, and prior status.
* **Re-open / appeal (V1 decision):** A rejected request is **terminal**; the customer may submit a new request. No reversal/appeal flow in V1.

---

# Additional Information Workflow

When information is incomplete:

* **Status:** Request enters `Information Required`.
* **Specify needs:** Staff indicate what is missing (structured checklist and/or message). Recommended to make the ask explicit to reduce round-trips.
* **Customer notified:** In-app notification prompts the customer to update the request.
* **Customer responds:** Customer provides the requested details/attachments. *Assumption (documented):* while in `Information Required`, the customer regains a constrained edit capability limited to the requested fields (reconciles with the upstream "editable window" rule).
* **Resume review:** On customer update, the request returns to `Pending Review` (optionally flagged "Updated"), reappears in the queue, and staff resume the decision.
* **No-response handling (V1 decision):** **No automatic timeout** in V1. `Information Required` requests remain until the customer updates them or staff manually reject/cancel. (Auto-timeout/escalation is a future enhancement.)
* **Audit:** Record the information request (what/when/by whom) and the customer's response event.

---

# Priority Management

Requests should support prioritization to aid triage.

* **Recommended levels:** `Low`, `Normal` (default), `High`, `Urgent`.
* **Rationale:** Enables staff to surface time-sensitive repairs, supports SLA management, and improves technician utilization by ordering work meaningfully. The queue counters and sorting benefit from a priority dimension.
* **Who sets it:** Staff/Administrator during review; may be informed by the customer-indicated severity/urgency (advisory, not authoritative).
* **Default & enablement:** Default `Normal`. Whether priority is enabled and/or mandatory is config-driven via "approval settings". *Assumption (documented):* priority is enabled and optional in V1.
* **Effects:** Influences queue sort/filter; carried to the created job for downstream scheduling/assignment prioritization.
* **Open Questions:** Should customer-selected urgency map directly to priority? Are SLA targets attached to each level?

---

# Status Lifecycle

Review-related statuses (subset/extension of PRD §9). For each: **Description**, **Owner**, **Allowed transitions**.

| Status | Description | Owner | Allowed Transitions (within/adjacent to review) |
| --- | --- | --- | --- |
| `Pending Review` | Initial status at submission; awaiting/under staff evaluation; appears in queue. (Any "Received"/"Submitted" wording is a UI label for this state.) | Staff | → `Approved`, → `Rejected`, → `Information Required`, → `Cancelled`. |
| `Information Required` | Staff requested more info; awaiting customer. | Staff (set) / Customer (resolves) | → `Pending Review` (customer updates), → `Cancelled`. |
| `Approved` | Request validated and accepted; **a Job is created automatically (1:1)**. | Staff | → Job lifecycle (see Job Management spec). Review-stage terminal. |
| `Rejected` | Request declined with mandatory reason. | Staff | **Terminal in V1** (customer may submit a new request). |
| `Cancelled` | Withdrawn by customer while `Pending Review`/`Information Required`. | Customer | Terminal. |

**State transition model (review scope):**

```
Submitted ──auto──▶ Pending Review
                     │  ├─▶ Approved ──▶ (Job created → downstream)
                     │  ├─▶ Rejected (reason required) ─▶ [terminal]
                     │  ├─▶ Information Required ──customer update──▶ Pending Review
                     │  └─▶ Cancelled [terminal]
Information Required ─┴─(timeout/policy)─▶ Rejected/Cancelled
```

**Wireframe reconciliation (resolved):** The "Received" value shown on the wireframe is treated as a **UI label for `Pending Review`** (requests) and `Created` (jobs); it is **not** a separate canonical status. The canonical request status set is the table above.

**Future considerations (deferred):** `On Hold`, `Escalated`, an explicit `Updated/Resubmitted` indicator, and an `Appealed`/re-open path for rejected requests.

---

# Queue Management

How staff view and manage requests (Requests tab):

* **Default view:** Open items requiring action — `Pending Review` first, with `Information Required` (updated) surfaced for resumption.
* **Summary counters:** Pending / Approved / Active Jobs (per wireframe) for at-a-glance workload.
* **Sort order (recommended):**
  * Default by **oldest pending first** (FIFO / age) to protect SLA, with **priority** as an override (Urgent/High rise to top).
  * Allow sort by submission date, priority, status, device category.
* **Filters (recommended):**
  * **Status** (Pending Review, Information Required, Approved, Rejected, Cancelled).
  * **Priority** (Low/Normal/High/Urgent).
  * **Date** (submission date range; age buckets).
  * **Device category / brand.**
  * **Customer** (and repeat-customer flag).
  * *(Wireframe shows "Filter by Technician" on the Jobs tab; that filter is assignment-oriented and belongs to Jobs, not the review queue.)*
* **Search (recommended):** By Request ID/Reference, customer name, phone, or device.
* **Pagination/performance:** Queue must scale to PRD's 10,000+ records with responsive search (<2s) and load (<3s); use paging/lazy loading (implementation detail deferred).
* **Recommendations:** Show an **age/SLA indicator** per item; visually distinguish `Information Required` items awaiting staff vs. awaiting customer; badge updated/resubmitted requests.

---

# Internal Notes

* **Who can add:** Staff and Administrators. Technicians cannot add review notes (note-adding on assigned jobs is a separate downstream concern).
* **Visibility rules:** Internal-only — never shown to customers. Distinct from any customer-facing rejection/info message.
* **Edit permissions:** Notes are **append-only** by default; recommended that authors cannot silently edit/delete to preserve integrity. If editing is allowed, prior versions must be retained and audited. *Assumption (documented):* append-only in V1.
* **Attribution:** Each note records author identity and timestamp.
* **Audit requirements:** Note creation (and any edit/delete, if permitted) is auditable. Notes must not be used to store data that belongs in structured fields (e.g., decisions/reasons).

---

# Notifications

**Version 1 — in-app notifications.**

| Triggering event | Recipient | Channel (V1) |
| --- | --- | --- |
| New request submitted (enters queue) | Staff/Admin | In-app (queue badge/counter, optional alert) |
| Request approved | Customer | In-app |
| Request rejected (with customer-facing reason) | Customer | In-app |
| Additional information requested | Customer | In-app (prompt to update) |
| Customer provided info (resubmitted) | Staff/Admin | In-app (queue update/badge) |
| Priority set/changed to High/Urgent | Staff/Admin (optional) | In-app |

**Future versions** (must integrate without redesign): **Push** (Phase 2), **SMS** (Phase 2; uses registered mobile), **Email** (requires email capture), **WhatsApp** (opt-in + provider).

*Assumptions:* notification preferences/opt-in/consent are future concerns; V1 relies on in-app. Internal staff alerts should avoid notification fatigue (batch/badge rather than per-event spam) — exact policy TBD.

---

# Validation Rules

* **Approval requires complete, valid mandatory information** — required request fields present and valid; selected device category still valid (not archived); if priority is mandatory (config), it must be set.
* **Rejection requires a reason** — a valid reason code (and/or free text) is mandatory; empty reason blocks the action.
* **Information request requires a specified need** — staff must indicate what information is missing (message and/or checklist); empty request blocked.
* **Priority requires a valid value** — must be one of the defined levels; invalid/blank rejected when priority is being set.
* **Status transition validity** — only allowed transitions from the current status are permitted (see Status Lifecycle); illegal transitions are blocked.
* **Role authorization** — only Staff/Admin may perform review actions; enforced server-side, not just UI.
* **Decision exclusivity** — exactly one terminal decision per review cycle; cannot approve and reject simultaneously.
* **Stale-state guard** — action is validated against the request's *current* status at execution time to prevent acting on out-of-date data (see Concurrency in Error Handling).
* **Notes content** — internal notes must be non-empty when added; length-limited.
* **Customer-facing text safety** — outbound messages must not contain internal-only data.

---

# Error Handling

For each: **User experience** and **Recovery strategy**.

| Failure | User Experience | Recovery Strategy |
| --- | --- | --- |
| **Network failure** (loading queue/details or saving decision) | Clear non-blocking error; no false success; in-progress decision data retained in the UI. | Offer retry; preserve entered reason/notes; confirm final state on reconnect before re-submitting. |
| **Concurrency conflict** (two staff act on the same request, or status changed since opened) | Inform that the request was already updated by someone else; show the current status. | Reload latest state; require the reviewer to re-confirm against current data; never silently overwrite. Recommend soft-lock/claim to reduce conflicts (Open Question). |
| **Invalid state transition** (e.g., approving an already-rejected/cancelled request) | Explain the action is not allowed in the current status. | Refresh to current status; offer only valid actions. |
| **Attachment access failure** (cannot load/preview an image) | Indicate the attachment couldn't be loaded; other content still usable. | Offer retry; allow decision to proceed if attachment is non-blocking, or block if review policy requires viewing it. |
| **Save failure** (decision/notes/priority not persisted) | No false confirmation; show error with correlation id if available; data retained. | Retry safely (idempotent intent so a decision isn't double-applied); escalate/log for support. |
| **Job-creation failure on approval** | Inform that approval could not complete job creation; status not falsely shown as Approved. | Roll back to a consistent state (request not left half-approved); allow retry; alert support. *(Consistency requirement; mechanism is architecture.)* |
| **Permission denied** | Inform that the user lacks permission for the action. | Hide/disable unauthorized actions; log attempt. |
| **Session expired/invalid** | Prompt re-login. | Route to login; return to the request; preserve unsaved decision data where feasible. |

**Cross-cutting:** Never show a decision as applied unless persisted. Error messages must not expose sensitive/system internals (Constitution).

---

# Reporting Requirements

Data captured by this feature must enable at least:

* **Requests approved** (count, by period).
* **Requests rejected** (count, by period) and **rejection reasons distribution**.
* **Approval rate** and **rejection rate** (of reviewed requests).
* **Information-request rate** (share needing more info) and **round-trip count** per request.
* **Average review time** (submission → first review; submission → decision) and **time-in-queue** distribution.
* **SLA adherence** (share decided within target, if SLAs defined).
* **Throughput** — requests reviewed per staff member / per day (workload balancing).
* **Backlog** — current Pending / Information Required counts and aging buckets.
* **Priority distribution** of reviewed requests.
* **Conversion rate** — approved requests that became jobs / completed.
* **Reviewer activity** — decisions by reviewer (for management; respect privacy/HR policy).

*Note:* defines data-capture obligations; report visualization is out of scope.

---

# Audit Requirements

Auditable events (who, what, when; before/after where applicable):

* **Request opened for review** (reviewer, timestamp) — supports "request opened".
* **Status changed** (from → to, reviewer, timestamp).
* **Request approved** (reviewer, timestamp, priority/notes at approval, resulting job reference).
* **Request rejected** (reviewer, timestamp, reason code, internal + customer-facing text).
* **Additional information requested** (reviewer, what was requested, timestamp) and **customer response received**.
* **Priority changed** (old → new, by whom, timestamp).
* **Internal note added** (author, timestamp; edits/deletes if ever permitted).
* **Notification sent** (event, recipient, channel, timestamp).
* **Permission-denied attempts** on review actions (security signal).

**Retention expectations:** Audit records must be tamper-evident, attributable, and retained per policy (recommend aligning with overall data-retention policy; financial/operational disputes may require multi-year retention — TBD). Sensitive values must be protected within audit logs (Constitution: never expose sensitive info).

---

# Security Requirements

* **Access control / Authentication:** All review actions require an authenticated session (Authentication spec; JWT, non-expiring until logout/disable/delete). No anonymous access.
* **Role permissions (RBAC):**
  * Review actions (approve/reject/request-info/notes/priority) restricted to **Staff** and **Administrator**.
  * Approval-settings configuration restricted to **Administrator**.
  * **Customers** see only their own request status and customer-facing messages; never the queue, internal notes, reviewer identities, or other customers' data.
  * **Technicians** get read-only context for assigned work only (queue access off by default).
* **Data visibility:**
  * Sensitive fields (Serial/IMEI, full contact details) shown only to authorized internal roles, least-exposure/masked where appropriate.
  * Internal notes and internal rejection rationale are never exposed to customers.
  * Attachment retrieval restricted to authorized roles; no publicly guessable access.
* **Audit requirements:** All decisions and sensitive-field access (where policy requires) are logged and attributable; logs protected from tampering and from leaking sensitive data.
* **Challenged assumptions:**
  * **Single-reviewer risk:** Nothing currently prevents the same staff who could be biased from both reviewing and rejecting without oversight. Consider whether high-impact actions (e.g., bulk reject) need a second check or are Admin-only. *Decision needed.*
  * **No segregation between review and assignment in UI:** The wireframe blends Requests/Jobs/Schedule; ensure RBAC still cleanly separates *who can approve* from *who can assign/schedule* even if screens are adjacent.
  * **"Received" status** in the wireframe is undefined in the PRD — using an unmodeled status is a data-integrity risk; reconcile before build.
  * **Technician queue visibility** default (off) should be confirmed against operational needs.

---

# Acceptance Criteria

Given/When/Then. Covers approval, rejection, information-request, error, and permission scenarios.

### Approval scenarios

**AC-1 — Approve a complete request**
* **Given** a Staff member viewing a `Pending Review` request with complete, valid information
* **When** they approve it
* **Then** the status becomes `Approved`, a job is created and appears as Unassigned in Jobs, the customer is notified in-app, and an audit entry is recorded.

**AC-2 — Approval updates counters**
* **Given** the Requests & Jobs screen with a Pending count of N
* **When** a request is approved
* **Then** Pending decreases by one and Approved/Active Jobs reflect the new job.

**AC-3 — Approve with priority**
* **Given** priority is enabled and a Staff member sets `High` while approving
* **When** the approval completes
* **Then** the created job carries `High` priority and the queue/job ordering reflects it.

### Rejection scenarios

**AC-4 — Reject requires a reason**
* **Given** a Staff member choosing to reject a request
* **When** they attempt to confirm without selecting/entering a reason
* **Then** the action is blocked until a valid reason is provided.

**AC-5 — Reject with reason notifies customer**
* **Given** a Staff member rejecting with a valid reason and customer-facing message
* **When** they confirm
* **Then** the status becomes `Rejected`, the customer is notified in-app with the customer-facing reason only (no internal notes), and the decision is audited.

### Information request scenarios

**AC-6 — Request additional information**
* **Given** a `Pending Review` request lacking detail
* **When** a Staff member requests specific additional information
* **Then** the status becomes `Information Required`, the customer is notified and prompted, and the request awaits the customer's update.

**AC-7 — Resume after customer update**
* **Given** an `Information Required` request
* **When** the customer provides the requested information
* **Then** the request returns to `Pending Review`, is flagged as updated in the queue, and staff can resume the decision.

### Error scenarios

**AC-8 — Concurrency conflict**
* **Given** two Staff members open the same `Pending Review` request
* **When** one approves and the other then attempts to reject
* **Then** the second action is blocked with a message that the request was already decided, and the current status is shown without overwrite.

**AC-9 — Invalid transition**
* **Given** a request already `Rejected`
* **When** a Staff member attempts to approve it
* **Then** the action is rejected with an explanation and only valid actions are offered.

**AC-10 — Save/network failure on decision**
* **Given** a Staff member confirming a decision when the save fails
* **When** the failure occurs
* **Then** no false success is shown, the entered reason/notes are preserved, and a safe retry is offered that does not double-apply the decision.

**AC-11 — Attachment load failure**
* **Given** a request whose attachment cannot be loaded
* **When** the Staff member reviews it
* **Then** they are informed, other content remains usable, and (per policy) they may retry or the decision is blocked until the attachment is viewed.

**AC-12 — Job creation failure on approval**
* **Given** approval is confirmed but job creation fails
* **When** the failure occurs
* **Then** the request is not left in an inconsistent "approved-without-job" state, the error is surfaced, and the action can be safely retried.

### Permission scenarios

**AC-13 — Customer cannot access the queue**
* **Given** an authenticated Customer
* **When** they attempt to access the review queue or another customer's request
* **Then** access is denied and no internal data is exposed.

**AC-14 — Technician cannot decide**
* **Given** an authenticated Technician
* **When** they open an assigned request's context
* **Then** they have read-only access and no approve/reject/request-info/priority/notes actions are available.

**AC-15 — Only Admin configures approval settings**
* **Given** a Staff member (non-admin)
* **When** they attempt to change approval settings
* **Then** the action is denied; only Administrators may configure them.

**AC-16 — Internal notes hidden from customers**
* **Given** a request with internal notes
* **When** the customer views their request status
* **Then** internal notes are never displayed; only customer-facing messages appear.

---

# V1 Decisions (Resolved)

The following decisions resolve the previously open questions, optimized for a simple first version that can serve multiple customers and be enhanced later.

**Status & lifecycle**
1. **"Received" status:** A UI label for `Pending Review` (requests) / `Created` (jobs); not a separate canonical status.
2. **Initial status:** `Pending Review` (no separate `Submitted` state).
3. **Rejection:** **Terminal** in V1; the customer may submit a new request (no re-open/appeal).
4. **Information Required edit scope:** The customer may edit **any field** while `Information Required`; on resubmission the request returns to `Pending Review`.
5. **Stale Information Required:** **No auto-timeout** in V1; staff manually reject/cancel if needed.

**Decision behavior**
6. **Approve → Job:** **Automatic, 1:1** on approval; no explicit "Convert to Job" step.
7. **Approval settings:** Minimal in V1 — Settings covers **working hours** and **slot duration** only. **No auto-approval**, no approval thresholds, no SLA configuration.
8. **Rejection reasons:** A **mandatory reason is required** — a short predefined list (Duplicate, Unsupported Device, Insufficient Information, Spam/Invalid, Non-repairable) plus **optional free-text**. A customer-facing message is shown to the customer; internal notes are never exposed.
9. **Priority:** **Enabled and optional**, default **`Normal`**, set by staff. No customer-driven urgency capture and no SLA targets in V1.

**Queue & operations**
10. **Concurrency:** **Optimistic concurrency** (detect conflicts on save); **no record locking/claim** in V1.
11. **Inline status change:** Review decisions are made in the **details view**. The queue is for triage/open only (no inline approve/reject).
12. **Bulk actions:** **Not in V1** (decisions are one request at a time).
13. **Queue model:** **Shared pool** — any Staff/Admin may review any request; no per-reviewer assignment or workload balancing.
14. **Technician queue visibility:** **Off.** Technicians see only their assigned jobs (read-only context).

**Roles, security & governance**
15. **Reviewer oversight:** **No four-eyes/second-approver** in V1; any Staff/Admin may approve or reject.
16. **Self-review conflict:** Allowed for staff-assisted intake. A Staff/Admin who created the request on behalf of a customer **may** approve it (they are not the customer). See [staff-assisted-intake.md](staff-assisted-intake.md).
17. **Notifications:** Staff are alerted via the **queue badge/counter** (no per-event spam); customers are notified in-app on status change with a courteous, reason-bearing message on rejection.

**Reporting & retention**
18. **SLAs:** **None defined** in V1; timestamps are captured to enable future SLA reporting.
19. **Audit retention:** Audit records are **retained for the life of the record** and covered by the PRD's daily backups; no special regulatory retention assumed in V1.
20. **Reviewer-level reporting:** Per-staff decision counts are available to **Administrators only**.

*Future enhancements (deferred):* re-open/appeal, info-required auto-timeout, configurable approval settings/SLAs, bulk actions, per-reviewer assignment, record locking, multi-channel notifications.
