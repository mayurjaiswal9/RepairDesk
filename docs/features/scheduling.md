# Scheduling - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Scheduling feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §6 (Schedule screen), §8 Feature 5 (Scheduling), §8 Feature 4 (Technician Assignment), §8 Feature 8 (Settings — working hours, slot duration); the **Requests & Jobs** wireframe (Schedule tab visible alongside Requests and Jobs tabs); and the upstream [Job Management](jobs.md) specification. Scheduling manages the *when* and *who* of repair work; Job Management owns the *what* and *progress*.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Scheduling |
| **Feature ID** | FEAT-SCH-005 |
| **Source Requirement** | PRD §6 Schedule screen ("View appointments, Assign technicians, Reschedule jobs"); §8 Feature 4 & 5; Settings ("Working hours, Appointment durations"); Requests & Jobs wireframe — Schedule tab. |
| **Purpose** | Manage when repair jobs happen and who performs them by creating, confirming, viewing, and adjusting appointments against a business calendar and technician workload. |
| **Business Value** | Reduces scheduling conflicts, improves technician utilization, makes repair timelines predictable for customers, and provides an operational calendar view that keeps staff in control of daily and weekly workload. Directly supports the PRD success metrics of reduced scheduling conflicts and improved technician utilization. |
| **Related Features** | Job Management (FEAT-JOB-003, primary consumer — scheduling moves jobs to `Scheduled`); Request Review (FEAT-RV-002, upstream — approved requests carry a preferred appointment suggestion); Settings Management (Feature 8, supplies business hours and slot duration); Staff Management (Feature 6, supplies active technician list); Authentication & RBAC; Notifications; Repair Status Tracking (customer view of appointment). |
| **Dependencies** | A. A job exists in `Assigned` or `Created` state (from Job Management). B. At least one active technician is available in Staff Management. C. Business hours and slot duration are configured in Settings. D. Authentication & RBAC (Staff/Administrator roles for scheduling; Technician for read). E. Notification service (in-app, V1). |

**Scope boundary:** This feature owns the appointment entity — creation, confirmation, rescheduling, cancellation, and the calendar view. Technician *assignment* (choosing which technician works a job) is initiated from the Jobs view and is recorded here as part of the appointment. Technician *availability management* (leave, absences) is **not automated in V1** — staff manage it manually using the calendar and technician filter.

---

# Business Objectives

Scheduling exists to:

* **Reduce scheduling conflicts** — ensure repairs are booked into valid time slots within business hours and that staff can see the full appointment picture before committing.
* **Optimize technician utilization** — give staff visibility of each technician's workload so appointments can be distributed evenly and peak capacity is visible.
* **Improve customer experience** — customers receive a confirmed date/time, reducing uncertainty and no-shows.
* **Improve repair completion predictability** — a confirmed schedule drives the job into `Scheduled → In Progress` and makes cycle-time reporting meaningful.
* **Provide operational planning** — a calendar view lets staff plan the day/week, anticipate bottlenecks, and handle re-arrangements proactively.
* **Support future SLA and capacity management** — by capturing appointment data now, the platform can layer in capacity rules, SLA targets, and availability engines later without redesign.

---

# User Roles

## Staff Member

* **Responsibilities**
  * Create and confirm appointments for approved jobs.
  * Assign (or re-assign) technicians to appointments.
  * Reschedule or cancel appointments as circumstances change.
  * Monitor the appointment calendar and balance technician workload.
* **Permissions**
  * Create, edit, reschedule, and cancel any appointment.
  * Assign any active technician to an appointment.
  * View the full calendar across all technicians.
  * View the customer's requested preference as a scheduling suggestion.
* **Restrictions**
  * Cannot manage user accounts, product catalog, or global system configuration.
  * Appointments must comply with configured business hours and slot duration; system blocks invalid entries.

## Administrator

* **Responsibilities**
  * All Staff scheduling capabilities, plus configuration of business hours, slot duration, and related settings that the scheduling feature depends on.
  * Oversight of scheduling data, corrections, and audit.
* **Permissions**
  * All Staff permissions.
  * Configure working hours and slot duration via Settings.
  * View full audit history and scheduling reports.
