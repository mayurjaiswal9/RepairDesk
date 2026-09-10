# RepairDesk - Product Requirements Document (PRD)

**Version:** 1.1
**Status:** Draft (aligned with V1 Gap Analysis — see §16 Change Log)
**Product Type:** Service Management Application
**Frontend:** Flutter
**Backend:** Spring Boot

---

# 1. Product Vision

RepairDesk is a service management platform that enables customers to submit repair requests while allowing internal staff and technicians to manage repair operations from a single application.

The platform aims to digitize repair intake, technician assignment, scheduling, repair tracking, and customer management.

The initial release will prioritize operational simplicity and mobile-first workflows while supporting future expansion to web platforms.

---

# 2. Platform Strategy

Version 1 will be delivered as a Flutter application targeting:

* Android
* iOS
* Tablets

All user roles will access the same application.

Role-based access control will determine available screens, actions, and data visibility.

The architecture must support future deployment to Flutter Web without requiring major redesign.

Backend services will be provided through a Spring Boot REST API.

---

# 3. Business Goals

## Primary Goals

* Digitize repair request intake
* Reduce manual administrative work
* Improve repair request visibility
* Optimize technician assignment
* Improve scheduling efficiency
* Centralize customer and repair records

## Success Metrics

* 90% of repair requests submitted digitally
* Reduced request processing time
* Reduced scheduling conflicts
* Increased repair completion visibility
* Improved technician utilization

---

# 4. User Roles

All users authenticate through a single account system. Any user may self-register, and an Administrator assigns elevated roles (Technician, Staff Member, Administrator). Newly self-registered accounts default to the Customer role. See [Authentication Feature Specification](features/authentication.md) for full details.

## Customer

Customers can:

* Self-register an account (username, mobile number, password)
* Submit repair requests
* Upload photos and videos
* Select preferred appointment dates
* Track repair status

Customers cannot:

* Access operational management screens

---

## Technician

Technicians can:

* View assigned jobs
* Update repair progress
* Add repair notes
* Mark repairs as completed

---

## Staff Member

Staff members can:

* Review repair requests
* Approve or reject requests
* Manage repair jobs
* Assign technicians
* Manage schedules

---

## Administrator

Administrators can:

* Perform all staff functions
* Manage users
* Assign roles to users (Technician, Staff Member, Administrator)
* Disable or delete user accounts
* Manage products
* Configure settings
* Access reporting and administrative features

A single default Administrator account is seeded automatically on first setup. Its default credentials — username **`admin`**, password **`Admin@1234`** — are intended to be handed over to the app holder (ACE Telecom) for initial sign-in and **must be changed after first login**. These credentials are documented in a separate handover note and are not shown in any user-facing UI. See [Staff Management](features/staff-management.md) for account administration and [Authentication](features/authentication.md) §7 for seeding rules.

Account management (create, edit, assign role, enable, disable, delete) is performed by Administrators via [Staff Management](features/staff-management.md). Customer records are viewed (read-only) by Staff and Administrators via [Customer Management](features/customer-management.md).

---

# 5. Wireframe Authority

The approved wireframes form part of this PRD and represent baseline functional requirements.

Implementation must preserve:

* Screen structure
* User workflows
* Required fields
* Navigation patterns
* Core actions

Visual styling may evolve during implementation.

Functional behavior must remain consistent with approved wireframes unless explicitly changed through requirements updates.

---

# 6. Screen Inventory

## Customer Screens

### Repair Request Submission

Purpose:

Allow customers to submit repair requests.

Capabilities:

* Enter customer information
* Describe repair issue
* Upload photos/videos
* Select appointment date
* Submit request

---

### Customer Home (Repair Status Tracking)

See [Repair Status Tracking (Customer Home) Feature Specification](features/repair-status-tracking.md) for the full specification.

Purpose:

Act as the customer's landing screen and let customers view repair progress. There is **no separate "Repair Status Tracking" screen**; Customer Home is the list of the customer's repairs, plus a detail view for a selected repair and a notification inbox.

Capabilities:

* View a list of own repair requests/jobs (most-recently-updated first)
* Open a repair to view its detail (customer-friendly status, appointment, assigned technician name, issue, status history, notification history)
* View repair status using customer-friendly labels
* View assigned technician name (once assigned/scheduled)
* View appointment details (once scheduled)
* View notification inbox (in-app, polling-based)
* Submit a new repair request

Status updates use **polling** (no real-time updates). There is no chat, no customer messaging, and no customer job cancellation in V1.

---

## Internal Screens

### Login

Purpose:

Authenticate application users.

