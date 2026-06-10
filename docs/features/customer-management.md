# Customer Management - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Customer Management feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §6 (Customer Management screen), §8 Feature 6, the **Dashboard** wireframe ("Customers — View and manage customer records"), and the approved V1 decisions in [Gap Analysis](../gap-analysis.md) (GAP-01 Customer Management, DATA-04, PERM-01). Customer accounts themselves are created via [Authentication](authentication.md) (self-registration) and managed via [Staff Management](staff-management.md); this feature provides a **read-only operational view** of customers and their repair history.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Customer Management |
| **Feature ID** | FEAT-CUST-006 |
| **Source Requirement** | PRD §6 (Customer Management screen), §8 Feature 6; Dashboard wireframe ("Customers — View and manage customer records"); Gap Analysis GAP-01 (Customer Management V1 scope), DATA-04 (authoritative contact record), PERM-01. |
| **Business Purpose** | Give internal staff a single, searchable view of each customer and their complete repair history so they can identify callers, answer status questions, and make informed operational decisions. |
| **Business Value** | Centralizes customer and repair records (a primary PRD business goal), reduces time spent finding a customer's requests and jobs, supports the phone/in-person contact channel that V1 relies on, and improves service quality without the overhead of a full CRM. |
| **Related Features** | Authentication & Registration (account source of truth), Staff Management (account create/edit/disable/delete), Repair Request (FEAT-RR-001), Request Review (FEAT-RV-002), Job Management (FEAT-JOB-003), Scheduling (FEAT-SCH-005), Repair Status Tracking (customer-facing view), Notifications. |
| **Dependencies** | A. Authentication & RBAC (Staff/Administrator roles). B. Customer accounts (created via self-registration). C. Repair Request, Job, and Appointment records linked to a customer account. D. Search/listing capability scalable to PRD's 10,000+ records. |

**Scope note (V1):** Customer Management is **read-only** for both Staff and Administrators. It does not create, edit, merge, disable, or delete customer accounts — those operations live in Authentication (self-registration), the customer's own Account Settings (self-edit of mobile/password), and Staff Management (Admin account administration). This feature is purely a **lookup and history** view.

---

# Business Purpose

Customer Management exists to:

* **Centralize customer records** — provide one place to find any customer and everything about their repairs.
* **Speed up support** — when a customer calls or visits, staff can locate them and their requests/jobs in seconds (PRD search target <2s).
* **Provide context for decisions** — reviewers and schedulers can see a customer's prior requests, jobs, and account status while reviewing or scheduling.
* **Establish an authoritative contact record** — the account profile (username, mobile) is the canonical contact source, distinct from request-local name/contact overrides (Gap Analysis DATA-04).
* **Stay deliberately simple** — no editing, no merging, no segmentation, no marketing/CRM features in V1.

---

# User Roles

## Staff Member
* **Responsibilities**
  * Look up customers to answer questions, identify callers, and gather context before reviewing requests or scheduling jobs.
* **Permissions**
  * Search customers; view a customer profile; view that customer's repair history (requests, jobs, appointments) with statuses; view account status.
* **Restrictions**
  * **Read-only.** Cannot edit, create, disable, delete, or merge customer accounts.
  * Cannot view sensitive credential data (passwords are never exposed).
  * Cannot change a customer's role or account status (Administrator scope, via Staff Management).

## Administrator
* **Responsibilities**
  * All Staff lookup capabilities; uses Customer Management as the read context that complements account administration done in Staff Management.
* **Permissions**
  * Same read access as Staff (search, profile, repair history, account status).
* **Restrictions**
  * **Read-only within this feature.** Account administration (create/edit/disable/delete/role) is performed in [Staff Management](staff-management.md), not here. (V1 keeps Customer Management as a view-only surface to avoid duplicate, divergent edit paths.)

## Technician
* **Permissions**
  * **No access** to Customer Management in V1. Technicians see the customer name/contact only within the context of jobs assigned to them (per [Job Management](jobs.md)).

## Customer
* **Permissions**
  * **No access** to Customer Management. Customers view only their own profile via Account Settings and their own repairs via [Repair Status Tracking](repair-status-tracking.md). Customers can never view other customers.

---

# User Stories

### Staff Member
1. **Find a customer quickly** — *Goal:* search by name, username, or mobile number. *Motivation:* a customer is calling about their repair. *Outcome:* a matching result list; selecting one opens the profile.
2. **See who I'm dealing with** — *Goal:* open a customer profile. *Motivation:* confirm identity and account status. *Outcome:* username, mobile, account status, and date registered are shown.
3. **Review repair history** — *Goal:* see all of a customer's requests and jobs with current status. *Motivation:* answer "what's happening with my repair?" without switching screens. *Outcome:* a chronological list of the customer's requests and jobs with statuses and references.
4. **Jump to a specific repair** — *Goal:* open a request or job from the customer's history. *Motivation:* take action (review/schedule) on the right record. *Outcome:* navigation into the existing Request/Job detail screen.
5. **Confirm the authoritative contact** — *Goal:* see the canonical mobile number for a customer. *Motivation:* request-level contact may differ. *Outcome:* the account profile is shown as the authoritative contact record.