* **Restrictions**
  * Configuration changes (e.g., shortening working hours) must not retroactively invalidate existing scheduled appointments; affected appointments should be flagged for review, not silently corrupted.

## Technician

* **Responsibilities**
  * Be aware of their own schedule; acknowledge and action assigned appointments.
* **Permissions**
  * View their **own** scheduled appointments (date, time, job reference, customer name, device/issue summary).
  * Update job status from the context of their schedule (starting work moves job to `In Progress`).
* **Restrictions**
  * Cannot create, reschedule, or cancel appointments.
  * Cannot view other technicians' appointments or the full calendar.
  * Cannot change assignment or priority.

## Customer

* **Responsibilities**
  * Provide a preferred date/time at request submission (advisory only).
  * Honour the confirmed appointment or contact the business to reschedule.
* **Permissions**
  * View their **own** confirmed appointment (date, time, technician name) via Repair Status Tracking.
  * Receive in-app notifications when an appointment is scheduled, rescheduled, or cancelled.
* **Restrictions**
  * Cannot book, change, or cancel appointments directly in V1 (self-service scheduling is a future enhancement).
  * Cannot view the operational calendar or other customers' appointments.

---

# User Stories

### Staff Member

1. **Schedule an appointment for an approved job**
   * *Goal:* Assign a date, time, and technician to a job.
   * *Motivation:* Move work from backlog to a committed slot.
   * *Expected outcome:* Appointment created; job moves to `Scheduled`; customer notified; appointment appears on the calendar.

2. **Use the customer's preferred time as a starting suggestion**
   * *Goal:* Pre-fill the scheduling form with the customer's requested date/time.
   * *Motivation:* Save time and align with customer preference where possible.
   * *Expected outcome:* The preference is displayed; staff confirm or adjust before saving.

3. **View all appointments on a calendar**
   * *Goal:* See the day/week schedule across all technicians.
   * *Motivation:* Understand current workload and spot free slots.
   * *Expected outcome:* A visual calendar with appointments grouped or filterable by technician and date.

4. **Filter the schedule by technician**
   * *Goal:* See only one technician's appointments.
   * *Motivation:* Balance workload or find capacity for a specific technician.
   * *Expected outcome:* Calendar/list filtered to the selected technician's appointments.

5. **Reschedule an appointment**
   * *Goal:* Move an appointment to a different date/time.
   * *Motivation:* Technician unavailable, customer requested change, or capacity conflict.
   * *Expected outcome:* Appointment updated; old slot freed; customer notified; audit recorded.

6. **Cancel an appointment**
   * *Goal:* Remove an appointment from the schedule.
   * *Motivation:* Job cancelled, or no feasible time found for rescheduling.
   * *Expected outcome:* Appointment cancelled with reason; job status updated; customer notified.

7. **Identify unscheduled jobs**
   * *Goal:* See approved jobs that have no appointment yet.
   * *Motivation:* Ensure nothing is left unattended in backlog.
   * *Expected outcome:* Unscheduled jobs surfaced in the queue or as a filter option.

### Administrator

8. **Configure working hours and slot duration**
   * *Goal:* Define the business calendar that constrains scheduling.
   * *Motivation:* Ensure appointments only land on valid working days and times.
   * *Expected outcome:* Scheduling enforces the updated hours/slots immediately for new bookings.

### Technician

9. **View my daily schedule**
   * *Goal:* See what jobs are scheduled for me today and this week.
   * *Motivation:* Prepare and prioritise my work.
   * *Expected outcome:* A personal schedule view showing date, time, job reference, device, and issue for each appointment.

10. **Start work on a scheduled appointment**
    * *Goal:* Move the job to `In Progress` when arriving at the appointment.
    * *Motivation:* Reflect real status.
    * *Expected outcome:* Job status changes to `In Progress`; time recorded.

### Customer

11. **See my confirmed appointment**
    * *Goal:* Know when and with whom the repair is happening.
    * *Motivation:* Plan my day; know when device will be serviced.
    * *Expected outcome:* Confirmed date, time, and technician name visible in Repair Status Tracking.

---

# Scheduling Concepts

Shared terminology for this feature:

| Term | Definition |
| --- | --- |
| **Appointment** | A confirmed booking of a specific job to a specific technician at a specific date and time. An appointment is the scheduling entity; it is always linked to exactly one job and one technician. |
| **Time Slot** | A discrete unit of time available for booking, derived from the configured **slot duration** (e.g., 60 minutes). Slots start on the hour or at regular intervals within business hours. |
| **Technician Availability** | Whether a given technician is free at a given time. In V1 availability is inferred from the absence of a conflicting appointment; no separate leave or shift management exists. |
| **Working Hours** | The configured days and times during which the business accepts repair appointments (configured in Settings). Appointments cannot be placed outside working hours. |
| **Business Calendar** | The set of working days and hours derived from Settings. V1 uses weekly recurring hours only (no holiday calendar). |
| **Schedule Conflict** | A situation where an appointment would be placed at a time that overlaps with an existing appointment for the same technician. In V1, conflicts are surfaced as a **warning** but are not hard-blocked (staff have final authority). |
| **Rescheduling** | Changing the date/time (and optionally the technician) of an existing appointment. The old slot is released and a new one is created. History is retained. |
| **Preferred Time** | The customer's non-binding preferred appointment date/time captured at request submission. Used as a scheduling *suggestion*, not a commitment. |

---

# Appointment Creation

* **When:** Appointments are created when Staff assign a date, time, and technician to a job in status `Created` or `Assigned`. The job then moves to `Scheduled`.
* **Trigger:** Manual by Staff/Admin. Appointments are **never created automatically** in V1 (no auto-scheduling engine).
* **Who can create:** Staff and Administrators only.
* **Technician assignment requirement (V1 decision):** A technician must be assigned **at the point of scheduling** — an appointment cannot exist without an assigned technician. (This reconciles PRD Features 4 and 5: assignment and scheduling happen together in a single scheduling action, or the technician may be pre-assigned on the job before scheduling. Both sequences are valid; the appointment record always includes both technician and time.)
* **Customer preference as seed:** The customer's requested date/time from the repair request is displayed as a suggestion in the scheduling form. Staff may accept, adjust, or ignore it.
* **Slot selection:** Staff select from available slots derived from the business calendar (working days, business hours, slot duration). Times outside these bounds are not selectable.
* **Postconditions:** Appointment created; job status → `Scheduled`; customer notified; appointment visible on calendar and in technician's schedule.

**V1 decision — assignment and scheduling together:** Because V1 has no separate availability engine, requiring a technician at scheduling time is a lightweight safeguard; a slot without an owner has no operational value.

---

# Appointment Information

Fields stored on an appointment:

| Field | Description | Required |
| --- | --- | --- |
| **Appointment ID** | System-generated unique identifier. | System |
| **Appointment Reference** | Human-readable reference (linked to / same as Job/Request reference). | System |
| **Job Reference** | Link to the associated job. | Required |
| **Customer** (name, mobile) | Copied from the job for calendar display. | System (copied) |
| **Technician** | Assigned technician (single, V1). | Required |
| **Scheduled Date** | Confirmed appointment date (business day). | Required |
| **Start Time** | Slot start time within business hours. | Required |
| **End Time** | Start time + slot duration (derived). | System (derived) |
| **Status** | Current appointment status (see lifecycle). | System |
| **Customer Requested Date** | Original customer preference (display only, not binding). | Optional |
| **Customer Requested Time** | Original customer preference time (display only). | Optional |
| **Device Summary** | Category — Issue summary (for calendar card). | System (copied) |
| **Appointment Notes** | Optional internal note for this appointment (e.g., special access instructions). | Optional |
| **Created By** | Staff member who created the appointment. | System |
| **Created At** | Timestamp of creation. | System |
| **Last Updated By / At** | Audit trail of changes. | System |
| **Reschedule History** | Prior date/time/technician values with actor and timestamp. | System |

---

# Appointment Lifecycle

```
Job Approved → Job Created/Assigned
        ↓
  Staff schedules (date + time + technician)
        ↓
     Scheduled ──────────────▶ Cancelled  (by Staff/Admin; reason required)
        ↓
  Technician begins work
        ↓
    In Progress  (appointment mirrors job status)
        ↓
  Repair done
        ↓
    Completed  (terminal; mirrors job Completed)
```

