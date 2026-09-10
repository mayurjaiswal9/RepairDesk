# Repair Request - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Repair Request feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Those artifacts are to be derived from this specification in later phases.

---

# Feature Information


| Attribute              | Value                                                                                                                                                                                                                                                                                                                       |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Feature Name**       | Repair Request Submission                                                                                                                                                                                                                                                                                                   |
| **Feature ID**         | FEAT-RR-001                                                                                                                                                                                                                                                                                                                 |
| **Source Requirement** | PRD §6 (Repair Request Submission screen), §8 Feature 1, Wireframe #1                                                                                                                                                                                                                                                       |
| **Business Purpose**   | Provide the primary digital entry point into the repair lifecycle, allowing customers to submit repair requests for devices requiring service.                                                                                                                                                                              |
| **Business Value**     | Digitizes repair intake, reduces manual administrative work, improves request visibility, and feeds a structured, reviewable queue to staff. Directly supports the PRD success metric of *90% of repair requests submitted digitally*.                                                                                      |
| **Related Features**   | Authentication & Registration (FEAT — auth), Request Review (PRD Feature 2), Job Management (Feature 3), Scheduling (Feature 5), Customer Management (Feature 6), Product Management (Feature 7), Repair Status Tracking (PRD §6), Notifications, Settings Management (Feature 8).                                          |
| **Dependencies**       | A. Authenticated Customer account (registration/login). B. Product/Device catalog (Feature 7) to populate device categories. C. Business Settings (Feature 8) for working hours, appointment durations, and capacity. D. File/attachment storage (deferred to architecture phase). E. Notification service (in-app for V1). |


**Critical-path dependency note:** The PRD's original Repair Request fields (Customer Name, Phone Number) predate the decision to make customers authenticated users. With self-registration now in place, some form data can be derived from the customer's account. This tension is addressed in *Form Definition*, *Device Information Requirements*, and *Open Questions*.

---

# User Roles

This feature is primarily a **Customer** capability, but it produces records consumed by internal roles. All four roles are documented because each interacts with the repair request, even if only the Customer creates it.

## Customer

- **Responsibilities**
  - Initiate a repair request for a device requiring service.
  - Provide accurate device, issue, contact, and preferred-appointment information.
  - Attach supporting evidence (photos) where helpful.
  - Submit the request and review the in-app confirmation.
- **Permissions**
  - Create a new repair request.
  - View, and (subject to business rules) cancel, their *own* requests while in an early status.
  - Track status of their own requests.
- **Restrictions**
  - Cannot view or edit requests belonging to other customers.
  - Cannot change request status (approve/reject/assign).
  - Cannot edit a request after it has progressed past the editable window (see *Business Rules*).
  - Cannot access operational/management screens.

## Staff Member

- **Responsibilities**
  - Review submitted requests, validate completeness, and approve, reject, or request additional information.
  - Convert approved requests into jobs (handled in downstream features).
- **Permissions**
  - View all repair requests (when granted queue access).
  - Change request status within their authority (e.g., Pending Review → Approved/Rejected).
  - Add review notes / request more information.
  - Create a request on behalf of a customer ([FEAT-SAI-001](staff-assisted-intake.md)).
- **Restrictions**
  - Should not silently edit customer-entered factual content (device, issue) without an auditable note; corrections must be traceable.
  - Cannot manage users, products, or global settings (Administrator scope).

## Technician

- **Responsibilities**
  - Consume request/job details (device, issue, attachments) as read context to perform repairs.
- **Permissions**
  - View requests/jobs assigned to them, including the originating request details and attachments.
- **Restrictions**
  - Cannot create repair requests on behalf of customers from the technician UI (staff-assisted intake is a Staff/Admin capability — [FEAT-SAI-001](staff-assisted-intake.md)).
  - Cannot approve/reject requests.
  - Cannot view requests unrelated to their assignments (unless broadened by policy).

## Administrator

- **Responsibilities**
  - Configure the inputs this feature depends on: device categories/products, business hours, appointment durations, capacity, and notification settings.
  - Oversee data integrity and access policy.
- **Permissions**
  - All Staff permissions, plus management of products, settings, and users.
  - View all requests and full audit history.
- **Restrictions**
  - Administrative changes to configuration that affect intake (e.g., disabling a device category) must not corrupt previously submitted requests.

---

# User Stories

### Customer