Capabilities:

* Log in with username/mobile number and password
* Register a new account (username, mobile number, password)
* Reset a forgotten password

Branding:

* Display **ACE Telecom** as the company name on the Login screen.

---

### Dashboard

See [Dashboard & Landing Screens Feature Specification](features/dashboard.md) for the full specification.

Purpose:

Provide role-appropriate landing and quick navigation to operational areas. The Dashboard is a **navigation/quick-access hub, not an analytics screen**.

Role-based landing (after login):

* **Customer** → Customer Home (no separate dashboard)
* **Technician** → personal schedule (today's appointments)
* **Staff Member / Administrator** → Requests & Jobs screen

Capabilities (Staff / Administrator navigation hub):

* Navigate to Requests & Jobs (with Pending / Approved / Active Jobs counters)
* Navigate to Customers (read-only)
* Navigate to Staff Members, Products, and Shop Settings (**Administrator only**)
* View light summary counters and notification/queue badges (no charts or KPI analytics)

---

### Requests & Jobs

Purpose:

Manage repair requests and active jobs.

Capabilities:

* View requests
* Approve requests
* Reject requests
* Convert requests into jobs
* Update job status

---

### Schedule

Purpose:

Manage technician schedules and appointments.

Capabilities:

* View appointments
* Assign technicians
* Reschedule jobs

---

### Staff Management

See [Staff Management Feature Specification](features/staff-management.md) for the full specification.

Purpose:

Manage all user accounts and roles. **Administrator only.**

Capabilities:

* Create users (username, mobile, password, role)
* Edit users (name, mobile, role)
* Assign roles (Customer, Technician, Staff Member, Administrator)
* Enable users
* Disable users
* Delete users

No approval workflow, organizational hierarchy, leave management, or shift management in V1.

---

### Customer Management

See [Customer Management Feature Specification](features/customer-management.md) for the full specification.

Purpose:

Provide a **read-only** operational view of customers and their repairs. Available to Staff and Administrators.

Capabilities:

* Search customers
* View customer profile (username, mobile, account status, date registered)
* View repair history (requests, jobs, appointments with status)

Customer Management is **read-only** in V1: no customer editing by staff, no merge accounts, and no advanced CRM functionality. Customers self-edit their own mobile/password via Account Settings; Administrators manage accounts via Staff Management.

---

### Product Management

Purpose:

Manage supported repair categories and products.

Capabilities:

* Add products
* Edit products
* Archive products

---

### Settings

Purpose:

Configure business settings.

Capabilities:

* Working hours
* Appointment durations
* Notification settings
* Business information

---

# 7. User Flow

## Repair Request Lifecycle

Customer submits request

↓

Request Created

↓

Pending Review

↓

Staff Review

↓

Approved / Rejected

↓

Job Created

↓

Technician Assigned

↓

Appointment Scheduled

↓

Repair In Progress

↓

Completed (technician marks done)

↓

Customer Notified

---

# 8. Core Features

## Feature 1: Repair Request Submission

See [Repair Request Feature Specification](features/repair-request.md) for the full specification.

### Required Fields

* Customer Name
* Phone Number
* Device Type
* Issue Description
* Preferred Date
* Preferred Time

### Optional Fields

* Attachments (up to 5 images — optional, not required)

### Validation Rules

* Name required
* Phone number required
* Device type required
* Issue description required
* Attachments optional; maximum 5 if provided

---

## Feature 2: Request Review

See [Request Review Feature Specification](features/request-review.md) for the full specification.

Staff can:

* View requests
* Approve requests (auto-creates a Job, 1:1)
* Reject requests (mandatory reason)
* Request additional information

---

## Feature 3: Job Management

See [Job Management Feature Specification](features/jobs.md) for the full specification.

Staff can:

* Create jobs
* Update status
* Assign technicians
* Add repair notes

---

## Feature 4: Technician Assignment

Technician assignment requirements are fully covered within the [Job Management](features/jobs.md) and [Scheduling](features/scheduling.md) feature specifications. No separate spec document is required for V1.

Staff can:

* Assign technicians (from the Jobs view)
* Reassign technicians (from the Jobs view or Schedule view)
* View technician workload (via Filter by Technician on the Jobs / Schedule tabs)

---

## Feature 5: Scheduling

See [Scheduling Feature Specification](features/scheduling.md) for the full specification.

Staff can:

* Schedule appointments (manual; Staff/Admin only)
* Reschedule appointments (customer notified)
* Cancel appointments (reason required; does not cancel the job)
* View the schedule calendar (daily and weekly views, filterable by technician)

---

## Feature 6: Customer Management

See [Customer Management Feature Specification](features/customer-management.md) for the full specification.

Read-only view (Staff/Admin) of:

* Customer profile (username, mobile, account status, date registered)
* Contact information (the account profile is the authoritative contact record)
* Repair history (requests, jobs, appointments with status)

No customer editing by staff, no merge accounts, no advanced CRM in V1.

---

## Feature 7: Product Management

Manage:

* Device categories
* Supported products

Examples:

* Mobile Phones
* Laptops
* Tablets
* Cameras

---

## Feature 8: Settings Management

Configure:

* Working hours
* Appointment slots
* Business information
* Notification preferences

---

## Feature 9: Authentication & Registration

See [Authentication Feature Specification](features/authentication.md) for the full specification.

Users can:

* Self-register an account with username, mobile number, and password (defaults to Customer role)
* Log in with username/mobile number and password
* Reset a forgotten password
* Log out

The system shall:

* Seed a single default Administrator account on first setup for handover to the app holder
* Let Administrators assign roles (Technician, Staff Member, Administrator) and disable or delete accounts
* Maintain a non-expiring session that remains valid until the user logs out or the account is disabled or deleted
* Enforce role-based access control across all protected screens and actions

---

# 9. Repair Statuses

Statuses are split between the **Request** stage (intake/review) and the **Job** stage (active repair). "Submitted"/"Received" are UI labels for the initial state, not separate statuses.

## Request Statuses

* Pending Review (initial)
* Information Required
* Approved (auto-creates a Job, 1:1)
* Rejected (terminal)
* Cancelled (terminal)

## Job Statuses

* Created (initial, unassigned)
* Assigned
* Scheduled
* In Progress
* Awaiting Parts
* Completed (terminal)
* Cancelled (terminal)

Note: `Ready For Pickup` and `Closed` are **not used in V1**. This is a service business; the technician marks work `Completed` directly. `Completed` is the single terminal state.

Detailed lifecycle and transition rules are defined in the feature specifications: [Repair Request](features/repair-request.md), [Request Review](features/request-review.md), and [Job Management](features/jobs.md).

---

# 10. Non-Functional Requirements

## Performance

* Screen load time under 3 seconds
* Search response under 2 seconds
* Support at least 10,000 repair records

## Security

* JWT Authentication (non-expiring sessions; valid until logout or account removal/disable)
* Self-registration with Administrator-assigned roles
* Password minimum length: 8 characters (no additional complexity requirement in V1)
* Default Administrator seeded on first setup (`admin` / `Admin@1234`); must change password after first login
* Role-Based Access Control
* Secure File Uploads
* Audit Logging

## Reliability

* Daily backups
* Error monitoring
* Recovery procedures

## Usability

* Mobile-first user experience
* Responsive tablet layouts
* Consistent navigation patterns

---

# 11. Technical Constraints

Frontend:

* Flutter

Backend:

* Spring Boot

Database:

* PostgreSQL

API Style:

* REST

Authentication:

* JWT

File Storage:

* To be defined during architecture phase

---

# 12. Assumptions

* Single business deployment for Version 1
* Single application for all user roles
* Mobile-first experience
* Online payments not included in Version 1
* Inventory management not included in Version 1

## V1 Scope Decisions (Resolved)

These decisions keep Version 1 simple and reusable across customers; each can be enhanced later.

* **Notifications:** In-app only (no email/SMS/push/WhatsApp). Email is not collected at registration. Two-layer model: customers get a persisted **notification inbox** (last 30 items, polling ~30s in foreground); staff/technicians get **counter badges / transient alerts** (no inbox). See [Repair Status Tracking](features/repair-status-tracking.md) for the customer inbox.
* **Customer intake:** Customers self-submit in the mobile app. **Staff-assisted / walk-in intake** is specified in [Staff-Assisted Intake](features/staff-assisted-intake.md) (Admin Panel AP-18): staff create a request owned by the customer, then **Save for review** or **Create and approve**. Direct job creation without a request is not supported. No drafts or offline submission.
* **Device selection:** Managed catalog at Category level; brand/model optional free text.
* **Appointments:** Preference-only at intake; 60-day horizon; slot duration from Settings; weekly working hours only (no holiday calendar); no capacity enforcement at intake.
* **Attachments:** Up to 5 images per request; 10 MB/file, 25 MB total; JPEG/PNG/HEIC/WebP.
* **Review:** Approval auto-creates one job (1:1); rejection requires a reason and is terminal; shared review pool; no bulk actions; optimistic concurrency (no locking).
* **Priority:** Low / Normal / High / Urgent; default Normal; set by staff; no SLA targets in V1.
* **Jobs:** Status set is Created → Assigned → Scheduled → In Progress → (Awaiting Parts ⇄ In Progress) → Completed, plus Cancelled. No `Ready For Pickup` or `Closed` in V1. Technicians may mark jobs `Completed` directly. Job Number reuses the Request reference.

---

# 13. Risks

* Large media uploads
* Scheduling conflicts
* High repair volume
* Incorrect technician assignment
* Future scaling requirements

---

# 14. Future Enhancements

## Phase 2

* SMS notifications
* Push notifications
* Technician location tracking
* Online payments

## Phase 3

* Inventory management
* Spare parts tracking
* Invoicing
* Analytics dashboards
* Multi-branch support
* Flutter Web deployment

---

# 15. Wireframe References

The following wireframes are considered source requirements for Version 1:

1. Repair Request Submission
2. Dashboard
3. Requests View
4. Jobs View
5. Schedule View

Future implementation must align with these wireframes unless superseded by approved design updates.

---

# 16. PRD Change Log — V1 Gap Analysis Alignment

The following changes were applied to align this PRD with the approved V1 decisions in [Gap Analysis](gap-analysis.md). Each entry references the relevant gap-analysis item.

| # | Section changed | Change made | Gap-analysis reference |
| --- | --- | --- | --- |
| 1 | §4 Administrator | Added the default Administrator credentials (`admin` / `Admin@1234`), the requirement to change the password after first login, and links to Staff Management / Authentication. Added a statement that account management is done via Staff Management and customer records are viewed read-only via Customer Management. | GAP-03 |
| 2 | §6 Customer Screens — "Repair Status Tracking" | Renamed to **Customer Home (Repair Status Tracking)** and reframed as the customer's landing screen = repair list + detail view + notification inbox (no separate tracking screen). Added customer-friendly status labels, notification inbox, polling-only updates, "submit new request", and a spec link. Noted no chat / no customer messaging / no customer job cancellation. | GAP-01 (Repair Status Tracking / Customer Home), GAP-02, OP-02/OP-05 |
| 3 | §6 Dashboard | Reframed as a **navigation/quick-access hub (not analytics)**; documented role-based landing (Customer → Customer Home; Technician → personal schedule; Staff/Admin → Requests & Jobs); tile visibility by role; light counters/badges; added a spec link. | GAP-01 (Dashboard), GAP-02, OP-06, Workflow Review |
| 4 | §6 Staff Management | Marked **Administrator only**; expanded capabilities to create/edit/assign role/enable/disable/delete with the four supported roles; noted no approval workflow/hierarchy/leave/shift management; added a spec link. | GAP-01 (Staff Management) |
| 5 | §6 Customer Management | Changed from "Update customer information" to a **read-only** view (search, profile, repair history); added authoritative-contact note; stated no editing/merge/CRM; added a spec link. | GAP-01 (Customer Management), DATA-04, PERM-01 |
| 6 | §8 Feature 6 Customer Management | Rewrote as a read-only Staff/Admin view with profile/contact/history; added a spec link and the no-edit/no-merge/no-CRM note. | GAP-01 (Customer Management) |
| 7 | §10 Security (Non-Functional) | Added password minimum length (8 characters, no complexity rule) and the seeded default Administrator note. | GAP-04, GAP-03 |
| 8 | §12 V1 Scope Decisions — Notifications | Documented the two-layer notification model: customer persisted inbox (last 30, polling ~30s) vs. staff/technician counter badges/transient alerts; added a spec link. | GAP-02 |

**Items reviewed and already consistent (no change required):**

* **§8 Feature 1 — Attachments are optional.** The Required Fields list already excludes Attachments and the Optional Fields list marks them "optional, not required." (GAP-06 / DATA-01 — already resolved.)
* **§8 Feature 4 — Technician Assignment** already notes it is covered by Job Management and Scheduling, with cross-links. (DUP-01 — already resolved.)
* **§9 Repair Statuses** already excludes `Ready For Pickup` and `Closed`, with `Completed` as the single terminal job status. (GAP-05 — already resolved.)

**Related feature-file corrections (outside this PRD) already applied in their source files:**

* `repair-request.md`: "Save as draft — not in V1" wording (CON-04), Issue Category / Symptom removed (DATA-02), Service Mode removed (DATA-03), and the downstream job-status chain no longer lists `Ready For Pickup`/`Closed` (GAP-05).
* `jobs.md`: `In Progress → Scheduled` (reschedule mid-job) transition present (CON-02).
