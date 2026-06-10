# Requirements Gap Analysis

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Gap Analysis (implementation-independent)
**Version:** 1.0
**Date:** June 2026
**Status:** Final for Architecture Phase

> This document reviews all current specifications against each other and against the PRD, identifies every gap, contradiction, and missing requirement, and makes a practical V1 decision for each item. It does not generate code, APIs, or architecture. Its purpose is to give the development team a single clean checklist before architecture begins.

---

# Executive Summary

The RepairDesk V1 specification set is in strong shape. The five core feature specs (Authentication, Repair Request, Request Review, Job Management, Scheduling) are thorough, internally consistent after the recent rounds of revision, and carry explicit V1 decisions. The status model is clean and agreed. The permissions model is coherent.

There are **no showstopper contradictions** in the existing specs. The gaps are of two kinds:

1. **Missing feature specs (6 features)** — Settings, Product Management, Customer Management, Staff Management, Repair Status Tracking, and the Dashboard/Customer Home screens are each referenced as a dependency by the existing specs but have no spec document of their own. These are the primary blocker for architecture.
2. **Cross-cutting decisions still needed (15 items)** — notification inbox model, default admin credentials, password strength, file storage intent, customer-facing status labels, and a handful of small internal consistencies.

Both categories are resolved in this document with simple, practical V1 decisions.

**Requirements maturity score: 7 / 10**

The scored features (auth, repair-request, request-review, jobs, scheduling) rate 9/10 individually. The overall product scores 7/10 because of the six unspecified support features. Once those are addressed (or their decisions captured here), the set will be ready for architecture.

**Readiness for architecture phase: Mostly Ready.**

A single focused session to capture specs (or decisions) for the six missing features unblocks architecture completely. That work is estimated at roughly 1–2 days of spec writing at the same level of detail as the existing features. This document provides the V1 decisions to seed all six.

---

# Critical Gaps

Issues that must be resolved before architecture begins.

## GAP-01 — Six Feature Specifications Missing

**Description:** The following PRD features are referenced as dependencies in multiple existing specs but have no specification document:

| Feature | Referenced by | Key dependency |
| --- | --- | --- |
| Settings Management (Feature 8) | Scheduling, Repair Request, Request Review | Working hours, slot duration, default values |
| Product Management (Feature 7) | Repair Request, Request Review, Jobs | Device category catalog drives the request form |
| Customer Management (Feature 6) | Request Review, Jobs, Scheduling | Customer profile, repair history, contact data |
| Staff Management | Scheduling, Jobs | Active technician list for assignment |
| Repair Status Tracking (Customer screen) | All job/request features | Customer-facing view of their requests and jobs |
| Dashboard / Customer Home | All features | Post-login landing screens |

**Impact:** Architecture cannot be completed for these features, and the specs that depend on them have unresolved implementation details (e.g., "device list loaded from catalog" — how exactly?). This is the single largest gap.

**V1 decision:** Create lightweight specifications for all six, or capture the V1 decisions below and proceed to architecture. The decisions below are sufficient to unblock architecture; full specs can be written in parallel with architecture.

### Settings Management (V1 scope)
* **Working hours:** per day of week (Mon–Sun), configurable open/close time, or mark as closed. Default: Mon–Sat, 09:00–18:00.
* **Slot duration:** single global value in minutes. Default: 60 minutes. Options: 30 / 60 / 90 / 120.
* **Business information:** name, address, phone, email (for display/reference only; not used for system notifications in V1).
* **Notification settings:** in-app only in V1; no configuration needed. Reserved field for V2.
* **Product/category management is a separate screen** (not part of Settings).
* **Administrator-only access** to all settings.

### Product Management (V1 scope)
* **Entities:** Device Categories only in V1 (Mobile Phone, Laptop, Tablet, Camera, and any others the admin adds). No separate brand/model managed lists in V1.
* **Operations:** Add category (name + optional description), edit name/description, archive (soft-delete). Archived categories are hidden from the request form but existing requests retain their category value.
* **Administrator-only access.**
* **No per-category repair eligibility rules in V1** (all active categories are eligible).

### Customer Management (V1 scope)
* **Read-only for staff in V1.** Staff can search customers, view profile, and view repair history. Staff cannot edit customer accounts (customers edit their own profile; admins manage accounts via Staff Management).
* **Customer profile shows:** username, mobile, account status, date registered, list of their requests and jobs with status.
* **Staff/Admin access only** (not customers viewing other customers).