Lifecycle notes:
* Appointment status mirrors the associated job status for simplicity in V1. The appointment is **not an independent entity** with a separate lifecycle; it is a scheduling record attached to a job.
* `Scheduled` → `In Progress` occurs when the job transitions to `In Progress`.
* `In Progress` → `Completed` occurs when the job is marked `Completed`.
* `Cancelled` is the only appointment-specific terminal state triggered directly on the appointment (when an appointment is removed without the job being cancelled, e.g., rescheduling clears the old appointment and creates a new one).
* **No `Draft`, `Confirmed`, or `No Show` states in V1** (see Appointment Statuses below).

---

# Appointment Statuses

| Status | Meaning | Owner | Entry Criteria | Exit Criteria | Allowed Transitions |
| --- | --- | --- | --- | --- | --- |
| `Scheduled` | Appointment booked; awaiting start. | Staff | Valid date/time/technician set; job is `Scheduled`. | Technician starts work, or rescheduled/cancelled. | → `In Progress`, → `Cancelled`. |
| `In Progress` | Repair is actively being performed. | Technician/Staff | Job → `In Progress`. | Repair completed. | → `Completed`, → `Cancelled`. |
| `Completed` | Repair done; appointment closed. Terminal. | Technician/Staff | Job → `Completed`. | — | Terminal. |
| `Cancelled` | Appointment removed (reason required). Terminal. | Staff/Admin | Cancellation reason provided. | — | Terminal. |

**V1 decisions on additional statuses:**
* `Draft` — **not needed.** Appointments are either created (Scheduled) or not. No partial saves.
* `Confirmed` — **not needed.** All created appointments are considered confirmed in V1; no separate customer confirmation step.
* `No Show` — **deferred.** A no-show flag or reschedule prompt is a future enhancement.
* `Rescheduled` — **not a status.** Rescheduling cancels the old appointment and creates a new `Scheduled` one, preserving history.

---

# Technician Availability

In V1, availability is managed **manually by staff**. There is no automated availability engine.

* **Working days and hours:** Derived from Settings (weekly recurring; no holiday calendar in V1). A technician is considered available during working hours on working days unless they have a conflicting appointment.
* **Existing appointments:** The system shows the technician's existing appointments on the calendar; staff use this to avoid double-booking. A conflict warning is shown if a new appointment would overlap with an existing one for the same technician (soft warning, not a hard block).
* **Leave periods:** **No formal leave management in V1.** If a technician is unavailable, staff should not schedule them during that period; there is no system-enforced leave calendar. (Leave management is a future enhancement.)
* **Holidays:** **Not in V1** (consistent with all other features). Weekly working hours only.
* **Capacity per slot:** **No slot capacity limit in V1.** The system does not enforce a maximum number of concurrent jobs per technician per slot; staff use judgment.

**V1 assumption:** For a small service operation, manual availability management is sufficient. The technician filter and calendar view give staff the information they need. Automated availability, leave, and capacity management are planned for future versions.

---

# Schedule Conflict Rules

A conflict occurs when a new appointment would overlap in time with an existing appointment for the same technician.

| Rule | Validation | User Experience | Resolution |
| --- | --- | --- | --- |
| **Overlapping appointment (same technician)** | Check existing `Scheduled`/`In Progress` appointments for the technician within the slot window. | **Soft warning** displayed: "This technician already has an appointment at this time." Staff may proceed or choose a different time/technician. | Staff adjusts time, chooses a different technician, or acknowledges and proceeds. |
| **Outside business hours** | Slot must fall within configured working days and hours. | **Hard block:** times outside hours are not selectable; if somehow submitted, server rejects. | Staff selects a valid time. |
| **Past date/time** | Appointment date/time must be in the future at creation. | **Hard block.** | Staff selects a future slot. |
| **Inactive/disabled technician** | Technician must have an active account with the Technician role. | **Hard block:** inactive technicians are not listed for selection. | Staff selects an active technician. |
| **Job already scheduled** | A job may only have one active appointment at a time. | If a job already has a `Scheduled` appointment, creating a second requires the first to be cancelled/rescheduled. | Existing appointment is cancelled as part of rescheduling; not a silent duplicate. |

