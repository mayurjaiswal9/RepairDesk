# AP-06 · Jobs

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-06 |
| **Route** | `/jobs` (filters/sort/page in query string, e.g. `/jobs?status=IN_PROGRESS&technicianId=…&page=0&size=20`) |
| **Roles** | All internal roles — `ADMIN`, `STAFF`, `TECHNICIAN` (technicians see only their assigned jobs; see §7) |
| **Entry points** | Sidebar → Work → Jobs · Dashboard "Active jobs" counter · Technician landing → "My jobs" · [AP-05](AP-05-request-detail.md) Job panel (deep link to one job) · Notification deep link |
| **Primary endpoints** | `GET /jobs?page=&size=&sort=&status=&technicianId=&q=` · `GET /users?role=TECHNICIAN` (technician filter options) |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-07 Job Detail](AP-07-job-detail.md) · [AP-09 Appointments](AP-09-appointments.md) · [features/jobs.md](../../features/jobs.md) · [API.md §13](../../API.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The operational board for every repair currently in flight. Staff and admins use it to see what is unassigned, what is blocked on parts, who is carrying which workload, and what has gone quiet — and to jump into any job to act on it. Technicians use the same page as their personal work queue, scoped by the server to jobs assigned to them. Unlike the request queue, no decision is made here: this page is about finding the right job quickly among thousands, so filtering by status and technician, sorting by what changed most recently, and scanning priority at a glance are the whole job of the design.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Jobs                                                        [ Refresh ]    │  page header
│ 128 jobs · 14 unassigned                                                   │
├────────────────────────────────────────────────────────────────────────────┤
│ [All][New][Assigned][Scheduled][In progress][Awaiting parts][Completed]    │  filter bar
│ [Cancelled]                                                                │
│ [ 🔍 Search reference, customer, device ]   [ Technician ▾ ]   [ Sort ▾ ]  │
├────────────────────────────────────────────────────────────────────────────┤
│ Reference │ Customer  │ Device │ Technician │ Status      │ ! │ Updated    │  data table
│ RQ-1042   │ A. Kumar  │ Mobile │ Priya S.   │ In progress │ ↑ │ 2h ago     │
│           │ @akumar   │ Phone  │            │             │   │            │
│ RQ-1039   │ M. Iyer   │ Laptop │ Unassigned │ New         │ ‼ │ 5h ago     │
│ …                                                                          │
├────────────────────────────────────────────────────────────────────────────┤
│ Page 1 of 7 · 128 jobs                [20 ▾]        ‹ Prev      Next ›      │  pagination
└────────────────────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Jobs" — or "My jobs" for `TECHNICIAN` | The technician wording states the scoping the server applies, so an empty list is never mistaken for an empty shop. |
| 2 | Result summary | Text, `body-small` | `totalElements` + unassigned count | "128 jobs · 14 unassigned". The unassigned figure is admin/staff only and depends on the open question in §10 item 2; until it is resolved it is omitted rather than estimated from the current page. Hidden while loading. |
| 3 | Refresh | Icon button | — | Refetches the current view without resetting filters; spins while in flight. |
| 4 | Status filter | Segmented control | All · New · Assigned · Scheduled · In progress · Awaiting parts · Completed · Cancelled | Maps to the `status` query param (**All** omits it) using the canonical values `CREATED`, `ASSIGNED`, `SCHEDULED`, `IN_PROGRESS`, `AWAITING_PARTS`, `COMPLETED`, `CANCELLED`. `CREATED` is labelled **New**, matching the shared chip mapping ([Design-System §5.2](../Design-System.md)). Single-select; the API takes one status at a time. Default **All**; selection persists in the URL. |
| 5 | Technician filter | Dropdown | `GET /users?role=TECHNICIAN` (`[{ id, name, username, … }]`, active technicians only) | Options: **All technicians** (default) · **Unassigned** · one entry per active technician, sorted by name. Selecting a technician sends `technicianId`; **Unassigned** has no documented representation in the API — see §10 item 1, **Blocking**. Hidden entirely for `TECHNICIAN` users, whose list is already scoped to themselves. |
| 6 | Search | Debounced text input (350ms) | Reference number, customer name, contact name, device | Maps to `q` ([API.md §13](../../API.md)). Clear (✕) button when non-empty. |
| 7 | Sort control | Dropdown | Updated (newest/oldest) · Reference · Status · Priority | Maps to `sort=field,asc\|desc`. Default `updatedAt,desc` — the most recently touched work is what an operator is usually looking for. |
| 8 | Column: Reference | Table cell, `mono` + `--color-primary` | `referenceNumber` | The job number equals the originating request's reference ([Database.md §1](../../Database.md)), so the same string finds the request and the job. This cell is the row's accessible name. |
| 9 | Column: Customer | Table cell, two lines | `contactName` / `@customerUsername` | Each line truncates to one line with an ellipsis and a tooltip carrying the full value; never wraps letter-by-letter ([Bugs.md](../../Bugs.md) #17). |
| 10 | Column: Device | Table cell | `deviceCategoryName` | The list response carries the category only; brand and model are on the detail page. Folds into the Customer cell below 1024px. |
| 11 | Column: Technician | Table cell | `assignedTechnicianName` | Renders **Unassigned** in `text-secondary` italic when null — never a blank cell, because unassigned is an actionable state, not missing data. |
| 12 | Column: Status | Status chip | `status` | Shared job mapping ([Design-System §5.2](../Design-System.md)). |
| 13 | Column: Priority | Icon + accessible text | `priority` | Shows a marker **only** for `HIGH` (warning up-arrow) and `URGENT` (error double-mark). `NORMAL` and `LOW` render nothing at all — badging the default is noise. The marker carries visually hidden text ("Priority: Urgent") so it is not conveyed by color and shape alone. |
| 14 | Column: Updated | Table cell | `updatedAt` | Relative under 7 days, absolute after, absolute in a tooltip ([Design-System §10](../Design-System.md)). Adding a note bumps this value — see [AP-07](AP-07-job-detail.md) and [Bugs.md](../../Bugs.md) #8. |
| 15 | Row click | — | — | Navigates to `/jobs/{id}` ([AP-07](AP-07-job-detail.md)). Real anchor semantics: middle-click and Ctrl-click open a new tab. |
| 16 | Row action menu | Overflow (⋮) menu | Open job | Only **Open job** in V1. All status transitions and assignment happen on the detail page, where the current status and the technician roster are already loaded and the required dialogs live; see §9. |
| 17 | Pagination footer | Data-table footer | `page`, `totalPages`, `totalElements` | Page-size selector 20/50/100; arrows disabled at bounds. |
| 18 | Empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

This page has no mutating actions and therefore no dialogs — every job action is on [AP-07](AP-07-job-detail.md). **There is no Create job control.** Walk-in work starts as a request on [AP-18](AP-18-create-request.md).

| Action | Trigger | Dialog / form | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Filter by status** | Segmented control | — | `GET /jobs?status=…` | URL updates, table refetches, paging resets to page 0, scroll returns to the top of the table only. |
| **Filter by technician** | Technician dropdown | — | `GET /jobs?technicianId=…` | As above. The **Unassigned** option depends on §10 item 1. |
| **Search** | Debounced input | — | `GET /jobs?q=…` | Exactly one request 350ms after the last keystroke. |
| **Sort** | Sort dropdown or column header | — | `GET /jobs?sort=…` | Server-side; the active sort is reflected in the column header and the URL. |
| **Change page / page size** | Pagination footer | — | `GET /jobs?page=&size=` | Server-side; the footer reports true position and totals from the paged envelope. |
| **Open job** | Row click or row menu | — | — | Navigates to [AP-07](AP-07-job-detail.md). |
| **Clear filters** | Filtered-empty panel or the filter chips' **Clear all** | — | `GET /jobs` | Removes every filter and search term from the URL and refetches. |

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Page header and filter bar render immediately and are interactive; the table shows 10 skeleton rows. |
| **Loading (refetch)** | Existing rows stay visible, slightly dimmed, with a top progress bar. |
| **Empty** (no filters) | Wrench icon, "No jobs yet", "Jobs are created automatically when a repair request is approved." No primary action — jobs cannot be created by hand ([features/jobs.md](../../features/jobs.md) §Job Creation), so offering one would be a lie. |
| **Empty (technician)** | For a `TECHNICIAN` with nothing assigned: "No jobs are assigned to you right now", with a link to their schedule ([AP-09](AP-09-appointments.md)). Distinct from the shop-wide empty state. |
| **Filtered-empty** | Names the active status, technician, and query, and offers **Clear filters**. Must never claim no jobs exist. |
| **Error** | Error panel with the mapped message, **Retry**, and the `correlationId` — never a raw exception or JSON dump ([README §6.2](../README.md)). |
| **Forbidden** | A `403` from `GET /jobs` renders the standard forbidden panel with a link to `/dashboard`. Reachable in practice only for a `CUSTOMER` token, since all internal roles may list jobs. |

## 7. Permissions & visibility

- **Route guard**: all internal roles. `CUSTOMER` accounts cannot sign into the panel at all ([README §3](../README.md)).
- **Technician scoping is server-side.** `GET /jobs` returns all jobs for `STAFF`/`ADMIN` and only the caller's assigned jobs for `TECHNICIAN` ([API.md §13](../../API.md); confirmed in `backend/.../job/service/JobService.java`, which routes technician callers to a query filtered by `assignedTechnicianId`). The panel does **not** apply a client-side filter and must not imply it is doing so — the list a technician sees is authoritative, and a technician cannot widen it by editing the URL.
- **Technician filter is hidden for `TECHNICIAN`** because it can only ever return their own jobs or nothing; showing it would suggest a capability they do not have.
- **The unassigned count** in the result summary is shown to `ADMIN` and `STAFF` only.
- **No row-level actions are role-gated here**, because there are none. Role gating of job actions is specified in [AP-07 §7](AP-07-job-detail.md).

## 8. Acceptance criteria

**AC-AP06-01** — Given I am an admin or staff member opening `/jobs` for the first time,
When the list loads,
Then all jobs are shown sorted by most recently updated, the URL reflects the default sort and page, and the summary reports the total count.

**AC-AP06-02** — Given the jobs list is loading,
When I wait for the response,
Then I see skeleton rows in the table rather than a bare centered spinner, and the page header and filter bar are already interactive.

**AC-AP06-03** — Given I select **Awaiting parts** in the status filter,
When the table refreshes,
Then the request carries `status=AWAITING_PARTS`, only jobs in that status are listed, paging resets to the first page, and the URL records the filter.

**AC-AP06-04** — Given I select a specific technician in the technician filter,
When the table refreshes,
Then the request carries that technician's `technicianId` and only their jobs are listed.

**AC-AP06-05** — Given I select **Unassigned** in the technician filter,
When the table refreshes,
Then only jobs with no assigned technician are listed. *(Depends on §10 item 1 — no API representation for "unassigned" is documented today.)*

**AC-AP06-06** — Given a job with no assigned technician,
When its row renders,
Then the Technician cell reads "Unassigned" rather than being blank or showing an em-dash.

**AC-AP06-07** — Given jobs of every priority are listed,
When the rows render,
Then only `HIGH` and `URGENT` jobs show a priority marker, `NORMAL` and `LOW` show nothing, and each marker carries text for screen readers rather than relying on color alone.

**AC-AP06-08** — Given I type continuously in the search box,
When I pause,
Then exactly one request is issued 350ms after my last keystroke, carrying my text as `q`.

**AC-AP06-09** — Given I change the sort to "Reference" or move to page 3,
When the table refreshes,
Then the data is fetched server-side with the new `sort`/`page`, and the footer accurately reports the page position and total record count.

**AC-AP06-10** — Given I have filtered, sorted, and paged to a specific view,
When I copy the URL into a new tab or press browser Back,
Then the same view is restored from the URL without re-selecting anything.

**AC-AP06-11** — Given I click a row,
When navigation completes,
Then I am on `/jobs/{id}` for that job; Ctrl-clicking or middle-clicking the same row opens it in a new tab instead.

**AC-AP06-12** — Given I am signed in as a `TECHNICIAN`,
When `/jobs` loads,
Then the page is titled "My jobs", only jobs assigned to me are listed because the server scoped them, and the technician filter is not shown.

**AC-AP06-13** — Given I am a `TECHNICIAN` with no jobs assigned,
When the list renders,
Then I see "No jobs are assigned to you right now" rather than the shop-wide "No jobs yet" message.

**AC-AP06-14** — Given a status filter and a search term that together match nothing,
When the empty result renders,
Then it names both the active filter and my query and offers **Clear filters**, and it does not say that no jobs exist.

**AC-AP06-15** — Given `GET /jobs` returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** button that refetches the current view, and the `correlationId` in small text — and no raw exception text or JSON.

**AC-AP06-16** — Given I navigate the table with the keyboard,
When I tab through it,
Then every row is reachable and openable with `Enter`, the table exposes proper header semantics to screen readers, and the result count is announced when it changes.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Card list, one job per card | Data table with sortable columns | Comparing technician load, priority, and staleness across many jobs is the core task; a table shows far more per screen. |
| Status filter chips omit **Scheduled** entirely | Full eight-option status filter including Scheduled | `SCHEDULED` is a real lifecycle state ([features/jobs.md](../../features/jobs.md) §Job Statuses); omitting it makes scheduled work unfindable by filter. |
| No technician filter at all | Technician dropdown including **Unassigned** | Required by [Tasks.md](../../Tasks.md) JOB-020 and the wireframe; without it, finding unassigned work — the thing that most needs action — means reading every page. |
| No search | Debounced search bound to `q` | `GET /jobs` already documents `q` ([API.md §13](../../API.md)); the 10k-record target in [PRD §10](../../PRD.md) makes scrolling unusable. |
| `size=20` hardcoded, no paging UI, no sorting | Server-side pagination, page-size selector, and sorting | Same 10k-record target; also makes "oldest untouched job" findable. |
| Priority hides both `NORMAL` **and** `LOW` markers | Same behavior, kept deliberately | Correct as shipped and consistent with [Design-System §5.2](../Design-System.md); recorded so it is not "fixed" by mistake. |
| Filters live in widget state only | Filter, sort, and page live in the URL | Deep links, refresh-safety, and working browser Back. |
| Status label "Waiting Parts", "Done" | "Awaiting parts", "Completed" | One shared status vocabulary across every screen ([Design-System §5.2](../Design-System.md)). |
| Wireframe proposes an inline status control in the list | Transitions only on the detail page | Transitions carry required inputs (resolution summary, cancellation reason) and role/status rules; a one-click inline control invites mistakes on an irreversible action. |

## 10. Open questions

1. **Filtering for unassigned jobs.** [API.md §13](../../API.md) documents `technicianId` on `GET /jobs`, but there is no documented way to express "no technician" — an omitted param means "all", and no sentinel value is specified. Options: accept `technicianId=none` (or an explicit `assigned=false` param), or add a dedicated `unassigned=true` param. Until one exists the **Unassigned** option cannot be implemented, and [Tasks.md](../../Tasks.md) JOB-020 requires it. **Blocking for element #5.**
2. **Documented parameters are not all implemented.** API.md §13 documents `status`, `technicianId`, `priority`, `q`, `scheduled`, `dateFrom`, `dateTo`, and `sort`, but the deployed controller currently accepts only `status` plus pagination (`backend/.../job/controller/JobController.java`). Confirm which parameters actually exist before the panel is built; **blocking for elements #5 and #6** if the gap is real, since the filter bar would silently return unfiltered data.
3. **Unassigned count in the result summary.** No counter endpoint reports it — `GET /dashboard/counters` returns `pending`, `approved`, and `activeJobs` only ([API.md §17](../../API.md)). Options: derive it from a `size=1` query using the unassigned filter from item 1 (cheap, but depends on that filter), add it to the counters endpoint, or drop the figure. Currently specified as omitted until resolved.
4. **Default status filter.** This spec defaults to **All**, matching the mobile app. An "Active work" default (everything except `COMPLETED` and `CANCELLED`) would be more useful day to day, but `status` is single-valued, so it would need either a multi-value `status` param or a server-side `active=true` shorthand. Product and API decision needed.
5. **Sorting by priority.** The sort dropdown offers Priority, and [features/jobs.md](../../features/jobs.md) recommends priority-then-age as the default ordering, but sorting on an enum column sorts alphabetically unless the server defines an explicit ordinal ordering. Confirm the server's behavior or remove the option.
6. **Scheduled date is not in the list response.** `JobSummary` carries no appointment date, so "what is scheduled for today" cannot be answered here; it belongs to [AP-09](AP-09-appointments.md). Confirm that split is intended, or add `scheduledDate` to the summary and a column for it.
7. **Technician job scoping.** Server-side scoping is confirmed for `GET /jobs` and `GET /jobs/{id}`, and [features/jobs.md](../../features/jobs.md) §Security flags the assigned-only default as a challenged assumption ("if the shop is small, technicians may need the shared board"). No change is proposed for V1, but if the assumption is revisited, this page and its title change.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/jobs/presentation/jobs_list_screen.dart`, `frontend/lib/features/jobs/data/jobs_repository.dart`, the `JobSummary` response record in `backend/.../job/dto/JobSummary.java`, [API.md §13](../../API.md), [features/jobs.md](../../features/jobs.md), and [Tasks.md](../../Tasks.md) JOB-020.*