### Staff Management (V1 scope)
* **Entities:** User accounts with roles Technician, Staff Member, Administrator.
* **Operations:** Create user (username, mobile, password, role), edit details (name, mobile, role), disable/enable, delete.
* **Administrator-only access** to create/edit/disable/delete.
* **Technician list** (for scheduling assignment) = all active users with Technician role.

### Repair Status Tracking / Customer Home (V1 scope)
* **Customer home screen** shows a **list of their repair requests/jobs** ordered by most-recently-updated. Each item shows: reference number, device category, current status (customer-friendly label), and date submitted.
* **Tapping an item** opens a detail view showing: full status (mapped to a customer-friendly label), appointment date/time (once scheduled), assigned technician name (once assigned/scheduled), device info, issue description, and an in-app notification history for that record.
* **No separate "Repair Status Tracking" screen** — it is the customer's home screen + the detail view for a selected request/job. One screen, not two.
* **Empty state:** "No repair requests yet — tap + to submit one."
* Customer-friendly status label mapping:
  * `Pending Review` → "Under Review"
  * `Information Required` → "Information Needed"
  * `Approved` → "Accepted"
  * `Rejected` → "Unable to Process"
  * `Cancelled` → "Cancelled"
  * `Created` / `Assigned` → "Preparing"
  * `Scheduled` → "Appointment Confirmed"
  * `In Progress` → "Repair in Progress"
  * `Awaiting Parts` → "Awaiting Parts"
  * `Completed` → "Completed"
  * `Cancelled` (job) → "Cancelled"