1. **Submit a repair request**
  - *Goal:* Create a request for a malfunctioning device.
  - *Motivation:* Get the device repaired without visiting or calling the shop.
  - *Expected outcome:* A request is created, assigned an initial status, and the customer receives an in-app confirmation with a reference identifier.
2. **Attach photos of the problem**
  - *Goal:* Upload images showing the device/defect.
  - *Motivation:* Help staff understand the issue and reduce back-and-forth.
  - *Expected outcome:* Up to the allowed number of images are attached to the request and visible to staff.
3. **Indicate a preferred appointment time**
  - *Goal:* Suggest a convenient date and time.
  - *Motivation:* Align the repair with the customer's availability.
  - *Expected outcome:* A preferred date/time is captured as a *preference* (not a confirmed booking) for staff to consider.
4. **Track my request after submission**
  - *Goal:* See the current status of a submitted request.
  - *Motivation:* Know whether it has been reviewed/approved and what happens next.
  - *Expected outcome:* The request appears in the customer's tracking view with its current status.
5. **Correct a mistake before review**
  - *Goal:* Edit or cancel a request shortly after submitting.
  - *Motivation:* I entered the wrong device or changed my mind.
  - *Expected outcome:* Editing/cancellation is permitted while the request is still in an early, pre-review status, and is blocked afterward with a clear message.

### Staff Member

1. **Review a newly submitted request**
  - *Goal:* Open a request and assess completeness and validity.
  - *Motivation:* Decide whether to approve, reject, or request more info.
  - *Expected outcome:* The request and its attachments are fully visible; a decision can be recorded with notes.
2. **Request additional information**
  - *Goal:* Ask the customer for missing details.
  - *Motivation:* The submission lacks enough detail to proceed.
  - *Expected outcome:* The request is flagged as needing info; the customer is notified (in-app for V1).

### Technician

1. **Read request context for an assigned job**
  - *Goal:* See the original issue description and photos.
  - *Motivation:* Prepare for and perform the repair accurately.
  - *Expected outcome:* Read-only access to relevant request details and attachments.

### Administrator

1. **Maintain the device catalog that drives intake**
  - *Goal:* Add/edit/archive device categories and products.
  - *Motivation:* Keep the request form options accurate.
  - *Expected outcome:* The repair request form reflects only currently supported, non-archived categories.
2. **Configure appointment availability**
  - *Goal:* Set working hours, durations, and capacity.
    - *Motivation:* Ensure customer preferences map to feasible slots.
    - *Expected outcome:* The form's date/time selection honors configured constraints.

---

# Screen Overview

