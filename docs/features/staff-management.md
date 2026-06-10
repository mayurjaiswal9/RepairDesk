# Staff Management - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Staff Management feature must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §4 (User Roles), §6 (Staff Management screen), the **Dashboard** wireframe ("Staff Members — Add and manage technicians and staff members"), [Authentication](authentication.md) (account model, roles, sessions), and the approved V1 decisions in [Gap Analysis](../gap-analysis.md) (GAP-01 Staff Management, GAP-03 default admin, GAP-04 password strength, Permissions Review). Staff Management is the **Administrator** surface for managing all user accounts and roles.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Staff Management |
| **Feature ID** | FEAT-STAFF-007 |
| **Source Requirement** | PRD §4 (User Roles), §6 (Staff Management screen: create/edit/disable users, assign roles), §8 Feature 9 (Admin role administration); Dashboard wireframe ("Staff Members"); Gap Analysis GAP-01 (Staff Management V1 scope), GAP-03 (default admin), GAP-04 (password strength), Permissions Review. |
| **Business Purpose** | Allow an Administrator to manage all user accounts — create, edit, enable, disable, delete — and assign each user exactly one role, so the right people have the right access. |
| **Business Value** | Enables the operational team to onboard/offboard staff and technicians, controls access via role assignment (supporting RBAC across the product), and supplies the **active technician list** that Scheduling and Jobs depend on for assignment. |
| **Related Features** | Authentication & Registration (account model, login, sessions, default admin), Job Management (consumes active technician list for assignment), Scheduling (consumes active technician list), Customer Management (read-only customer view), Notifications, all RBAC-protected features. |
| **Dependencies** | A. Authentication & RBAC (Administrator role; account/session model). B. The default seeded Administrator account (Gap Analysis GAP-03) to bootstrap access. C. Password strength rule (Gap Analysis GAP-04). D. Session termination on disable/delete (Authentication §8). |

**Scope note (V1):** Staff Management governs **all** user accounts (Customer, Technician, Staff Member, Administrator). It is **Administrator-only**. It does not include approval workflows, organizational hierarchy, leave management, or shift management. The active **Technician list** used elsewhere = all enabled accounts with the Technician role.

---

# Business Purpose

Staff Management exists to:

* **Control access** — create accounts and assign exactly one role per user so each person can do only what their role allows.
* **Onboard and offboard** — add new technicians/staff/admins and disable or delete accounts when people leave.
* **Maintain the technician roster** — provide the authoritative list of active technicians that Scheduling and Job Management use for assignment.
* **Preserve accountability** — every account change is attributable and auditable.
* **Stay simple** — direct, immediate administration with no multi-step approval, hierarchy, or HR features in V1.

---

# User Roles

## Administrator
* **Responsibilities**
  * Create, edit, enable, disable, and delete user accounts; assign and change roles; reset/set passwords where needed; keep the technician roster accurate.
* **Permissions**
  * Full access to all Staff Management operations on all accounts (Customer, Technician, Staff Member, Administrator).
* **Restrictions**
  * Must not perform actions that lock the business out of administration (see Business Rules: at least one active Administrator must always exist).
  * Cannot view stored passwords (passwords are write-only; never displayed or returned — Authentication §3/§9).

## Staff Member
* **Permissions**
  * **No access** to Staff Management in V1. Staff manage requests/jobs/scheduling and view customers (read-only), but cannot manage accounts or roles.

## Technician
* **Permissions**
  * **No access** to Staff Management. Technicians are *managed* here (created/edited/assigned), but cannot use the feature.

## Customer
* **Permissions**
  * **No access** to Staff Management. Customer accounts are *managed* here by an Administrator but customers cannot use the feature. Customers self-edit their own mobile/password via Account Settings (Authentication / Gap Analysis DEP-02).

---

# User Stories

### Administrator
1. **Create a user** — *Goal:* add a new account with username, mobile, password, and role. *Motivation:* onboard a new technician/staff/admin. *Outcome:* an active account exists with the chosen role and can sign in.
2. **Assign / change a role** — *Goal:* set or change a user's single role. *Motivation:* promote a customer to technician, or change responsibilities. *Outcome:* the user's access updates to the new role; change is audited.
3. **Edit a user** — *Goal:* update a user's name, mobile, or role. *Motivation:* correct details or reflect a change. *Outcome:* the account record is updated and audited.
4. **Disable a user** — *Goal:* temporarily revoke access without deleting. *Motivation:* a technician is on extended leave or under review. *Outcome:* the account cannot authenticate; any active session ends; data is retained.
5. **Enable a user** — *Goal:* restore access to a disabled account. *Motivation:* the person returns. *Outcome:* the account can authenticate again.
6. **Delete a user** — *Goal:* permanently remove an account. *Motivation:* an account created in error or a permanent offboard. *Outcome:* the account is removed, access permanently revoked, sessions ended; historical records remain attributable.
7. **Find a user** — *Goal:* search/filter the user list. *Motivation:* locate the right account to manage. *Outcome:* a filtered list by name, mobile, role, or status.
8. **See the technician roster** — *Goal:* view active technicians. *Motivation:* confirm who is assignable for scheduling. *Outcome:* a list of enabled Technician-role accounts (the same set used for assignment).