### Dashboard (V1 scope — internal roles)
* **Customer:** no separate dashboard; the Customer Home (above) IS the landing screen.
* **Technician:** lands on their **personal schedule view** (today's appointments). From there, navigation to their job list.
* **Staff:** lands on the **Requests & Jobs** screen (per wireframe). The dashboard wireframe serves as the navigation hub when staff navigate back to it.
* **Admin:** same as Staff; also has access to Settings and Staff Management tiles from the Dashboard (as shown in the wireframe).
* The Dashboard is a **navigation screen**, not a data screen; it requires no complex spec.

---

## GAP-02 — In-App Notification Mechanism Undefined

**Description:** Every feature (authentication excepted) sends "in-app notifications" but the delivery model is never defined. Is it a toast/snackbar (transient)? A notification bell with inbox? Both? What happens if the user is offline? Do notifications persist?

**Impact:** The notification model determines a non-trivial amount of backend and Flutter infrastructure. Without a decision, the features cannot be built consistently.

**V1 decision:**
* **Two-layer model:**
  1. **Queue badge/counter** for internal users (staff, technicians): the Requests & Jobs screen shows live counts (Pending, Active Jobs). No separate inbox.
  2. **Notification inbox** for customers: a bell icon on the Customer Home shows the last 30 notifications (status changes, appointment confirmations). Tapping a notification navigates to the relevant request/job. Notifications are persisted server-side.
* **Delivery:** Polling-based (client polls for unread notifications every 30–60 seconds in foreground) in V1. Real-time push is Phase 2.
* **Toast/snackbar:** Used for action confirmations only (e.g., "Appointment saved", "Request approved") — not persistent, not counted.
* **Offline:** Notifications are stored server-side and delivered on next foreground poll.
* **Internal staff notifications** (new request in queue, awaiting parts flag) are reflected via **badge counters on the Requests & Jobs screen**, not individual notifications — no notification inbox for staff in V1.

---

## GAP-03 — Default Administrator Credentials Undefined

**Description:** The Authentication spec states a default admin account is seeded on first deployment and credentials are "handed to the app holder." The actual credentials (username and initial password) are never defined.

**Impact:** First deployment will fail if the developer doesn't know what to configure. Handover to ACE Telecom is blocked.

**V1 decision:**
* Default username: **`admin`**
* Default password: **`Admin@1234`** (meets minimum strength; must be changed on first login)
* The seed script must check for the existence of the `admin` account before creating it to prevent duplicates on restart.
* Document these credentials in a separate handover note for ACE Telecom; do not embed them in user-facing UI.

---

## GAP-04 — Password Strength Requirement Undefined

**Description:** Authentication spec says passwords "should meet a minimum length/strength requirement" but never defines what that means.

**Impact:** Architecture and UI cannot implement the password field consistently; validation rules cannot be written.

**V1 decision:** Minimum **8 characters**. No additional complexity requirement (no forced uppercase/number/symbol) in V1 — this keeps registration friction low for a mobile app. A clear message ("Password must be at least 8 characters") is shown on failure.

---

## GAP-05 — Stale Status References in repair-request.md

**Description:** The Status Lifecycle section in `repair-request.md` still lists `Ready For Pickup` and `Closed` in the downstream job statuses note (line 406). These statuses were removed in a later update to `jobs.md`.

**Impact:** A developer reading repair-request.md in isolation would see incorrect downstream statuses.

**V1 decision:** Update the Status Lifecycle section in repair-request.md to remove `Ready For Pickup` and `Closed` from the downstream job status chain reference.

---

## GAP-06 — PRD Lists "Attachments" as Required; Spec Marks Them Optional

**Description:** PRD §8 Feature 1 lists "Attachments" in the Required Fields list. The `repair-request.md` Form Definition correctly marks attachments as Optional (with business justification). This is a direct contradiction.

**Impact:** Developers reading the PRD may implement attachment as mandatory; those reading the spec will make it optional. Inconsistent behavior.

**V1 decision:** Attachments are **Optional**. The PRD Required Fields list was drafted before the detailed spec and is incorrect on this point. Update PRD §8 Feature 1 to remove Attachments from Required Fields and note them as optional.

---

# Feature Consistency Review

## Contradictions

### CON-01 — Scheduling allows scheduling a `Created` (unassigned) job; but also requires a technician at scheduling time

`scheduling.md` states "The associated job must be in `Created` or `Assigned` status" for appointment creation. It also states "A technician must be assigned at the point of scheduling." If a job is `Created` (unassigned) and staff schedule it, the technician is assigned as part of that scheduling action — this is actually fine, but the spec is ambiguous about whether `Created` jobs may be scheduled (they can, if a technician is also selected).

**V1 decision:** Clarify: staff may schedule a `Created` job in a single action that simultaneously assigns a technician and sets the date/time. The job then moves directly to `Scheduled` (bypassing a separate `Assigned` step). Both sequences (`Created → Scheduled` in one step and `Created → Assigned → Scheduled` in two) are valid.

### CON-02 — `In Progress` job rescheduling is in scheduling.md but not in jobs.md

`scheduling.md` states a job `In Progress` may be rescheduled and "the job returns to `Scheduled`." The `jobs.md` status table does not list `In Progress → Scheduled` as an allowed transition.

**V1 decision:** Add `In Progress → Scheduled` (reschedule/reassign) as an allowed transition in `jobs.md`. This is a valid operational scenario and the scheduling spec correctly identified it.

### CON-03 — Duplicate notifications when job is cancelled

`jobs.md` sends a "Job cancelled → Customer" notification. `scheduling.md` sends an "Appointment cancelled → Customer" notification. When a Staff member cancels a job, the appointment is also cancelled (scheduling cancellation workflow), potentially generating two customer notifications.

**V1 decision:** When a **job** is cancelled, the system cancels any active appointment atomically (no separate appointment-cancellation notification). The customer receives **one** notification: "Your repair job [reference] has been cancelled." Appointment-specific notifications (scheduled, rescheduled, cancelled) are only sent for appointment-only operations where the job is not also being cancelled.

### CON-04 — `repair-request.md` Screen Overview references "Save as draft" as an open candidate

The Screen Overview section still says "Save as draft *(candidate — see Open Questions; not assumed for V1)*". Open Questions have been resolved; drafts are explicitly out of scope.

**V1 decision:** Update the wording to "Save as draft — **not in V1** (decided)."

## Duplicated Responsibilities

### DUP-01 — Technician Assignment is both Feature 4 and part of Features 3 and 5

PRD Feature 4 "Technician Assignment" has no spec document and no cross-link (unlike Features 1, 2, 3, 5). The functionality is fully covered within `jobs.md` and `scheduling.md`.

**V1 decision:** Add a note to PRD Feature 4 that its requirements are covered within the Job Management and Scheduling specifications, and add a cross-link. No separate spec document needed.

## Missing Dependencies

### DEP-01 — Issue Category / Symptom has no defined data source

`repair-request.md` defines "Issue Category / Symptom" as an optional selection field but never specifies where the list comes from. Is it managed in Product Management? Settings? Is it hardcoded?

**V1 decision:** **Not managed in V1.** Remove the Issue Category / Symptom field from the V1 form. The Issue Description (free text) is sufficient for V1; a structured symptom list requires catalog management overhead that is out of scope. Deferred to a future release once the business has enough data to define useful categories.

### DEP-02 — No spec for customer account profile / change-password screen

Customers register with a username, mobile, and password, but there is no spec for how they view or edit their own profile after registration. Can they change their mobile number? Change their password from within the app?

**V1 decision:**
* Customers can access a simple **Account Settings** screen (reachable from their home screen).
* On this screen: view their username, mobile number, and a "Change Password" action.
* Change password requires: current password + new password + confirm. Follows the same strength rules as registration.
* Customers cannot change their username (contact Admin to do so). Mobile number changes are allowed (validated for uniqueness).

---

# Workflow Review

End-to-end workflow from authentication through job completion, with gap analysis at each seam.

```
User opens app
     ↓
[Authentication / Auto-login]
     ↓ role-appropriate landing
Customer Home  ← Technician Schedule ← Staff Requests & Jobs ← Admin Dashboard
     ↓ (Customer)
Submit Repair Request
     ↓ Pending Review
[Request Review] — Staff
     ↓ Approved (auto-creates Job)
[Job Management] — Created / Assigned
     ↓ [Scheduling] — Scheduled
     ↓ In Progress / Awaiting Parts
     ↓ Completed
Customer notified
```

### WORK-01 — Missing: What does a Customer see between "Approved" and "Scheduled"?

When a request is approved, the job enters `Created` state (Unassigned). From the customer's perspective, the customer-friendly label is "Preparing." The customer has no appointment information yet. There is no notification sent to the customer for the `Created` or `Assigned` job states.

**Gap:** No notification is triggered for `Approved → Created` from the customer's perspective. The customer sees "Accepted" (the request status) and then later "Appointment Confirmed." Nothing tells them what is happening in between.

**V1 decision:** When the request is approved, the customer receives an in-app notification: "Your repair request [reference] has been accepted and is being prepared." Status label becomes "Preparing." When scheduled, they receive the appointment notification. The transition from "Accepted" to "Preparing" (request → job creation) is a single notification event.

### WORK-02 — No notification for Awaiting Parts to customer

`jobs.md` sends "Status → Awaiting Parts" only to Staff/Admin. The customer is not informed their repair is delayed.

**V1 decision:** Add a customer notification for `In Progress → Awaiting Parts`: "We're waiting for parts for your repair [reference]. We'll update you when work resumes." This is a meaningful transparency improvement at negligible cost.

### WORK-03 — No flow defined for what happens after a customer is notified of rejection

When a request is rejected, the customer is notified with a reason. The customer-friendly label shows "Unable to Process." There is no guidance in the spec on whether the customer is prompted to submit a new request or contact the business.

**V1 decision:** The rejection notification message should include: the customer-facing reason + "If you have questions, please contact us. You may submit a new request at any time." This closes the customer journey gracefully without building a separate help/contact screen.

### WORK-04 — Technician's own request submissions

The permissions matrix confirms Staff Members and Admins can submit repair requests (role "Customer" row: Staff Yes, Admin Yes). A technician submitting a request as a customer is not defined.

**V1 decision:** Any authenticated user (any role) can submit a repair request. The request is owned by the submitting account. A technician submitting a personal request is treated as a customer. A Staff member submitting for a walk-in customer is **not supported in V1** (walk-in intake is deferred). The form is the same for all roles.

---

# Entity Review

All business entities implied by the specifications:

| Entity | Specified? | Notes |
| --- | --- | --- |
| **User / Account** | Yes (auth.md) | Single entity, role-tagged |
| **Repair Request** | Yes (repair-request.md) | |
| **Job** | Yes (jobs.md) | 1:1 with approved Request |
| **Appointment** | Yes (scheduling.md) | 1:1 with Job (at most one active at a time) |
| **Device Category** | Partially (PRD, repair-request.md) | Managed in Product Management (no spec yet) |
| **Notification** | Referenced everywhere | No entity spec; defined in GAP-02 above |
| **Audit Event** | Referenced in all specs | Implied entity; spec-level requirements met |
| **Settings** | Referenced everywhere | No spec yet; defined in GAP-01 above |
| **Attachment/File** | Referenced in multiple specs | Ownership and retention defined; storage deferred to architecture |

### Missing entities

**ENTY-01 — No "Notification" entity spec**
Resolved in GAP-02 above. Notifications are persisted server-side for customers; defined as polling-delivered.

**ENTY-02 — No "Audit Log" entity spec**
Each feature spec defines what events to audit, but there is no cross-cutting audit entity spec. For architecture, treat the audit log as an append-only event journal attached to each record (request, job, appointment). No separate spec required before architecture; audit requirements are captured per-feature.

### Unnecessary entities

**ENTY-03 — "Appointment" as a separate entity may be overkill**
The scheduling spec correctly notes that the appointment status mirrors the job status and the appointment is not an independent entity. For V1, the appointment is effectively a date/time/technician record attached to the job. The architecture may implement it as a sub-table of the job rather than a fully independent entity. This is an architecture decision, not a requirements decision.

**V1 decision:** The Appointment is a **child record of the Job** — one job has at most one active appointment, but reschedule history means multiple appointment records may exist per job. Architecture will determine table structure.

---

# Status Review

## Canonical V1 Status Sets

### Request statuses (clean, no issues)

| Status | Terminal? | Owner |
| --- | --- | --- |
| `Pending Review` | No | System (initial) / Staff |
| `Information Required` | No | Staff (set) / Customer (resolves) |
| `Approved` | Review-terminal | Staff |
| `Rejected` | Yes | Staff |
| `Cancelled` | Yes | Customer |

### Job statuses (clean after recent revisions)

| Status | Terminal? | Owner |
| --- | --- | --- |
| `Created` | No | System |
| `Assigned` | No | Staff |
| `Scheduled` | No | Staff |
| `In Progress` | No | Technician / Staff |
| `Awaiting Parts` | No | Technician / Staff |
| `Completed` | Yes | Technician / Staff |
| `Cancelled` | Yes | Staff / Admin |

### Missing transition (CON-02 above)
`In Progress → Scheduled` (reschedule) must be added to jobs.md. **Decision:** add it.

### Status simplification opportunities
No further simplification is recommended. The current sets are the minimum needed for operational tracking. Previous reductions (removing `Ready For Pickup`, `Closed`, `Submitted`) were correctly applied.

---

# Permissions Review

## Consolidated V1 Permission Model

| Capability | Customer | Technician | Staff | Admin |
| --- | --- | --- | --- | --- |
| **Authentication** | | | | |
| Register, login, logout, reset password | ✓ | ✓ | ✓ | ✓ |
| Change own password / mobile | ✓ | ✓ | ✓ | ✓ |
| **Repair Requests** | | | | |
| Submit repair request | ✓ | — | ✓ | ✓ |
| View own requests | ✓ | — | ✓ | ✓ |
| Edit/cancel own request (while Pending Review / Information Required) | ✓ | — | — | — |
| View all requests | — | — | ✓ | ✓ |
| Approve / reject / request info | — | — | ✓ | ✓ |
| Add review notes / set priority | — | — | ✓ | ✓ |
| **Jobs** | | | | |
| View own job status (via tracking) | ✓ | — | — | — |
| View assigned jobs (full detail) | — | ✓ | ✓ | ✓ |
| View all jobs | — | — | ✓ | ✓ |
| Assign / reassign technician | — | — | ✓ | ✓ |
| Change job status (full set) | — | — | ✓ | ✓ |
| Change job status (own, limited set) | — | ✓ | — | — |
| Add repair notes / upload attachments | — | ✓ (own) | ✓ | ✓ |
| Cancel job | — | — | ✓ | ✓ |
| **Scheduling** | | | | |
| View own appointment (via tracking) | ✓ | — | — | — |
| View own schedule | — | ✓ | — | — |
| View full calendar | — | — | ✓ | ✓ |
| Create / reschedule / cancel appointments | — | — | ✓ | ✓ |
| **Administration** | | | | |
| Manage users (create/edit/disable/delete) | — | — | — | ✓ |
| Assign roles | — | — | — | ✓ |
| Manage product catalog | — | — | — | ✓ |
| Configure settings | — | — | — | ✓ |
| View customer management | — | — | ✓ | ✓ |

### Permission gaps found

**PERM-01 — Can Staff view Completed / historical jobs?**
Not explicitly stated. **V1 decision:** Staff view all jobs regardless of status (no time limit). Historical completed jobs are accessible via search/filter.

**PERM-02 — Can Technicians view their completed jobs?**
Not specified. **V1 decision:** Yes — technicians can see all jobs assigned to them, including completed ones (for personal record). Read-only.

**PERM-03 — Can Staff submit repair requests on behalf of customers?**
Covered in WORK-04 above. Staff can submit requests as themselves; walk-in assisted intake is not in V1.

**PERM-04 — Who can view audit logs?**
Not explicitly stated in one place. **V1 decision:** Administrators only can view full audit logs. Staff can see the audit/history section of individual requests and jobs they can access (read).

---

# Notification Review

## Consolidated V1 In-App Notifications (Minimum Set)

All notifications are in-app only in V1.

### Customer notifications (stored in notification inbox)

| Event | Message (guideline) |
| --- | --- |
| Request submitted | "Your repair request [REF] has been received and is under review." |
| Request approved | "Your repair request [REF] has been accepted and is being prepared." |
| Request rejected | "We were unable to process your request [REF]: [reason]. You may submit a new request." |
| Additional info requested | "We need more information for your request [REF]. Please update it." |
| Appointment scheduled | "Your repair appointment is confirmed: [date, time] with [technician name]." |
| Appointment rescheduled | "Your repair appointment [REF] has been moved to [new date, time]." |
| Appointment cancelled | "Your repair appointment [REF] has been cancelled. [reason if customer-appropriate]" |
| Job in progress | "Your device repair [REF] is now in progress." |
| Awaiting parts | "We're waiting for parts for your repair [REF]. We'll update you when work resumes." |
| Job completed | "Your repair [REF] is complete. Thank you for choosing ACE Telecom." |
| Job cancelled | "Your repair [REF] has been cancelled. [reason]. Contact us if you have questions." |

### Technician notifications (in-app alert only; no persistent inbox)

| Event | Alert |
| --- | --- |
| Job assigned to me | "A new job has been assigned to you: [device] for [customer name]." |
| Job reassigned away | "Job [REF] has been reassigned." |
| Appointment today | "You have an appointment today at [time]: [customer name] — [device/issue]." |

### Staff/Admin notifications (counter badges only)

| Signal | Where shown |
| --- | --- |
| New request in queue | Pending counter on Requests & Jobs screen |
| Customer responded to info request | Badge/highlight on the updated request in queue |
| Awaiting Parts flag | Reflected in job status and active jobs count |

### Deferred notifications (future)
Push notifications, SMS, Email, WhatsApp — all Phase 2+.

---

# Data Collection Review

## Identified Issues

### DATA-01 — PRD Required Fields includes "Attachments" — contradiction

Resolved in GAP-06: Attachments are Optional in V1. PRD to be updated.

### DATA-02 — Issue Category / Symptom field has no data source

Resolved in DEP-01: Remove from V1 form entirely. Free-text issue description is sufficient.

### DATA-03 — "Service Mode" field — stale reference in repair-request.md

`repair-request.md` Form Definition still includes a "Drop-off / Service Mode" field marked "Optional (recommended)" with a note that it is "Shown only if business offers multiple modes." The V1 decision already excluded service modes, but the field remains in the form definition table.

**V1 decision:** Remove the Service Mode field from the V1 Form Definition table. Add a note that it is explicitly deferred.

### DATA-04 — Customer Name and Phone on request form: "request-local" edit means duplicate data risk

When a customer edits their name or phone on the request form, it is request-local and does not update the account. Across multiple requests, a customer could have different names on each. Staff need to know which is authoritative.

**V1 decision:** The account profile (username, mobile) is the authoritative source. Request-level edits are treated as "name/contact to use for this repair only." The Customer Management screen should show the account profile, not the request-level overrides, as the canonical contact record. This is already the practical behaviour; add a clarifying note to Customer Management when that spec is written.

### DATA-05 — Attachment count limit inconsistency

`repair-request.md`: ≤5 images per request.
`jobs.md`: ≤10 images per job (repair evidence/completion photos).

**V1 decision:** These are intentionally different limits for different purposes (customer upload at intake vs. technician repair documentation). Document this explicitly: request attachments ≤5, job (internal) attachments ≤10. No change needed to either spec.

### DATA-06 — "Warranty Status" field: customer self-report vs. staff confirmation is correct, but no structured field on the job

The request captures optional `Warranty Status` (customer-indicated: In Warranty / Out of Warranty / Unknown). The job copies this from the request. But there is no spec for staff to update/confirm the warranty status on the job during review or repair.

**V1 decision:** Add a "Warranty Confirmed By Staff" flag (yes/no/unknown) to the job's Device Information section. Staff can update it during review or early job stages. This is a one-field addition with no workflow change.

---

# Operational Review

Evaluation from the perspective of a small repair business running RepairDesk V1.

## OP-01 — Staff are the bottleneck: every job must be manually scheduled

After a request is approved and a job is created (`Created` / `Assigned`), it waits for a staff member to manually create an appointment. On a busy day with many approved jobs, this is administrative overhead.

**V1 decision (practical):** Accept this as a V1 limitation. The unscheduled jobs indicator in the calendar view ensures nothing is forgotten. Auto-scheduling is Phase 2. For a small business this is manageable.

## OP-02 — No way for a customer to cancel after a job is created

Once a request is approved and a job exists, only Staff/Admin can cancel. The customer has no self-service cancellation path. This means customer calls/messages requesting cancellation must be handled manually.

**V1 decision:** Accept this as a V1 limitation with one practical mitigation: the rejection notification and completed notification both include "Contact us if you have questions" to direct customers to the right channel. Self-service cancellation of jobs is Phase 2.

## OP-03 — No walk-in / same-day intake path

A walk-in customer has no digital path in V1. Staff cannot create requests/jobs on behalf of a customer. The entire workflow assumes the customer self-registers and submits digitally.

**V1 decision:** Accepted and documented. ACE Telecom must either guide walk-ins to self-register on-site (using their own device or a kiosk device) or handle walk-in jobs outside the system in V1. Walk-in intake is deferred to a future version.

## OP-04 — Notification polling may cause slight delays in customer updates

With 30–60 second polling, a customer's status update (e.g., "Completed") may appear up to a minute after the event. This is acceptable for V1.

**V1 decision:** Accepted. Polling interval default: 30 seconds when app is in foreground, no polling when backgrounded. Phase 2 push notifications will eliminate this delay.

## OP-05 — No way to communicate with a customer other than status change notifications

If staff need to ask something informally, the only structured channel is "Request Additional Information" (while in review). Post-approval, there is no in-app messaging. Notes on the job are internal-only.

**V1 decision:** Accepted. The mobile phone number captured on the request is the fallback contact channel. In-app messaging is a future enhancement. This is a minor operational gap for V1, manageable with phone/WhatsApp outside the app.

## OP-06 — No view of the total picture: approved requests + unscheduled jobs + scheduled jobs in one place

Staff navigate between the Requests tab (for pending reviews), the Jobs tab (for active jobs), and the Schedule tab (for appointments). There is no single "operational overview" that shows everything at once.

**V1 decision:** The Dashboard (and the summary counters on the Requests & Jobs screen: Pending / Approved / Active Jobs) serve as the closest approximation. A unified operational overview is a future enhancement. Accept for V1.

---

# Architecture Readiness

## Architecture Blockers (must resolve before or during architecture)

| Blocker | Resolution |
| --- | --- |
| Settings Management spec missing | V1 decisions captured in GAP-01 |
| Product Management spec missing | V1 decisions captured in GAP-01 |
| Customer Management spec missing | V1 decisions captured in GAP-01 |
| Staff Management spec missing | V1 decisions captured in GAP-01 |
| Repair Status Tracking / Customer Home spec missing | V1 decisions captured in GAP-01 |
| In-app notification mechanism undefined | V1 decisions captured in GAP-02 |
| Default admin credentials undefined | V1 decisions captured in GAP-03 |
| Password strength undefined | V1 decisions captured in GAP-04 |
| `In Progress → Scheduled` transition missing from jobs.md | Minor update needed (CON-02) |

## Architecture Pre-conditions (ready now)

* Status model: clean and complete (request + job)
* RBAC model: complete and consistent
* Authentication / JWT strategy: complete
* Repair request intake: complete
* Request review workflow: complete
* Job lifecycle: complete
* Scheduling model: complete
* File storage: deferred to architecture (requirements are clear: server-side enforcement, role-restricted access, retention follows record lifecycle)
* Audit log: requirements captured per-feature; architecture to determine implementation pattern
* Performance targets: defined (3s load, 2s search, 10k records)
* Tech stack: locked (Flutter, Spring Boot, PostgreSQL, JWT, Flyway, Riverpod, GoRouter, Dio)

## Items deferred to architecture (not blockers)

* File storage provider selection
* Notification polling vs. WebSocket/SSE for future real-time upgrades
* Audit log storage pattern (embedded in record vs. separate journal table)
* Appointment as sub-table vs. independent entity
* Pagination/lazy-loading mechanism for lists
* UUID vs. sequential ID strategy (Constitution says "UUID where appropriate")
* Image processing/validation pipeline (true type validation, size enforcement)

---

# Recommended Decisions

Final V1 decisions to be accepted before architecture begins. All items above are summarised here as a single reference checklist.

**Core model decisions (all previously decided, confirmed here):**
1. Single role per user account; four roles: Customer, Technician, Staff, Admin.
2. Self-registration defaults to Customer; Admin assigns elevated roles.
3. Non-expiring JWT; sessions end on logout/disable/delete only.
4. Default admin: username `admin`, password `Admin@1234`, must change after first login.
5. Password minimum: 8 characters. No complexity rule in V1.
6. Repair request → Pending Review (single initial status; no Submitted state).
7. Approval auto-creates one job (1:1); no manual job creation in V1.
8. `Completed` is the single terminal job status; no `Ready For Pickup`, no `Closed`.
9. Job Number = Request Reference (one reference number per repair lifecycle).
10. One technician per job; one active appointment per job at a time.
11. Appointment is a child record of the job; cancels old on reschedule.

**Form and data decisions:**
12. Attachments on repair request: **Optional** (not Required). PRD to be updated.
13. Issue Category / Symptom field: **removed from V1 form**.
14. Service Mode field: **removed from V1 form**.
15. Brand/Model at intake: **optional free text** (no managed list in V1).
16. Appointment preferred date/time at intake: **Required** (preference, not booking).
17. Request attachment limits: ≤5 images, 10 MB/file, 25 MB total. JPEG/PNG/HEIC/WebP.
18. Job attachment limits: ≤10 images, 10 MB/file. Same formats.
19. Add "Warranty Confirmed By Staff" flag to job device section.

**Workflow decisions:**
20. `In Progress → Scheduled` is a valid transition (reschedule mid-job). Add to jobs.md.
21. Job cancellation = single customer notification (no separate appointment cancellation notification).
22. Add "Awaiting Parts" customer notification.
23. Customer-friendly status label map: defined in GAP-01 (Repair Status Tracking).
24. Customer Home = their request/job list (default landing screen after login).
25. Technician landing = personal schedule/today's appointments.
26. Staff/Admin landing = Requests & Jobs screen (dashboard as nav hub).

**Notification decisions:**
27. Customer notification inbox: persisted server-side, last 30 items, polling every 30s in foreground.
28. Staff notifications: counter badges only (no inbox).
29. Technician notifications: transient in-app alerts (no inbox in V1).
30. All notifications in-app only; push/SMS/email deferred to Phase 2.

**Operational scope:**
31. No walk-in intake in V1. Customers self-register and self-submit.
32. No customer self-service job cancellation in V1.
33. No structured parts management in V1 (`Awaiting Parts` + free text).
34. No invoicing/payments in V1.
35. No leave/availability management in V1.

**Missing feature scope (from GAP-01):**
36. Settings: working hours (per-day config), slot duration (single global), business info. Admin only.
37. Product Management: device categories (add/edit/archive). Admin only. No brand/model lists.
38. Customer Management: search + view profile + view repair history. Staff/Admin read. No edit.
39. Staff Management: create/edit/disable/delete users + assign roles. Admin only.
40. Repair Status Tracking = Customer Home list + detail view with status map.

---

# Final Verdict

## Mostly Ready for Architecture

The five core feature specifications (authentication, repair request, request review, job management, scheduling) are complete, internally consistent, and carry explicit V1 decisions. The status model is clean. The permissions model is coherent. No fundamental redesign is required.

The **six missing feature specs** (Settings, Product Management, Customer Management, Staff Management, Repair Status Tracking, Dashboard) are the only blocker. These are all simple CRUD/display features whose V1 scope is fully captured in this document. They do not require a design session — the decisions are made. A developer could write those specs in parallel with the architecture document, or use the decisions in this gap analysis directly.

**To move to Architecture.md:**
1. Apply the minor corrections to `repair-request.md` (stale status reference, draft wording, Service Mode field, Issue Category field) and `jobs.md` (`In Progress → Scheduled` transition).
2. Update PRD §8 Feature 1 to mark Attachments as Optional.
3. Add a cross-link to PRD Feature 4.
4. Use the V1 decisions in this document to author lightweight specs for the six missing features, or fold them directly into the architecture as configuration tables.

Architecture can begin **immediately in parallel** with the minor spec corrections. There are no requirements ambiguities remaining that would cause an architectural decision to be wrong.