**Rationale for soft conflict vs. hard block:** Requiring hard blocks on technician conflicts would break operations in cases where staff intentionally schedule back-to-back or same-time jobs (e.g., two independent quick repairs). A warning that requires acknowledgement balances safety with operational flexibility for V1.

---

# Time Slot Management

* **Slot type (V1 decision): Fixed slots.** Time slots are discrete units derived from the configured slot duration (Settings). If slot duration is 60 minutes, valid slot starts are on the hour during working hours. This is simpler to implement and reason about than free-form time picking.
* **Slot duration:** Configured in Settings (default 60 minutes; configurable by Administrator). Applies uniformly to all appointments in V1. Per-job or per-category durations are a future enhancement.
* **Business hour constraints:** Slots that would start or end outside working hours are not available. The last valid slot must end at or before the close of business.
* **Buffer times:** **No automatic buffers in V1.** Staff are responsible for leaving gaps if needed. (Buffer time between appointments is a future Settings option.)
* **Slot display:** The scheduling UI presents available slots as a time picker constrained to valid options. Slots already fully occupied by conflicting appointments for the selected technician are flagged (soft warning) but remain selectable by staff.

---

# Calendar View

The Schedule view (third tab on the Requests & Jobs screen per wireframe) provides a visual and list-based overview of all appointments.

* **Default view (V1 decision): Weekly view** — the most operationally useful for a repair shop planning a working week. Daily view available for granular scheduling.
* **Daily view:** All appointments for a selected day, ordered by start time, grouped by technician.
* **Weekly view:** A grid of days × technicians (or days with technician lanes) showing appointment cards.
* **Appointment card content:** Job reference, customer name, device category, time slot, status indicator.
* **Filters:**
  * **Technician** — filter to one technician or all (consistent with Jobs tab filter).
  * **Status** — Scheduled / In Progress / Completed / Cancelled.
  * **Date range** — select day or week.
* **Search:** By job reference, customer name, or device category.
* **Unscheduled jobs indicator:** Surface jobs in `Created`/`Assigned` state that have no appointment — a backlog visibility tool.
* **Navigation:** Forward/back by day or week; jump to today.
* **Performance:** Must load within PRD's 3-second screen load target; large date ranges use paging (mechanism deferred to architecture).

---

# Rescheduling Workflow

Rescheduling changes the confirmed date/time (and optionally the technician) of an existing `Scheduled` appointment.

* **Who can reschedule:** Staff and Administrators only. Technicians and Customers cannot reschedule in V1.
* **Steps:**
  1. Staff opens the appointment (from calendar or job details).
  2. Staff selects a new date, time, and/or technician.
  3. System validates the new slot (business hours, future date, conflict check — soft warning).
  4. Staff confirms.
  5. The original appointment is marked `Cancelled` (internally as "rescheduled"); a new appointment is created in `Scheduled` status.
  6. The job remains in `Scheduled` status; the appointment reference on the job is updated to the new appointment.
  7. Reschedule history is retained (old date/time/technician, actor, timestamp).
  8. Customer is notified in-app of the new date/time.
* **Restrictions:**
  * Cannot reschedule a `Completed` or `Cancelled` appointment.
  * A job `In Progress` may be rescheduled (e.g., technician interrupted mid-job and reassigned) — the job returns to `Scheduled`; this is an unusual but valid operational scenario.
* **Customer notification:** Always sent on reschedule (customer needs to know their appointment changed).

---

# Cancellation Workflow

Appointment cancellation removes a booking from the schedule.

* **Who may cancel:** Staff and Administrators only.
* **Reason required:** A cancellation reason is mandatory (free text in V1; a predefined list is a future configuration option).
* **Steps:**
  1. Staff opens the appointment and selects Cancel.
  2. Staff provides a mandatory reason.
  3. Appointment status → `Cancelled`.
  4. If the associated job is still active (not `Completed`/`Cancelled`), the job reverts to `Assigned` (if a technician is still assigned) or `Created` (if the technician is also being removed), signalling it needs rescheduling.
  5. Customer is notified in-app.
  6. Audit entry written.
* **Job impact:** Cancelling an appointment does **not** cancel the job. Staff must separately cancel the job if the work will not proceed. This separation allows a job to be rescheduled without losing its data.
* **Cannot cancel:** A `Completed` appointment (the job is already done).

