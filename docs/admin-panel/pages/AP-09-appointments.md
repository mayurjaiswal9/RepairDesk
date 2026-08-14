# AP-09 · Appointments Calendar

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-09 |
| **Route** | `/appointments` — view mode, range anchor, and filters in the query string, e.g. `/appointments?view=week&date=2026-08-03&technicianId=<uuid>&status=SCHEDULED` |
| **Roles** | `ADMIN`, `STAFF`, `TECHNICIAN` — technicians see only their own schedule, via a different endpoint (§7) |
| **Entry points** | Sidebar → Work → Appointments · [AP-03 Dashboard](AP-03-dashboard.md) · [AP-07 Job Detail](AP-07-job-detail.md) appointment panel · technician alert deep link |
| **Primary endpoints** | `GET /appointments?from=&to=` (`STAFF`, `ADMIN`) · `GET /me/schedule?from=&to=` (`TECHNICIAN`) · `GET /settings/scheduling-config` · `GET /users?role=TECHNICIAN` · `GET /jobs?status=CREATED` and `?status=ASSIGNED` (unscheduled panel) |
| **Archetype** | Page-specific — a calendar grid rather than one of the three standard archetypes ([Design-System §4](../Design-System.md)); the page header and filter bar follow the list-page pattern |
| **Related** | [AP-08 Schedule / Reschedule](AP-08-schedule-appointment.md) · [AP-07 Job Detail](AP-07-job-detail.md) · [features/scheduling.md](../../features/scheduling.md) §Calendar View · [API.md §14](../../API.md) |

> **Contract note.** [API.md §14](../../API.md) documents `GET /appointments` with `dateFrom`, `dateTo`, `technicianId`, `status`, `q`, and pagination. The shipped endpoint takes **only `from` and `to`**, both required, and returns an unpaged array. This spec is written against the shipped contract; the consequences for filtering are in §5.2 and the divergence is §10 Q1.

## 2. Purpose

The operational picture of who is doing what, and when. Staff use it to plan a working week, spot free capacity before committing to a customer, and notice that Thursday is empty while Friday is triple-booked; technicians use it as their own worklist. Two things separate a calendar that is decoration from one that is a tool: every entry must lead somewhere — clicking an appointment opens the job behind it, so the calendar is a way *into* work rather than a read-only poster — and the grid must show the shop's real shape, shading closed days and out-of-hours time so a gap reads as "we're shut" rather than "nobody booked anything". The page is entirely URL-driven, so a particular week, technician, and view can be sent to a colleague.

## 3. Layout anatomy

```
┌───────────────────────────────────────────────────────────────────────────┐
│ Appointments                                                              │  page header
│ 17 appointments · 3 – 9 Aug 2026                                          │
├───────────────────────────────────────────────────────────────────────────┤
│ ‹ ›  [ Today ]  3 – 9 Aug 2026    [Day|Week]  [Tech ▾] [Status ▾]  [ ⟳ ]  │  toolbar
│                                   [ 🔍 Reference, customer, category   ]  │
├──────────────────────────────────────────────────────────┬────────────────┤
│        Mon 3   Tue 4   Wed 5   Thu 6   Fri 7  Sat 8 Sun 9│ Unscheduled    │
│                                                    Closed│ 6 jobs         │
│ 08:00 ░░░░░░░ ░░░░░░░ ░░░░░░░ ░░░░░░░ ░░░░░░ ░░░░░ ▓▓▓▓▓│ ┌────────────┐ │
│ 09:00 ┌─────┐         ┌─────┐                 ░░░░░ ▓▓▓▓▓│ │ JOB-1077   │ │
│ 10:00 │JOB-1│         │JOB-1│┌────┐┌────┐     ░░░░░ ▓▓▓▓▓│ │ A. Kumar   │ │
│       │042  │         │055  ││1061││1062│     ░░░░░ ▓▓▓▓▓│ │ Mobile     │ │
│       │Kumar│         │Rao  ││Iyer││Shah│     ░░░░░ ▓▓▓▓▓│ │ Assigned   │ │
│       │Priya│         │Dev  ││Priya││Dev │    ░░░░░ ▓▓▓▓▓│ └────────────┘ │
│ 11:00 └─────┘         └─────┘└────┘└────┘     ░░░░░ ▓▓▓▓▓│ ┌────────────┐ │
│ 12:00                                         ░░░░░ ▓▓▓▓▓│ │ JOB-1081   │ │
│ …                                                        │ └────────────┘ │
│ 17:00 ░░░░░░░ ░░░░░░░ ░░░░░░░ ░░░░░░░ ░░░░░░ ░░░░░ ▓▓▓▓▓│                │
└──────────────────────────────────────────────────────────┴────────────────┘
  ░ = outside working hours     ▓ = closed day
```