### Administrator
6. **Look up a customer before administering the account** — *Goal:* view a customer's profile and history. *Motivation:* decide whether to disable an account or correct details. *Outcome:* read context here, then perform the change in Staff Management.

---

# Screen Definitions

## 1. Customer List / Search Screen
* **Entry point:** Dashboard → **Customers** card (Staff/Admin only).
* **Purpose:** Find a customer and open their profile.
* **Content:**
  * A **search field** (search by username, name, or mobile number).
  * A **result list** of customers showing: name (or username), mobile number, account status (Active / Disabled), and a count or indicator of their repairs (e.g., "3 repairs").
  * Default view (no search term): most recently active customers, or an alphabetical/registration-ordered list (implementation choice; paged/lazy-loaded for scale).
* **Available actions:** Enter a search term; clear search; select a customer to open the profile.
* **Empty/`no results` state:** "No customers match your search."
* **Navigation exit points:** Into a Customer Profile; back to Dashboard.

## 2. Customer Profile Screen
* **Entry point:** Selecting a customer from the list.
* **Purpose:** Show the customer's canonical profile and their repair history.
* **Content — Profile section (authoritative account record):**
  * Username
  * Mobile number (authoritative contact — Gap Analysis DATA-04)
  * Account status (Active / Disabled)
  * Date registered
* **Content — Repair history section:**
  * **Requests** — each showing reference number, device category, current status, and submission date.
  * **Jobs** — each showing job/reference number, device category, current status, assigned technician (if any), and scheduled appointment date/time (if any).
  * Ordered most-recently-updated first; requests and jobs may be combined into one timeline or shown in two grouped lists (implementation choice).
* **Available actions:** Open a listed request (→ Request Details / Review), open a listed job (→ Job Details), open a listed appointment context (→ Schedule/Job). All targets respect existing per-feature permissions.
* **Empty state (history):** "This customer has no repair requests yet."
* **Navigation exit points:** Into a Request, Job, or Appointment detail screen; back to the customer list.

> **Note (V1):** There are no edit controls on either screen. The profile reflects the account record (from Authentication/Staff Management) and the linked requests/jobs/appointments. Request-level name/contact overrides are **not** shown as the canonical contact; the account profile is authoritative (Gap Analysis DATA-04).

---

# Business Rules

1. **Read-only feature (V1):** Customer Management never creates, edits, disables, deletes, or merges customer accounts. All such operations belong to Authentication (self-registration), customer Account Settings (self-edit), or [Staff Management](staff-management.md).
2. **No merge accounts (V1):** Duplicate customer accounts are not merged in V1. Each account is shown independently.
3. **Account profile is authoritative:** The customer's account username and mobile number are the canonical contact record. Request-level name/contact edits are "for that repair only" and do not appear here as the authoritative contact (Gap Analysis DATA-04).
4. **Complete history regardless of status:** A customer's repair history shows all their requests and jobs irrespective of status, including terminal ones (Rejected, Cancelled, Completed) — consistent with Staff full-history visibility (Gap Analysis PERM-01).
5. **Customers are users with the Customer role:** A "customer" in this feature is any account currently holding the Customer role. (Staff/Technician/Admin accounts may also have submitted requests as themselves per Gap Analysis WORK-04; whether they appear in the customer list follows the same role rule — only Customer-role accounts are listed.)
6. **No cross-customer exposure:** Opening a customer profile reveals only that customer's data. The feature must never combine or leak another customer's records.
7. **Scale and responsiveness:** Search must return within the PRD's <2s target and the list must load within <3s, scaling to 10,000+ records (paging/lazy-load; mechanism deferred to architecture).
8. **No CRM functionality (V1):** No tags, segments, notes-on-customer, communication log, lifetime-value, or marketing features. Operational notes belong on the request/job, not the customer.

---

# Permissions

| Capability | Customer | Technician | Staff | Administrator |
| --- | --- | --- | --- | --- |
| Access Customer Management | — | — | ✓ | ✓ |
| Search customers | — | — | ✓ | ✓ |
| View customer profile (username, mobile, status, registered date) | — | — | ✓ | ✓ |
| View customer repair history (requests, jobs, appointments) | — | — | ✓ | ✓ |
| View customer account status | — | — | ✓ | ✓ |
| Open a request/job/appointment from history | — | — | ✓ (per record permissions) | ✓ |
| Edit / create / disable / delete / merge a customer account | — | — | — | — (done in Staff Management) |

* All access requires an authenticated session and is enforced server-side (not UI-only), consistent with [Authentication](authentication.md).
* Passwords and other sensitive credential data are never returned or displayed.

