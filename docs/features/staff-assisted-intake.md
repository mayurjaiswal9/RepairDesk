# Staff-Assisted Intake - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* staff-assisted intake must do and *why*. Screen layout lives in [AP-18](../admin-panel/pages/AP-18-create-request.md). API contract lives in [API.md](../API.md) §9 and §12.3.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Staff-Assisted Intake |
| **Feature ID** | FEAT-SAI-001 |
| **Source Requirement** | [gap-analysis.md](../gap-analysis.md) OP-03 / WORK-04 (walk-in deferred from V1); product decision 2026-08-27 to add Create request in the Admin Panel |
| **Business Purpose** | Let staff open a repair for a walk-in, phone-in, or existing customer who did not submit from the app, without leaving the FSM model (request → optional review → job). |
| **Business Value** | Counter and phone work stay in RepairDesk instead of a paper job or a staff account submitting as themselves. Same reference, history, and job lifecycle as digital intake. |
| **Related Features** | Repair Request (FEAT-RR-001), Request Review (FEAT-RV-002), Job Management (FEAT-JOB-003), Customer Management, Notifications |
| **Dependencies** | A. Customer account (existing or created at the counter). B. Device catalog (silent default category). C. Scheduling config (silent next-slot). D. Approve → job (already shipped). |

---

# Why this is a request, not a job

A **Repair Request** is intake. A **Job** is the work order created when intake is accepted.

Staff-assisted intake **creates a request owned by the customer**. It does not create a job directly. The job still appears only through approval — either later from the review queue, or in the same action via **Create and approve**.

That keeps:

* one reference number for request and job
* review, reject, and request-info available when the story is incomplete
* customer history and notifications on the same records as app-submitted work
* no orphan jobs without an originating request

Manual "Create job" on the Jobs list remains **out of scope**.

---

# User Roles

## Staff Member

- Create a request **on behalf of a customer** (never owned by the staff account).
- Choose **Save for review** or **Create and approve**.
- Create a new customer account when the person is not already in the directory.
- Cannot create requests for `DISABLED` or `DELETED` customers.
- Cannot create a request owned by themselves through this feature (use the mobile app for a personal device).

## Administrator

- All Staff capabilities.
- Oversight of generated credentials when a walk-in account is created.

## Technician

- **Cannot** use assisted intake. Counter work is a staff/admin task.
- Assigned jobs from assisted requests look identical to jobs from app-submitted requests.

## Customer

- Does not use this screen.
- Receives the same in-app notifications as a self-submitted request (received / accepted).
- May later sign in to the mobile app with the account created at the counter (after changing a generated password).

---

# In scope

* Find an existing customer by name, username, or mobile, or create a new `CUSTOMER` account.
* Capture **contact + the same device and issue fields as the customer app** (device type, brand, model, issue, optional photos), plus desk-only intake channel. Preferred slot is filled from shop defaults at submit (schedule later). The right rail appears only after a customer is selected — it is context, not a second form.
* Two finishes:
  * **Save for review** → `PENDING_REVIEW`, appears in the Repair Requests queue.
  * **Create and approve** → request `APPROVED` + job `CREATED` in one atomic action.
* Optional priority and internal note when approving immediately.
* Record origin `STAFF_ASSISTED` and the acting staff user for audit.
* Duplicate-within-24h **warning** (non-blocking), same rule as self-submit.

# Out of scope

* Create job without a request.
* Technician-assisted intake.
* Service mode (walk-in vs on-site vs drop-off) — still a single implicit shop model.
* Editing another customer's request after submit (existing editable-window rules apply to the **customer**, not to staff rewriting history).
* Payments, quotes, or inventory.
* Email/SMS of generated passwords (in-app / verbal handover only in this increment).
* Changing the Jobs list to include a Create job button.

---

# User journeys

## J1 — Known customer, incomplete story (phone)