---

# Screen Definitions

## 1. User List Screen
* **Entry point:** Dashboard → **Staff Members** card (Administrator only).
* **Purpose:** Browse, search, and select user accounts to manage.
* **Content:** A list of users showing name (or username), mobile number, **role** (Customer / Technician / Staff Member / Administrator), and **status** (Active / Disabled).
* **Available actions:**
  * **Search/filter** by name, username, mobile, role, or status.
  * **Create User** (primary action).
  * Select a user to open the User Detail/Edit screen.
* **Display rules:** Status and role are clearly indicated (e.g., badges). Paged/lazy-loaded for scale.
* **Navigation exit points:** Create User form; User Detail/Edit screen; back to Dashboard.

## 2. Create User Screen
* **Entry point:** "Create User" on the User List.
* **Purpose:** Add a new account.
* **Fields:**
  * **Username** (required, unique)
  * **Mobile number** (required, valid format, unique)
  * **Name** (optional/display)
  * **Password** (required; min 8 characters per Gap Analysis GAP-04)
  * **Role** (required; one of Customer / Technician / Staff Member / Administrator)
* **Available actions:** Save (create), Cancel.
* **Behavior:** On success the account is **Active** by default and can sign in. Created accounts are not auto-logged-in (that auto-sign-in behavior applies only to self-registration in Authentication §3).
* **Navigation exit points:** Back to User List (with the new user reflected) on success.

## 3. User Detail / Edit Screen
* **Entry point:** Selecting a user from the list.
* **Purpose:** View and modify a single account.
* **Content / editable fields:** Name, mobile number, role. Username is shown (changing username is an Administrator-only edit; see Business Rules). Account status (Active/Disabled) is shown.
* **Available actions (role-appropriate, Administrator only):**
  * **Edit details** (name, mobile, role) → Save.
  * **Assign/Change role.**
  * **Set/Reset password** (subject to the same strength rule).
  * **Disable** / **Enable** the account.
  * **Delete** the account (with confirmation).
* **Display rules:** Destructive/irreversible actions (Delete) require explicit confirmation. Passwords are never displayed; only set/reset is offered.
* **Navigation exit points:** Back to User List after save/disable/enable/delete.

> **Note:** Staff Management edits the **account record**; the customer-facing read view of customers and their repairs lives in [Customer Management](customer-management.md). The two are intentionally separate: Customer Management is read-only context; Staff Management is the write surface for accounts.

---

# Business Rules

1. **Administrator-only:** All Staff Management operations (create/edit/enable/disable/delete/assign role/search) are restricted to Administrators and enforced server-side.
2. **Exactly one role per user:** Every account has exactly one role at a time (Authentication §2). Assigning a new role replaces the previous one.
3. **Four roles supported:** Customer, Technician, Staff Member, Administrator. No custom roles in V1.
4. **Unique username and mobile:** Username and mobile number must be unique across all accounts (Authentication §13). Duplicates are rejected.
5. **Password strength:** Passwords set here must be **at least 8 characters** (Gap Analysis GAP-04). No additional complexity requirement in V1.
6. **Disable vs. delete:**
   * **Disable** is reversible: the account cannot authenticate and any active session ends, but the record and its history are retained. Enable restores access.
   * **Delete** is permanent: the account is removed and access is permanently revoked; active sessions end (Authentication §8). Historical records (requests/jobs/audit) must remain attributable to the (now-removed) account.