Below 1280px the unscheduled panel collapses to a toolbar toggle and opens as an overlay; below 1024px the week grid becomes a day-by-day agenda list, since seven legible columns do not fit.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Appointments" | Static. |
| 2 | Result summary | Text, `body-small` | Count of appointments after filtering + the range label | "17 appointments · 3 – 9 Aug 2026". Announced via `aria-live="polite"` when the range or filters change. Hidden while loading. |
| 3 | Previous / next range | Icon buttons | — | Move the anchor by one day or one week according to the active view. Update `date` in the URL and refetch. `PageUp` / `PageDown` when the grid has focus. |
| 4 | Today | Button | — | Resets the anchor to the current date, keeping view and filters. Disabled when the current range already contains today. |
| 5 | Range label | Text, `title-medium` | Derived from anchor and view | Week: "3 – 9 Aug 2026". Day: "Wednesday, 5 Aug 2026". |
| 6 | View switcher | Segmented control | Day · Week | **Week is the default.** Writes `view` to the URL. Month is deliberately excluded from V1 — see §5.3 and §10 Q4. |
| 7 | Technician filter | Dropdown | `GET /users?role=TECHNICIAN` (returns active technicians), plus "All technicians" | Applied **client-side** to the loaded range (§5.2). Hidden entirely for `TECHNICIAN` users, whose scope is already themselves. Persists in the URL as `technicianId`. |
| 8 | Status filter | Dropdown | `SCHEDULED` · `IN_PROGRESS` · `COMPLETED`, plus "All statuses" | Applied client-side. Defaults to **All statuses**, because both endpoints already exclude `CANCELLED` server-side — there is no clutter to hide, and defaulting to a subset would hide completed work for no reason. `CANCELLED` is not offered, since it can never be returned (§10 Q3). |
| 9 | Search | Debounced text input (350ms) | Job reference, customer name, device category | Applied client-side across `referenceNumber`, `customerName`, and `deviceCategoryName`. Non-matching entries are hidden rather than dimmed. Available to technicians too, since the filtering is local. |
| 10 | Refresh | Icon button | — | Refetches the current range without resetting view or filters; spins while in flight. There is no polling (§5.4). |
| 11 | Day column headers | Grid header | Weekday name + date | Today's column is highlighted. A closed day's header carries a "Closed" label, so the shading is explained in text and not by colour alone. |
| 12 | Time axis | Grid row labels | Generated from the earliest `openTime` and latest `closeTime` across the visible days, stepped by `slotDurationMinutes` | From `GET /settings/scheduling-config`. If an appointment falls outside those bounds — legal, because changing hours does not invalidate existing bookings, and possible anyway because the server does not validate hours ([AP-08 §10 Q7](AP-08-schedule-appointment.md)) — the axis extends to include it rather than clipping it out of sight. |
| 13 | Working-hours shading | Grid background | `workingHours[dayOfWeek]` | Cells outside `openTime`–`closeTime` are shaded; a day with `isClosed = true` is shaded full height. Weekday lookup uses **`0 = Monday … 6 = Sunday`** ([Design-System §10](../Design-System.md), [Bugs.md](../../Bugs.md) #14). The shading is decorative and hidden from assistive technology; the textual "Closed" label carries the meaning. |
| 14 | Appointment entry | Clickable block, positioned by time | One appointment from the response: `startTime`–`endTime`, `referenceNumber` (`mono`), `customerName`, `technicianName`, `deviceCategoryName`, `status` chip | **Every entry is a real link to `/jobs/{jobId}`** (§5.5) — the central improvement over the mobile calendar. `jobId` is present on every appointment payload, so no lookup is needed. Height is proportional to duration; content degrades as the block shrinks (time and reference first, then customer, then technician and category), with full detail always in the tooltip and accessible name. Technician name is dropped when the technician filter is pinned to one person. |
| 15 | Overlapping entries | Layout rule | — | Concurrent appointments in the same slot split the column width evenly, side by side, ordered by start time then reference. At four or more the column shows three plus a "+N more" chip opening a popover list of all of them; nothing is ever hidden behind another block. |
| 16 | Current-time indicator | Horizontal line | Browser clock | Drawn across today's column when today is in range; updates each minute. |
| 17 | Unscheduled jobs panel | Side list, 280px | `GET /jobs?status=CREATED` and `GET /jobs?status=ASSIGNED`, merged and sorted by `updatedAt` ascending | Jobs in these two statuses are by definition the unscheduled backlog: scheduling moves a job to `SCHEDULED`, and cancelling an appointment returns it to `ASSIGNED` or `CREATED`. Each item shows `referenceNumber`, `contactName`, `deviceCategoryName`, priority chip, and status, and links to `/jobs/{id}/schedule`, so the calendar is where unscheduled work gets booked. Collapsible, with the state remembered per browser. Hidden for `TECHNICIAN`. Shows "Everything is scheduled" when both lists are empty. See §10 Q5 on the derivation. |
| 18 | Loading / empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Range and view are URL state

Every toolbar control writes to the query string: `view` (`day` / `week`), `date` (the anchor, `YYYY-MM-DD`), `technicianId`, `status`, `q`. The range sent to the API is derived from `view` and `date`:

| View | `from` | `to` |
| --- | --- | --- |
| Day | the anchor date | the anchor date |
| Week | Monday of the anchor's week | the following Sunday |

Weeks start on **Monday**, consistent with the backend's `0 = Monday` convention and with the mobile calendar's week anchor. Browser back and forward move through range and filter changes; a pasted URL restores the exact view.

### 5.2 Filtering is client-side, and that is a deliberate constraint

The shipped endpoints accept only `from` and `to`. Technician, status, and text filtering therefore run in the browser over the appointments already loaded for the visible range. This is acceptable and even preferable here — a week of appointments for a single-site repair shop is a small array, filtering is instant with no round trip, and the filters can be applied while offline from a cached range.

It has two consequences worth stating plainly. Filters never change what is fetched, so switching technicians does not re-hit the API; and the response is unpaged, so a very wide range fetches every appointment in it at once. The second is why Month view is excluded from V1 (§5.3). If the appointment volume ever outgrows a browser-side filter, `technicianId` and `status` should move to the server, as API.md already anticipates.

### 5.3 View behavior

| View | Content |
| --- | --- |
| **Day** | One column, full-height entries ordered by start time. The densest view — used when a single day is too busy to read in the week grid. |
| **Week** | Seven day columns against the time axis. The default, and the only view the mobile app has. |

**Month is not specified for V1.** A month grid would fetch every appointment in the month in one unpaged response and render it without a time axis, and the value — "how busy is the back half of the month" — is better served by an aggregate the API does not offer. Recorded as §10 Q4 rather than half-built.

### 5.4 Refresh and staleness

The page refetches on range or view change, and on the explicit refresh button. Filter changes do not refetch (§5.2). Refetches keep the existing grid visible with a top progress bar rather than blanking it ([Design-System §6](../Design-System.md)). There is no polling: appointments change on human timescales, and a grid that reflows underneath someone reading it is worse than one a few minutes stale.

### 5.5 Opening an appointment

Clicking or pressing Enter on an entry navigates to `/jobs/{jobId}` — the job detail page, which carries the full repair context, the appointment panel, and whatever action the user wants next. Entries are rendered as real anchors, so middle-click and Ctrl-click open a new tab and the browser's own link affordances work.

This is deliberately a navigation, not a popover: the mobile calendar renders display-only cards with no tap handler at all, which makes it a dead end — a user who spots a problem has to memorise the reference number and go find it in the Jobs list. Rescheduling is reached from the job, not by dragging blocks on this grid; drag-to-reschedule is out of scope for V1 because it invites accidental changes to commitments the customer has already been told about, and every reschedule notifies them.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Toolbar and grid chrome — day headers, time axis, working-hours shading — render as soon as the scheduling config resolves; the entry area shows skeleton blocks. The structure never jumps when data arrives. |
| **Loading (refetch)** | Existing entries stay visible and dimmed with a top progress bar; toolbar controls remain usable. |
| **Empty — staff/admin** | Calendar icon, "No appointments this week", and the hint "Appointments are scheduled from a job." When the unscheduled panel has items, the empty state points at it: "6 jobs are waiting to be scheduled." |
| **Empty — technician** | "No appointments this week", with "Anything scheduled for you will appear here." No scheduling hint, because they cannot act on it. |
| **Filtered-empty** | Names the active filters — "No appointments for Priya Sharma in 3 – 9 Aug 2026" — and offers **Clear filters**. Never claims the calendar is empty when a filter is merely narrow. |
| **Error** | Error panel with the mapped message, **Retry**, and `correlationId`. The toolbar stays usable so the user can move to another range instead of being stuck. |
| **Forbidden** | A `403` renders the standard panel with the user's role and a link to `/dashboard`. Customers never reach the panel at all ([README §3](../README.md)). |
| **Config unavailable** | If `GET /settings/scheduling-config` fails, the grid still renders on a plain 8:00 am – 8:00 pm axis with **no** shading, plus an inline notice that working hours could not be loaded. Appointments matter more than shading, and guessing at hours would shade the wrong cells — which is worse than shading none. |

## 7. Permissions & visibility

- **`ADMIN` and `STAFF`** use `GET /appointments?from=&to=`, which returns every technician's appointments in the range, excluding cancelled ones, ordered by date then start time. They get the technician filter and the unscheduled jobs panel.
- **`TECHNICIAN`** uses `GET /me/schedule?from=&to=`, which the server scopes to the caller's own appointments with the same exclusions and ordering. The role determines the endpoint at load time rather than filtering client-side, because the two endpoints are mutually exclusive server-side: `GET /appointments` is restricted to staff and admin, and `GET /me/schedule` is restricted to technicians — an admin calling it would get `403`.
- The technician view is read-only: no technician filter (redundant), no unscheduled panel, and no scheduling actions anywhere on the page.
- Entries still link to `/jobs/{jobId}` for technicians; job detail is available to the assigned technician, and by construction every appointment on their own schedule belongs to a job assigned to them.
- Server authorization is the real boundary. The role-based endpoint choice is a UX affordance, and a `403` from either endpoint produces the forbidden panel.

## 8. Acceptance criteria

**AC-AP09-01** — Given I open `/appointments` with no query string,
When the page loads,
Then the week view is shown for the week containing today, starting on Monday, and the URL is normalised to include the resolved view and anchor date so it can be copied and shared.

**AC-AP09-02** — Given I am viewing a week,
When I press next twice and then **Today**,
Then each step updates the range label, the URL, and the `from`/`to` sent to the API; **Today** returns me to the current week; and the browser back button walks back through those ranges.

**AC-AP09-03** — Given an appointment is displayed on the grid,
When I click it,
Then I navigate to `/jobs/{jobId}` for that appointment's job using the `jobId` on the appointment payload; and when I Ctrl-click or middle-click it, the job opens in a new tab, because the entry is a real link.

**AC-AP09-04** — Given an appointment entry,
When it renders at any supported viewport width,
Then its time range, job reference, customer name, technician, device category, and status are either visible or available through its tooltip and accessible name — no field is silently dropped.

**AC-AP09-05** — Given the scheduling config marks Sunday as closed and sets Wednesday hours of 9:00 am to 5:00 pm,
When the week grid renders,
Then Sunday's column is shaded full height and labelled "Closed" in text, and Wednesday's cells before 9:00 am and after 5:00 pm are shaded as outside working hours.

**AC-AP09-06** — Given working hours use `dayOfWeek` where Monday is 0,
When the grid maps each column to its working-hours entry,
Then Monday reads index 0 and Sunday reads index 6 — so with a config in which only Monday is closed, the Monday column is shaded and no other. This is the regression in [Bugs.md](../../Bugs.md) #14 and must be covered by an automated test.

**AC-AP09-07** — Given two appointments for different technicians occupy the same 10:00 am slot on the same day,
When that slot renders,
Then both entries are visible side by side, both remain legible, and both are independently clickable — neither is hidden behind the other.

**AC-AP09-08** — Given four or more appointments occupy one slot,
When that slot renders,
Then three are shown alongside a "+N more" affordance that reveals the rest, and every appointment in the slot is reachable.

**AC-AP09-09** — Given I select a technician in the technician filter,
When the grid updates,
Then only that technician's appointments are shown, no new request is issued because filtering is client-side, the filter appears as a removable chip, and the choice is preserved in the URL across a refresh.

**AC-AP09-10** — Given I am signed in as a technician,
When I open `/appointments`,
Then the page loads from `GET /me/schedule`, I see only my own appointments, and no technician filter or unscheduled jobs panel is rendered.

**AC-AP09-11** — Given I am signed in as a technician with nothing booked this week,
When the empty state renders,
Then it reads "No appointments this week" without the "scheduled from a job" hint, which is shown only to staff and administrators.

**AC-AP09-12** — Given I am staff and the current week has no appointments while unscheduled jobs exist,
When the empty state renders,
Then it tells me appointments are scheduled from a job and reports how many jobs are waiting to be scheduled.

**AC-AP09-13** — Given the unscheduled jobs panel lists a job,
When I select it,
Then I navigate to `/jobs/{id}/schedule` for that job; and after scheduling it, the job leaves the panel on the next refresh because its status is no longer `CREATED` or `ASSIGNED`.

**AC-AP09-14** — Given the appointments request fails with a 500,
When the error renders,
Then I see a human-readable message, a **Retry** that refetches the current range, and the `correlationId` — with no raw exception text — and the toolbar remains usable so I can change range or filters.

**AC-AP09-15** — Given a technician filter and a search term that together match nothing in the loaded range,
When the empty result renders,
Then it names the active filters and the range and offers **Clear filters**, rather than claiming there are no appointments.

**AC-AP09-16** — Given I am navigating with the keyboard,
When I tab into the calendar grid,
Then the grid takes a single tab stop, arrow keys move focus between appointment entries, Enter opens the focused entry's job, `PageUp` and `PageDown` move to the previous and next range, each entry's accessible name includes its time, reference, customer, technician, and status, and the focused entry always shows a visible focus ring.

**AC-AP09-17** — Given an appointment that starts before the shop's configured opening time — booked before the hours changed, or created by a client that skipped the hours check —
When the grid renders,
Then the time axis extends to include it and the appointment is visible, positioned in the shaded out-of-hours region rather than clipped or dropped.

**AC-AP09-18** — Given the range contains a day the shop is closed and an appointment exists on it,
When the grid renders,
Then the day is still shaded and labelled "Closed" and the appointment is still shown on it — the shading never suppresses real data.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Appointment cards have **no tap action** — the calendar is display-only | Every entry is a link to `/jobs/{jobId}` | The single most important change on this page. A calendar you cannot act from is a dead end: the mobile user must memorise a reference and go find it in the Jobs list. The payload has carried `jobId` all along; nothing but the tap handler was missing. Same class of defect as [Bugs.md](../../Bugs.md) #13, where customer history rows were unclickable. |
| Week view only, with no way to switch | Day and Week views, Week as the default | Week stays the operational default; Day is needed when one day is too dense to read in the week grid. |
| An agenda list grouped by day, with no time axis | A true time grid with proportional entry heights | Gaps are the point of a calendar. Three cards stacked under "Wednesday" cannot show that the afternoon is free. |
| No awareness of working hours; a closed Sunday looks identical to an unbooked Monday | Non-working hours and closed days shaded, closed days labelled in text | Removes the main ambiguity of an empty cell, and reuses config the page already needs. |
| No technician, status, or text filters | Technician filter, status filter, and search, all in the URL | The feature spec calls for exactly these ([features/scheduling.md](../../features/scheduling.md) §Calendar View); without them a busy week is unreadable. Applied client-side, since the endpoint takes no filter params. |
| Range lives in component state only; no deep link | View, anchor date, and every filter live in the query string | Shareable links, refresh safety, and working browser back. |
| No unscheduled work visible anywhere on the calendar | Collapsible unscheduled jobs panel | Backlog visibility is an explicit requirement ([features/scheduling.md](../../features/scheduling.md) §Calendar View), and it makes the calendar the place where booking actually happens rather than a report. |
| Bare centered spinner while loading; error renders `e.toString()` | Skeleton grid with real chrome; mapped message with **Retry** and `correlationId` | [README §6.2](../README.md) and [§6.5](../README.md) — raw exception text is prohibited and the layout must not jump. |
| Status chip colours defined locally, with `IN_PROGRESS` rendered green | Shared status chip mapping ([Design-System §5.2](../Design-System.md)) | `IN_PROGRESS` is the secondary colour and green means completed everywhere else in the product; a status must look the same on every screen. |
| Overlapping appointments stack as consecutive list items | Side-by-side layout with a "+N more" overflow | The mobile list makes concurrency invisible, which is precisely what staff most need to see. |
| Pull-to-refresh only | Explicit refresh button, and refetch on range change | Desktop has no pull gesture; the affordance has to be visible. |

## 10. Open questions

1. **API.md describes `GET /appointments` incorrectly.** It documents `dateFrom`, `dateTo`, `technicianId`, `status`, `q`, pagination, and a `GET /appointments/{id}` detail endpoint. The backend implements `from` and `to` only, both required, returns an unpaged array, and has no detail endpoint. The mobile client agrees with the implementation. API.md should be corrected, or the endpoint extended to match it — the latter would let §5.2's filtering move server-side.
2. **`GET /me/schedule` parameter names.** Same divergence: API.md documents `dateFrom`/`dateTo`, the backend takes `from`/`to`. Same resolution needed.
3. **Cancelled appointments are unreachable.** Both range endpoints exclude `CANCELLED`, so the status filter cannot offer it and a cancelled booking cannot be reviewed on the calendar at all. This is probably right for an operational view, but it means the calendar can never answer "what was booked here before it moved?" — related to the missing reschedule history in [AP-08 §10 Q3](AP-08-schedule-appointment.md). Confirm whether an `includeCancelled` parameter is wanted.
4. **Month view.** Deferred (§5.3). It needs either an aggregate endpoint returning per-day counts, or confidence that fetching an unpaged month is cheap enough. Neither exists today, and no endpoint should be invented for it.
5. **Sourcing the unscheduled backlog.** API.md §13 documents a `scheduled=true/false` filter on `GET /jobs`; the backend implements no such parameter — `GET /jobs` accepts only `status` and pagination. Element #17 therefore derives the backlog from two calls, `status=CREATED` and `status=ASSIGNED`, which is exactly equivalent given the job lifecycle but costs an extra request and cannot be paged as one list. Adding the documented `scheduled` filter, or letting `status` take multiple values, would collapse it to one call. Not blocking — the panel works as specified — but worth fixing.
6. **No appointment detail endpoint.** There is no `GET /appointments/{id}`, so a popover preview of an entry could only re-use data already in the range response. This is why entries navigate to the job rather than opening a detail overlay; confirm that is the intended model.
7. **Timezone of `startTime` and `endTime`.** These are bare `HH:mm` values, like the working hours they are drawn against, while every timestamp elsewhere in the panel is UTC rendered in the browser's timezone ([Design-System §10](../Design-System.md)). The grid treats appointment times as shop-local wall-clock times. Confirm, since a user in another timezone would otherwise see a subtly and silently wrong calendar.
8. **Range size limits.** Because the response is unpaged and both parameters are required but unvalidated against each other, a hand-edited URL can request an arbitrarily wide range. The panel only ever requests a day or a week, but confirm whether the server should cap the span defensively.

---

*Spec status: draft. Verified against `frontend/lib/features/scheduling/presentation/calendar_screen.dart`, `frontend/lib/features/scheduling/data/scheduling_repository.dart`, the shipped `AppointmentController`, `AppointmentRepository`, `JobController`, and `SettingsController`, [API.md](../../API.md) §8, §11, §13–14, [features/scheduling.md](../../features/scheduling.md) §Calendar View, and [Bugs.md](../../Bugs.md) #13–14.*