---

# Technician Interaction

What technicians can do in the scheduling context:

* **View their own schedule** — appointments for today and the current/upcoming week, showing date, time, job reference, customer name, device category, and issue.
* **Open an appointment** — read-only view of appointment details and associated job context (issue, attachments, notes).
* **Start work (transition to In Progress)** — from the appointment view, the technician can mark the job `In Progress`, triggering the job status transition and time recording.
* **Cannot:** Create, reschedule, or cancel appointments. Cannot view other technicians' schedules. Cannot change the technician assignment.

---

# Customer Visibility

Via Repair Status Tracking, for their own job:
* **Confirmed appointment date and time** (once scheduled).
* **Assigned technician name** (first name or full name; no contact details).
* **Appointment status indicator** (e.g., "Appointment confirmed", "Service in progress").
* **In-app notification** when appointment is first scheduled, rescheduled, or cancelled.

Customers **cannot** see: the full calendar, other customers' appointments, technician identity beyond name, internal notes, or any scheduling controls.

---

# Notifications

**Version 1 — in-app.**

| Triggering event | Recipient | Channel |
| --- | --- | --- |
| Appointment created (scheduled) | Customer | In-app |
| Appointment rescheduled | Customer | In-app |
| Appointment cancelled | Customer | In-app |
| Appointment assigned / reassigned | Assigned technician | In-app |
| Technician reassigned via reschedule | Previously assigned technician (removal notice) | In-app |
| Upcoming appointment reminder (same day) | Technician | In-app |

**Future versions** (integrate without redesign): Push (Phase 2), SMS reminders (Phase 2), Email reminders, WhatsApp. Reminder timing and opt-in are future configuration concerns.

---

# Validation Rules

### Appointment creation
* Technician is required and must be an active user with Technician role.
* Date must be a configured working day.
* Start time must fall within business hours.
* End time (start + slot duration) must not exceed business-hours close.
* Date/time must be in the future.
* The associated job must be in `Created` or `Assigned` status.

### Availability validation
* If a technician has an existing `Scheduled`/`In Progress` appointment overlapping the requested slot, a **soft conflict warning** is displayed; staff may acknowledge and proceed.
* Inactive/disabled technicians are not selectable (hard block).

### Conflict validation
* Outside-hours and past-date submissions are hard-blocked server-side (client-side constraint is a UX convenience, not a security control).
* A job may not have two active (`Scheduled`/`In Progress`) appointments simultaneously.

### Rescheduling validation
* Same rules as creation apply to the new slot.
* Cannot reschedule a terminal appointment (`Completed` or `Cancelled`).

### Cancellation validation
* A reason must be provided (non-empty).
* Cannot cancel a `Completed` appointment.
* Cancellation is Staff/Admin-only.

### Settings-change validation
* When working hours are changed, **existing scheduled appointments are not automatically invalidated**; they are flagged if they now fall outside the new hours (implementation to surface a warning list; mechanism deferred to architecture).

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **Scheduling conflict (same technician, overlapping slot)** | Soft warning: "This technician has an existing appointment at this time." Staff may proceed or adjust. | Offer alternative times or a different technician. |
| **Outside business hours (hard block)** | Time picker restricts invalid times; error if submitted directly. | Select a valid time within hours. |
| **Past date/time** | Hard block; clear message. | Select a future date/time. |
| **Inactive technician selected** | Block; technician not shown in list. | Select an active technician. |
| **Concurrent reschedule (two staff reschedule the same appointment)** | Second actor informed the appointment was already changed; shown the current state. | Reload latest; re-confirm against current data. Optimistic concurrency (no locking in V1). |
| **Network failure on save** | No false success; entered data retained; retry offered. | Idempotent retry (no duplicate appointment created). |
| **Job state mismatch (job moved to Completed before scheduling)** | Inform that the job can no longer be scheduled. | Refresh job state; no scheduling action available. |
| **Settings change breaks existing appointments** | Appointments outside new hours are flagged in a warning list (not silently invalidated). | Staff review and reschedule affected appointments. |
| **Session expired** | Prompt re-login; scheduling form data preserved. | Re-authenticate and return to the scheduling form. |

---

# Reporting Requirements

