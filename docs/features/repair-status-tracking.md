# Repair Status Tracking (Customer Home) - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Repair Status Tracking feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §6 (Repair Status Tracking screen), §7 (User Flow), the **Customer Home** landing concept, and the approved V1 decisions in [Gap Analysis](../gap-analysis.md) (GAP-01 Repair Status Tracking / Customer Home, GAP-02 notification inbox, WORK-01/02/03, the customer-friendly status map, Notification Review). Upstream data comes from [Repair Request](repair-request.md), [Request Review](request-review.md), [Job Management](jobs.md), and [Scheduling](scheduling.md).

> **Key V1 decision (GAP-01):** There is **no separate "Repair Status Tracking" screen**. It is the **Customer Home** (the customer's landing screen — a list of their requests/jobs) plus a **detail view** for a selected record. One feature, two screens, plus a notification inbox.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Repair Status Tracking (Customer Home) |
| **Feature ID** | FEAT-TRACK-008 |
| **Source Requirement** | PRD §6 (Repair Status Tracking: view status, assigned technician, appointment details), §7 (User Flow → Customer Notified); Gap Analysis GAP-01 (Customer Home = list + detail), GAP-02 (customer notification inbox), WORK-01/02/03 (customer-facing transitions/notifications), customer-friendly status map. |
| **Business Purpose** | Give each customer a single landing screen showing all their repairs and a detail view of any one, so they always know the current status, appointment, and assigned technician without contacting the business. |
| **Business Value** | Improves repair completion visibility (a PRD goal/metric), reduces inbound status enquiries, and closes the customer journey gracefully (acceptance → preparing → scheduled → completed) using friendly, non-internal language. |
| **Related Features** | Repair Request (creates records; entry point from Home), Request Review (request statuses/notifications), Job Management (job statuses/technician/visibility), Scheduling (appointment date/time/technician), Authentication (customer landing screen), Notifications (GAP-02 inbox + polling). |
| **Dependencies** | A. Authenticated Customer session (Authentication — Customer landing screen). B. The customer's own requests/jobs/appointments. C. Server-side notification store (GAP-02). D. Customer-friendly status mapping (this spec). E. Polling delivery (GAP-02; 30s foreground). |

**Scope note (V1):** Read-only for the customer. No real-time updates (polling only), no chat, no customer messaging, and no customer job cancellation. The customer may still **submit a new request** and **cancel/edit a request only while it is pre-review** (that behavior is owned by [Repair Request](repair-request.md); it is surfaced from Home but not redefined here).

---

# Business Purpose

Repair Status Tracking exists to:

* **Be the customer's home** — the default screen after login, listing all their repairs most-recently-updated first.
* **Answer "what's happening with my repair?"** — show a clear, customer-friendly status for every request/job.
* **Surface the essentials** — appointment date/time and assigned technician name once known.
* **Keep customers informed** — a notification inbox records milestone updates and links back to the relevant repair.
* **Stay simple** — no messaging, no real-time, no operational controls; transparency only.

---

# User Roles

## Customer
* **Responsibilities**
  * Monitor their own repairs; respond to "information needed" prompts (via the Repair Request edit path); attend confirmed appointments.
* **Permissions**
  * View a list of **their own** requests/jobs (Customer Home).
  * Open any of their own records to a detail view (status, appointment, technician name, device/issue, status history, notification history).
  * View their notification inbox.
  * Start a new repair request from Home (entry into [Repair Request](repair-request.md)).
* **Restrictions**
  * Read-only with respect to job/appointment status; cannot change status, schedule, reschedule, or cancel a **job/appointment**.
  * Cannot view internal notes, job attachments, priority, audit detail beyond their own status history, technician contact details, or any other customer's records.
  * Cannot cancel a job (V1; Gap Analysis OP-02). May only cancel/edit a **request** while pre-review (owned by Repair Request).

## Staff / Administrator / Technician
* **Permissions**
  * **No use** of this customer-facing feature. Internal roles track work via the Requests & Jobs and Schedule screens. (Staff submitting their own personal request would see it here as a customer — Gap Analysis WORK-04.)

---

# User Stories

### Customer
1. **See all my repairs at a glance** — *Goal:* open the app and see my repairs. *Motivation:* know where everything stands. *Outcome:* Customer Home lists my requests/jobs with reference, device, friendly status, and date, newest activity first.
2. **Check one repair in detail** — *Goal:* tap a repair. *Motivation:* see the full picture. *Outcome:* a detail view with friendly status, appointment (once scheduled), assigned technician name, device/issue, status history, and notifications for that record.
3. **Know my appointment** — *Goal:* see when and with whom my repair happens. *Motivation:* plan my day. *Outcome:* confirmed date/time and technician name shown once scheduled.
4. **Stay updated** — *Goal:* receive notifications on meaningful changes. *Motivation:* I don't want to keep checking. *Outcome:* a notification inbox (bell) shows recent updates; tapping one opens the related repair.
5. **Understand a rejection** — *Goal:* know why a request couldn't proceed and what to do. *Motivation:* decide next steps. *Outcome:* a friendly "Unable to Process" status with the reason and guidance to contact the business or submit a new request (Gap Analysis WORK-03).
6. **Start a new repair** — *Goal:* submit another request. *Motivation:* a new device needs servicing. *Outcome:* the "+ New Repair Request" action opens the request form.
7. **Begin from empty** — *Goal:* understand what to do with no repairs yet. *Motivation:* I'm new. *Outcome:* an empty state inviting me to submit my first request.

---

# Customer-Friendly Status Mapping

Internal request/job statuses are presented to customers using the approved labels below (Gap Analysis GAP-01). The customer never sees raw internal status names.

| Internal status | Customer-friendly label |
| --- | --- |
| `Pending Review` (request) | **Under Review** |
| `Information Required` (request) | **Information Needed** |
| `Approved` (request) | **Accepted** |
| `Rejected` (request) | **Unable to Process** |
| `Cancelled` (request) | **Cancelled** |
| `Created` / `Assigned` (job) | **Preparing** |
| `Scheduled` (job) | **Appointment Confirmed** |
| `In Progress` (job) | **Repair in Progress** |
| `Awaiting Parts` (job) | **Awaiting Parts** |
| `Completed` (job) | **Completed** |
| `Cancelled` (job) | **Cancelled** |

**Notes:**
* The repair lifecycle spans a request stage and (after approval) a job stage under **one reference number** (Job Number = Request Reference — Jobs V1 decision). The customer sees a single, continuous repair whose label progresses through the map above.
* Internal `Created` and `Assigned` are both shown as **Preparing** — the customer is not exposed to internal assignment detail (Jobs Customer Visibility).

---

# Screen Definitions

## 1. Customer Home (Repair List) — landing screen
* **Entry point:** Default landing screen after a Customer logs in or auto-logs-in (Authentication §10/§11). This screen **is** the customer dashboard (Gap Analysis Dashboard decision: customer has no separate dashboard).
* **Purpose:** List all of the customer's repairs and provide entry to detail, new request, and notifications.
* **Content:**
  * **Repair list**, ordered **most-recently-updated first**. Each item shows: **reference number**, **device category**, **current status** (customer-friendly label), and **date submitted**.
  * **Notification bell** (inbox entry point) with an unread badge (GAP-02).
  * **Primary action: "+ New Repair Request"** (entry into [Repair Request](repair-request.md)).
  * **Account access** entry (to Account Settings — view username/mobile, change password; owned by Authentication / Gap Analysis DEP-02).
* **Empty state:** "No repair requests yet — tap + to submit one." (Gap Analysis GAP-01.)
* **Refresh:** Pull-to-refresh and automatic foreground **polling** (default 30s) update statuses and the unread badge (GAP-02; no real-time push in V1).
* **Navigation exit points:** Detail view (tap an item); Repair Request form (+); Notification inbox (bell); Account Settings.

## 2. Repair Detail View
* **Entry point:** Tapping a repair on Customer Home, or tapping a notification that references it.
* **Purpose:** Show the full customer-facing picture of one repair.
* **Content:**
  * **Reference number** and **device info** (category; brand/model if provided).
  * **Current status** (customer-friendly label).
  * **Issue description** (as the customer submitted it).
  * **Appointment date/time** — shown once **Scheduled**; before that, the customer's requested preference may be shown as "requested" (not confirmed).
  * **Assigned technician name** — shown once assigned/scheduled (name only, no contact details — Jobs/Scheduling Customer Visibility).
  * **Status history** — the customer-facing progression of this repair (friendly labels with timestamps). Excludes internal-only transitions detail, notes, and actor identities.
  * **Notification history for this record** — the in-app notifications sent about this repair (GAP-01).
  * **Rejection guidance** (when "Unable to Process"): the customer-facing reason plus "If you have questions, please contact us. You may submit a new request at any time." (Gap Analysis WORK-03.)
* **Available actions:**
  * **Cancel/Edit request** — **only while the request is pre-review** (`Under Review` / `Information Needed`), delegating to [Repair Request](repair-request.md) rules. **Not available once a job exists.**
  * **Provide requested information** — when status is "Information Needed", an entry to update the request (Repair Request / Request Review).
* **Explicitly absent (V1):** No chat/messaging, no job cancellation, no reschedule request, no internal notes, no job attachments/photos, no priority, no technician contact.
* **Navigation exit points:** Back to Customer Home; into the Repair Request edit flow (only when permitted).

## 3. Notification Inbox
* **Entry point:** Notification bell on Customer Home.
* **Purpose:** Show recent in-app notifications and route to the related repair.
* **Content:** The **last 30** notifications for this customer (GAP-02), each with a short message and timestamp, unread items distinguished. Notifications are persisted server-side.
* **Available actions:** Tap a notification → open the related Repair Detail View; mark as read (reading/opening clears unread state).
* **Delivery:** Populated via **polling** (default 30s in foreground; no polling when backgrounded — Gap Analysis OP-04). No real-time push in V1.
* **Empty state:** "No notifications yet."
* **Navigation exit points:** Repair Detail View; back to Customer Home.

---

# Business Rules

1. **Customer Home is the customer landing screen.** Customers have no separate dashboard; this list is where login lands (Gap Analysis Dashboard / Authentication).
2. **Own-records-only.** A customer sees only repairs they own and notifications addressed to them. Cross-customer access is denied.
3. **One continuous repair per reference.** The request and its resulting job share one reference (Job Number = Request Reference); the customer perceives a single repair progressing through the friendly status map.
4. **Friendly labels only.** Internal status names are never shown; the approved mapping is authoritative.
5. **Read-only on jobs/appointments.** Customers cannot change job status, schedule, reschedule, or cancel a job/appointment (Gap Analysis OP-02). The only customer-initiated changes are submitting a new request and cancelling/editing a **request while pre-review** (owned by Repair Request).
6. **Ordering.** Home lists repairs most-recently-updated first.
7. **Appointment visibility.** Confirmed appointment date/time appears only once the job is `Scheduled`; the technician **name** appears once assigned/scheduled (no contact details).
8. **Notification inbox = customers only.** The bell/inbox model applies to customers; internal staff use queue badge counters instead (GAP-02). Inbox retains the **last 30** items.
9. **Polling, not real-time.** Status and notifications refresh via foreground polling (~30s). Updates may lag the event by up to the poll interval (Gap Analysis OP-04) — acceptable for V1.
10. **No chat / no messaging.** There is no customer↔staff messaging channel in V1; the registered mobile number is the out-of-band contact (Gap Analysis OP-05).
11. **Rejection closure.** A rejected request shows the customer-facing reason and guidance to contact the business / submit a new request (Gap Analysis WORK-03).
12. **Customer-visible milestones only.** The status history and notifications reflect meaningful customer milestones (see Notifications); purely internal transitions (e.g., `Created`→`Assigned`) appear as a single "Preparing" state, not separate entries.

---

# Permissions

| Capability | Customer (owner) | Technician | Staff | Administrator |
| --- | --- | --- | --- | --- |
| View Customer Home (own repairs) | ✓ | — | — | — |
| View Repair Detail (own record) | ✓ | — | — | — |
| View status history (own, customer-facing) | ✓ | — | — | — |
| View notification inbox (own) | ✓ | — | — | — |
| Submit a new repair request | ✓ | — | — | — |
| Cancel/edit a request while pre-review | ✓ (per Repair Request rules) | — | — | — |
| Change job/appointment status, schedule, reschedule, cancel job | — | — | — | — (internal features only) |
| View another customer's repairs/notifications | — | — | — | — |

* Internal roles (Technician/Staff/Admin) do not use this feature; they track work via internal screens. A staff/admin who personally submitted a request would view *that* request here as its owner (Gap Analysis WORK-04).
* All access requires an authenticated session and is enforced server-side; customers are scoped to their own data only.

---

# Validation Rules

* **Ownership scoping:** Every list query and detail/notification fetch is constrained to the authenticated customer's own records server-side. Requests for non-owned records are denied (not just hidden).
* **Status mapping integrity:** Every internal status must map to exactly one customer-friendly label per the table; an unmapped status must never leak a raw internal name (fallback to a safe generic label and log the anomaly).
* **Appointment display guard:** Appointment date/time is only displayed when an active confirmed appointment exists (job `Scheduled`); otherwise show "requested" preference or nothing.
* **Notification cap:** The inbox returns at most the **last 30** notifications for the customer.
* **Polling cadence:** Foreground polling default 30s; no polling while backgrounded.
* **Action gating:** Cancel/edit-request actions are only offered when the underlying request is in a customer-editable state (`Under Review`/`Information Needed`); otherwise hidden/disabled with explanation if attempted.

---

# Notifications

**Version 1 — in-app only, persisted, delivered by polling (GAP-02).** These are the customer-facing notifications stored in the inbox and reflected in status history. Messages follow the consolidated set in [Gap Analysis](../gap-analysis.md) (Notification Review).

| Triggering event (internal) | Customer-facing label after event | Notification message (guideline) |
| --- | --- | --- |
| Request submitted | Under Review | "Your repair request [REF] has been received and is under review." |
| Request → Information Required | Information Needed | "We need more information for your request [REF]. Please update it." |
| Request approved (job created) | Accepted → Preparing | "Your repair request [REF] has been accepted and is being prepared." (single event for accepted→preparing — Gap Analysis WORK-01) |
| Request rejected | Unable to Process | "We were unable to process your request [REF]: [reason]. If you have questions, please contact us. You may submit a new request." (Gap Analysis WORK-03) |
| Appointment scheduled | Appointment Confirmed | "Your repair appointment is confirmed: [date, time] with [technician name]." |
| Appointment rescheduled | Appointment Confirmed | "Your repair appointment [REF] has been moved to [new date, time]." |
| Appointment cancelled (job not cancelled) | Preparing | "Your repair appointment [REF] has been cancelled. [reason if customer-appropriate]" |
| Job → In Progress | Repair in Progress | "Your device repair [REF] is now in progress." |
| Job → Awaiting Parts | Awaiting Parts | "We're waiting for parts for your repair [REF]. We'll update you when work resumes." (Gap Analysis WORK-02) |
| Job → Completed | Completed | "Your repair [REF] is complete. Thank you for choosing ACE Telecom." |
| Job cancelled | Cancelled | "Your repair [REF] has been cancelled. [reason]. Contact us if you have questions." (single notification; appointment auto-cancelled atomically — Gap Analysis CON-03) |

* **One notification on job cancellation** (no separate appointment-cancellation notification) — Gap Analysis CON-03.
* **Toast/snackbar** is used only for the customer's own action confirmations (e.g., "Request submitted") and is **not** stored in the inbox (GAP-02).
* **Future (deferred):** push, SMS, email, WhatsApp (Phase 2+).

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **No repairs yet** | Empty state: "No repair requests yet — tap + to submit one." | Tap + to submit a request. |
| **No notifications** | Empty inbox: "No notifications yet." | None needed. |
| **Network failure** (loading list/detail/inbox) | Non-blocking error; last loaded data may remain visible; no false updates. | Pull-to-refresh / retry; resume on reconnect via next poll. |
| **Polling failure** | Silent retry on next interval; no error spam to the customer. | Next successful poll refreshes status/badge. |
| **Record not found / not owned** | Generic "this repair is not available"; never reveal another customer's data. | Return to Customer Home. |
| **Unmapped internal status** | Show a safe generic label (e.g., "In Progress"/"Updating"); never show raw internal name. | Log the anomaly for engineering; status corrects on next poll once mapped. |
| **Attempt an unavailable action** (e.g., cancel a request after review started) | Block with explanation directing to the appropriate channel. | Offer contact guidance; action hidden where possible. |
| **Server error (5xx)** | Friendly error; no fabricated status. | Retry later. |
| **Session expired/invalid** | Prompt re-login. | Re-authenticate; return to Customer Home (auto-login resumes per Authentication). |

**Cross-cutting:** Never display a status as changed unless it reflects persisted server state; error messages must not expose internal/system details (Constitution).

---

# Acceptance Criteria

Given/When/Then.

**AC-1 — Customer Home lists repairs**
* **Given** a logged-in customer with one or more repairs
* **When** they land on Customer Home
* **Then** their repairs are listed most-recently-updated first, each showing reference number, device category, customer-friendly status, and submission date.

**AC-2 — Friendly status only**
* **Given** a repair in any internal status
* **When** the customer views it
* **Then** the approved customer-friendly label is shown and no raw internal status name is ever displayed.

**AC-3 — Detail view content**
* **Given** a customer tapping a repair
* **When** the detail view opens
* **Then** it shows reference, device/issue, friendly status, status history, and the notification history for that record.

**AC-4 — Appointment and technician shown once scheduled**
* **Given** a job that has been scheduled
* **When** the customer opens its detail view
* **Then** the confirmed appointment date/time and the assigned technician's name are displayed (name only, no contact details).

**AC-5 — Accepted→Preparing single update**
* **Given** a request that is approved (auto-creating a job)
* **When** the approval completes
* **Then** the customer receives one notification ("accepted and is being prepared") and the status reads "Preparing".

**AC-6 — Awaiting parts notification**
* **Given** a job moved to Awaiting Parts
* **When** the change is persisted
* **Then** the customer receives the awaiting-parts notification and the status reads "Awaiting Parts".

**AC-7 — Rejection guidance**
* **Given** a request that is rejected
* **When** the customer views it
* **Then** the status reads "Unable to Process", the customer-facing reason is shown, and guidance to contact the business / submit a new request is included.

**AC-8 — Single notification on job cancellation**
* **Given** a job with an active appointment is cancelled
* **When** the cancellation completes
* **Then** the customer receives exactly one notification about the job cancellation (no separate appointment-cancellation notification).

**AC-9 — Notification inbox cap and routing**
* **Given** a customer with notifications
* **When** they open the inbox
* **Then** up to the last 30 notifications are shown, and tapping one opens the related repair's detail view.

**AC-10 — Polling refresh, no real-time**
* **Given** a status changes server-side while the customer is on Home
* **When** the next foreground poll occurs (~30s)
* **Then** the status and unread badge update; there is no real-time/push delivery in V1.

**AC-11 — Read-only on jobs**
* **Given** a customer viewing a job that exists
* **When** they look for cancel/reschedule/status controls
* **Then** none are available (job cancellation and scheduling are staff-only in V1).

**AC-12 — Request edit only pre-review**
* **Given** a request that has entered review or become a job
* **When** the customer attempts to cancel/edit it from the detail view
* **Then** the action is unavailable with an explanation (delegating to Repair Request rules).

**AC-13 — Empty state**
* **Given** a customer with no repairs
* **When** they open Customer Home
* **Then** the empty state "No repair requests yet — tap + to submit one." is shown.

**AC-14 — Ownership isolation**
* **Given** an authenticated customer
* **When** they attempt to access another customer's repair or notification
* **Then** access is denied and no data is exposed.

**AC-15 — New request entry**
* **Given** a customer on Customer Home
* **When** they tap "+ New Repair Request"
* **Then** the Repair Request submission flow opens.

---

# V1 Decisions (Resolved)

1. **Customer Home = list + detail** (one feature, two screens); **no separate tracking screen** (Gap Analysis GAP-01).
2. **Customer Home is the customer's landing screen**; no separate customer dashboard.
3. **Approved customer-friendly status map** is authoritative; internal names never shown.
4. **Notification inbox** for customers: persisted server-side, **last 30**, polling **~30s** foreground (GAP-02).
5. **Polling only**, no real-time/push in V1 (Gap Analysis OP-04).
6. **No chat, no customer messaging** (Gap Analysis OP-05).
7. **No customer job cancellation**; request cancel/edit only while pre-review (Gap Analysis OP-02; Repair Request rules).
8. **Single notification on job cancellation** (CON-03); **Accepted→Preparing** is one event (WORK-01); **Awaiting Parts** customer notification added (WORK-02); **rejection closure guidance** included (WORK-03).
9. **Technician name only**, no contact details; **no internal notes/attachments/priority** exposed.

*Future enhancements (deferred):* real-time push notifications, in-app customer↔staff messaging, customer self-service job cancellation and reschedule requests, sharing completion photos with customers, richer notification preferences.