7. **Session termination on disable/delete:** Disabling or deleting an account immediately invalidates its session/token on the next protected request (Authentication §8/§9).
8. **At least one active Administrator must exist:** The system must always retain at least one enabled Administrator. An Administrator cannot disable, delete, or demote (change role away from Administrator) the **last** remaining active Administrator — such an action is blocked. (Prevents lock-out; consistent with the seeded default admin model, GAP-03.)
9. **Self-management guardrails:** An Administrator may edit their own profile and password, but cannot delete or disable their own account if they are the last active Administrator (rule 8).
10. **Technician roster definition:** The active technician list consumed by Scheduling/Jobs = all **enabled** accounts with the **Technician** role. Disabling or deleting a technician, or changing their role, removes them from assignable technicians.
11. **Effect on in-flight work:** Disabling/deleting/role-changing a technician must not corrupt existing jobs/appointments. Jobs already assigned to a now-unavailable technician remain but should be flagged for reassignment by Staff (the technician is no longer selectable for *new* assignments). (Consistent with Jobs/Scheduling: only active technicians are assignable.)
12. **No approval workflow:** Account changes take effect immediately; there is no maker/checker or multi-step approval in V1.
13. **No hierarchy / leave / shift management:** No org charts, reporting lines, leave calendars, or shift schedules in V1.
14. **Default Administrator:** A single default Administrator (`admin` / `Admin@1234`) is seeded on first setup and must change its password after first login (Gap Analysis GAP-03; Authentication §7). Staff Management is the surface used to manage all other accounts thereafter.
15. **Password confidentiality:** Passwords are write-only — never displayed, returned, or logged (Authentication / Constitution).

---

# Permissions

| Capability | Customer | Technician | Staff | Administrator |
| --- | --- | --- | --- | --- |
| Access Staff Management | — | — | — | ✓ |
| View user list / search users | — | — | — | ✓ |
| Create user | — | — | — | ✓ |
| Edit user (name, mobile) | — | — | — | ✓ |
| Assign / change role | — | — | — | ✓ |
| Set / reset user password | — | — | — | ✓ |
| Enable user | — | — | — | ✓ |
| Disable user | — | — | — | ✓ |
| Delete user | — | — | — | ✓ |
| View technician roster | — | — | — | ✓ |

* Customers self-edit only their own mobile and password via Account Settings (not via this feature).
* All operations require an authenticated Administrator session and are enforced server-side.

---

# Validation Rules

### Create user
* **Username:** required, non-empty, trimmed, unique. Reject duplicates.
* **Mobile number:** required, non-empty, trimmed, valid mobile format, unique. Reject duplicates.
* **Password:** required, **≥ 8 characters** (GAP-04). No complexity requirement.
* **Role:** required; must be one of Customer / Technician / Staff Member / Administrator.
* **Name:** optional; length-limited if provided.

### Edit user
* Same field rules as create for any changed field. Uniqueness re-checked for username/mobile if changed.
* Role change must resolve to exactly one valid role.
* **Last-admin guard:** changing the last active Administrator's role away from Administrator is blocked.

### Set / reset password
* New password required and **≥ 8 characters**.

### Disable / Enable
* Target account must exist. **Last-admin guard:** disabling the last active Administrator is blocked.

### Delete
* Requires explicit confirmation. **Last-admin guard:** deleting the last active Administrator is blocked.
* Deletion must preserve attributability of historical records.

### General
* Inputs trimmed of leading/trailing whitespace before validation.
* All actions validated against the requester's Administrator role and against the account's current state at execution time (avoid acting on stale data).

---

# Notifications

**Version 1 — in-app, minimal.** Account administration is a low-volume, Administrator-driven activity; notifications are kept to what is operationally useful.

| Triggering event | Recipient | Channel (V1) |
| --- | --- | --- |
| Account created for a user | (Optional) the new user on first sign-in; no proactive channel in V1 | In-app (none required) |
| Role assigned / changed | The affected user (reflected on next session/login via role-appropriate landing) | In-app (implicit; no push) |
| Account disabled / deleted | The affected user's session ends; on next access they are routed to Login | In-app (session termination) |
| Technician assignment availability changed | Reflected in Scheduling/Jobs technician lists | In-app (list refresh) |

