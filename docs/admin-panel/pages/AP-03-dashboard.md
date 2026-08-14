# AP-03 · Dashboard

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-03 |
| **Route** | `/dashboard` |
| **Roles** | `ADMIN`, `STAFF`. `TECHNICIAN` is redirected to `/jobs` (§7, and §10 Q4) |
| **Entry points** | Default landing after sign-in ([AP-01 §5.5](AP-01-login.md)) · Sidebar → Work → Dashboard · Brand/logo in the top bar · "Back to dashboard" on the error and forbidden panels |
| **Primary endpoints** | `GET /dashboard/counters` (the page's only request) |
| **Archetype** | Hub — a short page of counter cards, one conditional banner, and quick links. Not one of the three standard archetypes, because it holds no records |
| **Related** | [AP-02 App Shell](AP-02-app-shell.md) · [AP-04 Repair Requests](AP-04-repair-requests.md) · [AP-06 Jobs](AP-06-jobs.md) · [features/dashboard.md](../../features/dashboard.md) · [PRD §6](../../PRD.md) |

## 2. Purpose

A landing page that answers one question — *where should I start?* — and then gets out of the way. [PRD §6](../../PRD.md) and [features/dashboard.md](../../features/dashboard.md) are explicit that this is a navigation and quick-access hub, **not** an analytics screen: no charts, no trends, no KPIs, no date-range pickers. What it does have is four live counts of work that is queued, each one a door into the list that contains that work, plus a warning when approved jobs are sitting unscheduled. In a panel with a permanent sidebar the dashboard is no longer the only way to get anywhere, which frees it to do the one thing the sidebar cannot: say how much of each kind of work is waiting, and take you straight to it pre-filtered.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Dashboard                                          [ ⟳ Refresh ]   │  page header
│ Welcome back, Anita                                                │
├────────────────────────────────────────────────────────────────────┤
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │  counter cards
│ │     5      │ │     2      │ │     11     │ │     3      │        │  (clickable)
│ │ Pending    │ │ Needs      │ │ Active     │ │ Unscheduled│        │
│ │ review     │ │ info       │ │ jobs       │ │ jobs       │        │
│ │ →/requests │ │ →/requests │ │ →/jobs     │ │ →/jobs     │        │
│ └────────────┘ └────────────┘ └────────────┘ └────────────┘        │
├────────────────────────────────────────────────────────────────────┤
│ ⚠ 3 jobs are not yet scheduled.            [ Schedule them → ]     │  warning banner
├────────────────────────────────────────────────────────────────────┤
│ Go to                                                              │  quick links
│ ┌──────────────────────┐ ┌──────────────────────┐                  │
│ │ ▣ Repair Requests    │ │ ▣ Jobs               │                  │
│ │ Review and approve   │ │ Track repair jobs    │                  │
│ └──────────────────────┘ └──────────────────────┘                  │
│ ┌──────────────────────┐ ┌──────────────────────┐                  │
│ │ ▣ Appointments       │ │ ▣ Customers          │                  │
│ └──────────────────────┘ └──────────────────────┘                  │
│ ┌──────────────────────┐ ┌──────────────────────┐                  │
│ │ ▣ Staff              │ │ ▣ Device Categories  │                  │
│ └──────────────────────┘ └──────────────────────┘                  │
│ ┌──────────────────────┐                                           │
│ │ ▣ Business Settings  │                                           │
│ └──────────────────────┘                                           │
└────────────────────────────────────────────────────────────────────┘
```

Counter cards sit on one row at ≥1024px, two per row below that, and stack below 768px. Quick links are a two-column grid that becomes one column below 1024px.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Dashboard" | Static; receives focus on route change ([AP-02 §5.6](AP-02-app-shell.md)). |
| 2 | Greeting | Text, `body-medium` | `name` from `GET /me`, already loaded by the shell | "Welcome back, Anita", falling back to "Welcome back" when no name is set. Never triggers its own request. |
| 3 | Refresh | Icon button | — | Refetches `GET /dashboard/counters`. Spins while in flight; the existing numbers stay visible during the refetch rather than blanking. |
| 4 | Counter card: Pending review | Card, **link** | `pendingReview` | Warning-toned. Navigates to `/requests?status=PENDING_REVIEW`. Accessible name reads the whole card: "Pending review: 5 requests". A real anchor, so Ctrl-click opens a new tab. |
| 5 | Counter card: Needs info | Card, **link** | `infoRequired` | Secondary-toned. Navigates to `/requests?status=INFORMATION_REQUIRED`. |
| 6 | Counter card: Active jobs | Card, **link** | `activeJobs` | Primary-toned. Intended destination `/jobs?status=IN_PROGRESS`, but the server counts `ASSIGNED + SCHEDULED + IN_PROGRESS + AWAITING_PARTS` while `GET /jobs` filters on one status at a time — so this link cannot currently show the jobs it counted. **See §10 Q2; blocked.** |
| 7 | Counter card: Unscheduled jobs | Card, **link** | `unscheduledJobs` | Warning-toned. Intended destination `/jobs?scheduled=false`, the unscheduled-backlog filter documented in [API.md §13](../../API.md). **See §10 Q3; blocked** — the shipped list endpoint does not accept that parameter. Rendered at all values including 0: a zero here is meaningful information, not an empty state. |
| 8 | Counter value | Number, 28px | Counter field | Rendered as text, never as an icon or bar. A value of 0 renders "0", not a dash — the dash is reserved for *unknown* (#9). |
| 9 | Counter failure placeholder | Dashed-border card | — | Replaces **all four** cards when the counters request fails: dashed border, an em-dash where each number would be, the label "Couldn't load counts", and a **Retry** button. Cards are **never** hidden on error ([README §6.7](../README.md)); a missing card reads as "nothing is queued", which is the opposite of the truth. Cards stay clickable in this state — the destinations work even when the counts do not. |
| 10 | Unscheduled-jobs banner | Warning banner | `unscheduledJobs` | Shown only when `unscheduledJobs > 0`. Text: "3 jobs are not yet scheduled." (singular "1 job is"), with a **Schedule them** action going to `/jobs?scheduled=false`. Absent at 0 and while counters are loading or failed. Duplicating card #7 is deliberate: the backlog is the one condition on this page that warrants an interruption ([features/scheduling.md](../../features/scheduling.md), SCH-023). |
| 11 | Quick links section | Heading + card grid | Static list | Heading "Go to". One card per sidebar destination, each with an icon, the destination name, and a one-line description. Role-gated **identically** to the sidebar ([AP-02 §7](AP-02-app-shell.md)) — a link here that the sidebar hides would be a permissions bug, so both read from the same definition. |
| 12 | Quick link card | Card, link | — | Whole card is the click target and a real anchor. Descriptions: Repair Requests "Review and approve incoming requests" · Jobs "Track and manage repair jobs" · Appointments "Weekly calendar view" · Customers "Search customer profiles and history" · Staff "Manage team members" · Device Categories "Manage the device catalog" · Business Settings "Business details and working hours". |
| 13 | Analytics | *Intentionally absent* | — | No charts, trend lines, sparklines, percentages, averages, or date-range controls. [PRD §6](../../PRD.md) and [features/dashboard.md](../../features/dashboard.md) scope this page to navigation plus light counts; adding analytics would require aggregate endpoints that do not exist. |

## 5. Interactions & dialogs

The page has no dialogs, no forms, and no mutations. Everything on it either navigates or refetches.

| Action | Trigger | Endpoint | Result |
| --- | --- | --- | --- |
| Load counters | Route activation | `GET /dashboard/counters` | Populates cards #4–#7 and decides whether banner #10 renders. |
| Refresh | Refresh button, or returning to the page from another route | `GET /dashboard/counters` | Numbers update in place; no skeleton on a refetch, only the button's spinner. Returning to the dashboard after approving a request must show the decreased count rather than a cached one. |
| Retry | Retry in the failure placeholder | `GET /dashboard/counters` | Replaces the placeholder with real cards on success; on repeated failure the placeholder persists with the `correlationId` shown in `body-small`. |
| Open a counter | Click / `Enter` on a card | — | Navigates to the pre-filtered list; that page's filter bar shows the filter as an active, removable chip, so the user can see why the list is narrowed ([Design-System §5.8](../Design-System.md)). |
| Open a quick link | Click / `Enter` on a card | — | Plain navigation, identical to the equivalent sidebar item. |

Counters are fetched on activation and on demand. They are not polled: the sidebar is always visible, the bell already polls, and a hub page that silently changes its numbers under a user who is reading them adds nothing. Confirm against the shared 30s polling policy — §10 Q6.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Four skeleton cards of the final size and position, plus the quick links rendered immediately at full fidelity — quick links need no data, so they must never wait on the counters request. |
| **Loaded** | Cards, the conditional banner, and quick links as sketched. |
| **All-zero** | Every counter 0. Not an empty state: the cards render "0" and a single line under the header reads "Nothing is waiting for review right now." A queue that is genuinely clear is good news and should look like it — and must be visually distinguishable from a failed load. |
| **Counter error** | The dashed placeholder (#9) in place of the cards, with **Retry**, the mapped message from [README §6.2](../README.md), and the `correlationId`. Quick links stay fully usable. The banner does not render, since its condition is unknown. |
| **Counter forbidden** (`403`) | Only reachable if a role that cannot read counters lands here (see §10 Q4). Cards are replaced by a one-line explanation — "Work counts aren't available for your role" — while the quick links remain. The page itself does **not** become a forbidden panel; the user can still navigate. |
| **Empty / Filtered-empty** | Not applicable — the page has no collection. |
| **Partial data** | If a future counter field is absent from the response, its card renders an em-dash rather than 0, so a missing field can never be mistaken for a cleared queue. |

## 7. Permissions & visibility

- `GET /dashboard/counters` is `STAFF`, `ADMIN` ([API.md §17](../../API.md)). Both roles receive identical, shop-wide counts: the endpoint computes them from unscoped status counts and takes no parameters, so nothing about the numbers is personalized. The cards must therefore be labelled as shop-wide work, never as "your" queue.
- **`STAFF` sees the Pending review and Needs info counters but cannot open the request queue**, which is admin-only under the decision recorded in [README §3](../README.md). As specified, those two cards are still rendered for staff (they are real, relevant workload signals) but they do not link anywhere for a staff user; they render as static cards with the tooltip "Only administrators can review requests." This is an uncomfortable compromise and is recorded as §10 Q5 — it disappears entirely if the staff-access question resolves in staff's favor.
- **`TECHNICIAN`** is redirected from `/dashboard` to `/jobs` by the route guard, matching the mobile app, where the technician landing renders the jobs list. Technicians are not shown a broken dashboard and never call `GET /dashboard/counters`, which would return `403` for them. See §10 Q4.
- **`CUSTOMER`** never reaches the panel ([AP-01 §5.2](AP-01-login.md)).
- Quick links are gated by exactly the same role rules as the sidebar; a link is never shown to a role whose route guard would refuse it.

## 8. Acceptance criteria

**AC-AP03-01** — Given I am an administrator and `GET /dashboard/counters` returns `pendingReview: 5`, `infoRequired: 2`, `activeJobs: 11`, `unscheduledJobs: 3`,
When the dashboard renders,
Then I see four cards showing 5, 2, 11, and 3 with the labels Pending review, Needs info, Active jobs, and Unscheduled jobs.

**AC-AP03-02** — Given the counters are loaded,
When I click the **Pending review** card,
Then I land on `/requests?status=PENDING_REVIEW` with the Pending filter applied and visible in that page's filter bar.

**AC-AP03-03** — Given the counters are loaded,
When I click the **Needs info** card,
Then I land on `/requests?status=INFORMATION_REQUIRED` with that filter applied.

**AC-AP03-04** — Given the counters are loaded,
When I click the **Active jobs** card,
Then I land on the jobs list filtered to active work, and the count I clicked matches the number of records that list reports.

**AC-AP03-05** — Given the counters are loaded,
When I click the **Unscheduled jobs** card or the banner's **Schedule them** action,
Then I land on `/jobs?scheduled=false` showing jobs with no appointment.

**AC-AP03-06** — Given `GET /dashboard/counters` fails,
When the page renders,
Then the counter area shows a dashed placeholder with em-dashes, the text "Couldn't load counts", a **Retry** button, and the `correlationId` — and it is **never** blank, hidden, or showing zeros.

**AC-AP03-07** — Given the counter placeholder is showing after a failure,
When I click **Retry** and the request succeeds,
Then the real counters replace the placeholder without a full page reload.

**AC-AP03-08** — Given the counters request is still in flight,
When the page renders,
Then I see four skeleton cards in the final layout, and the quick links are already present and clickable.

**AC-AP03-09** — Given `unscheduledJobs` is 3,
When the page renders,
Then a warning banner reads "3 jobs are not yet scheduled." with an action leading to the unscheduled jobs list; and given `unscheduledJobs` is 0, no banner is rendered anywhere on the page.

**AC-AP03-10** — Given every counter returns 0,
When the page renders,
Then all four cards show "0" with a line confirming that nothing is waiting — visibly different from the failure state in AC-AP03-06.

**AC-AP03-11** — Given I am signed in as `STAFF`,
When the dashboard renders,
Then I see the same four counters as an administrator, and the quick links omit Repair Requests, Staff, Device Categories, and Business Settings — matching my sidebar exactly.

**AC-AP03-12** — Given I am signed in as `TECHNICIAN` and I navigate to `/dashboard`,
When the guard runs,
Then I am redirected to `/jobs`, no request is made to `GET /dashboard/counters`, and I never see a forbidden panel.

**AC-AP03-13** — Given I approve a request from [AP-04](AP-04-repair-requests.md) and then navigate back to the dashboard,
When the page renders,
Then the Pending review counter reflects the decision rather than a cached pre-approval value.

**AC-AP03-14** — Given I navigate the dashboard with the keyboard,
When I tab through it,
Then each counter card and each quick link is a single focus stop with a visible focus ring, `Enter` activates it, and its accessible name states both the label and the number, for example "Pending review: 5".

**AC-AP03-15** — Given the dashboard is fully loaded,
When I inspect the page,
Then it contains no chart, graph, trend indicator, percentage, or date-range control — only counts, the conditional banner, and navigation.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| **Counters are hidden entirely when the request fails** (`error: (_, err) => const SizedBox.shrink()`) | Dashed placeholder with **Retry** and the `correlationId` | The mobile behavior renders a failure identically to "nothing is queued". An admin glances at an empty dashboard and concludes the queue is clear while five requests sit unreviewed. This is the single most consequential fix on the page, and it is mandated by [README §6.7](../README.md). |
| Counter cards are decorative — not tappable, no destination | Every counter is a link to its matching filtered list | Seeing "5 pending" and then having to find the queue and re-apply the filter is the whole journey done twice. |
| A bare centered spinner occupies the counter row while loading | Skeleton cards in the final layout | No layout jump when the data lands ([Design-System §6](../Design-System.md)). |
| Quick actions are a vertical list of full-width tiles that duplicate the only navigation available | Quick links are a secondary grid **below** the counters, duplicating the sidebar for discoverability only | With a permanent sidebar the tiles are no longer the sole route anywhere, so they stop being the page's main content. |
| Technician landing silently renders the jobs list inside a screen titled "RepairDesk" | Technician is redirected to `/jobs`, which is a real URL with its own title and breadcrumb | A URL that shows something other than what it names is confusing to bookmark and impossible to share. |
| Unscheduled banner is text only, with no way to act on it | Banner carries an action to the unscheduled jobs list | A warning you cannot act on is just noise. |
| No refresh affordance except pull-to-refresh | Explicit **Refresh** button plus a refetch on route activation | Pull-to-refresh has no desktop equivalent, and stale counts after a decision undermine trust in the numbers. |

## 10. Open questions

1. **The counters contract is documented wrong.** [API.md §17](../../API.md) says `GET /dashboard/counters` returns `{ pending, approved, activeJobs }`. The endpoint actually returns `pendingReview`, `infoRequired`, `activeJobs`, and `unscheduledJobs` — confirmed in the shipped `DashboardController`, in the mobile client's `DashboardCounters.fromJson`, and by DASH-001 in [Progress.md](../../Progress.md). The documented `approved` counter does not exist; two undocumented ones do. This page is specified against the real shape, and API.md needs correcting. **Blocking for elements #4–#7** until the contract is authoritative.
2. **The Active jobs card cannot link to the jobs it counted.** The server computes `activeJobs` as `ASSIGNED + SCHEDULED + IN_PROGRESS + AWAITING_PARTS`, while `GET /jobs` accepts a single `status` value — the shipped controller takes only `status` and pagination. So `/jobs?status=IN_PROGRESS` returns a strict subset and the list total visibly contradicts the number just clicked. Options: add multi-status filtering (for example `status=IN_PROGRESS,AWAITING_PARTS,ASSIGNED,SCHEDULED` or a named `active=true` filter — preferred), relabel the card to the one status it can filter by, or drop the link. **Blocking for element #6.**
3. **The unscheduled deep link has no working filter.** [API.md §13](../../API.md) documents `scheduled=true|false` on `GET /jobs`, but the shipped endpoint implements only `status` and pagination — none of `scheduled`, `technicianId`, `priority`, `q`, `dateFrom`, or `dateTo` exist. Separately, the counter is computed as `CREATED + ASSIGNED` (a status proxy for "has no appointment"), not from appointment records, so even once a filter exists the two definitions must be reconciled. Until then card #7 and banner #10 have nowhere correct to send the user. **Blocking for element #7 and the banner action** — and it means the wider `GET /jobs` contract in API.md overstates what the API does, which affects [AP-06](AP-06-jobs.md) far more than this page.
4. **Technician dashboard.** [PRD §6](../../PRD.md) says a technician lands on their personal schedule (today's appointments), which `GET /me/schedule` could supply; the mobile app lands them on the jobs list; `GET /dashboard/counters` refuses them outright. This spec redirects `/dashboard` → `/jobs`. Decide between (a) that redirect, (b) a technician variant of this page built on `GET /me/schedule` and `GET /me/alerts`, or (c) hiding `/dashboard` from technicians entirely. Ties directly to Q2 and Q3 in [AP-02 §10](AP-02-app-shell.md).
5. **Staff see counts for a queue they cannot open.** Under the admin-only queue decision in [README §3](../README.md), a staff user sees Pending review and Needs info but has nowhere to click. The spec renders them as non-clickable cards with an explanatory tooltip. Better alternatives if the decision holds: hide those two cards for staff, or have them link to a read-only request list. Resolves itself if staff gain review access.
6. **Counter freshness.** [Tasks.md](../../Tasks.md) NOTIF-021 defines a single shared 30s polling interval covering "staff counters". This page specifies fetch-on-activation plus manual refresh instead, on the grounds that a hub page whose numbers change while being read is unhelpful. Confirm which behavior is wanted; if polling is required, the numbers must animate in a way that does not move the click target under the cursor.
7. **The four counters overlap, so they cannot be read as a breakdown.** `ASSIGNED` jobs are counted in **both** Active jobs and Unscheduled jobs, so the numbers do not partition the work and must never be summed or presented as parts of a whole. The current labels are honest individually; confirm whether that is acceptable or whether Unscheduled should be narrowed to `CREATED` only. Presentational consequence: the cards stay four independent counts with no total anywhere on the page.

---

*Spec status: draft. Verified against `frontend/lib/features/dashboard/presentation/dashboard_screen.dart`, `frontend/lib/features/dashboard/data/dashboard_repository.dart`, the shipped `DashboardController`, `JobController`, and `RepairRequestController` in `backend/`, [API.md](../../API.md) §13, §17, [PRD.md](../../PRD.md) §6, [features/dashboard.md](../../features/dashboard.md), and [README.md](../README.md) §6.7.*