---

# Validation Rules

* **Search input:** Trimmed of leading/trailing whitespace before searching. An empty search returns the default list (does not error). Partial matches on name/username/mobile are acceptable.
* **Authorization check:** Every access to the list, a profile, or a history record is validated against the requester's role (Staff/Admin only) at execution time.
* **Record existence:** Opening a profile for a customer that no longer exists (e.g., deleted between list load and selection) returns a clear "customer not found" state, not an error page.
* **No write validation:** There are no input/save operations in V1, so no create/edit validation rules apply within this feature.

---

# Notifications

* **None originate from Customer Management in V1.** Viewing a customer or their history is a passive, read-only action and generates no notifications.
* Notifications related to a customer's repairs are produced by their source features (Repair Request, Request Review, Job Management, Scheduling) and surfaced to the customer via [Repair Status Tracking](repair-status-tracking.md). See the consolidated notification set in [Gap Analysis](../gap-analysis.md) (GAP-02, Notification Review).

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **No search results** | Friendly empty state ("No customers match your search."); not an error. | Adjust the search term. |
| **Customer not found** (selected record deleted) | Inform the customer record is no longer available; return to list. | Refresh the list; pick another customer. |
| **Network failure** (loading list/profile/history) | Non-blocking error; no partial/false data shown. | Offer retry; preserve the current search term. |
| **Server error (5xx)** | Friendly error; correlation id if available; no data fabricated. | Retry later; log for support. |
| **Permission denied** (non-Staff/Admin attempts access) | Inform the user lacks permission; no customer data exposed. | Hide/disable the feature entry for unauthorized roles; log the attempt. |
| **Session expired/invalid** | Prompt re-login. | Route to login; return to the previous Customer Management view afterward. |
| **Slow/large history load** | Show progressive/paged loading rather than blocking. | Lazy-load additional history items on demand. |

**Cross-cutting:** Error messages must never expose sensitive or system-internal information (per Constitution security principles).

---

# Acceptance Criteria

Given/When/Then.

**AC-1 — Search and find a customer**
* **Given** a Staff member on the Customer List screen
* **When** they search by name, username, or mobile number
* **Then** matching customers are returned within the performance target, each showing name/username, mobile, and account status.

**AC-2 — View a customer profile**
* **Given** a Staff member selecting a customer from results
* **When** the profile opens
* **Then** the username, authoritative mobile number, account status, and date registered are displayed.

**AC-3 — View repair history**
* **Given** a customer with prior requests and jobs
* **When** Staff open that customer's profile
* **Then** all of the customer's requests and jobs are listed with reference, device category, current status, and date, ordered most-recently-updated first.

**AC-4 — Open a repair from history**
* **Given** a customer profile with a listed job
* **When** Staff select that job
* **Then** they are taken to the existing Job Details screen for that job (subject to job permissions).

**AC-5 — Read-only enforcement**
* **Given** any Staff or Administrator viewing a customer
* **When** they look for edit/disable/delete/merge controls within Customer Management
* **Then** none are available; account changes are directed to Staff Management.

**AC-6 — Authoritative contact shown**
* **Given** a customer who entered a different name/phone on a specific request
* **When** Staff view the customer profile
* **Then** the account profile's username and mobile are shown as the canonical contact, not the request-level override.

**AC-7 — Complete history including terminal records**
* **Given** a customer with Rejected, Cancelled, and Completed repairs
* **When** Staff view their history
* **Then** all records appear regardless of status (no time limit, no status filtering that hides terminal records).

**AC-8 — Role restriction**
* **Given** an authenticated Technician or Customer
* **When** they attempt to access Customer Management
* **Then** access is denied and no customer data is exposed.

**AC-9 — Customer isolation**
* **Given** a Staff member viewing one customer's profile
* **When** the profile and history render
* **Then** only that customer's records are shown; no other customer's data is visible.

**AC-10 — Empty state**
* **Given** a customer with no repairs
* **When** Staff open their profile
* **Then** a clear empty state ("This customer has no repair requests yet.") is shown instead of an error.

---

# V1 Decisions (Resolved)

1. **Read-only for Staff and Admin** within this feature; account edits live in Staff Management / Account Settings (Gap Analysis GAP-01).
2. **No customer editing by staff**; customers self-edit mobile/password via Account Settings (Gap Analysis DEP-02).
3. **No merge accounts** and **no advanced CRM** in V1.
4. **Account profile is the authoritative contact**; request-level overrides are not canonical (Gap Analysis DATA-04).
5. **Full history visible** regardless of status (Gap Analysis PERM-01).
6. **Technicians and customers have no access** to this feature.

*Future enhancements (deferred):* staff-editable customer details, duplicate detection/merge, customer notes/tags, communication history, segmentation/marketing, customer lifetime metrics.
