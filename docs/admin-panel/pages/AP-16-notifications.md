# AP-16 · Notifications

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-16 |
| **Route** | `/notifications` (`?filter=unread` in query string) |
| **Roles** | All authenticated internal roles (`ADMIN`, `STAFF`, `TECHNICIAN`) — see §7 and open question 1 |
| **Entry points** | Top-bar bell → **See all notifications** ([AP-02 §Top bar](AP-02-app-shell.md)) · Avatar menu → Notifications · Direct URL |
| **Primary endpoints** | `GET /me/notifications` · `POST /me/notifications/{id}/read` · `POST /me/notifications/read-all` · `GET /me/notifications/unread-count` (owned by the bell, [AP-02](AP-02-app-shell.md)) |
| **Archetype** | List page ([Design-System §4](../Design-System.md)), rendered as a list rather than a table |
| **Related** | [AP-02 App Shell](AP-02-app-shell.md) · [API.md §15](../../API.md) · [Database.md §3.7](../../Database.md) |

## 2. Purpose

The full inbox behind the top-bar bell: everything the system has told **this** user, newest first, with unread items obvious and one click away from being read and acted on. It exists because the bell can only ever show a count and a short preview — the inbox is where a user catches up after being away, finds the notification they dismissed too quickly, and gets from "your job was reassigned" to the job itself. Every item is about a specific record, so the page's real job is not to display messages but to route people to work.

## 3. Layout anatomy

```
┌──────────────────────────────────────────────────────────────────────┐
│ Notifications                                    [ Mark all read ]   │  page header
│ 30 notifications · 4 unread                                          │
├──────────────────────────────────────────────────────────────────────┤
│ [ All ] [ Unread ]                                                   │  filter
├──────────────────────────────────────────────────────────────────────┤
│ ● │ Job JOB-1042 has been assigned to you              2 hours ago   │  unread
│   │ JOB-1042                                                         │
├───┼──────────────────────────────────────────────────────────────────┤
│   │ Appointment for RQ-1088 rescheduled to 8 Aug        Yesterday    │  read
│   │ RQ-1088                                                          │
├───┼──────────────────────────────────────────────────────────────────┤
│   │ Repair request RQ-1075 was approved              28 Jul 2026     │
│   │ RQ-1075                                                          │
├──────────────────────────────────────────────────────────────────────┤
│ Showing your 30 most recent notifications.                           │  footer note
└──────────────────────────────────────────────────────────────────────┘
```

Content column is capped at 720px so message text stays readable on a wide monitor ([Design-System §2](../Design-System.md)).

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Notifications" | Static. |
| 2 | Result summary | Text, `body-small` | Item count + unread count from the loaded list | "30 notifications · 4 unread". The unread figure is counted from the loaded items, not from `GET /me/notifications/unread-count` — see §5.4. Hidden while loading. |
| 3 | Mark all read | Secondary button, page header | — | Disabled when nothing is unread. §5.3. |
| 4 | Filter | Segmented control | **All** (default) · **Unread** | Client-side — `GET /me/notifications` documents no filter param ([API.md §15](../../API.md)). Selection lives in the URL as `?filter=unread`. |
| 5 | Unread marker | Dot + weight | `read = false` | A filled `--color-primary` dot in the leading gutter, message text at 600 weight, and a faint `--color-primary` row tint. Three signals, not one, because color alone is not allowed to carry meaning ([Design-System §8](../Design-System.md)). Read items have an empty gutter and normal weight. |
| 6 | Message | Text, `body-medium` | `message` | Rendered as plain text, never as HTML. Wraps to at most three lines with the full text in the item's accessible name; no truncation of a short message. |
| 7 | Reference number | Text, `mono`, `--color-primary` | `referenceNumber` | Shown only when present ([Database.md §3.7](../../Database.md) allows null). The row omits the line entirely rather than rendering an em-dash — an absent reference is not a missing value, it is a notification that has none. |
| 8 | Timestamp | Text, `body-small`, `text-secondary` | `createdAt` | Relative under 7 days ("2 hours ago"), absolute after, with the absolute local value in a `title` tooltip ([Design-System §10](../Design-System.md)). |
| 9 | Item click target | Row | — | Marks the item read and, where the target can be resolved, navigates to the related record. §5.1 and §5.2. |
| 10 | Row action | Overflow (⋮) menu | Mark as read / Mark as unread *(see open question 4)* · Open record | Present only when the row has an action beyond its default click; **Mark as read** is offered on unread rows so a user can clear an item without navigating away. |
| 11 | Footer note | Text, `body-small` | Static | "Showing your 30 most recent notifications." Always visible when the list is full, so nobody assumes older items were deleted. §5.5. |
| 12 | Empty / filtered-empty / error / loading | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