**Screen:** Repair Request Submission (Wireframe #1).

- **Purpose:** Allow an authenticated customer to compose and submit a repair request in a single, mobile-first flow.
- **Primary actions**
  - Complete the request form (customer info, device info, issue info, appointment preference, attachments).
  - **Submit** the request.
- **Secondary actions**
  - Add / remove attachments.
  - Save as draft — **not in V1** (decided; deferred).
  - Cancel / discard the in-progress form.
  - Inline validation feedback and field-level help.
- **Navigation entry points**
  - From the customer's home/landing screen primary call-to-action ("New Repair Request").
  - Potentially from an empty state in the customer's request-tracking list ("Submit your first request").
- **Navigation exit points**
  - On successful submission → Confirmation view, then to Repair Status Tracking for the new request.
  - On cancel/discard → back to the previous screen (home or tracking list).
  - On session loss → redirect to Login (per Authentication spec), preserving in-progress data where feasible *(candidate — see Open Questions)*.

---

# Form Definition

The form is grouped into logical sections. For eawirech field: **Type**, **Required/Optional**, **Validation**, **Display rules**, **Business rules**.

> **Assumption (documented):** Because customers are now authenticated, contact fields may be pre-populated from the account profile. The PRD form still lists Customer Name and Phone Number explicitly, so they are retained but recommended to be pre-filled and editable. See *Open Questions*.

## Customer Information


| Field                      | Type         | Req/Opt    | Validation                                                                             | Display Rules                                                        | Business Rules                                                                 |
| -------------------------- | ------------ | ---------- | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Customer Name              | Text         | Required   | Non-empty; reasonable max length (e.g., 100 chars); letters/spaces/common punctuation. | Pre-filled from profile if available; editable.                      | Used for identification and communication.                                     |
| Mobile / Phone Number      | Text (phone) | Required   | Non-empty; valid phone format; consistent with account where applicable.               | Pre-filled from the authenticated account's mobile number; editable. | Primary contact channel for V1 (in-app + phone).                               |
| Email                      | Text (email) | Optional   | Valid email format when provided.                                                      | Shown if collected; optional in V1.                                  | Reserved for future email notifications (not collected at registration today). |
| Customer Account Reference | System       | System-set | N/A                                                                                    | Not user-editable; may be hidden.                                    | Associates the request with the authenticated customer for ownership/tracking. |


## Device Information


| Field                  | Type                | Req/Opt                                           | Validation                                                                 | Display Rules                                                                                   | Business Rules                                                                                  |
| ---------------------- | ------------------- | ------------------------------------------------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Device Category / Type | Selection           | Required                                          | Must be a currently supported, non-archived category from the catalog.     | Sourced from Product Management; ordered/labeled per catalog.                                   | The PRD names this "Device Type"; recommended to back it with the managed Device Category list. |
| Brand                  | Selection or Text   | Recommended (see Device Information Requirements) | If selectable, must be a known brand; if free text, length-limited.        | Shown after category selection; may be category-dependent.                                      | Improves routing/diagnostics.                                                                   |
| Model                  | Selection or Text   | Optional (recommended)                            | Length-limited; printable characters.                                      | May depend on selected brand.                                                                   | Helps technicians prepare.                                                                      |
| Serial Number / IMEI   | Text                | Optional                                          | Length/format check; printable characters; treat as potentially sensitive. | Optional field with helper text.                                                                | Aids identification and warranty checks; see security note.                                     |
| Purchase Date          | Date                | Optional                                          | Must not be in the future.                                                 | Date picker.                                                                                    | Supports warranty determination.                                                                |
| Warranty Status        | Selection / Derived | Optional                                          | One of: In Warranty / Out of Warranty / Unknown.                           | May be derived from purchase date if policy known; otherwise user-selectable or left "Unknown". | Influences staff handling; final determination by staff.                                        |


## Issue Information


| Field                    | Type           | Req/Opt                | Validation                                                                                              | Display Rules                                     | Business Rules                               |
| ------------------------ | -------------- | ---------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------------------- | -------------------------------------------- |
| Issue Description        | Multiline text | Required               | Non-empty; minimum meaningful length (e.g., ≥10 chars recommended); maximum length (e.g., 2,000 chars). | Prominent multiline input with character counter. | Core diagnostic input for staff/technicians. |
| ~~Issue Category / Symptom~~ | ~~Selection~~ | **Not in V1** | Removed — no managed list in V1. Free-text Issue Description is sufficient. Deferred. | — | — |
| ~~Severity / Urgency~~ | ~~Selection~~ | **Not in V1** | Removed — priority is set by staff during review, not captured at intake. Deferred. | — | — |


## Appointment Information


| Field                   | Type         | Req/Opt                | Validation                                                                                              | Display Rules                                 | Business Rules                                          |
| ----------------------- | ------------ | ---------------------- | ------------------------------------------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------- |
| Preferred Date          | Date         | Required (per PRD)     | Not in the past; within an allowed booking horizon; on an operating day.                                | Date picker constrained by business calendar. | Captured as a **preference**, not a confirmed booking.  |
| Preferred Time / Slot   | Time or Slot | Required (per PRD)     | Within business hours; aligned to configured slot granularity; respects capacity if enforced at intake. | Slot selector derived from Settings.          | Preference only; staff confirm actual scheduling later. |
| ~~Drop-off / Service Mode~~ | ~~Selection~~ | **Not in V1** | Removed — single implicit service mode in V1. Deferred. | — | — |


## Attachments


| Field       | Type                   | Req/Opt  | Validation                                                                          | Display Rules                                                         | Business Rules                                                |
| ----------- | ---------------------- | -------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------- |
| Attachments | File list (images, V1) | Optional | ≤ 5 files (PRD); supported image types only; per-file size limit; total size limit. | Thumbnail previews; add/remove controls; count indicator (e.g., 3/5). | Evidence to support diagnosis; see *Attachment Requirements*. |


## System / Derived Fields (not user-entered)


| Field                       | Purpose                                                      |
| --------------------------- | ------------------------------------------------------------ |
| Request ID / Reference      | Unique, customer-visible reference for tracking and support. |
| Status                      | Lifecycle state (see *Status Lifecycle*).                    |
| Created Timestamp           | When the request was submitted.                              |
| Created-By (Customer)       | Ownership and audit.                                         |
| Last Updated Timestamp / By | Audit and concurrency.                                       |


---

# Device Information Requirements

Recommendation on each candidate field, with rationale:


| Field                    | Recommendation (V1)                         | Rationale                                                                                                                                                                     |
| ------------------------ | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Device Category**      | **Required.**                               | Aligns with the PRD's "Device Type" and the managed catalog (Mobile Phones, Laptops, Tablets, Cameras). Essential for triage, routing, and reporting "requests per category". |
| **Brand**                | **Recommended (optional→encouraged).**      | Improves diagnosis and parts preparation. Can be free text in V1 if a managed brand list is not ready, then upgraded to selection later.                                      |
| **Model**                | **Optional in V1.**                         | Useful but often unknown to customers; making it required would add friction and hurt the digital-submission target.                                                          |
| **Serial Number / IMEI** | **Optional, treated as sensitive.**         | Strongly aids identification and warranty checks, but customers may not have it at hand. Should be masked/limited in exposure (see Security).                                 |
| **Purchase Date**        | **Optional.**                               | Enables warranty inference; many customers won't recall it, so keep optional.                                                                                                 |
| **Warranty Status**      | **Optional / Derived, finalized by staff.** | Customers cannot authoritatively determine warranty; capture intent but let staff confirm. Avoid making business commitments based solely on customer input.                  |


**Challenge to assumption:** The PRD calls the field "Device Type" but Product Management manages "Device categories" and "products". These should be reconciled so the form draws from the managed catalog rather than free text, ensuring reporting and consistency. Flagged in *Open Questions*.

---

# Appointment Requirements

The appointment captured here is a **customer preference**, not a confirmed booking; final scheduling is owned by Staff (PRD Feature 5). The following rules apply to the preference capture.

- **Date selection rules**
  - Cannot be in the past.
  - Must fall on a configured operating day (non-closed day).
  - Must be within a configurable booking horizon (e.g., today … today + N days). *Assumption: horizon value is a Settings parameter; default TBD.*
- **Time slot rules**
  - Must fall within configured business hours.
  - Aligned to configured slot granularity / appointment duration (Settings, Feature 8).
- **Business hours constraints**
  - Driven by Settings (working hours). Selection outside business hours is blocked or hidden.
- **Holiday constraints**
  - *Assumption (documented):* A holiday/closure calendar may not exist in V1. If unavailable, only weekly working-hours rules apply, and staff manually avoid holidays. Recommend a holiday calendar in Settings (see *Open Questions*).
- **Capacity constraints**
  - *Assumption (documented):* V1 likely does **not** enforce hard capacity at intake because the value is only a preference; staff reconcile capacity during scheduling. If capacity enforcement at intake is desired, it requires defined slot capacity in Settings. Flagged in *Open Questions*.

**V1 decisions (resolved):** the appointment is **preference-only** (no intake-time capacity enforcement); booking horizon is **60 days**; slot granularity comes from Settings **slot duration** (default 60 minutes); there is **no holiday calendar** in V1 (weekly working hours only; staff avoid closures manually). **Service mode is not offered in V1** (single implicit drop-off/walk-in model) — the Service Mode field is deferred.

---

# Attachment Requirements

- **Supported file types (V1):** Images only — recommended JPEG and PNG; consider HEIC/WebP given mobile cameras *(confirm device coverage — Open Questions)*.
- **Maximum upload count:** 5 attachments (per PRD §8 Feature 1).
- **Maximum file size (V1 decision):** **10 MB per file** and **25 MB total** per request. Accepted formats: **JPEG, PNG, HEIC, WebP**. EXIF/location stripping is **not** performed in V1 (deferred); attachment access is still restricted to authorized roles.
- **Security requirements**
  - Validate true file type (not just extension) and reject mismatches.
  - Enforce size/count limits server-side, not only in the UI.
  - Strip or avoid executing any embedded active content; treat uploads as untrusted.
  - Consider stripping location EXIF metadata for privacy *(policy decision — Open Questions)*.
  - Access to attachments must be restricted to the owning customer and authorized internal roles.
- **Storage considerations** *(intent only — not architecture)*
  - Attachments must be retrievable for the life of the request and any retention period.
  - Capacity planning must account for up to 5 images per request at scale (PRD risk: large media uploads).
  - A retention/deletion policy should exist (e.g., when a request is cancelled/closed). *Policy TBD.*
- **Future support (post-V1):** Video, Documents (e.g., receipts/PDF), and Audio. Each adds type validation, larger size limits, and additional security/scanning considerations and should be introduced without redesign of the attachment model.

---

# Validation Rules

Validation must be enforced both at the point of entry (for UX) and authoritatively at submission. Categories below are exhaustive for V1 intent.

### Missing data

- Customer Name missing → block submission.
- Phone/Mobile number missing → block submission.
- Device Category/Type missing → block submission.
- Issue Description missing → block submission.
- Preferred Date missing → block submission (per PRD required fields).
- Preferred Time/Slot missing → block submission (per PRD required fields).

### Invalid data

- Phone number not a valid format → block with format guidance.
- Email present but malformed → block until corrected.
- Issue Description below minimum meaningful length → warn/block per configured minimum.
- Issue Description above maximum length → block; show counter.
- Preferred Date in the past → block.
- Preferred Date outside allowed horizon / non-operating day → block.
- Preferred Time outside business hours or off slot granularity → block.
- Purchase Date in the future → block.
- Device Category not in the active catalog (e.g., archived after form load) → block and prompt to reselect.

### Unsupported files

- File type not an allowed image type → reject that file with a clear reason.
- File exceeds per-file size limit → reject that file.
- Adding a file beyond the max count (5) → prevent and inform.
- Total attachment size exceeds limit → reject the last addition.
- File fails type-authenticity or security check → reject.

### Business rule violations

- Submission while session invalid/expired → block; route to login (state preserved where feasible).
- Duplicate submission (same customer + device + issue within a short window) → flag/handle per *Business Rules*.
- Attempt to edit/cancel after the editable window → block with explanation.
- Selecting an appointment that violates capacity (only if intake capacity enforcement is enabled) → block and offer alternatives.

---

# Business Rules

1. **Authentication required:** Only an authenticated user with the Customer role (or higher) may create a request; the request is owned by that account.
2. **Required information must be provided:** All PRD-required fields must be present and valid before a request can be created.
3. **Appointments cannot be in the past:** Preferred date/time must be now or future, within configured constraints.
4. **Appointment is a preference, not a commitment:** Creating a request does not confirm a booking; staff perform actual scheduling. The customer must not be led to believe a slot is guaranteed.
5. **Catalog-driven device selection:** Device category must come from the active, non-archived catalog at submission time.
6. **Attachment limits:** Maximum 5 image attachments; type/size limits enforced.
7. **Duplicate submission handling (V1):** Detect likely duplicates (same customer + same device category + similar issue within **24 hours**) and **warn** the customer; submission is still allowed (non-blocking).
8. **Device eligibility (V1):** Any **active catalog category is eligible**. No device/brand exclusion rules in V1.
9. **Editable window:** A request may be edited or cancelled by its owner only while in the initial pre-review status; once staff review begins, customer edits are disabled and changes must go through "request additional information".
10. **Immutability of audit trail:** Factual changes after submission (by staff) must be recorded with attribution; original customer input should remain reconstructable.
11. **Configuration changes do not corrupt existing requests:** Archiving a device category or changing settings must not invalidate or alter already-submitted requests.
12. **No sensitive data exposure:** Serial/IMEI and contact data must be shown only to authorized roles.

---

# Workflow

Complete intake workflow, step by step:

1. **Entry** — Authenticated customer opens the Repair Request Submission screen from a primary entry point.
2. **Pre-fill** — Known profile data (name, mobile) is pre-populated; device options are loaded from the active catalog; appointment constraints are loaded from Settings.
3. **Customer enters information** — Customer completes Customer, Device, Issue, and Appointment sections and optionally adds image attachments. Inline validation guides the customer in real time.
4. **Client-side validation** — On attempting to submit, the form validates presence and format of all required fields and attachment constraints; errors are surfaced field-by-field.
5. **Submission** — The customer confirms and submits; the form is locked from further edits during processing to prevent double submission.
6. **Authoritative validation** — The system re-validates all rules (required data, formats, catalog validity, appointment constraints, attachment security, duplicate check) independently of the client.
7. **Request creation** — On success, a request record is created with a unique reference, the initial status, ownership, timestamps, and linked attachments.
8. **Confirmation** — The customer receives an in-app confirmation showing the reference ID and current status, and is offered navigation to status tracking.
9. **Hand-off to review** — The request becomes visible in the staff Requests queue for review (downstream feature).
10. **Failure handling** — If any step fails (validation, upload, network, server), the customer is informed with a recoverable error and entered data is preserved (see *Error Handling*).

```
Customer opens form
        ↓
Pre-fill profile + catalog + settings
        ↓
Customer enters information
        ↓
Client-side validation
        ↓
Submission (form locked)
        ↓
Authoritative validation
        ↓
Request creation (ID, status, ownership, attachments)
        ↓
In-app confirmation
        ↓
Visible to staff for review
```

---

# Status Lifecycle

**V1 decision (resolved):** `Pending Review` is the single **initial status** set at creation. There is no separate `Submitted` state; submission *is* the creation event that places the request into `Pending Review`. Any "Submitted"/"Received" wording is a UI label for this state, not a distinct status.

- **Request statuses owned by this feature and review:**
  - `Pending Review` (initial) → `Information Required` ↔ `Pending Review` → (`Approved` | `Rejected`)
  - `Cancelled` (by customer while in `Pending Review`/`Information Required`)
- **Allowed transitions:**
  - System: create → `Pending Review`.
  - Customer: `Pending Review` / `Information Required` → `Cancelled` (within editable window).
  - Staff: `Pending Review` → `Approved` | `Rejected` | `Information Required`; `Information Required` → `Pending Review` (on customer update).
- **On approval:** a Job is created automatically (1:1). Job statuses (`Created` → `Assigned` → `Scheduled` → `In Progress` → `Awaiting Parts` → `Completed`) belong to the [Job Management spec](jobs.md) and are out of scope here.
- **Ownership of status changes**
  - **System** owns creation-time status and automatic job creation on approval.
  - **Customer** owns cancellation within the permitted window.
  - **Staff/Administrator** own review decisions.

---

# Notifications

**Version 1**

- **In-app confirmation** on successful submission (reference ID + current status).
- In-app status indication when the request changes state (e.g., approved/rejected/needs-info) surfaced in Repair Status Tracking.

**Future versions** (must integrate without redesign)

- **Push notifications** (Phase 2 per PRD) — submission received, status changes.
- **SMS** (Phase 2) — leveraging the mobile number captured at registration.
- **Email** — requires capturing/verifying email (not collected at registration today).
- **WhatsApp** — future channel; requires opt-in and provider integration.

*Assumption (documented):* notification channel preferences and opt-in/consent management are future concerns; V1 relies on in-app only.

---

# Error Handling

For each failure mode: **User experience** and **Recovery strategy**. Entered data must be preserved wherever feasible.


| Failure                             | User Experience                                                                                      | Recovery Strategy                                                                                                                   |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| **Validation failure**              | Field-level inline errors with clear, specific messages; submit blocked; focus moved to first error. | Customer corrects fields and resubmits; no data lost.                                                                               |
| **Attachment/upload failure**       | The specific file is marked failed with reason (type/size/network); other files unaffected.          | Allow retry of the failed file or removal; do not block submission of the rest unless the file is required.                         |
| **Network failure**                 | Non-blocking message: "Couldn't reach the server"; form data retained.                               | Offer retry; consider local retention so the customer can resubmit when back online. *(Offline draft = candidate, Open Questions.)* |
| **Timeout**                         | Indicate the request is taking too long; avoid implying success.                                     | Safe retry that prevents duplicate creation (idempotent submission intent); inform the customer how to verify in tracking.          |
| **Server error (5xx)**              | Friendly error; no false confirmation; reference/correlation id shown if available.                  | Allow retry later; preserve entered data; log for support.                                                                          |
| **Session expired/invalid**         | Inform that re-login is needed.                                                                      | Route to login; restore the in-progress form afterward where feasible.                                                              |
| **Duplicate detected**              | Warn that a similar request appears to exist.                                                        | Let the customer view the existing request or confirm intentional re-submission (per duplicate policy).                             |
| **Catalog item became unavailable** | Inform that the selected device option is no longer available.                                       | Prompt reselection from the refreshed catalog.                                                                                      |


**Cross-cutting:** Never display a success confirmation unless request creation is confirmed. Error messages must not expose sensitive/system internals (per Constitution security principle).

---

# Reporting Requirements

The Repair Request feature must capture data enabling at least the following reports (consumed by Administrator/Staff reporting features):

- **Requests per day / week / month** (volume and trend).
- **Requests per device category** (and per brand if captured).
- **Requests by issue category / symptom** (if captured).
- **Approval rate** (approved vs. rejected of reviewed requests).
- **Rejection reasons distribution** (if reasons are captured at review).
- **Average time from submission to first review** (intake responsiveness).
- **Completion rate** (downstream, but originates from request volume).
- **Digital submission share** (supports the 90% PRD success metric).
- **Attachment usage** (share of requests with attachments; avg per request) — capacity insight.
- **Preferred vs. actual appointment variance** (scheduling effectiveness) — depends on downstream scheduling data.

*Note:* These define **data-capture obligations** for this feature; report design/visualization is out of scope.

---

# Audit Requirements

The following events must be auditable (who, what, when; with before/after where applicable). Aligns with PRD Security: Audit Logging and Constitution security principles.

- **Request created** (by which customer, timestamp, initial status).
- **Request edited** before review (fields changed, old/new values, by whom).
- **Attachment uploaded** (file reference, type, size, by whom).
- **Attachment removed** (which file, by whom, when).
- **Status changed** (from → to, by whom, with reason/notes for review decisions).
- **Request cancelled** (by whom, reason if provided).
- **Additional information requested** (by staff) and **provided** (by customer).
- **Access to sensitive fields** (e.g., serial/IMEI) where policy requires access logging.

Audit records must be tamper-evident and retained per policy; sensitive values must be protected within audit logs.

---

# Security Requirements

- **Authentication**
  - Creating, viewing, editing, or cancelling a request requires an authenticated session (per Authentication spec; JWT, non-expiring until logout/disable/delete).
- **Authorization**
  - Customers may act only on **their own** requests; cross-customer access is denied.
  - Staff/Technician/Administrator access is governed by role-based access control; review/approve/reject restricted to Staff+.
  - Technicians see only request data tied to their assignments (unless policy broadens this).
- **File upload security**
  - Server-side enforcement of type, count, and size; validate true content type; reject mismatches.
  - Treat uploads as untrusted; no execution of embedded content; consider malware scanning for future non-image types.
  - Restrict attachment retrieval to authorized roles; avoid publicly guessable access.
  - Consider EXIF/location metadata stripping for privacy.
- **Data protection**
  - Never expose sensitive information in responses or errors (Constitution).
  - Treat serial/IMEI and contact details as sensitive; minimize exposure and mask where appropriate.
  - Apply retention/deletion policy to request data and attachments.
- **Challenged assumptions**
  - The PRD form collects Customer Name and Phone Number as free text even though the user is authenticated. **Recommendation:** derive contact data from the verified account to reduce spoofing/typo risk; allow edits but record overrides. Flagged in *Open Questions*.
  - "Secure File Uploads" is listed in the PRD but undefined — explicit limits and validation rules (above) are proposed and must be ratified.

---

# Acceptance Criteria

Written in Given/When/Then. Covers happy paths, validation paths, and error paths.

### Happy paths

**AC-1 — Successful submission**

- **Given** an authenticated customer on the Repair Request Submission screen with valid required data (name, phone, device category, issue description, preferred date, preferred time)
- **When** they submit the form
- **Then** a request is created with a unique reference and initial status, the customer sees an in-app confirmation, and the request becomes available to staff for review.

**AC-2 — Submission with attachments**

- **Given** a customer adding up to 5 valid images
- **When** they submit
- **Then** all attachments are stored, linked to the request, and visible to authorized staff.

**AC-3 — Pre-filled contact details**

- **Given** an authenticated customer with profile data
- **When** the form opens
- **Then** name and mobile number are pre-populated and remain editable.

**AC-4 — Track after submit**

- **Given** a successfully submitted request
- **When** the customer navigates to tracking
- **Then** the request appears with its current status and reference.

### Validation paths

**AC-5 — Missing required field**

- **Given** a customer who leaves Issue Description empty
- **When** they attempt to submit
- **Then** submission is blocked with a clear field-level error and no request is created.

**AC-6 — Past appointment date**

- **Given** a customer selecting a date in the past
- **When** they attempt to submit
- **Then** the date is rejected with guidance and submission is blocked.

**AC-7 — Appointment outside business hours**

- **Given** configured business hours
- **When** a customer selects a time outside those hours
- **Then** the selection is blocked or unavailable.

**AC-8 — Too many attachments**

- **Given** a customer who already added 5 images
- **When** they try to add a 6th
- **Then** the addition is prevented with an explanatory message.

**AC-9 — Unsupported file type**

- **Given** a customer selecting a non-image (or disallowed) file
- **When** they attempt to attach it
- **Then** the file is rejected with a clear reason and other attachments are unaffected.

**AC-10 — Invalid phone format**

- **Given** a customer entering an invalid phone number
- **When** they attempt to submit
- **Then** submission is blocked with format guidance.

**AC-11 — Stale catalog selection**

- **Given** a device category archived after the form loaded
- **When** the customer submits
- **Then** the system blocks submission and prompts reselection from the refreshed catalog.

### Error paths

**AC-12 — Network failure on submit**

- **Given** a customer submitting while connectivity is lost
- **When** the submission fails to reach the server
- **Then** the customer sees a non-blocking error, entered data is preserved, and a retry is offered; no false confirmation is shown.

**AC-13 — Server error**

- **Given** the server returns an error during creation
- **When** the customer submits
- **Then** no request is confirmed, data is preserved, and the customer can retry without creating duplicates.

**AC-14 — Session expired**

- **Given** a customer whose session is no longer valid
- **When** they submit
- **Then** they are routed to login and, after re-authenticating, returned to the form with data preserved where feasible.

**AC-15 — Duplicate submission**

- **Given** a customer who recently submitted an identical request
- **When** they submit again within the duplicate window
- **Then** they are warned and offered to view the existing request or confirm intentional re-submission (per ratified policy).

**AC-16 — Edit window enforcement**

- **Given** a request that has entered staff review
- **When** the owning customer attempts to edit or cancel it
- **Then** the action is blocked with an explanation directing them to the appropriate channel.

**AC-17 — Authorization boundary**

- **Given** an authenticated customer
- **When** they attempt to access another customer's request
- **Then** access is denied and no data is exposed.

---

# V1 Decisions (Resolved)

The following decisions resolve the previously open questions. They favor simplicity for the first version and are designed to be reused across customers and enhanced later.

1. **Contact fields:** Name and Mobile are **pre-filled from the authenticated account and editable**. Edits are **request-local** and do **not** modify the account profile.
2. **Email:** **Not collected at registration and not required.** Email remains an **optional** field on the request form (reserved for future email notifications); V1 uses in-app notifications only.
3. **Device selection:** Driven by the **managed catalog at the Category level** (e.g., Mobile, Laptop, Tablet, Camera). **Brand and Model are optional free text** in V1.
4. **Initial status:** Single initial status **`Pending Review`** (no separate `Submitted` state). See Status Lifecycle.
5. **Editable window:** Customer may **edit or cancel while `Pending Review`**, and **edit while `Information Required`**. Once `Approved`/`Rejected`/`Cancelled`, the request is locked to the customer.
6. **Duplicate submissions:** **Warn only (non-blocking).** Detection = same customer + same device category + similar issue within **24 hours**; the customer may proceed.
7. **Device eligibility:** **Any active catalog category is eligible.** No special eligibility rules in V1.
8. **Appointment:** **Preference-only**, **60-day** horizon, slot granularity from Settings **slot duration** (default 60 min), **no intake capacity enforcement**.
9. **Holiday calendar:** **Not in V1** — weekly working hours only.
10. **Attachments:** **≤5 images**, **10 MB/file**, **25 MB total**, formats **JPEG/PNG/HEIC/WebP**. No EXIF stripping in V1. Attachments are **deleted with the request** if the request is deleted.
11. **Drafts / offline:** **Not in V1.** A request is created only on successful submission.
12. **Staff-assisted intake:** Specified in [staff-assisted-intake.md](staff-assisted-intake.md). Staff/Admin create a request **owned by the customer** (find or create the account), then Save for review or Create and approve. Technicians cannot. Self-submit in the app is unchanged.
13. **Severity / issue category at intake:** **Not captured at intake** in V1. **Staff set Priority during review** (see Request Review spec).
14. **Warranty:** Customer may optionally indicate purchase date / warranty; **warranty is confirmed by staff**. No automatic derivation.
15. **Service mode:** **Single implicit mode** in V1 (no selectable walk-in/drop-off/on-site). Field deferred.
16. **Idempotency:** **Required.** The UI prevents double submission and retries must not create duplicate requests.
17. **Sensitive data:** Serial/IMEI is **optional**, visible to **internal roles only**, with no special masking in V1 beyond role-based access. No additional regulatory handling assumed for V1.

*Future enhancements (explicitly deferred):* account-synced contact details, email/SMS/push notifications, managed brand/model lists, holiday calendar, intake capacity, drafts/offline, service modes, EXIF stripping. **Staff-assisted intake is no longer deferred** — see [staff-assisted-intake.md](staff-assisted-intake.md).