Data captured must enable at least:
* **Appointments scheduled per day / week / technician** (planning and utilization).
* **Technician utilization rate** (booked slots vs. available slots within working hours).
* **Appointment completion rate** (Completed vs. Scheduled in a period).
* **Cancellation rate** and cancellation reasons distribution.
* **Reschedule rate** (appointments changed at least once).
* **Average time from job creation to first appointment** (scheduling responsiveness).
* **Preferred vs. scheduled date variance** (how closely staff honour customer preferences).
* **Unscheduled job backlog** (jobs without an appointment, by age).
* **Conflict warnings acknowledged** (indicator of schedule stress).

---

# Audit Requirements

Auditable events (actor, timestamp; before/after where applicable):
* **Appointment created** (job ref, technician, date/time, created by, timestamp).
* **Appointment rescheduled** (old date/time/technician → new, by whom, reason if provided).
* **Appointment cancelled** (reason, by whom, resulting job status).
* **Technician reassigned on an appointment** (old → new, by whom).
* **Conflict warning acknowledged** (technician, slot, acknowledged by whom — signals operational decision).
* **Permission-denied attempts** on scheduling actions.

**Retention:** Consistent with audit requirements across all features — records retained for the life of the job and per the PRD backup policy.

---

# Security Requirements

* **Authentication:** All scheduling actions require an authenticated session (JWT, non-expiring per Authentication spec).
* **Role permissions (RBAC):**
  * **Staff / Admin:** full create, read, reschedule, cancel across all appointments and technicians.
  * **Technician:** read own schedule only; initiate `In Progress` transition from their schedule view.
  * **Customer:** read own confirmed appointment via Repair Status Tracking only; no scheduling controls.
* **Data visibility:**
  * Customers see only their own appointment date/time/technician name.
  * Internal scheduling data (conflict flags, technician workload, notes) is not exposed to customers.
  * Sensitive job fields (Serial/IMEI) are not surfaced in calendar cards.
* **Audit:** All scheduling actions are logged and attributable.
* **Challenged assumptions:**
  * **Soft conflict warning vs. hard block:** using a soft warning gives staff flexibility but creates a risk of chronic over-booking. For a V1 with a small team this is acceptable; if utilization reporting shows frequent conflicts, hard blocks should be introduced.
  * **No customer self-scheduling in V1:** this is a deliberate scope decision. If customers are given scheduling access, the entire conflict, slot, and availability model must be re-evaluated for a public-facing context.
  * **Settings-change impact on existing appointments:** flagging (not auto-cancelling) is the safest V1 behavior; silently invalidating future appointments would create operational chaos.

---

# Acceptance Criteria

Given/When/Then. Covers scheduling, conflict, rescheduling, cancellation, and permission scenarios.

### Scheduling scenarios

**AC-1 — Create a valid appointment**
* **Given** a job in `Assigned` status, an active technician, and a valid future time slot within business hours
* **When** a Staff member creates the appointment
* **Then** the appointment is saved in `Scheduled` status, the job moves to `Scheduled`, the customer is notified in-app with the date and time, and the appointment appears on the calendar.

**AC-2 — Customer preference pre-filled**
* **Given** a job whose originating request included a preferred date/time
* **When** a Staff member opens the scheduling form for that job
* **Then** the customer's preferred date/time is pre-filled as a suggestion and is editable.

**AC-3 — Technician sees their own schedule**
* **Given** an authenticated technician with scheduled appointments
* **When** they open their schedule view
* **Then** they see only their own appointments with date, time, job reference, customer name, and device/issue; no other technicians' data is visible.

**AC-4 — Calendar shows all appointments**
* **Given** multiple appointments across multiple technicians
* **When** a Staff member views the weekly calendar
* **Then** all appointments are shown and filterable by technician and status.

### Conflict scenarios

**AC-5 — Soft conflict warning**
* **Given** a technician already has an appointment at a given time
* **When** Staff attempt to book the same technician at an overlapping time
* **Then** a soft warning is displayed; Staff may acknowledge and proceed or select a different time/technician.

**AC-6 — Hard block — outside business hours**
* **Given** configured business hours (e.g., 9:00–17:00)
* **When** Staff attempt to schedule at 18:00
* **Then** the time is not selectable and submission is blocked.