There is **no pagination footer and no page-size selector**. `GET /me/notifications` returns a plain array capped at the 30 most recent, with no `page`, `size`, or `sort` params ([API.md §15](../../API.md), [Database.md §3.7](../../Database.md)) — a deliberate, documented deviation from [README §6.4](../README.md). See §9 and open question 3.

## 5. Interactions & dialogs

### 5.1 Reading an item

Clicking anywhere on an unread row calls `POST /me/notifications/{id}/read` and, on `204`, updates the row in place — the dot clears, the weight drops, the tint disappears — and decrements both the summary count and the top-bar bell's badge without waiting for the next poll. Clicking an already-read row does not re-issue the call.

If the mark-read request fails, the row **stays unread** and an error toast appears; the client never shows an item as read that the server did not record. Navigation (§5.2) is not blocked by a failed mark-read: getting the user to the record matters more than the badge.

### 5.2 Deep linking to the related record

Every notification is about a record. `related_type` is one of `REPAIR_REQUEST`, `JOB`, or `APPOINTMENT`, alongside a `related_id` ([Database.md §3.7](../../Database.md)), which is exactly the pair needed to route:

| `relatedType` | Destination |
| --- | --- |
| `REPAIR_REQUEST` | `/requests/{relatedId}` ([AP-05](AP-05-request-detail.md)) |
| `JOB` | `/jobs/{relatedId}` ([AP-07](AP-07-job-detail.md)) |
| `APPOINTMENT` | `/appointments?…` focused on that appointment ([AP-09](AP-09-appointments.md)) |

Clicking a row marks it read **and** navigates. Rows that resolve to a destination are real anchors, so middle-click and Ctrl-click open a new tab; in that case the item is still marked read.

**This is currently not buildable.** [API.md §15](../../API.md) documents no response shape for `GET /me/notifications` at all, and the payload the server actually returns carries `relatedEntityId` but **no `relatedType`** (verified against `backend/.../dto/NotificationResponse.java`), so the client has an id with no way to know which of the three detail pages it belongs to. Inferring the type from the `type` string is not acceptable: that vocabulary is itself undocumented (open question 2), and a wrong guess routes the user to a `404` for a record that exists. Until `relatedType` is exposed, rows are not links: clicking marks the item read and nothing else, and element #10's **Open record** is absent. Recorded as a **Blocking** open question (1). The routing table above is the specified behavior for the moment the field lands, not a description of today.

### 5.3 Mark all read

**Mark all read** calls `POST /me/notifications/read-all` → `204`, then re-reads the inbox and refreshes the bell's count. No confirmation dialog — it is not destructive and nothing is lost, which is why it is exempt from [README §6.8](../README.md)'s confirmation rule. A toast confirms: "All notifications marked as read". The button is disabled while nothing is unread and while the request is in flight.

If the call fails, nothing is marked read locally, the error toast carries the mapped message, and the button re-enables so it can be retried.

### 5.4 Relationship to the top-bar bell

