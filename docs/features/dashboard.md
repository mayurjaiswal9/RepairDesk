# Dashboard & Landing Screens - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Feature Specification (implementation-independent)
**Version:** 1.0
**Status:** Draft

> This document defines *what* the Dashboard / landing-screen behavior must do and *why*. It deliberately excludes implementation detail (no Flutter/Spring Boot code, no database schema, no API contracts, no architecture). Source requirements: PRD §6 (Dashboard screen), §7 (User Flow → role-appropriate landing), the **Dashboard** and **Requests & Jobs** wireframes, and the approved V1 decisions in [Gap Analysis](../gap-analysis.md) (GAP-01 Dashboard scope, GAP-02 staff badge counters, Workflow Review landing, OP-06). The Dashboard is a **navigation/quick-access hub**, not an analytics screen.

> **Key V1 decision (GAP-01):** The Dashboard is a **navigation screen, not a data screen**. It provides role-appropriate landing and quick access to operational areas. It is deliberately lightweight — no analytics-heavy dashboards.

---

# Feature Information

| Attribute | Value |
| --- | --- |
| **Feature Name** | Dashboard & Landing Screens |
| **Feature ID** | FEAT-DASH-009 |
| **Source Requirement** | PRD §6 (Dashboard: view requests/jobs, access schedules, access administration); §7 (role-appropriate landing); Dashboard wireframe (tiles: Requests & Jobs, Staff Members, Customers, Products, Shop Settings); Requests & Jobs wireframe (Pending/Approved/Active Jobs counters); Gap Analysis GAP-01 (Dashboard V1 scope), GAP-02 (staff counter badges), Workflow Review (landing per role), OP-06. |
| **Business Purpose** | Route each role to the right landing screen after login and provide a simple navigation hub with quick access to the areas that role can use, plus light summary counts where useful. |
| **Business Value** | Reduces clicks to common tasks, gives staff/admin an at-a-glance workload signal via counters, and provides the closest V1 approximation of an operational overview (Gap Analysis OP-06) without building analytics infrastructure. |
| **Related Features** | Authentication (role + landing routing), Request Review & Job Management (Requests & Jobs hub, counters), Scheduling (technician schedule landing, Schedule tab), Staff Management (Admin tile), Customer Management (tile), Product Management (tile), Settings (tile), Repair Status Tracking (the customer's "dashboard"), Notifications (badge counters). |
| **Dependencies** | A. Authentication & RBAC (the authenticated role determines landing and visible tiles). B. The destination features each tile/landing routes to. C. Summary counts (Pending/Approved/Active Jobs) from Request/Job data. D. Notification/queue counters (GAP-02). |

**Scope note (V1):** This feature defines **landing routing per role** and the **navigation hub** (tiles + light counters). It does **not** introduce charts, trends, KPIs, or analytics. Counters shown are simple counts already defined by other features (e.g., the Requests & Jobs Pending/Approved/Active Jobs counters).

---

# Business Purpose

The Dashboard / landing behavior exists to:

* **Land each role correctly** — after login/auto-login, route the user to the most useful screen for their role.
* **Provide quick navigation** — one hub with tiles to the areas a role can access.
* **Surface light workload signals** — simple counters (Pending / Approved / Active Jobs) and queue badges so staff know where to look first (GAP-02).
* **Avoid complexity** — no analytics, no configurable widgets, no role-specific KPI dashboards in V1.

---

# User Roles

## Customer
* **Landing:** **No separate dashboard.** The customer's landing screen is **Customer Home** (their repair list), defined in [Repair Status Tracking](repair-status-tracking.md) (Gap Analysis GAP-01).
* **Navigation provided there:** list of repairs, "+ New Repair Request", notification bell, Account Settings.
* **Restrictions:** No access to any operational/admin tiles or counters.

## Technician
* **Landing:** Their **personal schedule view** — today's appointments (Gap Analysis GAP-01; Workflow Review). From there they navigate to their job list.
* **Navigation provided:** today's/this week's appointments (from [Scheduling](scheduling.md)), entry to their assigned jobs (from [Job Management](jobs.md)), notifications/alerts (transient, no inbox — GAP-02).
* **Restrictions:** No access to the full calendar, other technicians' schedules, the admin tiles, or staff queues.

## Staff Member
* **Landing:** The **Requests & Jobs** screen (Gap Analysis GAP-01; Workflow Review). The Dashboard wireframe serves as the **navigation hub** staff return to.
* **Navigation provided (Dashboard hub):** **Requests & Jobs** (with Requests / Jobs / Schedule tabs and Pending/Approved/Active Jobs counters), **Customers** (read-only), and notifications via counter badges.
* **Restrictions:** Staff do **not** see Staff Members (Staff Management) or Shop Settings tiles (Administrator scope). Product Management is Administrator-only (see Permissions).

## Administrator
* **Landing:** Same as Staff — the **Requests & Jobs** screen (Gap Analysis GAP-01). The Dashboard hub additionally exposes administration tiles.
* **Navigation provided (Dashboard hub, per wireframe):** **Requests & Jobs**, **Staff Members** (Staff Management), **Customers** (Customer Management), **Products** (Product Management), **Shop Settings** (Settings).
* **Restrictions:** None beyond standard auth; admins inherit all staff navigation plus admin tiles.

---

# User Stories

### Customer
1. **Land on my repairs** — *Goal:* open the app and see my repairs immediately. *Motivation:* I only care about my own repairs. *Outcome:* I land on Customer Home (no operational dashboard).

### Technician
2. **Land on today's work** — *Goal:* see today's appointments on login. *Motivation:* start the day knowing my schedule. *Outcome:* my personal schedule (today) is the landing screen, with a path to my jobs.

### Staff Member
3. **Land on the operational queue** — *Goal:* go straight to Requests & Jobs. *Motivation:* that's where my work is. *Outcome:* I land on Requests & Jobs with Pending/Approved/Active Jobs counters.
4. **Navigate quickly** — *Goal:* jump to Customers or back to Requests & Jobs. *Motivation:* handle a customer query or switch tasks. *Outcome:* the Dashboard hub gives me one tap to each area I can use.
5. **See where to focus** — *Goal:* glance at counters/badges. *Motivation:* know if there's a pending backlog. *Outcome:* counts show Pending requests and Active Jobs at a glance (GAP-02).

### Administrator
6. **Reach administration** — *Goal:* open Staff Members, Products, or Shop Settings. *Motivation:* manage users, catalog, or configuration. *Outcome:* the Dashboard hub shows admin tiles in addition to operational ones.

---

# Screen Definitions

## 1. Role-Based Landing (post-login routing)
* **Trigger:** Successful login, auto-login, or auto-registration sign-in (Authentication §3/§4/§10).
* **Behavior — route by role:**
  * **Customer →** Customer Home (Repair Status Tracking).
  * **Technician →** Personal Schedule (today's appointments).
  * **Staff Member →** Requests & Jobs screen.
  * **Administrator →** Requests & Jobs screen (admin tiles available via the Dashboard hub).
* **Rules:** Landing must reflect the user's **current** role and account status; it never grants access beyond the role (Authentication §10/§11). A disabled/deleted account is routed to Login instead.

## 2. Dashboard Navigation Hub (Staff / Administrator)
* **Entry point:** The "Dashboard"/home navigation control from within the authenticated app (the screen staff/admin return to between tasks). Per the wireframe, titled with the business ("RepairDesk") and a role indicator.
* **Purpose:** Quick access to the areas the role can use.
* **Content — tiles (visibility by role):**

  | Tile | Routes to | Staff | Administrator |
  | --- | --- | --- | --- |
  | **Requests & Jobs** | Requests/Jobs/Schedule (Review, Jobs, Scheduling) | ✓ | ✓ |
  | **Staff Members** | Staff Management | — | ✓ |
  | **Customers** | Customer Management (read-only) | ✓ | ✓ |
  | **Products** | Product Management | — | ✓ |
  | **Shop Settings** | Settings | — | ✓ |

  * Each tile shows a short descriptor (per wireframe), e.g., "Track requests, approve/reject, assign technicians, and manage job status".
* **Light counters (no analytics):** Where useful, surface the simple counts already defined by Request/Job features — **Pending**, **Approved**, **Active Jobs** — and notification queue badges (GAP-02). No charts, trends, or KPIs.
* **Available actions:** Select a tile to navigate; view counters/badges.
* **Navigation exit points:** Into the selected feature; logout.

## 3. Technician Landing (Personal Schedule)
* **Entry point:** Technician login/auto-login.
* **Purpose:** Show today's appointments and provide a path to assigned jobs.
* **Content:** Today's appointments (date, time, job reference, customer name, device/issue summary) from [Scheduling](scheduling.md); entry to the technician's assigned jobs ([Job Management](jobs.md)); transient in-app alerts (no inbox).
* **Available actions:** Open an appointment (start work → In Progress, per Scheduling/Jobs); navigate to assigned jobs.
* **Restrictions:** Read-only on scheduling; no full calendar, no admin tiles.

## 4. Customer Landing
* **Defined elsewhere:** The customer's landing screen is **Customer Home** in [Repair Status Tracking](repair-status-tracking.md). This feature only specifies the *routing* to it; the screen itself is owned by that spec. No customer dashboard tiles exist.

---

# Business Rules

1. **Dashboard is navigation, not analytics (V1):** No charts, KPIs, trends, or configurable widgets. Only navigation tiles and simple, already-defined counts (Gap Analysis GAP-01).
2. **Role-appropriate landing:** Login routes each role to its defined landing screen (Customer Home / Technician Schedule / Requests & Jobs). (Gap Analysis Workflow Review.)
3. **Tile visibility follows RBAC:** Tiles are shown only for areas the role may access. Staff see Requests & Jobs and Customers; Administrators additionally see Staff Members, Products, and Shop Settings. Hidden tiles are also enforced server-side (hiding alone is not security).
4. **Counters reuse existing data:** Pending / Approved / Active Jobs counters are the same counts owned by Request Review and Job Management (per the Requests & Jobs wireframe). Notification/queue badges follow GAP-02 (staff get counter badges, not an inbox).
5. **Customer has no dashboard:** Customer Home replaces any separate customer dashboard concept.
6. **No operational overview screen (V1):** A unified "everything at once" operational view is not built; the Dashboard hub + Requests & Jobs counters are the accepted approximation (Gap Analysis OP-06).
7. **Landing reflects current role/status:** If a user's role changes or account is disabled/deleted, the next landing reflects the new state (or routes to Login) — never stale access (Authentication §10/§11).
8. **Product Management tile is Administrator-only:** Consistent with the permissions model (product catalog management is Admin-only; Gap Analysis Permissions Review). The wireframe's Products tile appears under the Admin view.

---

# Permissions

| Capability | Customer | Technician | Staff | Administrator |
| --- | --- | --- | --- | --- |
| Land on Customer Home | ✓ | — | — | — |
| Land on Personal Schedule | — | ✓ | — | — |
| Land on Requests & Jobs | — | — | ✓ | ✓ |
| See Dashboard hub (tiles) | — | — | ✓ | ✓ |
| Requests & Jobs tile | — | — | ✓ | ✓ |
| Customers tile (read-only) | — | — | ✓ | ✓ |
| Staff Members tile | — | — | — | ✓ |
| Products tile | — | — | — | ✓ |
| Shop Settings tile | — | — | — | ✓ |
| See Pending/Approved/Active Jobs counters | — | — | ✓ | ✓ |
| Notification/queue badges | (own inbox — see Repair Status Tracking) | (transient alerts) | ✓ (badges) | ✓ (badges) |

* Each tile/landing only routes to features the role is authorized to use; all destinations enforce their own RBAC server-side.
* Technician alerts are transient (no inbox); staff/admin signals are counter badges (GAP-02); customer notifications are the inbox defined in Repair Status Tracking.

---

# Validation Rules

* **Landing resolution:** Landing is computed from the authenticated user's **current** role and active status at each app entry; an invalid/disabled/deleted session resolves to Login (Authentication §9/§10).
* **Tile/counter authorization:** Each tile and counter is rendered only if the role is authorized; unauthorized navigation attempts (e.g., deep link to an admin area) are denied server-side regardless of tile visibility.
* **Counter accuracy:** Displayed counts must reflect current server state within the app's refresh/poll cadence; counts are read-only signals (no action taken from the counter itself).
* **No write operations:** The Dashboard performs no data mutations; it only navigates. (Any action is performed in the destination feature.)

---

# Notifications

* **The Dashboard originates no notifications.** It **surfaces** signals defined elsewhere:
  * **Staff/Admin:** queue **counter badges** (Pending, Active Jobs) on the Requests & Jobs hub — no inbox (GAP-02).
  * **Technician:** transient in-app alerts on the schedule landing (e.g., "appointment today", "job assigned") — no inbox (GAP-02; Job/Scheduling notifications).
  * **Customer:** the notification **inbox/bell** on Customer Home — owned by [Repair Status Tracking](repair-status-tracking.md).
* Delivery for badges/alerts follows the GAP-02 polling model (foreground refresh).

---

# Error Handling

| Scenario | User Experience | Recovery Strategy |
| --- | --- | --- |
| **Cannot determine landing** (role/data unavailable) | Fall back to a safe default for the role (e.g., Requests & Jobs for staff/admin; Customer Home for customer); never expose another role's screen. | Retry resolving role on next entry; if session invalid, route to Login. |
| **Counter fetch fails** | Show the hub without counts (or a neutral placeholder), not an error blocking navigation. | Counts populate on next successful poll; navigation remains available. |
| **Network failure on hub load** | Tiles still render where possible (navigation should not be blocked by counters); non-blocking error for missing dynamic data. | Retry; navigation to features still works. |
| **Unauthorized tile/deep link** | Deny access; no restricted data exposed; user kept on an authorized screen. | Log the attempt; show only permitted tiles. |
| **Role changed since login** | On next landing/hub load, reflect the new role's tiles/landing. | Re-resolve role; if downgraded/disabled, route appropriately. |
| **Session expired/invalid** | Prompt re-login. | Re-authenticate; resume at the role-appropriate landing. |

**Cross-cutting:** Missing dynamic data (counters/badges) must never block core navigation; error messages must not expose internal/system details (Constitution).

---

# Acceptance Criteria

Given/When/Then.

**AC-1 — Customer lands on Customer Home**
* **Given** an authenticated Customer
* **When** they log in or auto-login
* **Then** they land on Customer Home (their repair list), with no operational dashboard or admin tiles.

**AC-2 — Technician lands on personal schedule**
* **Given** an authenticated Technician
* **When** they log in
* **Then** they land on their personal schedule showing today's appointments, with a path to their assigned jobs and no admin tiles.

**AC-3 — Staff lands on Requests & Jobs**
* **Given** an authenticated Staff Member
* **When** they log in
* **Then** they land on the Requests & Jobs screen with Pending/Approved/Active Jobs counters.

**AC-4 — Administrator lands on Requests & Jobs with admin tiles available**
* **Given** an authenticated Administrator
* **When** they log in and open the Dashboard hub
* **Then** they land on Requests & Jobs and the hub shows Requests & Jobs, Staff Members, Customers, Products, and Shop Settings tiles.

**AC-5 — Staff tile visibility**
* **Given** a Staff Member on the Dashboard hub
* **When** the hub renders
* **Then** they see Requests & Jobs and Customers tiles but not Staff Members, Products, or Shop Settings.

**AC-6 — Tile navigation**
* **Given** a Staff/Admin on the Dashboard hub
* **When** they select a tile
* **Then** they navigate to the corresponding feature (which enforces its own permissions).

**AC-7 — Counters reflect data**
* **Given** pending requests and active jobs exist
* **When** Staff view the Requests & Jobs counters
* **Then** the Pending/Approved/Active Jobs counts reflect current state within the refresh cadence.

**AC-8 — No analytics**
* **Given** any role's dashboard/landing
* **When** it renders
* **Then** it contains only navigation, quick access, and simple counts — no charts, trends, or KPI widgets.

**AC-9 — RBAC on deep link**
* **Given** a Staff Member
* **When** they attempt to deep-link to an Administrator-only area (e.g., Settings)
* **Then** access is denied server-side regardless of tile visibility.

**AC-10 — Landing reflects current role/status**
* **Given** a user whose role was changed or account disabled
* **When** they next enter the app
* **Then** the landing/tiles reflect the new role, or they are routed to Login if disabled/deleted.

**AC-11 — Counter failure does not block navigation**
* **Given** the hub loads but counters fail to fetch
* **When** the screen renders
* **Then** tiles remain usable and navigation works; counts populate on the next successful refresh.

---

# Open Questions

* **Daily counter polling granularity:** The exact refresh cadence for staff/admin counter badges is inherited from the GAP-02 polling model (foreground ~30s). If counter freshness needs differ from notification freshness, a separate cadence may be tuned during architecture. (Not a blocker — the GAP-02 default applies.)

---

# V1 Decisions (Resolved)

1. **Dashboard = navigation hub, not analytics** (Gap Analysis GAP-01).
2. **Landing by role:** Customer → Customer Home; Technician → personal schedule (today); Staff/Admin → Requests & Jobs (Gap Analysis GAP-01, Workflow Review).
3. **Customer has no separate dashboard;** Customer Home is the customer landing.
4. **Tiles by role** per the wireframe: Staff see Requests & Jobs + Customers; Admin additionally sees Staff Members, Products, Shop Settings.
5. **Light counters only** (Pending/Approved/Active Jobs) reused from Request/Job features; staff signals are badges, not an inbox (GAP-02).
6. **No unified operational overview** screen in V1 (Gap Analysis OP-06).
7. **Product Management and Settings tiles are Administrator-only** (Permissions Review).

*Future enhancements (deferred):* role-specific KPI/analytics dashboards, configurable widgets, a unified operational overview combining requests + unscheduled jobs + scheduled jobs, real-time counter updates.
