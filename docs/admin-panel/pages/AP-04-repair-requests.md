# AP-04 · Repair Requests (Review Queue)

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-04 |
| **Route** | `/requests` (filters/sort/page in query string, e.g. `/requests?status=PENDING_REVIEW&page=0&size=20`) |
| **Roles** | `ADMIN` only — see §7 and the conflict note in [README §3](../README.md) |
| **Entry points** | Sidebar → Work → Repair Requests · Dashboard "Pending review" and "Needs info" counters · Notification deep link · return from [AP-18](AP-18-create-request.md) |
| **Primary endpoints** | `GET /requests?page=&size=&status=` · `POST /requests/{id}/approve` · `POST /requests/{id}/reject` · `POST /requests/{id}/request-info` |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-05 Request Detail](AP-05-request-detail.md) · [AP-18 Create Request](AP-18-create-request.md) · [features/request-review.md](../../features/request-review.md) · [features/staff-assisted-intake.md](../../features/staff-assisted-intake.md) |

## 2. Purpose

The triage desk for incoming customer repair requests. An admin scans what's waiting, opens anything ambiguous, and resolves each request one of three ways: **approve** (which auto-creates the job), **reject** (with a reason the customer sees), or **request more information** (which pauses the request pending the customer's reply). This page is the top of the operational funnel, so throughput matters more than decoration: the default view is the work that needs a decision, and a decision can be made without leaving the list.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Repair Requests                  [ Refresh ]  [ + Create request ] │  page header
│ 12 requests · 5 awaiting your decision                             │
├────────────────────────────────────────────────────────────────────┤
│ [All][Pending][Needs info][Approved][Rejected][Cancelled]          │  filter bar
│ [ 🔍 Search reference, customer, mobile ]  [Category ▾] [Sort ▾]   │
├────────────────────────────────────────────────────────────────────┤
│ Reference │ Customer │ Device │ Submitted │ Status │ Job │  ⋮       │  data table
│ RQ-1042   │ A. Kumar │ Mobile │ 2h ago    │Pending │  —  │  ⋮       │
│           │ @akumar  │ Phone  │           │        │     │         │
│ …                                                                   │
├────────────────────────────────────────────────────────────────────┤
│ Page 1 of 3 · 47 requests            [20 ▾]      ‹ Prev   Next ›    │  pagination
└────────────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Repair Requests" | Static. |
| 2 | Result summary | Text, `body-small` | `totalElements` + count needing a decision | "47 requests · 5 awaiting your decision" where the second figure counts `PENDING_REVIEW` + `INFORMATION_REQUIRED`. Hidden while loading. |
| 3 | Refresh | Icon button | — | Refetches the current view without resetting filters; spins while in flight. |
| 3a | Create request | Primary button | — | Navigates to `/requests/new` ([AP-18](AP-18-create-request.md)). The queue's single create action; jobs are still not created from this list. |
| 4 | Status filter | Segmented control | All · Pending · Needs info · Approved · Rejected · Cancelled | Maps to `status` query param (**All** omits it). Default on first visit is **Pending**, not All — the queue exists to surface undecided work. Selection persists in the URL. |
| 5 | Search | Debounced text input (350ms) | Reference number, customer name, username, mobile | Clear (✕) button when non-empty. See §10 open question on server support. |
| 6 | Category filter | Dropdown | `GET /device-categories` (active) | Optional; "All categories" default. |
| 7 | Sort control | Dropdown | Submitted (newest/oldest), Reference, Status | Maps to `sort=field,asc|desc`. Default `submittedAt,desc`. |
| 8 | Column: Reference | Table cell, `mono` + `--color-primary` | `referenceNumber` | Primary identifier; the row's accessible name. |
| 9 | Column: Customer | Table cell, two lines | `contactName` / `@customerUsername` | Truncates with ellipsis at one line each and exposes the full value in a tooltip. Never wraps letter-by-letter (the failure mode in [Bugs.md](../../Bugs.md) #17). |
| 10 | Column: Device | Table cell, two lines | Category name / `deviceBrand` + `deviceModel` | Second line shows "—" when brand and model are both absent. |
| 11 | Column: Submitted | Table cell | `submittedAt` | Relative under 7 days, absolute after, absolute in tooltip ([Design-System §10](../Design-System.md)). |
| 12 | Column: Status | Status chip | `status` | Shared mapping ([Design-System §5.2](../Design-System.md)). |
| 13 | Column: Job | Table cell | Job status + technician, when the request is `APPROVED` | Shows the resulting job's status chip and assigned technician (or "Unassigned"); links to [AP-07](AP-07-job-detail.md). Renders "—" for non-approved requests. |
| 14 | Attachment indicator | Icon + count in the Device cell | Attachment count for the request | Only shown when > 0. Tooltip: "3 photos". Clicking opens the row's detail page rather than a popover — photo review belongs on the detail page. |
| 15 | Row action menu | Overflow (⋮) menu | Approve · Request info · Reject · Open detail | Decision items appear **only** when status is `PENDING_REVIEW` or `INFORMATION_REQUIRED`; otherwise the menu holds just **Open detail**. |
| 16 | Row click | — | — | Navigates to `/requests/{id}`. Middle-click / Ctrl-click opens a new tab (real anchor semantics). |
| 17 | Pagination footer | Data-table footer | `page`, `totalPages`, `totalElements` | Page-size selector 20/50/100; disabled arrows at bounds. |
| 18 | Empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

| Action | Trigger | Dialog / form | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Approve** | Row menu → Approve | Confirmation naming the reference; explains a job will be created and the customer notified. Optional **Priority** select (`LOW`/`NORMAL`/`HIGH`/`URGENT`, default `NORMAL`) and optional internal **Note**. Confirm button: "Approve & create job". | `POST /requests/{id}/approve` with `{ priority?, note?, version }` | Success toast "RQ-1042 approved · job created". Row updates to `APPROVED` (and leaves the Pending filter). Dashboard counters refresh. |
| **Reject** | Row menu → Reject | Error-styled confirm. **Reason code** (required select) and **Message to customer** (required, multi-line — the customer reads this) plus optional **Internal reason**. | `POST /requests/{id}/reject` with `{ reasonCode, internalReason?, customerMessage, version }` | Toast "RQ-1042 rejected". Row updates to `REJECTED`. |
| **Request more info** | Row menu → Request info | **Message to customer** (required, multi-line) explaining what's needed. | `POST /requests/{id}/request-info` with `{ message, version }` | Toast "Information requested from A. Kumar". Row updates to `INFORMATION_REQUIRED`. |
| **Open detail** | Row click or menu | — | — | Navigates to [AP-05](AP-05-request-detail.md). |
| **Create request** | Header primary | — | — | Navigates to [AP-18](AP-18-create-request.md). |
| **Filter / search / sort / page** | Filter bar, sort, footer | — | `GET /requests?…` | URL query string updates; table refetches; scroll position resets to the top of the table only. |

All three decision dialogs are irreversible state transitions, so all three confirm explicitly, disable their confirm button while in flight, and surface `409 CONFLICT` as "This request was already actioned by someone else" with a **Reload** action.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Page header + filter bar render immediately; table shows 10 skeleton rows. |
| **Loading (refetch)** | Existing rows stay visible, dimmed slightly, with a top progress bar. Controls stay usable except the row action menus. |
| **Empty** (no filters) | Inbox icon, "No repair requests yet", "Customer submissions and walk-in intake will appear here." Primary action **Create request** → [AP-18](AP-18-create-request.md). |
| **Filtered-empty** | "No requests match your filters", names the active status/search, **Clear filters**. |
| **Error** | Error panel with mapped message, **Retry**, `correlationId`. |
| **Forbidden** | Shown when a `STAFF`/`TECHNICIAN` user reaches the URL directly: "Repair request review is limited to administrators", with a link to `/dashboard`. |
| **Stale-decision** | If a decision returns `409`, the affected row refreshes in place and shows an inline "Updated elsewhere" marker for 5 seconds. |

## 7. Permissions & visibility

- **Route guard**: `ADMIN` only, matching current shipped behavior (the Flutter app gates this tile to admins). `STAFF` and `TECHNICIAN` do not see the sidebar item and get the forbidden state on direct navigation.
- **Server is authoritative** — the guard is UX only; a `403` from `GET /requests` renders the forbidden state regardless of what the client believed.
- **Decision actions** are gated by record state, not role: only `PENDING_REVIEW` and `INFORMATION_REQUIRED` requests can be approved, rejected, or sent back for info.
- See the unresolved Staff-access question in [README §3](../README.md); if resolved in Staff's favor, only the guard and nav visibility change.

## 8. Acceptance criteria

**AC-AP04-01** — Given I am an admin opening `/requests` for the first time,
When the page loads,
Then the status filter defaults to **Pending**, the table shows only `PENDING_REVIEW` requests sorted newest-first, and the URL reflects that filter.

**AC-AP04-02** — Given the request list is loading,
When I wait for the response,
Then I see skeleton rows in the table (not a bare centered spinner) and the page header and filter bar are already interactive.

**AC-AP04-03** — Given the queue is filtered to **Pending**,
When I approve a request from its row action menu and confirm,
Then a success toast names the reference number, the row leaves the Pending filter, and the dashboard's Pending review counter decreases by one.

**AC-AP04-04** — Given I approve a request,
When the approval succeeds,
Then a job is created for it (server-side), and the row's Job column shows the new job's status with a link to the job detail page.

**AC-AP04-05** — Given I open the Reject dialog,
When I try to submit without a reason code or without a customer message,
Then submission is blocked with field-level messages, and no request is sent.

**AC-AP04-06** — Given a request whose status is `APPROVED`, `REJECTED`, or `CANCELLED`,
When I open its row action menu,
Then Approve / Reject / Request info are absent and only **Open detail** is offered.

**AC-AP04-07** — Given another admin actioned a request after my list loaded,
When I attempt a decision on that stale row,
Then I see "This request was already actioned by someone else" with a **Reload** action, and my decision is not applied.

**AC-AP04-08** — Given I have typed a search query that matches nothing,
When the empty result renders,
Then it names my query and offers **Clear filters**, and it does **not** say that no requests exist.

**AC-AP04-09** — Given I type continuously in the search box,
When I pause,
Then exactly one request is issued 350ms after my last keystroke.

**AC-AP04-10** — Given I have filtered, sorted, and paged to a specific view,
When I copy the URL into a new tab or press browser Back,
Then the same view is restored from the URL without re-selecting anything.

**AC-AP04-11** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/requests`,
Then I see the forbidden panel explaining the restriction with a link to the dashboard, and no request data is displayed.

**AC-AP04-12** — Given a customer name long enough to overflow its column,
When the row renders at any supported viewport width,
Then the name truncates to a single line with an ellipsis and a tooltip carrying the full value — it never wraps to one character per line.

**AC-AP04-13** — Given the request list endpoint returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** button that refetches the current view, and the `correlationId` in small text — and no raw exception text or JSON.

**AC-AP04-14** — Given a request with attached photos,
When its row renders,
Then an attachment indicator shows the count, and opening the row's detail page displays those photos.

**AC-AP04-15** — Given the list has more records than the page size,
When I change the page size or move to the next page,
Then the data is fetched server-side with the new `page`/`size`, and the footer accurately reports page position and total count.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Card list, one request per card, `ChoiceChip` status filter | Data table with sortable columns + segmented filter | Scanning and comparing many requests is the core task; a table shows more per screen and supports sorting. |
| Default filter "All" | Default filter **Pending** | The queue's purpose is undecided work; "All" buries it behind resolved records. |
| Approve dialog has no priority or note field | Adds optional priority + internal note | `POST /requests/{id}/approve` already accepts `{ priority?, note? }` — the mobile app simply doesn't surface them. |
| Reject sends a hardcoded `reasonCode` of `UNABLE_TO_SERVICE` with an optional message | Reason code and customer message both required and chosen by the user | The API treats the reason as mandatory ([API.md §12](../../API.md)); hardcoding it discards information the customer and audit log need. |
| Photos expand inline in the card | Count indicator in the list, full gallery on the detail page | Keeps rows uniform in height and scannable; photo review is a detail-page task. |
| `size=20` hardcoded, no paging UI | Server-side pagination with page-size selector | Required for the 10k-record target in [PRD §10](../../PRD.md). |
| Filters are in-memory only | Filters, sort, and page live in the URL | Deep links, refresh-safety, and working browser Back. |

## 10. Open questions

1. **Search support.** `GET /requests` documents `status` and pagination, but not a free-text `q` param (unlike `/customers`). Options: add `q` to the requests endpoint (preferred — consistent with customer search and needed for the <2s search target), or drop the search box from V1 of this page. **Blocking for element #5.**
2. **Category filter support.** Same question for filtering by `deviceCategoryId`; if unsupported, either add it or remove element #6.
3. **"Awaiting your decision" count.** Needs either two extra count queries or a small aggregate endpoint. Interim: derive from the current page only and label it accordingly, or reuse `GET /dashboard/counters` (which already returns `pendingReview` and `infoRequired`) — the latter is preferred.
4. **Staff access** to this queue — see [README §3](../README.md). Product decision required.
5. **Reason codes.** The reject dialog needs the canonical list of `reasonCode` values; the mobile app only ever sends `UNABLE_TO_SERVICE`. Source needed from [features/request-review.md](../../features/request-review.md) or the backend enum.
6. **Bulk actions.** Should admins be able to select and reject/request-info in bulk? Deferred pending user feedback — omitted from V1 rather than half-built.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/requests/presentation/request_queue_screen.dart` and [API.md](../../API.md) §12–13.*