The bell in the app shell is specified in [AP-02](AP-02-app-shell.md) and owns the unread **count**: it polls `GET /me/notifications/unread-count` every 30 seconds and caps its badge at `99+` ([README §6.7](../README.md)). This page does not duplicate that polling and does not own the badge. The two coordinate in one direction only:

- This page **pushes** to the shell: marking one item read decrements the badge; **Mark all read** zeroes it.
- This page **does not pull** from the shell: its own summary counts unread items in the list it loaded, so the number next to the heading always matches the rows on screen. When the badge and the summary disagree — which happens legitimately, since the badge counts every unread item and the list holds at most 30 — the summary is the one describing what you can see.
- The page does not poll. It fetches on entry and on an explicit refresh; a background poll that reorders rows under a reader's cursor is worse than a slightly stale list. If the bell's count rises while the page is open, a subtle "New notifications" bar appears at the top of the list with a **Refresh** action rather than injecting rows.

### 5.5 The 30-item cap

`GET /me/notifications` returns only the 30 most recent ([API.md §15](../../API.md); the limit is enforced server-side and confirmed in `backend/.../service/NotificationService.java`). There is no way to page further back and no total count. The page therefore states the cap in the footer (element #11) whenever a full 30 items load, so a user who remembers an older notification understands it is beyond the window rather than deleted. **Mark all read** still affects every unread notification the user has, not just the visible 30 — the endpoint is not scoped to the page.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Eight skeleton rows shaped like real items — gutter, two text lines, timestamp. |
| **Empty** | Bell icon, "No notifications", "You'll be notified here when something needs your attention." No action button; a user cannot create a notification. For internal roles this state may be permanent — see open question 5. |
| **Filtered-empty** | With the Unread filter active and nothing unread: "Nothing unread", "You're all caught up." with a **Show all** action. Distinct from the empty state and never claims there are no notifications. |
| **Error** | Standard error panel with the mapped message, **Retry**, and the `correlationId` ([README §6.2](../README.md)). Never a raw exception string. |
| **Item action failed** | Error toast; the affected row reverts to its previous state. The list is not reloaded and no other row is disturbed. |
| **Stale list** | The bell's count exceeded the unread items on screen: a "New notifications" bar with **Refresh** at the top of the list (§5.4). |
| **Related record deleted or not visible** | Following a deep link that resolves to a `404` or `403` lands on the destination page's own not-found or forbidden state, with a link back to `/notifications` in place of the usual parent-list link. The notification stays in the inbox and stays marked read — it is a historical fact, not a broken row, and removing it would make the record's disappearance harder to explain, not easier. §7 covers why a technician can hit this. |
| **Forbidden** | Not expected: `/me/notifications` is scoped to the caller. A `403` renders the standard forbidden panel and is treated as a bug — see open question 1. |

## 7. Permissions & visibility

- Every authenticated user reaches this page; the endpoints are self-scoped, returning only rows whose `recipient_id` is the caller, and `POST /me/notifications/{id}/read` rejects an id belonging to someone else. There is no role gate and no cross-user view.
- `CUSTOMER` accounts cannot sign in to the panel at all ([README §3](../README.md)), so in practice the audience here is `ADMIN`, `STAFF`, and `TECHNICIAN`.
- **A deep link can still be forbidden.** The notification is visible to its recipient, but the record it points at is governed by that record's own rules — a `TECHNICIAN` may only read jobs assigned to them ([API.md §13](../../API.md)), so a job that has since been reassigned yields a `403` on the destination page even though the notification remains legitimately in their inbox. The page does not pre-filter items by predicted access; it lets the destination page state the situation honestly.
- [API.md §15](../../API.md) lists the roles for these endpoints as `CUSTOMER`, which contradicts both [README §6.7](../README.md) (the bell polls the unread count for every panel user) and the implementation, which applies no role restriction. See open question 1.

## 8. Acceptance criteria

**AC-AP16-01** — Given I open `/notifications`,
When the list loads,
Then my notifications are shown newest first, with unread items distinguished by a marker and heavier text as well as tint — not by color alone.

**AC-AP16-02** — Given the inbox is loading,
When I wait for the response,
Then I see skeleton rows shaped like notification items rather than a bare centered spinner.

**AC-AP16-03** — Given an unread notification,
When I click it,
Then `POST /me/notifications/{id}/read` is called, the row becomes read in place without a full list reload, the unread summary decreases by one, and the top-bar bell's badge decreases by one.

**AC-AP16-04** — Given an unread notification,
When the mark-read request fails,
Then the row stays unread, an error toast explains the failure, and the bell's badge is unchanged.

**AC-AP16-05** — Given a notification whose related record can be resolved,
When I click it,
Then I am navigated to that record's detail page and the notification is marked read.

**AC-AP16-06** — Given a notification whose related record has since been deleted or is not visible to my role,
When I follow it,
Then I land on the destination page's not-found or forbidden state with a link back to `/notifications`, the item stays in my inbox, and no raw error text is shown.

**AC-AP16-07** — Given I have unread notifications,
When I use **Mark all read**,
Then `POST /me/notifications/read-all` is called, every visible row becomes read, the bell's badge drops to zero, and a toast confirms — with no confirmation dialog beforehand.

**AC-AP16-08** — Given nothing is unread,
When the page renders,
Then **Mark all read** is disabled.

**AC-AP16-09** — Given I select the **Unread** filter,
When the list re-renders,
Then only unread items are listed, the URL records the filter, and — if none are unread — I see a "Nothing unread / You're all caught up" state with **Show all**, not a claim that I have no notifications.

**AC-AP16-10** — Given a notification created less than seven days ago and one created more than seven days ago,
When their timestamps render,
Then the recent one shows a relative time and the older one an absolute date, and both expose the absolute local time in a tooltip.

**AC-AP16-11** — Given a notification with a reference number and one without,
When their rows render,
Then the reference number appears in monospace on the first and the second omits that line entirely — it never renders "null" or an empty placeholder.

**AC-AP16-12** — Given I have no notifications at all,
When the page renders,
Then I see the empty state with an explanation of what will appear here, and no action button.

**AC-AP16-13** — Given the inbox endpoint returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** that refetches the inbox, and the `correlationId` in small text — and no raw exception string.

**AC-AP16-14** — Given a full inbox of 30 items,
When the list renders,
Then a footer note states that only the 30 most recent notifications are shown.

**AC-AP16-15** — Given I operate the page with a keyboard,
When I tab through the list,
Then each item is a single focusable target with a visible focus ring, `Enter` performs the same action as a click, and the item's accessible name includes its read state, message, and time.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Tapping an item marks it read and nothing else — it never navigates | Items deep link to the related record and mark read in one action | A notification's whole purpose is to get you to the record. The mobile model tells you something happened and then abandons you on an inbox screen. (Blocked on `relatedType`, §5.2.) |
| No filter — the inbox is one undifferentiated list | **All / Unread** segmented filter, reflected in the URL | Catching up means finding what you haven't seen; scanning 30 items for four bold ones is the wrong task. |
| Absolute timestamps only ("Aug 6, 2:30 PM") | Relative under 7 days, absolute after, absolute in a tooltip | [Design-System §10](../Design-System.md). "2 hours ago" is what an inbox reader is actually asking. |
| Errors rendered as a raw exception string via `e.toString()` | Standard error panel with a mapped message, **Retry**, and `correlationId` | [README §6.2](../README.md); the raw-exception failure mode is [Bugs.md #6](../../Bugs.md). |
| Empty state is an icon and the words "No notifications" | Empty state explains what will appear here, and the unread-filtered case is a separate state | An unexplained empty screen reads as broken. |
| Whole list reloads after every mark-read | The affected row updates in place; the list reloads only on **Mark all read** or an explicit refresh | Reloading 30 rows to change one bold weight loses the reader's position. |
| No indication that the inbox is capped at 30 | Footer note stating the cap | Otherwise an older notification looks deleted rather than out of window. |
| *(cross-cutting)* [README §6.4](../README.md) requires server-side pagination on list pages | No pagination; a single unpaged request capped at 30 | `GET /me/notifications` accepts no pagination params and returns a plain array ([API.md §15](../../API.md)). Documented rather than faked. |

## 10. Open questions

1. **Notifications carry no `relatedType`.** [API.md §15](../../API.md) documents no response body for `GET /me/notifications`, and the payload the server returns carries `id`, `type`, `message`, `referenceNumber`, `relatedEntityId`, `read`, and `createdAt` — with no `relatedType` (verified against `backend/.../dto/NotificationResponse.java`), even though the column exists ([Database.md §3.7](../../Database.md)). Without it the client holds a record id it cannot route. Fix: expose `relatedType` (and document the full payload in API.md). **Blocking for §5.2, element #9's navigation, and element #10's Open record.** Until then, clicking marks read only.
2. **The `type` vocabulary is undocumented.** API.md refers to "event type (see API/notification set)" but no canonical list exists. The values observed in the backend senders are `JOB_APPROVED`, `REQUEST_REJECTED`, `INFO_REQUIRED`, `JOB_ASSIGNED`, `JOB_IN_PROGRESS`, `JOB_AWAITING_PARTS`, `JOB_COMPLETED`, `JOB_CANCELLED`, `APPOINTMENT_SCHEDULED`, `APPOINTMENT_RESCHEDULED`, and `APPOINTMENT_CANCELLED` (verified against `backend/.../feature/job/service/JobService.java` and `backend/.../feature/scheduling/service/AppointmentService.java`). A documented enum is needed before the page can map types to icons or group them, and it is also the only fallback if question 1 is resolved by inference rather than by adding the field.
3. **History beyond 30 items.** The cap is hard and there is no total count, so a user cannot reach anything older and the page cannot say how much is hidden. Confirm 30 is sufficient for internal roles — a busy technician could exceed it in a day — or add pagination to `GET /me/notifications` per the standard paged envelope.
4. **Mark as unread.** Element #10 lists it because "I'll deal with this later" is a real inbox need, but no endpoint un-reads a notification ([API.md §15](../../API.md)). Either add `POST /me/notifications/{id}/unread` or drop the menu item. **Blocking for that one menu item only**; the rest of element #10 is unaffected.
5. **Do internal roles receive notifications at all?** [Database.md §3.7](../../Database.md) notes that customers have a persisted inbox while staff use computed counters and technician alerts may be transient, and every notification the backend sends today is addressed to a customer except `JOB_ASSIGNED`, which goes to a technician (verified against `backend/.../feature/job/service/JobService.java`). On that behavior an `ADMIN` or `STAFF` user's inbox is permanently empty, which makes this page and the top-bar bell decorative for two of the three panel roles. Product decision required: either send staff/admin notifications for the events they care about (new request submitted, job overdue), or scope this page and the bell to `TECHNICIAN` and remove them from the other roles' shells. **Blocking for whether the page ships to `ADMIN`/`STAFF` at all.**
6. **Documented role scope.** [API.md §15](../../API.md) marks `/me/notifications` as `CUSTOMER`, while [README §6.7](../README.md) has every panel user polling the unread count and the endpoints apply no role restriction. Correct API.md to "Authenticated (self)" so the panel is not building against a contract that excludes its own users.
7. **Unread count versus list count.** §5.4 deliberately lets the bell and the page's summary disagree when unread items fall outside the 30-item window. Confirm this is acceptable, or resolve it by having the page display the polled count instead — at the cost of a number that does not match the visible rows.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/notifications/presentation/notifications_screen.dart`, `frontend/lib/features/notifications/data/notifications_repository.dart`, [API.md §15](../../API.md), and [Database.md §3.7](../../Database.md).*