1. Staff opens **Create request** from Repair Requests (or from the customer record).
2. Searches and selects the customer.
3. Enters contact (if different from the account) and the issue.
4. **Save for review**.
5. Lands on request detail. Queue shows the request as Pending. Another (or the same) admin can approve, reject, or request info later.

## J2 — Walk-in, ready to take the device (counter)

1. Staff opens **Create request**.
2. Customer is new → **New customer** (name + mobile). System creates the account.
3. Staff fills contact + issue.
4. **Create and approve** (optional priority / note).
5. Lands on the **job**. Staff assigns a technician and schedules from there.

## J3 — Prefill from customer detail

1. Staff is already on a customer record.
2. **New request** opens Create request with that customer locked in.
3. Continues as J1 or J2.

---

# Form definition

Device and issue fields match the customer app. Preferred slot is filled silently so `POST /requests` still validates (see [AP-18](../admin-panel/pages/AP-18-create-request.md) §4 #25).

| Field | On form? | Required | Notes |
| --- | --- | --- | --- |
| Customer | Yes — section 1 | Yes | Existing account **or** new account created in this flow. Owner of the request. |
| Contact name | Yes — section 2 | Yes | Pre-filled from the customer `name` (or username); **request-local**, does not rewrite the account. |
| Contact mobile | Yes — section 2 | Yes | Pre-filled from the account mobile; request-local. Prefix default 🇮🇳 +91. |
| Contact email | Yes — section 2 | No | Optional. |
| Device type | Yes — section 3 | Yes | Same catalog dropdown as the customer app (`Device type *`). |
| Brand | Yes — section 3 | No | Same `brandMode` behaviour. Disabled until a device type is chosen. |
| Model | Yes — section 3 | No | Same as the app. |
| Issue description | Yes — section 3 | Yes | Same min-length rule as the app. |
| Intake channel | Yes — section 3 | No | Phone / Walk-in / WhatsApp / Other. Desk-only. |
| Photos | Yes — section 3 | No | Same limits as the app. Uploaded after `201`. |
| Equipment / asset | Rail only, after customer | No | May pre-fill device type / brand / model. |
| Preferred date / time | No (silent) | Yes on API | Next valid shop slot. Not shown — schedule later. |
| Priority | Approve dialog only | No | Only on **Create and approve**. Default `NORMAL`. |
| Internal note | Approve dialog only | No | Only on **Create and approve**. |

---

# Customer find-or-create

A request **must** belong to a `CUSTOMER` user. Staff must not own the request.

## Existing customer

* Search the customer directory (`q` over name, username, mobile).
* Selecting a row sets the owner and pre-fills contact name/mobile.
* `DISABLED` / `DELETED` rows are not selectable; show why.

## New customer

Minimum: **name** and **mobile**.

* If `username` is omitted, derive a unique username from the mobile (collision → suffix).
* If `password` is omitted, generate one, set `mustChangePassword = true`, and **show it once** in a dialog so staff can tell the customer. It is not shown again.
* Role is always `CUSTOMER`. Account is `ACTIVE`. Staff are **not** signed in as that customer.
* Duplicate mobile or username → `409` with a field error and a shortcut to select the existing customer.

Staff who cannot use the internal **Add user** screen (`POST /users` is admin-only for internal roles) **can** still create a walk-in customer through this dedicated customer-create path.

---

# Submit modes

## Save for review

* Creates the request as `PENDING_REVIEW`.
* `origin = STAFF_ASSISTED`.
* Customer notification: request received (same copy as self-submit).
* No job yet.
* Operator is taken to request detail (or customer history if they cannot open the queue).

## Create request

* Same create as Save for review (`PENDING_REVIEW`, no job).
* Primary CTA. Lands on request detail so the operator can continue to review / schedule / assign — those steps are not on this screen.

## Create and approve

* **Atomic:** request created, then approved, job created. Failure rolls back so there is no pending request without a job and no job without a request.
* Request `APPROVED`, job `CREATED` / unassigned.
* Optional `priority` and `note` applied as on the existing approve action.
* Customer notification: accepted (the "received" notification is skipped so the customer is not told it is still under review).
* Operator is taken to **job detail** to assign and schedule.

Illegal: Create and approve for a disabled customer; Create and approve with missing required intake fields.

---

# Business rules

1. **Owner is always the customer**, never the acting staff user.
2. **Jobs are never created except through approval** (existing 1:1). Create and approve is still approval, not a new job factory.
3. **Same intake validation** as `POST /requests` today (category active, preferred slot in horizon / not closed / not in the past, issue present).
4. **Duplicate warning** (same customer + same category + similar issue within 24 hours) is non-blocking, same as self-submit.
5. **Idempotency** on submit (no double request if the operator double-clicks).
6. **Audit** records: actor, origin `STAFF_ASSISTED`, customer id, submit mode, resulting request id, job id if approved.
7. **Editable window** for the customer is unchanged (`PENDING_REVIEW` / `INFORMATION_REQUIRED`). A customer can still cancel or edit a staff-created pending request from the app.
8. **Self-approval** of a request the same staff member just created is **allowed** (they are not the customer).
9. Technicians have no intake UI.

---

# Notifications

| Event | Who | Message guideline |
| --- | --- | --- |
| Save for review | Customer inbox | Same as self-submit received. |
| Create and approve | Customer inbox | Same as request approved / accepted. Skip "under review". |
| Job assigned / scheduled / … | Unchanged | Existing job notifications. |

No extra staff inbox item; the queue counters already move.

---

# Acceptance criteria

**AC-SAI-01** — Given I am staff or admin on Create request,
When I submit without a customer selected,
Then the request is not created and the customer control shows a required error.

**AC-SAI-02** — Given I select an existing active customer and complete required fields,
When I choose Save for review,
Then a request is created in `PENDING_REVIEW`, owned by that customer, with origin staff-assisted, and I am taken to that request (or their history).

**AC-SAI-03** — Given the same valid form,
When I confirm Create and approve,
Then one request (`APPROVED`) and one job (`CREATED`) exist with the same reference, and I land on the job.

**AC-SAI-04** — Given Create and approve fails after the request insert,
When the error returns,
Then no request and no job remain (atomic).

**AC-SAI-05** — Given I create a new customer with only name and mobile,
When the account is created,
Then role is `CUSTOMER`, a username exists, `mustChangePassword` is true if the password was generated, and I see the password once.

**AC-SAI-06** — Given a disabled customer,
When I search,
Then I cannot select them as the request owner.

**AC-SAI-07** — Given I open Create request from a customer record,
When the form loads,
Then that customer is selected and not silently replaceable without an explicit Change action.

**AC-SAI-08** — Given I am a technician,
When I open `/requests/new`,
Then I am forbidden.

**AC-SAI-09** — Given required intake fields are missing,
When I submit either mode,
Then no request is sent and field errors are shown.

**AC-SAI-10** — Given a duplicate-within-24h match,
When I submit,
Then I see a warning I can proceed past, and a second request may be created.

---

# Open questions

1. **Staff access to the review queue** — shipped panel is admin-only for `/requests`. This feature grants STAFF create + approve-on-create. Should STAFF also see AP-04 / AP-05? **Recommended yes** so Save for review has a home; until then STAFF who save for review go to customer history.
2. **Generated password handover** — show-once dialog vs print/share. V1 of this feature: show once + copy. No SMS.
3. **Username scheme** for walk-ins without a chosen username (mobile digits vs `c<mobile>`).
4. Whether **preferred date/time** (and category) should appear on the desk form. **Wireframe 2026-09-02: no.** This spec fills them silently so `POST /requests` still validates. Confirm if the shop needs a dedicated "Walk-in / unspecified" category instead of first-active.

---

*Supersedes V1 decision "no walk-in intake" in [PRD.md](../PRD.md) §12, [gap-analysis.md](../gap-analysis.md) OP-03 / WORK-04, and [repair-request.md](repair-request.md) business rule 12.*