**AC-7 — Hard block — past date**
* **Given** any scheduling form
* **When** Staff select a date/time in the past
* **Then** submission is blocked with a clear message.

**AC-8 — Inactive technician not assignable**
* **Given** a disabled technician account
* **When** Staff open the technician selector
* **Then** the disabled technician is not listed; attempting to assign them is blocked.

### Rescheduling scenarios

**AC-9 — Reschedule a valid appointment**
* **Given** a `Scheduled` appointment
* **When** Staff change it to a new valid date/time
* **Then** the old appointment is marked cancelled (rescheduled), a new `Scheduled` appointment is created, the job retains `Scheduled` status with the new appointment, and the customer is notified of the change.

**AC-10 — Reschedule history preserved**
* **Given** an appointment that has been rescheduled
* **When** a Staff member reviews the appointment or job history
* **Then** the original date/time/technician and the actor/timestamp of the change are accessible.

**AC-11 — Cannot reschedule completed appointment**
* **Given** a `Completed` appointment
* **When** Staff attempt to reschedule it
* **Then** the action is blocked.

### Cancellation scenarios

**AC-12 — Cancel requires reason**
* **Given** a `Scheduled` appointment
* **When** Staff attempt to cancel without providing a reason
* **Then** the cancellation is blocked until a reason is entered.

**AC-13 — Cancellation does not cancel the job**
* **Given** Staff cancel an appointment for an active job
* **When** the cancellation completes
* **Then** the appointment is `Cancelled`, the job returns to `Assigned` or `Created` (not `Cancelled`), and Staff can create a new appointment for it.

**AC-14 — Customer notified on cancellation**
* **Given** an appointment is cancelled
* **When** the cancellation is saved
* **Then** the customer receives an in-app notification that their appointment has been cancelled.

### Permission scenarios

**AC-15 — Technician cannot schedule**
* **Given** an authenticated technician
* **When** they look for scheduling controls (create/reschedule/cancel appointment)
* **Then** none are available; they see only their own schedule in read mode.

**AC-16 — Customer cannot access calendar**
* **Given** an authenticated customer
* **When** they attempt to access the scheduling calendar or any other customer's appointment
* **Then** access is denied with no data exposed.

**AC-17 — Settings change flags affected appointments**
* **Given** existing appointments that fall within current working hours
* **When** an Administrator reduces working hours so some fall outside
* **Then** affected appointments are flagged for review, not silently cancelled, and Staff are informed to take action.

---

# V1 Decisions (Resolved)

All scheduling open questions are resolved below, keeping the feature simple and operable for a single-site repair business.

1. **Appointment creation:** Manual by Staff/Admin only. No auto-scheduling engine in V1.
2. **Technician required at scheduling:** Yes — an appointment always has an assigned technician; scheduling and assignment happen together.
3. **Slot type:** Fixed slots derived from Settings slot duration (default 60 minutes). No per-job or per-category duration.
4. **Buffer time:** No automatic buffers. Staff manage gaps manually.
5. **Conflict handling:** Soft warning for technician overlap; hard block for outside-hours and past-date.
6. **Holiday calendar:** Not in V1. Weekly working hours only.
7. **Leave / absence management:** Not in V1. Staff avoid scheduling absent technicians manually.
8. **Capacity per slot:** No enforcement in V1. Staff use judgment.
9. **Appointment lifecycle:** Mirrors job status. No separate `Draft`, `Confirmed`, or `No Show` states.
10. **Rescheduling:** Cancels old appointment and creates a new one; history retained; customer always notified.
11. **Cancellation:** Staff/Admin-only; reason mandatory; does not cancel the job; customer notified.
12. **Customer self-scheduling:** Not in V1 (future enhancement).
13. **Technician access:** Read own schedule + initiate `In Progress` only.
14. **Calendar default view:** Weekly.
15. **Double-booking enforcement:** Soft warning only in V1; hard block is a future enhancement.

*Future enhancements (deferred):* customer self-scheduling, automated availability/leave management, holiday calendar, per-technician/per-category slot durations, buffer times, hard double-booking enforcement, push/SMS appointment reminders, no-show handling, SLA-based scheduling prioritization.