* **No email/SMS/push** in V1 (consistent with the product-wide notification decision; email is not collected). Credential handover for created accounts is handled out-of-band by the Administrator (as with the default admin, GAP-03).
* Toast/snackbar confirmations are shown for successful administrative actions ("User created", "Role updated", "User disabled") per the GAP-02 toast model.

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **Duplicate username** | Block create/edit; clear message to choose another username. | Administrator enters a unique username. |
| **Duplicate mobile number** | Block create/edit; clear message to use a different number. | Administrator enters a unique mobile. |
| **Password too short (<8)** | Block; show "Password must be at least 8 characters." | Enter a longer password. |
| **Invalid mobile format** | Block; show format guidance. | Correct the number. |
| **Missing required field** | Block submission; field-level message. | Complete required fields. |
| **Last-admin guard hit** (disable/delete/demote last admin) | Block with explanation that at least one active Administrator must remain. | Assign/keep another active Administrator first. |
| **Acting on a deleted/changed account** (stale state) | Inform the account changed/no longer exists; show current state. | Refresh the list; retry against current data. |
| **Network failure on save** | No false success; entered data retained. | Offer idempotent retry so a user isn't double-created. |
| **Server error (5xx)** | Friendly error; correlation id if available. | Retry later; log for support. |
| **Permission denied** (non-admin attempts access) | Inform the user lacks permission; no account data exposed. | Hide/disable the feature for non-admins; log the attempt. |
| **Session expired/invalid** | Prompt re-login. | Re-authenticate; return to Staff Management. |

**Cross-cutting:** Never show an account change as applied unless persisted. Error messages must not expose sensitive/system internals (Constitution).

---

# Acceptance Criteria

Given/When/Then.

**AC-1 — Create a user**
* **Given** an Administrator on the Create User form with a unique username, unique valid mobile, an 8+ character password, and a selected role
* **When** they save
* **Then** an Active account is created with that single role, appears in the user list, and can sign in.

**AC-2 — Reject duplicate username/mobile**
* **Given** an Administrator creating a user with an already-used username or mobile
* **When** they save
* **Then** creation is blocked with a clear message and no account is created.

**AC-3 — Enforce password minimum**
* **Given** an Administrator setting a password shorter than 8 characters
* **When** they save
* **Then** the action is blocked with a "must be at least 8 characters" message.

**AC-4 — Assign / change role**
* **Given** an existing user
* **When** an Administrator changes their role to another single valid role
* **Then** the user's role is replaced, their access updates accordingly, and the change is audited.

**AC-5 — Edit user details**
* **Given** an existing user
* **When** an Administrator updates the name or mobile (mobile remaining unique and valid)
* **Then** the record updates and is audited.

**AC-6 — Disable a user**
* **Given** an active user with a live session
* **When** an Administrator disables the account
* **Then** the account can no longer authenticate, its active session ends on next protected request, and its data is retained.

**AC-7 — Enable a user**
* **Given** a disabled account
* **When** an Administrator enables it
* **Then** the account can authenticate again.

**AC-8 — Delete a user**
* **Given** an existing account
* **When** an Administrator confirms deletion
* **Then** the account is permanently removed, access is revoked, sessions end, and historical records remain attributable.

**AC-9 — Last-admin protection**
* **Given** only one active Administrator remains
* **When** an Administrator attempts to disable, delete, or demote that account
* **Then** the action is blocked with an explanatory message.

**AC-10 — Technician roster reflects changes**
* **Given** a Technician account
* **When** the Administrator disables/deletes it or changes its role away from Technician
* **Then** that technician no longer appears as assignable in Scheduling/Jobs, while existing assignments remain (flagged for reassignment).

**AC-11 — Role restriction**
* **Given** an authenticated Customer, Technician, or Staff Member
* **When** they attempt to access Staff Management
* **Then** access is denied and no account data is exposed.

**AC-12 — No approval step**
* **Given** an Administrator performing any account change
* **When** they confirm
* **Then** the change takes effect immediately with no secondary approval required.

**AC-13 — Passwords never exposed**
* **Given** any user record
* **When** an Administrator views it
* **Then** no stored password is displayed or returned; only a set/reset action is available.

---

# V1 Decisions (Resolved)

1. **Administrator-only** access to all operations (Gap Analysis GAP-01).
2. **Four roles**, exactly one per user: Customer, Technician, Staff Member, Administrator (Authentication §2).
3. **Disable is reversible; delete is permanent**; both end active sessions (Authentication §8).
4. **At least one active Administrator** must always exist (last-admin guard).
5. **Password minimum 8 characters**, no complexity rule (Gap Analysis GAP-04).
6. **Default admin** `admin` / `Admin@1234`, must change on first login (Gap Analysis GAP-03).
7. **Active technician roster** = enabled Technician-role accounts; consumed by Scheduling/Jobs.
8. **No approval workflow, no organizational hierarchy, no leave management, no shift management** in V1.
9. **No email/SMS/push** for account events; credential handover is out-of-band.

*Future enhancements (deferred):* invitation/email-based onboarding, self-service password-reset emails, approval/maker-checker workflows, organizational hierarchy and reporting lines, leave and shift management, custom roles/granular permissions, multi-role assignment.
