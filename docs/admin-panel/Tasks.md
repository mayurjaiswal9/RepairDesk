# Admin Panel — Implementation Task Plan (Batch 1: AP-01 … AP-04 · Batch 2 started)

**Workstream:** Angular Admin Panel (`admin-web/`), backend unchanged
**Specs:** [README.md](README.md) · [Design-System.md](Design-System.md) · [pages/](pages/) · [API-Contract-Gaps.md](API-Contract-Gaps.md)
**Batch 1 scope:** [AP-01 Sign In](pages/AP-01-login.md) · [AP-02 App Shell](pages/AP-02-app-shell.md) · [AP-03 Dashboard](pages/AP-03-dashboard.md) · [AP-04 Repair Requests](pages/AP-04-repair-requests.md)

> **Sequence:** every page is **mocked up and signed off before any implementation begins**. Phase M produces reviewable mockups, gate `AP00-M3` is the approval point, and only then does Phase 0/1 code start. Nothing in Phase 1 should begin while a mockup is still in review.

### Batch 1 mockup status

| ID | Task | Status | Artifact |
| --- | --- | --- | --- |
| AP00-M1 | Mockup harness & shared stylesheet | Done | [`mockups/`](mockups/) · [`mockup.css`](mockups/mockup.css) · [`index.html`](mockups/index.html) |
| AP00-M2 | Component sheet | Done (signed off) | [`mockups/AP00-components.html`](mockups/AP00-components.html) |
| AP01-M1 | Sign In mockup | Done (signed off) | [`mockups/AP-01-login.html`](mockups/AP-01-login.html) |
| AP02-M1 | App Shell mockup | Done (signed off) | [`mockups/AP-02-app-shell.html`](mockups/AP-02-app-shell.html) |
| AP03-M1 | Dashboard mockup | Done (signed off) | [`mockups/AP-03-dashboard.html`](mockups/AP-03-dashboard.html) |
| AP04-M1 | Repair Requests mockup | Done (signed off) | [`mockups/AP-04-repair-requests.html`](mockups/AP-04-repair-requests.html) |
| **AP00-M3** | **Mockup review & sign-off (gate)** | **Done** | Review complete 2026-08-07 — Phase 0/1 unblocked |

### Batch 1 implementation status

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| **APS-001** | Angular workspace scaffold | **Done** | `admin-web/` — Angular 19 + Material, standalone, lazy routes |
| **APS-002** | Design tokens & theme | **Done** | Tokens in `src/styles.scss` (from Design-System / mockup.css) |
| **APS-003** | HTTP layer, error mapping & correlation IDs | **Done** | `authInterceptor`, `mapHttpError`, typed `AuthApi` |
| **APS-004** | Session, guards & role visibility | **Done** | `SessionService` + `auth`/`guest`/`mustChange` guards |
| **APS-005** | Shared component kit | Partial | Shell/dashboard use shared nav config; full kit still open |
| **APS-006** | Environment config | **Done** | `environment.ts` / `.prod.ts` → `/api/v1`; CORS includes `:4200` |
| **APS-007** | Test harness & CI | Pending | — |
| **APB-008** | `GET /jobs` active / scheduled filters | **Done** | `active=true`, `scheduled=false` (+ comma `status`) |
| **AP01-D1** | Login page (form, branding, returnUrl) | **Done** | `features/auth/login` |
| **AP01-D2** | Role gate + forced change-password | **Done** | Rejects `CUSTOMER`; `/change-password` when `mustChangePassword` |
| **AP01-D3** | Forgot-password entry | **Done** | Uses shipped `POST /auth/reset-password` (see APB-003) |
| **AP01-V1** | Verify every `AC-AP01-nn` | In progress | `ng build` green; `POST /auth/login` as admin succeeds against local API — full AC checklist still open |
| **AP02-D1** | Shell layout | **Done** | Minimal shell for Batch 1: sidebar, top bar, avatar menu, collapse |
| **AP02-D2** | Role-based nav + guards | **Done** | Shared `nav.config.ts`; technician → `/jobs` |
| **AP02-D3** | Notification bell polling | **Done** | 30s unread-count; pauses when tab hidden |
| **AP02-D4** | Route-change a11y | Partial | Skip link + sticky titles; focus-to-h1 polish still open |
| **AP03-D1** | Counters, banner, quick links | **Done** | `GET /dashboard/counters` + role-gated quick links |
| **AP03-D2** | Deep links, skeletons, failure placeholder | **Done** | Active/unscheduled job filters; visible Retry + correlationId |
| **AP03-V1** | Verify every `AC-AP03-nn` | In progress | Build green; smoke against local API next |
| **APB-006** | Rejection reason codes | **Done** (interim) | Panel list: UNABLE_TO_SERVICE + Tasks decision 11 codes |
| **APB-007** | `q` (+ category) on `GET /requests` | **Done** | Search + `deviceCategoryId` on queue endpoint |
| **AP04-D1** | Request table, paging, URL state | **Done** | `features/requests/requests-list` |
| **AP04-D2** | Status filter, search, chips | **Done** | Default Pending; 350ms debounce |
| **AP04-D3** | Row menu + 3 decision dialogs | **Done** | Approve / Reject / Request info + 409 handling |
| **AP04-D4** | Page states + awaiting count | **Done** | Skeletons, empty, filtered-empty, error, forbidden |
| **AP04-V1** | Verify every `AC-AP04-nn` | In progress | Build green; manual AC pass next |

**Why these four first:** they're a dependency chain rather than an arbitrary slice. Sign-in gates everything; the shell hosts every later page; the dashboard is the landing target after login; and Repair Requests is the first page that does real operational work, exercising the full list-page pattern (table, filters, server paging, row actions, confirmation dialogs, all five states) that AP-06, AP-10, AP-12, AP-13 and AP-14 then reuse.

---

## How to read this plan

- **ID scheme:** `AP<page><type><n>` — traceable straight back to the specs and their acceptance criteria.
  - `AP00-*` cross-page / shared work
  - `M` = mockup · `D` = develop · `V` = verify
  - `APB-*` blockers (backend, docs, or product decisions) that must clear before the dependent page ships
  - `APS-*` project setup / foundation
- **Layer tag:** `[FE]` Angular · `[BE]` Spring Boot · `[DOC]` documentation · `[DEC]` product/security decision · `[INFRA]` config/tooling
- **Effort:** `S` ≈ 2h · `M` ≈ 4h · `L` ≈ 1 day. (Larger than the units in [../Tasks.md](../Tasks.md), which used S≈1h — this workstream's tasks are page-sized.)
- **Definition of done** for any `D` task: the referenced spec's element inventory is fully built, all five canonical page states from [Design-System §6](Design-System.md) are implemented, no raw error text can reach the user, list/filter state lives in the URL where the spec says so, and the page is keyboard-operable.
- **Definition of done** for any `V` task: every `AC-<page>-nn` in that page's spec §8 is demonstrably passing, and any that cannot pass is recorded as a spec deviation with a reason — not silently skipped.

---

# Phase M — Mockups & sign-off

Goal: agree what these four pages look and behave like **before** committing to Angular code. Mockups are throwaway artifacts whose only job is to make disagreement cheap.

**Approach (assumption to confirm):** static HTML + CSS mockups in `docs/admin-panel/mockups/`, no framework and no build step, using the real design tokens. This is deliberate — a static mockup opens in a browser from disk, can be iterated in minutes, doesn't presuppose the Angular scaffold, and can be thrown away without sunk cost. The alternative (mocking up directly in Angular with stub services) gives higher fidelity but makes every review round expensive and creates pressure to keep whatever was built. It follows the precedent already in the repo at `docs/tej-infotech-mockup/index.html`.

**Mockups must show real states, not just the happy path.** Every mockup task below lists its required states explicitly, because states invented during implementation are exactly where this project has previously shipped bugs (silent counter failures, raw error dumps, letter-by-letter text wrapping).

### AP00-M1 · Mockup harness & shared stylesheet `[FE]` (S)
**Description:** Create `docs/admin-panel/mockups/` with an `index.html` linking every mockup, and one shared `mockup.css` implementing the tokens from [Design-System §1–3](Design-System.md) as CSS custom properties.
**Dependencies:** None
**Acceptance Criteria:**
- Opens directly in a browser from the filesystem; no server or build step.
- Every colour, spacing, radius, and type value comes from `mockup.css` custom properties — no hardcoded hex in page mockups.
- Token values match `frontend/lib/shared/theme/app_theme.dart` exactly, so mobile and web stay one product.
- `index.html` lists the component sheet plus the four page mockups with one-line descriptions.

### AP00-M2 · Component sheet mockup `[FE]` (M) — **Under review**
**Description:** A single page rendering every shared component from [Design-System §5](Design-System.md) at production size, so the four page mockups compose from an agreed kit instead of each inventing its own buttons.
**Dependencies:** AP00-M1
**Artifact:** [`mockups/AP00-components.html`](mockups/AP00-components.html)
**Status:** Under review.
**Acceptance Criteria:**
- Buttons (primary/secondary/destructive/busy/disabled), form inputs with label + helper + error, select, checkbox, search input.
- Data table at both 48px comfortable and 36px compact density, with sticky header, sortable column, row overflow menu, and pagination footer.
- Status chips for **every** value in [Design-System §5.2](Design-System.md) — all request, job, appointment, account, priority, warranty and archive states — plus role badges. Confirms `NORMAL` priority renders nothing.
- Key–value block with its fixed 160px label column, including an em-dash for a missing optional value.
- Dialog (standard and destructive), toast (success and sticky error), filter bar with active-filter chips.
- All five canonical page states: loading skeleton, empty, filtered-empty, error with `correlationId`, forbidden.
- AA contrast verified for every text-on-fill combination; no status conveyed by colour alone.

### AP01-M1 · Sign In mockup `[FE]` (S) — **Under review**
**Description:** Mock up [AP-01](pages/AP-01-login.md) including its non-obvious paths.
**Dependencies:** AP00-M2
**Artifact:** [`mockups/AP-01-login.html`](mockups/AP-01-login.html) — review bar switches Default / Session / Error / Customer blocked / Disabled / Submitting / Forgot / Change password.
**Status:** Under review.
**Acceptance Criteria:**
- Default state with ACE Telecom branding prominent (per [features/authentication.md](../features/authentication.md) §12), username + masked password with show/hide.
- Shows the deliberate absences: **no** registration link, and the agreed decision on remember-me.
- States: submitting, invalid credentials (generic, non-enumerating), **customer account blocked** with its explanatory message, disabled account, network/server error.
- Forced change-password step shown as its own screen.
- Forgot-password entry point and its enumeration-safe generic confirmation.

### AP02-M1 · App Shell mockup `[FE]` (M) — **Under review**
**Description:** Mock up [AP-02](pages/AP-02-app-shell.md) — the chrome every later page lives in, so this mockup sets the frame for all of Batch 2 as well.
**Dependencies:** AP00-M2
**Artifact:** [`mockups/AP-02-app-shell.html`](mockups/AP-02-app-shell.html) — role ADMIN/STAFF/TECHNICIAN, rail toggle, avatar menu, 404/403, 99+ badge.
**Status:** Under review.
**Acceptance Criteria:**
- Sidebar expanded (240px) and collapsed rail (64px, icons + tooltips), with the active item's indication.
- Three role variants side by side: `ADMIN` (all groups), `STAFF` (no Repair Requests, Staff, Catalog, Settings), `TECHNICIAN` (Jobs and Appointments only).
- Top bar: brand, breadcrumb, notification bell with an unread badge and its `99+` cap, avatar menu open showing name, `@username`, role badge, My Account, Sign out.
- The 404 not-found and 403 forbidden pages, rendered inside the shell.
- Rendered at 1440px, 1024px, and 768px per [Design-System §7](Design-System.md).

### AP03-M1 · Dashboard mockup `[FE]` (S) — **Under review**
**Description:** Mock up [AP-03](pages/AP-03-dashboard.md) as a navigation hub — no charts or KPI widgets ([PRD §6](../PRD.md) is explicit that the dashboard is not analytics).
**Dependencies:** AP02-M1 *(started early with a simplified shell frame; full shell mockup still pending)*
**Artifact:** [`mockups/AP-03-dashboard.html`](mockups/AP-03-dashboard.html) — open in a browser; use the review bar to switch **Loaded / Loading / Error / All-zero** and **ADMIN / STAFF**.
**Status:** Under review — awaiting feedback before sign-off.
**Acceptance Criteria:**
- Four counter cards using the **real** payload field names (`pendingReview`, `infoRequired`, `activeJobs`, `unscheduledJobs`), each visibly clickable with its deep-link target labelled.
- The unscheduled-jobs warning banner in its visible state.
- **Counter-failure state shown explicitly** as a dashed placeholder with Retry — never a hidden card. This is the page's headline requirement; a hidden card reads as "no work queued".
- Loading skeletons for the counter row.
- Role-gated quick links matching AP02-M1's three variants.

### AP04-M1 · Repair Requests mockup `[FE]` (L) — **Under review**
**Description:** Mock up [AP-04](pages/AP-04-repair-requests.md), the reference implementation of the list-page archetype. Getting this one right sets the pattern for five later pages, so it warrants the most review attention.
**Dependencies:** AP03-M1
**Artifact:** [`mockups/AP-04-repair-requests.html`](mockups/AP-04-repair-requests.html) — Pending/All table, row menus, three decision dialogs, loading/empty/filtered-empty/error/forbidden.
**Status:** Under review.
**Acceptance Criteria:**
- Table with realistic data across all six request statuses, showing the two-line Customer and Device cells, the Job column for approved rows, and the attachment count indicator.
- Filter bar with the segmented status filter defaulting to **Pending**, search input, and active-filter chips with Clear all.
- Row overflow menu open in both variants: a decidable request (Approve / Request info / Reject / Open detail) and a resolved one (Open detail only).
- All three decision dialogs — Approve (with priority and note), Reject (reason code + customer message), Request info — including a busy confirm button.
- States: loading skeleton, empty, filtered-empty naming the query, error with `correlationId`, forbidden for a staff user.
- A deliberately long customer name proving single-line truncation with ellipsis, at 1024px and 768px.
- Pagination footer with page position, total count, and page-size selector.

### AP00-M3 · Mockup review & sign-off `[DEC]` (S) — **GATE**
**Description:** Walk the five mockups against each spec's element inventory and acceptance criteria, capture decisions, and **update the specs to match whatever was agreed**. The specs remain the source of truth; a mockup that diverges without a spec update creates two conflicting sources.
**Dependencies:** AP01-M1, AP02-M1, AP03-M1, AP04-M1
**Acceptance Criteria:**
- Each of the four pages is explicitly signed off, or has a written list of required changes.
- Every agreed change is reflected in the relevant page spec (element inventory, states, or acceptance criteria) and in [Design-System.md](Design-System.md) if a shared component changed.
- Decisions previously left open that the mockups force — remember-me, technician landing page, whether the bell ships in Batch 1 — are recorded as resolved or explicitly deferred.
- Phase 0 is not started before this gate closes.

---

# Phase B — Blockers to clear (can run in parallel with Phase M)

These are not Angular work, but Batch 1 cannot **ship** without them. Each names the page it blocks. Full detail in [API-Contract-Gaps.md](API-Contract-Gaps.md).

| ID | Task | Layer | Effort | Blocks | Notes |
| --- | --- | --- | --- | --- | --- |
| **APB-001** | Correct [API.md](../API.md) for the verified drift affecting Batch 1: login body/response, dashboard counters payload, and the `/users` verb and activate/deactivate paths | `[DOC]` | M | AP-01, AP-03 | Gaps §1.1–1.8. Cheapest task here and it unblocks treating API.md as the contract. |
| **APB-002** | Ratify JWT storage strategy for a web client with non-expiring tokens | `[DEC]` | S | AP-01, AP-02 | Gaps §3.3. Determines remember-me, 401 handling, and session lifetime — all three are in AP-01/AP-02. |
| **APB-003** | Fix `POST /auth/reset-password`: require proof of account ownership, or withdraw the public endpoint | `[BE]` | M | AP-01 (forgot-password) | Gaps §3.1. **Treat as a release blocker for internet exposure regardless of this panel** — currently any username, including `admin`, can be taken over. |
| **APB-004** | Add rate limiting to login and password reset | `[BE]` | M | AP-01 | Gaps §3.2. Nothing currently emits the documented `429`. |
| **APB-005** | Decide whether `STAFF` may access the review queue | `[DEC]` | S | AP-02 nav, AP-04 guard | Gaps §4.1. Docs and shipped code disagree; changes only the guard and nav visibility. |
| **APB-006** | Provide the canonical rejection `reasonCode` list | `[DEC]` | S | **Done (interim)** — panel ships UNABLE_TO_SERVICE + Tasks decision 11 codes |
| **APB-007** | Add `q` (and optionally category) filtering to `GET /requests` | `[BE]` | M | **Done** — `q` + `deviceCategoryId` on admin queue |
| **APB-008** | Add multi-status or `active=true` filtering to `GET /jobs` | `[BE]` | M | **Done** — `active=true` / `scheduled=false` / comma `status` on `GET /jobs` |
| **APB-009** | Confirm the `mustChangePassword` contract and the seeded admin's actual value | `[BE/DOC]` | S | AP-01 forced-change flow | Gaps §4.7. [Database.md](../Database.md) §9 says `true`; the seed reportedly inserts `false`, so the flow may never fire. |
| **APB-010** | Add the Angular dev origin (`http://localhost:4200`) and the deployed panel origin to `CORS_ALLOWED_ORIGINS` | `[INFRA]` | S | All pages | Config only. Current default covers `:3000` and `:8081`. |
| **APB-011** | Decide whether staff/admin receive notifications at all | `[DEC]` | S | AP-02 bell | Gaps §4.2. Verified that every notification currently targets a customer or a technician, so the bell is decorative for admins. Deferring means shipping the bell knowing it reads zero. |

---

# Phase 0 — Foundation (starts after AP00-M3)

Goal: a running, themed, authenticated Angular shell with the shared kit in place, so page work is page work rather than plumbing.

### APS-001 · Angular workspace scaffold `[FE]` (M) — **Done**
**Description:** Create `admin-web/` with standalone components, lazy-loaded feature routes mirroring the backend's package-by-feature layout, strict TypeScript, and the route skeleton from [README §4](README.md).
**Dependencies:** AP00-M3
**Acceptance Criteria:**
- App builds, serves, and renders a placeholder for each Batch 1 route.
- Every route from README §4 exists (later ones as placeholders), all deep-linkable.
- Strict mode on; lint and formatter configured and passing.

### APS-002 · Design tokens & theme `[FE]` (S) — **Done**
**Description:** Port `mockup.css` tokens into the application theme (Angular Material theming plus global custom properties), including both table densities.
**Dependencies:** APS-001, AP00-M2
**Acceptance Criteria:**
- Tokens exist in exactly one place; no component hardcodes a colour or spacing value.
- Values match `app_theme.dart`.
- Typography scale from [Design-System §2](Design-System.md) available as classes or mixins.

### APS-003 · HTTP layer, error mapping & correlation IDs `[FE]` (M) — **Done**
**Description:** Typed HTTP services with an interceptor attaching `Authorization: Bearer`, plus central mapping of the standard error envelope to the presentation rules in [README §6.2](README.md).
**Dependencies:** APS-001, APB-002
**Acceptance Criteria:**
- Every `errorCode` in README §6.2 maps to its specified presentation; `422 fieldErrors[]` bind to form controls by field name and unmatched ones surface in a form-level summary.
- **No code path can render a raw exception string, stack trace, or JSON dump.** Covered by a unit test.
- `correlationId` is available to error views; `409` exposes a reload affordance.
- A single `401` clears the session and redirects to `/login?returnUrl=…` exactly once, without a redirect loop.

### APS-004 · Session, guards & role visibility `[FE]` (M) — **Done**
**Description:** Auth/session service, route guards, and a role-visibility mechanism for nav and controls.
**Dependencies:** APS-003, APB-002, APB-005
**Acceptance Criteria:**
- Guards enforce the per-route roles in README §4; a direct URL entry by an unauthorized role renders the **page-level forbidden state**, not a toast or a blank page.
- `CUSTOMER` accounts cannot enter the shell.
- Client-side gating is treated as UX only — a server `403` still renders forbidden even if the client believed otherwise.

### APS-005 · Shared component kit `[FE]` (L)
**Description:** Implement the component sheet from AP00-M2 as reusable components: data table wrapper (server paging/sorting/URL state), status chip, role badge, key–value block, dialog service, toast service, filter bar, page-state panels, attachment gallery/viewer.
**Dependencies:** APS-002, APS-003
**Acceptance Criteria:**
- Status chip covers every value in Design-System §5.2 from one shared mapping; `NORMAL` priority renders nothing; an unrecognized value degrades to a neutral chip rather than breaking layout.
- Data table reflects filter/sort/page state in the URL and restores from it on refresh and browser Back.
- Key–value block renders an em-dash for missing values and never emits the string "null".
- Text cells truncate to a single line with a tooltip — a spaceless 30-character name must not wrap per character.
- Dialogs trap focus, restore it to the trigger on close, close on `Esc`, and disable the confirm button while in flight.
- Attachment images load via authenticated blob fetch, never `<img src>`, and object URLs are revoked on destroy.

### APS-006 · Environment config `[INFRA]` (S) — **Done**
**Description:** Environment files for local, dev-cloud, and prod API base URLs.
**Dependencies:** APS-001, APB-010
**Acceptance Criteria:**
- API base URL is build-time configurable; no URL is hardcoded in a component or service.
- Local config targets the local backend; dev config targets the deployed Azure Container Apps backend.
- Verified against the running dev-cloud API with CORS in place.

### APS-007 · Test harness & CI `[INFRA]` (M)
**Description:** Unit test setup with HTTP mocking, plus a CI job running lint, build, and tests.
**Dependencies:** APS-001
**Acceptance Criteria:**
- Unit tests can assert on service calls and mapped errors without a live backend.
- CI fails on lint errors, type errors, or failing tests.
- A documented convention for citing an `AC-<page>-nn` ID in a test name, so `V` tasks are auditable.

---

# Phase 1 — Page implementation

### AP-01 Sign In

| ID | Task | Layer | Effort | Status | Dependencies |
| --- | --- | --- | --- | --- | --- |
| **AP01-D1** | Login page: layout, branding, reactive form, masked password with show/hide, submit and busy states, `returnUrl` handling with open-redirect protection | `[FE]` | M | **Done** | APS-004, APS-005 |
| **AP01-D2** | Role gate and forced change-password: reject `CUSTOMER` before the shell ever renders, and route `mustChangePassword` accounts to the change-password step | `[FE]` | M | **Done** | AP01-D1, APB-009 |
| **AP01-D3** | Forgot-password entry with enumeration-safe confirmation copy | `[FE]` | S | **Done** | AP01-D1, APB-003 |
| **AP01-V1** | Verify every `AC-AP01-nn` | `[QA]` | S | In progress | AP01-D1…D3 |

**AP01-V1 acceptance criteria:** all 17 criteria in [AP-01 §8](pages/AP-01-login.md) pass; a customer account never sees the shell even momentarily; an invalid password and an unknown username are indistinguishable in the response shown; no password value appears in any log or in browser devtools storage.

### AP-02 App Shell

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP02-D1** | Shell layout: sidebar with grouped nav, collapse-to-rail with persistence and tooltips, top bar with breadcrumb and avatar menu | `[FE]` | L | **Done** |
| **AP02-D2** | Role-based nav visibility, route guard wiring, 401 interception behavior, and the 404 / 403 routes | `[FE]` | M | **Done** |
| **AP02-D3** | Notification bell: 30s unread-count polling, `99+` cap, and its route link | `[FE]` | S | **Done** |
| **AP02-D4** | Route-change a11y: focus moves to the page `<h1>`, browser tab title updates, skip-to-content link | `[FE]` | S | Partial |
| **AP02-V1** | Verify every `AC-AP02-nn` | `[QA]` | S | Pending |

**AP02-V1 acceptance criteria:** all 17 criteria in [AP-02 §8](pages/AP-02-app-shell.md) pass; the three role variants show exactly the nav items specified; the entire shell is operable by keyboard alone; polling stops when the tab is hidden and does not accumulate timers across navigations.

### AP-03 Dashboard

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP03-D1** | Counter cards from `GET /dashboard/counters` using the real field names, the unscheduled-jobs banner, and role-gated quick links | `[FE]` | M | **Done** |
| **AP03-D2** | Counter deep links to filtered lists, plus loading skeletons and the **visible** failure placeholder with Retry | `[FE]` | M | **Done** |
| **AP03-V1** | Verify every `AC-AP03-nn` | `[QA]` | S | In progress |

**AP03-V1 acceptance criteria:** all 15 criteria in [AP-03 §8](pages/AP-03-dashboard.md) pass; **a failed counter fetch is visibly distinguishable from a genuine zero** — the specific regression this page exists to prevent; each card lands on a list filtered to what it counted, or its deep link is deliberately absent pending APB-008.

### AP-04 Repair Requests

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP04-D1** | Request table: columns per the element inventory, server-side paging and sorting, URL-driven state, row navigation with real anchor semantics | `[FE]` | L | **Done** |
| **AP04-D2** | Filter bar: segmented status filter defaulting to Pending, 350ms debounced search, active-filter chips, Clear all | `[FE]` | M | **Done** |
| **AP04-D3** | Row action menu and the three decision dialogs (approve with priority/note, reject with reason code and customer message, request info), including `409` stale-decision handling | `[FE]` | L | **Done** |
| **AP04-D4** | All five page states plus the stale-decision inline marker, and the "awaiting your decision" count sourced from the counters endpoint | `[FE]` | M | **Done** |
| **AP04-V1** | Verify every `AC-AP04-nn` | `[QA]` | M | In progress |

**AP04-V1 acceptance criteria:** all 15 criteria in [AP-04 §8](pages/AP-04-repair-requests.md) pass; approving a request creates its job and the row reflects it; a stale decision is refused with a reload path rather than silently overwriting; filtered-empty never claims that no requests exist; a long customer name truncates rather than wrapping per character at every supported width.

### AP-05 Request Detail

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP05-D1** | Detail layout: breadcrumb, record header, two-column anatomy, device KV with masked serial reveal, issue prose, right panels (job / contact / preferred appointment) | `[FE]` | L | **Done** |
| **AP05-D2** | Information-exchange block, rejection message card, resolved-state line, action bar gated by status (`Request info` only from `PENDING_REVIEW`) | `[FE]` | M | **Done** |
| **AP05-D3** | Authenticated photo gallery (blob → object URL), per-tile retry, lightbox with keyboard nav; reuse AP-04 decision dialogs with in-place refetch + toasts | `[FE]` | L | **Done** |
| **AP05-D4** | Loading skeletons, refetch bar, forbidden / not-found / error panels, stale `409` marker | `[FE]` | M | **Done** |
| **AP05-V1** | Verify every `AC-AP05-nn` | `[QA]` | M | Pending |

**AP05-V1 acceptance criteria:** all 15 criteria in [AP-05 §8](pages/AP-05-request-detail.md) pass; photos never use a bare authenticated `<img src>`; serial remasks on blur/nav; approve stays on the record and surfaces the Job panel link.

### AP-06 Jobs

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP06-B1** | Backend: `GET /jobs` supports `q`, `technicianId`, `unassigned=true`; Pageable sort; technician list still scoped server-side | `[BE]` | M | **Done** |
| **AP06-D1** | Jobs table: columns, server paging/sort, URL state, real row anchors to `/jobs/{id}` | `[FE]` | L | **Done** |
| **AP06-D2** | Filter bar: status segments, 350ms search, technician dropdown (incl. Unassigned), sort; tech role hides technician filter and titles page "My jobs" | `[FE]` | M | **Done** |
| **AP06-D3** | Empty / filtered-empty / technician-empty / error / forbidden / skeleton + refetch states; unassigned count in summary | `[FE]` | M | **Done** |
| **AP06-V1** | Verify every `AC-AP06-nn` | `[QA]` | M | Pending |

**AP06-V1 acceptance criteria:** all 16 criteria in [AP-06 §8](pages/AP-06-jobs.md) pass; dashboard `active` / `scheduled=false` deep links still work; technicians cannot widen the board via URL params.

### AP-07 Job Detail

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP07-D1** | Detail layout: breadcrumb, header (created+updated), status action bar, device/contact with masked serial, notes, dual galleries, right panels | `[FE]` | L | **Done** |
| **AP07-D2** | Status transitions + assign/unassign/priority/warranty/note dialogs with `409` reload path | `[FE]` | L | **Done** |
| **AP07-D3** | Job attachment upload (per-file errors), customer photos read-only, appointment panel → AP-08 placeholder | `[FE]` | M | **Done** |
| **AP07-D4** | Role gating (tech vs staff/admin), forbidden/not-found/error/skeleton/refetch states | `[FE]` | M | **Done** |
| **AP07-V1** | Verify every `AC-AP07-nn` | `[QA]` | M | Pending |

**AP07-V1 acceptance criteria:** all 22 criteria in [AP-07 §8](pages/AP-07-job-detail.md) pass; adding a note bumps **Updated**; technicians never see assign/cancel/schedule controls.

### AP-08 Schedule / Reschedule Appointment

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP08-D1** | Schedule form: mode from `currentAppointmentId`, job strip, technician/date/slot pickers driven by `scheduling-config` (Mon=0, closed days, horizon, today past slots) | `[FE]` | L | **Done** |
| **AP08-D2** | Create / reschedule / cancel-appointment flows, soft overlap warning, customer preference "Use this", reassign-via-cancel path | `[FE]` | L | **Done** |
| **AP08-D3** | Forbidden / not-found / blocked (terminal, no techs, no config, all closed) + form error summary with reload on `409` | `[FE]` | M | **Done** |
| **AP08-V1** | Verify every `AC-AP08-nn` | `[QA]` | M | Pending |

**AP08-V1 acceptance criteria:** all 18 criteria in [AP-08 §8](pages/AP-08-schedule-appointment.md) pass; closed days and out-of-hours times are unreachable in the picker even though the server does not reject them.

### AP-09 Appointments Calendar

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP09-D1** | Modern week/day calendar: glass toolbar, shaded hours, today + now line, appointment blocks as real job links, agenda below 1024px | `[FE]` | L | **Done** |
| **AP09-D2** | URL state (`view`/`date`/filters), client-side tech/status/search, staff `GET /appointments` vs tech `GET /me/schedule` | `[FE]` | M | **Done** |
| **AP09-D3** | Unscheduled rail → `/jobs/{id}/schedule`, empty/filtered-empty/error/forbidden, PageUp/PageDown range nav | `[FE]` | M | **Done** |
| **AP09-V1** | Verify every `AC-AP09-nn` | `[QA]` | M | Pending |

**AP09-V1 acceptance criteria:** all 18 criteria in [AP-09 §8](pages/AP-09-appointments.md) pass; Mon=0 closed-day mapping matches config; entries are real anchors to job detail.

### AP-10 Customers

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP10-D1** | Customer directory: 350ms search (`q` omitted when empty), table with truncating name/username, client-side page/size in URL | `[FE]` | M | **Done** |
| **AP10-D2** | Admin-only reset password + delete dialogs via `/users/{id}`; STAFF read-only (no ⋮ menu) | `[FE]` | M | **Done** |
| **AP10-D3** | Empty / filtered-empty / error / forbidden / stale-row states | `[FE]` | S | **Done** |
| **AP10-V1** | Verify every `AC-AP10-nn` | `[QA]` | M | Pending |

**AP10-V1 acceptance criteria:** all 16 criteria in [AP-10 §8](pages/AP-10-customers.md) pass; unfiltered load never sends `q`; password never appears in toast/URL/logs.

### AP-11 Customer Detail

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP11-D1** | Customer detail: independent profile + history loads, breadcrumb with list query restore, two-column layout | `[FE]` | M | **Done** |
| **AP11-D2** | History tables as real anchors to AP-05/AP-07; empty / per-section empty / error / deleted banner | `[FE]` | M | **Done** |
| **AP11-D3** | Admin Account actions (reset/delete) reuse AP-10 dialogs; delete redirects to `/customers` with history counts | `[FE]` | S | **Done** |
| **AP11-V1** | Verify every `AC-AP11-nn` | `[QA]` | M | Pending |

**AP11-V1 acceptance criteria:** all 16 criteria in [AP-11 §8](pages/AP-11-customer-detail.md) pass; history failure never blanks profile; request/job rows are real anchors (Bugs #13).

### AP-12 Staff Management

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP12-D1** | Staff roster: three role-scoped fetches for All internal, client search/status/paging in URL, no customers | `[FE]` | M | **Done** |
| **AP12-D2** | Create / edit / enable / disable / reset / delete dialogs with self + last-admin guards | `[FE]` | L | **Done** |
| **AP12-D3** | Empty / filtered-empty / error / forbidden / sole-admin marker | `[FE]` | S | **Done** |
| **AP12-V1** | Verify every `AC-AP12-nn` | `[QA]` | M | Pending |

**AP12-V1 acceptance criteria:** all 16 criteria in [AP-12 §8](pages/AP-12-staff.md) pass; customers never appear; last-admin and self-delete refusal are explained in-dialog.

### AP-13 Device Categories

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP13-D1** | Categories list: search, show-archived, brand mode + brand count columns, client paging | `[FE]` | M | **Done** |
| **AP13-D2** | Create/edit/archive/restore dialogs; archive Undo toast; free-text mismatch ⚠ | `[FE]` | M | **Done** |
| **AP13-D3** | Backend `activeBrandCount` on category responses | `[BE]` | S | **Done** |
| **AP13-V1** | Verify every `AC-AP13-nn` | `[QA]` | M | Pending |

**AP13-V1 acceptance criteria:** all 15 criteria in [AP-13 §8](pages/AP-13-device-categories.md) pass; never say "delete"; Brands links to AP-14.

### AP-14 Brands

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP14-D1** | Per-category brands list with search/archived, free-text banner, Others footer note | `[FE]` | M | **Done** |
| **AP14-D2** | Add/edit (incl. save-and-add-another), archive/restore + Undo, inline switch to CONFIGURED | `[FE]` | M | **Done** |
| **AP14-V1** | Verify every `AC-AP14-nn` | `[QA]` | M | Pending |

**AP14-V1 acceptance criteria:** all 18 criteria in [AP-14 §8](pages/AP-14-brands.md) pass; client blocks creating an "Others" row; 404 category id shows not-found.

### AP-15 Business Settings

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP15-D1** | Settings form: business info + slot/horizon; dual load from `/settings` + scheduling-config | `[FE]` | M | **Done** |
| **AP15-D2** | Working hours Mon=0 mapping, open/closed toggles, dirty-group save + partial-failure banner | `[FE]` | L | **Done** |
| **AP15-D3** | Unit test for weekday labels (Bugs #14 regression) | `[FE]` | S | **Done** |
| **AP15-V1** | Verify every `AC-AP15-nn` | `[QA]` | M | Pending |

**AP15-V1 acceptance criteria:** criteria in [AP-15 §8](pages/AP-15-business-settings.md) pass; first row Monday/`dayOfWeek=0`; hours-only save never calls `PUT /settings`.

### AP-16 Notifications

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP16-D1** | Inbox list: All/Unread filter, unread markers, relative timestamps, 30-cap footer | `[FE]` | M | **Done** |
| **AP16-D2** | Mark read / mark all read; bell badge push via shared unread service; deep links | `[FE]` | M | **Done** |
| **AP16-D3** | Expose `relatedType` on notification payloads and set it at send sites | `[BE]` | S | **Done** |
| **AP16-V1** | Verify every `AC-AP16-nn` | `[QA]` | M | Pending |

**AP16-V1 acceptance criteria:** criteria in [AP-16 §8](pages/AP-16-notifications.md) pass; mark-read failure leaves row unread; rows with `relatedType` are real anchors.

### AP-17 My Account

| ID | Task | Layer | Effort | Dependencies |
| --- | --- | --- | --- | --- |
| **AP17-D1** | Account page: identity card, profile KV rows, security + session sections | `[FE]` | M | **Done** |
| **AP17-D2** | Change mobile / change password dialogs; must-change banner; confirmed sign-out | `[FE]` | M | **Done** |
| **AP17-V1** | Verify every `AC-AP17-nn` | `[QA]` | M | Pending |

**AP17-V1 acceptance criteria:** criteria in [AP-17 §8](pages/AP-17-account.md) pass; wrong current password stays a field error (not forbidden page); local logout clears session even if `POST /auth/logout` fails.

---

# Phase 2 — Cross-cutting verification

### APV-001 · Accessibility audit (Batch 1) `[QA]` (M)
**Dependencies:** AP01-V1, AP02-V1, AP03-V1, AP04-V1
**Acceptance Criteria:**
- Full keyboard traversal of all four pages including every dialog and menu; visible focus throughout; `Esc` closes overlays and returns focus to the trigger.
- Screen-reader pass: one `<h1>` per page, ordered headings, labelled inputs, `<th scope>` on data tables, `aria-live` announcements for toasts and result counts.
- AA contrast verified; no status conveyed by colour alone.
- Usable at 200% zoom and with `prefers-reduced-motion` set.

### APV-002 · Responsive check `[QA]` (S)
**Dependencies:** APV-001
**Acceptance Criteria:** all four pages behave per [Design-System §7](Design-System.md) at 1440px, 1024px, and 768px, with no horizontal scroll or clipped controls and the sidebar collapsing as specified.

### APV-003 · Error-handling audit `[QA]` (S)
**Dependencies:** APV-001
**Acceptance Criteria:** every endpoint used in Batch 1 is exercised for 401, 403, 404, 409, 422, and 500; each renders its specified presentation; **no raw exception text, stack trace, or JSON is ever displayed**; `correlationId` is present on every error state.

### APV-004 · Batch 1 sign-off & Batch 2 planning `[DEC]` (S)
**Dependencies:** APV-002, APV-003
**Acceptance Criteria:**
- Batch 1 demonstrated against the specs; any accepted deviation recorded in the relevant spec.
- [Progress.md](../Progress.md) Phase 7 updated.
- Batch 2 scope agreed — recommended next: [AP-05 Request Detail](pages/AP-05-request-detail.md), [AP-06 Jobs](pages/AP-06-jobs.md), [AP-07 Job Detail](pages/AP-07-job-detail.md), since AP-04's list pattern and AP-05's detail pattern then cover every remaining archetype.

---

## Execution order

```
Phase M (mockups)                    Phase B (blockers, in parallel)
AP00-M1 → AP00-M2 → AP01-M1 ┐        APB-001 … APB-011
                   AP02-M1 ─┤        (APB-003 is a security release blocker,
                   AP03-M1 ─┤         independent of this panel)
                   AP04-M1 ─┘
                            ↓
                        AP00-M3  ◄── GATE: sign-off before any code
                            ↓
Phase 0   APS-001 → APS-002 ┐
                    APS-003 ─┼→ APS-005 → (APS-006, APS-007)
                    APS-004 ─┘
                            ↓
Phase 1   AP01-D* → AP02-D* → AP03-D* → AP04-D*  (each followed by its V task)
                            ↓
Phase 2   APV-001 → APV-002 → APV-003 → APV-004
```

## Effort summary

| Phase | Tasks | Effort |
| --- | --- | --- |
| Phase M — mockups & sign-off | 7 | ~3 days |
| Phase B — blockers | 11 | ~2.5 days (mostly `[BE]`/`[DEC]`, parallel) |
| Phase 0 — foundation | 7 | ~4 days |
| Phase 1 — four pages | 14 | ~7 days |
| Phase 2 — verification | 4 | ~1.5 days |
| **Batch 1 total** | **43** | **~15 working days** of frontend effort, plus the parallel blocker work |

Estimates assume one developer and exclude review/iteration cycles on the mockups, which are the least predictable part — the gate exists precisely so that unpredictability lands on throwaway HTML rather than on Angular components.

---

*Last updated: 2026-08-07 — Phase 0 foundation (APS-001…004, APS-006) and AP-01 Sign In (AP01-D1…D3) implemented in `admin-web/`. AP01-V1 and APS-005/007 still open. Batches 2+ follow the same Mockup → Gate → Foundation-reuse → Implement → Verify shape, without needing Phase 0 again.*
