# AP-02 · Application Shell & Navigation

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-02 |
| **Route** | None — the shell is not a route. It wraps every authenticated route via a router outlet, and additionally owns the error routes `/403`, `/404`, and the `*` catch-all ([README §4](../README.md)) |
| **Roles** | All authenticated internal roles: `ADMIN`, `STAFF`, `TECHNICIAN`. Never rendered for `CUSTOMER` (refused at sign-in, [AP-01 §5.2](AP-01-login.md)) or for anonymous visitors |
| **Entry points** | Rendered the moment a session is established and on every subsequent navigation until sign-out |
| **Primary endpoints** | `GET /me` (identity for the avatar menu) · `GET /me/notifications/unread-count` (bell badge — **contract gap, see §10 Q1**) · `POST /auth/logout` (best-effort audit on sign-out) |
| **Archetype** | Persistent chrome: fixed sidebar + sticky top bar + content region ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-01 Sign In](AP-01-login.md) · [AP-03 Dashboard](AP-03-dashboard.md) · [AP-16 Notifications](AP-16-notifications.md) · [AP-17 My Account](AP-17-account.md) · [Design-System §4, §7, §8](../Design-System.md) |

## 2. Purpose

The shell is the panel's answer to the mobile app's biggest structural limitation: on mobile, every task begins and ends at the dashboard because navigation is a stack of pushed screens. Here, navigation is permanent furniture — an admin can move from the request queue to a customer's history to the appointment calendar without ever returning to a hub, and can see at a glance which section they are in. It also concentrates the things that must behave identically on every page: who you are signed in as, what needs your attention, where you are in the hierarchy, how you get out, and what happens when the session or the URL turns out to be invalid. Because it is always on screen, it is also the panel's keyboard and screen-reader backbone; the focus and page-title rules in §5.6 are what make route changes comprehensible to someone who cannot see the layout shift.

## 3. Layout anatomy

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [Skip to content]  (visible on focus, first tab stop)                    │
├────────────────┬─────────────────────────────────────────────────────────┤
│ ACE Telecom    │  Catalog › Device Categories        [🔔 3] [ AK ▾ ]     │  top bar (56px)
│ RepairDesk  ⟨⟩ ├─────────────────────────────────────────────────────────┤
├────────────────┤                                                         │
│ WORK           │                                                         │
│  ▸ Dashboard   │                                                         │
│  ▸ Repair Req. │   <main id="content">                                   │
│  ▸ Jobs        │     router outlet — the page's own <h1> and content     │
│  ▸ Appointments│                                                         │
│                │                                                         │
│ PEOPLE         │                                                         │
│  ▸ Customers   │                                                         │
│  ▸ Staff       │                                                         │
│                │                                                         │
│ CATALOG        │                                                         │
│  ▸ Device Cat. │                                                         │
│                │                                                         │
│ CONFIGURATION  │                                                         │
│  ▸ Business S. │                                                         │
│                │                                                         │
│ ────────────── │                                                         │
│  ⟨ Collapse    │                                                         │
└────────────────┴─────────────────────────────────────────────────────────┘
   240px expanded / 64px rail
```

Collapsed rail (768–1023px, or toggled at any width):

```
┌──────┬───────────────────────────────────────────┐
│  AT  │  Catalog › Device Categories   [🔔] [AK▾] │
├──────┤                                           │
│  ▣   │  (tooltip "Dashboard" on hover/focus)     │
│  ▣   │                                           │
│  ▣   │                                           │
└──────┴───────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Skip to content | Anchor | "Skip to content" | Visually hidden until focused; the **first** tab stop on every page. Moves focus into `<main>`, past the whole sidebar. Required by [Design-System §8](../Design-System.md). |
| 2 | Brand block | Logo + wordmark, link to landing | "ACE Telecom" / "RepairDesk" | Static text — the business name lives behind `GET /settings`, which is admin-only, so it is not fetched (§10 Q6). Links to the role's default landing page. In the rail it reduces to the monogram. |
| 3 | Sidebar group headings | Text, `label-large` / `text-secondary` | Work · People · Catalog · Configuration | Rendered as `<nav>` with grouped lists. A group whose every item is hidden for the current role is **not rendered at all** — no empty headings. Hidden in the rail (the group's items keep their tooltips). |
| 4 | Nav item: Dashboard | Nav link | `/dashboard` | Group **Work**. Visible to all internal roles per [README §4](../README.md) — but see §10 Q2, since [README §3](../README.md) describes technicians as seeing Jobs and Appointments only. This spec follows §3: hidden for `TECHNICIAN`. |
| 5 | Nav item: Repair Requests | Nav link | `/requests` | Group **Work**. `ADMIN` only, matching shipped behavior and the conflict note in [README §3](../README.md). |
| 6 | Nav item: Jobs | Nav link | `/jobs` | Group **Work**. All internal roles. For `TECHNICIAN` the destination lists only their assigned jobs — enforced server-side by `GET /jobs` ([API.md §13](../../API.md)), not by the nav. |
| 7 | Nav item: Appointments | Nav link | `/appointments` | Group **Work**. All internal roles per [README §4](../README.md), but `GET /appointments` is `STAFF`/`ADMIN` only; technicians read `GET /me/schedule` instead. **See §10 Q3** — the item must resolve to a technician-appropriate view or be hidden. |
| 8 | Nav item: Customers | Nav link | `/customers` | Group **People**. `ADMIN`, `STAFF`. |
| 9 | Nav item: Staff | Nav link | `/staff` | Group **People**. `ADMIN` only. |
| 10 | Nav item: Device Categories | Nav link | `/catalog/categories` | Group **Catalog**. `ADMIN` only. Stays active while on the child route `/catalog/categories/:id/brands`. |
| 11 | Nav item: Business Settings | Nav link | `/settings` | Group **Configuration**. `ADMIN` only. |
| 12 | Active-item indication | Visual state on #4–#11 | Current route | Active item gets a `--color-primary` left bar, a tinted background, the label in `--color-primary`, **and** `aria-current="page"` — three cues, because color alone is not a cue ([Design-System §8](../Design-System.md)). A nav item is active for its own route **and** its child routes (`/jobs` stays active on `/jobs/:id` and `/jobs/:id/schedule`). Exactly one item is ever active. |
| 13 | Collapse toggle | Icon button | — | Toggles 240px ⇄ 64px rail. `aria-expanded` reflects state; the label reads "Collapse sidebar" / "Expand sidebar". The choice is remembered per browser and restored on next load. Below 1024px the rail is the default, but the toggle still works; below 768px it becomes the drawer trigger (§5.7). |
| 14 | Rail tooltips | Tooltip | Item label | In the rail, every item shows its label on hover **and on keyboard focus**, and keeps an accessible name so the icon is never the only identification. |
| 15 | Breadcrumb | Trail, top bar | Group → page → record | Rendered for nested pages only ([Design-System §5.9](../Design-System.md)); a top-level page shows just its name or nothing. Record-level crumbs use the reference number once loaded and a neutral placeholder ("Loading…") before that — never a raw UUID. Each crumb except the last is a link. |
| 16 | Notification bell | Icon button with badge | `GET /me/notifications/unread-count` → `{ count }` | Polls every 30s while the tab is foregrounded ([README §6.7](../README.md)); polling pauses on `visibilitychange` and refetches immediately on return. Badge hidden at 0, shows the number to 99, then `99+`. Accessible name includes the count ("Notifications, 3 unread"). Poll failures are silent — the last known value is kept, never an error toast, never a reset to 0. Opens [AP-16](AP-16-notifications.md). **For `ADMIN` and `STAFF` this count is always 0 in practice — see §10 Q1.** |
| 17 | User avatar menu | Avatar button + menu | `GET /me` | Trigger shows initials from `name` (falling back to `username`). The open menu shows: display name, `@username`, a role badge ([Design-System §5.3](../Design-System.md)), a divider, **My account** → `/account`, **Sign out**. Standard menu semantics: `Esc` closes, arrow keys move, focus returns to the trigger. |
| 18 | Sign out | Menu item | — | See §5.4. |
| 19 | Content region | `<main id="content">` | Router outlet | Max width 1440px, centered, `--space-l` gutters ([Design-System §4](../Design-System.md)). Owns the page `<h1>`; the shell never renders a second one. |
| 20 | Session notice host | Toast/banner host | — | Toasts render bottom-right and are owned by the shell so they survive route changes ([Design-System §5.6](../Design-System.md)). `403`/`404` never appear here — they are page states. |
| 21 | Global search | *Deferred* | — | The shell sketch in [README §4](../README.md) shows a global search box. No cross-entity search endpoint exists — `q` is per-list on `/requests`, `/jobs`, `/customers`, `/users`, `/appointments`. Omitted from V1 rather than faked; §10 Q5. |

## 5. Interactions & dialogs

### 5.1 Navigating

Clicking a nav item performs a real route change: the URL updates, the browser history gains an entry, and Back/Forward work. Nav items are real anchors, so Ctrl-click and middle-click open a new tab. Navigating to the route you are already on is a no-op — no refetch, no history entry. Below 768px, choosing an item also closes the drawer.

### 5.2 Collapsing the sidebar

The toggle switches between the 240px expanded sidebar and the 64px rail. In the rail, group headings are hidden and each item is an icon with a tooltip and an accessible name. The preference persists per browser and is applied before first paint so the layout does not visibly jump. The persisted preference does not override the responsive rules in §5.7 — below 1024px the rail is the layout regardless of what was stored, and the stored preference is restored when the viewport widens again.

### 5.3 Notification bell

| Aspect | Behavior |
| --- | --- |
| Poll | `GET /me/notifications/unread-count` every 30s, foreground only; a single shared timer for the whole shell, not one per component. |
| Badge | Hidden at 0; the count up to 99; `99+` above that. |
| Failure | Silent. The previously known count stays on screen. A polling endpoint that shouts on every transient failure trains users to ignore the shell. |
| Open | Navigates to `/notifications`; the badge refreshes when that page marks items read, without waiting for the next poll tick. |

### 5.4 Sign out

There is no server-side session to end — the JWT is non-expiring and `POST /auth/logout` is an audit-only no-op that returns `204` ([API.md §6](../../API.md)). So sign-out is defined by what the client does:

1. Clear the token and all cached identity from memory and browser storage.
2. Stop all polling timers.
3. Discard cached page data so nothing from the previous session can be repainted.
4. Fire `POST /auth/logout` **best-effort**; failure, timeout, or being offline changes nothing about steps 1–3 ([features/authentication.md §5](../../features/authentication.md)).
5. Replace the current history entry with `/login` and show the notice "Sign out complete."

Because the entry is replaced rather than pushed, browser Back after sign-out does not restore the shell. No confirmation dialog: sign-out is not destructive and is trivially reversible by signing back in.

### 5.5 Session expiry / `401` interception

Any `401` from any endpoint, at any time — including a background poll — means the token is no longer accepted, typically because an administrator disabled or deleted the account ([API.md §2](../../API.md)). The shell handles it centrally, exactly once per event:

- Clear the session as in §5.4 steps 1–3 (no logout call — the token is already rejected).
- Navigate to `/login?returnUrl=<current path and query, encoded>`.
- Show "Your session is no longer valid. Please sign in again." on the sign-in page ([README §6.1](../README.md)).
- Concurrent in-flight requests that also `401` must not each trigger a redirect or stack notices; the first one wins and the rest are dropped.
- A `401` on a background poll is treated identically to one on a user action. Silently leaving someone on a shell whose data has stopped updating is worse than interrupting them.

### 5.6 Focus, page titles, and route changes

A route change in a single-page app is invisible to a screen reader unless the shell makes it visible.

- On every completed navigation, focus moves to the new page's `<h1>` (made programmatically focusable), so the next Tab continues inside the new content rather than at the top of the sidebar.
- The browser tab title becomes `<Page> · RepairDesk` — for record pages, `<Reference> · <Page> · RepairDesk` once the record loads. Distinct titles matter for anyone with several tabs of the panel open, which is the normal way this tool gets used.
- The breadcrumb updates in the same tick as the content, never lagging a route behind.
- Scroll position resets to the top of the content region — not the whole document — on navigation to a new route, and is restored on Back.

### 5.7 Responsive behavior

Per [Design-System §7](../Design-System.md):

| Width | Shell |
| --- | --- |
| ≥1440px | Expanded sidebar, full top bar with breadcrumb. |
| 1024–1439px | Expanded sidebar; breadcrumb truncates from the middle if needed. |
| 768–1023px | Sidebar collapses to the 64px rail; tooltips carry the labels; the toggle can still expand it as an overlay. |
| <768px | Sidebar becomes an overlay drawer opened from a hamburger button: it traps focus, closes on `Esc`, on backdrop click, and on choosing an item, and returns focus to the trigger. Not a primary target — admins on phones should use the mobile app. |

### 5.8 Error routes

Both render **inside** the shell, so the user keeps their navigation and can recover in one click. Both set the page title and a real `<h1>`, and neither is ever a toast ([Design-System §5.6](../Design-System.md)).

| Route | Content |
| --- | --- |
| `/404` and the `*` catch-all | Heading "Page not found". Body: "The address `/reqeusts` doesn't match anything in RepairDesk." (the attempted path shown verbatim as text, never rendered as markup). Action: **Back to dashboard** — or **Back to jobs** for a technician, i.e. always a page the user can actually open. |
| `/403` | Heading "You don't have access to this page". Body names the user's role: "Your account has the Technician role, which can't open Business Settings. Ask an administrator if you need access." Action: link to the role's landing page. Reached by direct URL entry, a stale bookmark, a shared link, or a server `403` on a page's primary request. |

A `403` returned by an individual page's own API call renders that page's forbidden state in place rather than navigating to `/403` — the URL should keep describing what the user tried to open ([README §6.5](../README.md)).

## 6. Page states

The shell has no data of its own beyond identity and the unread count, so its states are about the chrome, not a record.

| State | Presentation |
| --- | --- |
| **Booting** | Between a restored session and the first `GET /me`, the shell renders with its structure intact and the avatar and bell as skeletons. Navigation is usable immediately; nothing waits on identity. |
| **Identity failed** | If `GET /me` fails with a non-`401` error, the shell stays usable, the avatar falls back to a neutral icon with the menu limited to **My account** and **Sign out**, and a retry runs on the next navigation. Role-gated nav items are hidden until the role is known — failing closed, since guessing the role could show a technician the settings link. |
| **Loading (route change)** | A 2px indeterminate progress bar under the top bar while the incoming route resolves. The outgoing page stays visible until the new one is ready, so the shell never flashes empty. |
| **Bell unavailable** | Badge simply absent; the bell still opens the notifications page. |
| **Offline** | A persistent info strip below the top bar: "You're offline. Changes can't be saved until the connection returns." It disappears on reconnect. Failed requests still show their own page-level errors. |
| **Not found / Forbidden** | The §5.8 routes, rendered in the content region. |
| **Empty / Filtered-empty** | Not applicable to the shell. |

## 7. Permissions & visibility

| Role | Sidebar contents |
| --- | --- |
| `ADMIN` | Work: Dashboard, Repair Requests, Jobs, Appointments · People: Customers, Staff · Catalog: Device Categories · Configuration: Business Settings. |
| `STAFF` | Work: Dashboard, Jobs, Appointments · People: Customers. The Catalog and Configuration groups are not rendered at all, and Repair Requests is absent per the admin-only queue decision in [README §3](../README.md). |
| `TECHNICIAN` | Work: Jobs, Appointments only — the People, Catalog, and Configuration groups are not rendered, and no group heading is shown for a group with no visible items. The technician's default landing is `/jobs`. See §10 Q2 and Q3 for the two unresolved details: whether Dashboard is offered, and what Appointments points at given that `GET /appointments` refuses technicians. |
| `CUSTOMER` | Never reaches the shell ([AP-01 §5.2](AP-01-login.md)). |

- Hidden nav items are a **UX affordance, not a control**. Route guards mirror the table above, and every page still renders a forbidden state if the API refuses, because any user can type any URL ([README §3](../README.md)).
- The top-bar items — bell, avatar menu, My account — are available to every internal role.
- Role is read from `GET /me`, not from decoding the JWT client-side. The server is the authority on what an account currently is; a token minted before a role change would otherwise drive the navigation.

## 8. Acceptance criteria

**AC-AP02-01** — Given I am signed in as `ADMIN`,
When the shell renders,
Then the sidebar shows exactly the groups Work (Dashboard, Repair Requests, Jobs, Appointments), People (Customers, Staff), Catalog (Device Categories), and Configuration (Business Settings), in that order.

**AC-AP02-02** — Given I am signed in as `STAFF`,
When the shell renders,
Then Repair Requests, Staff, Device Categories, and Business Settings are absent, the Catalog and Configuration headings are not rendered at all, and no empty group heading appears anywhere.

**AC-AP02-03** — Given I am signed in as `TECHNICIAN`,
When the shell renders,
Then the sidebar offers only Jobs and Appointments, and my default landing page is `/jobs`.

**AC-AP02-04** — Given I am on `/jobs/JOB-1042` or `/jobs/JOB-1042/schedule`,
When I look at the sidebar,
Then the Jobs item is the single active item, marked with a colored bar, a tinted background, and `aria-current="page"` — not by color alone.

**AC-AP02-05** — Given I collapse the sidebar to the rail,
When I reload the browser or open another panel page,
Then the sidebar is still collapsed, it was collapsed before first paint without a visible jump, and each icon exposes its label as a tooltip on both hover and keyboard focus.

**AC-AP02-06** — Given I have 3 unread notifications,
When the shell renders and 30 seconds pass,
Then the bell shows the badge "3", the count endpoint has been polled again, and the bell's accessible name states the number of unread items.

**AC-AP02-07** — Given I have more than 99 unread notifications,
When the badge renders,
Then it displays `99+`.

**AC-AP02-08** — Given a notification count poll fails,
When the failure is handled,
Then the previously known count remains displayed, no error toast appears, and polling continues on the next tick.

**AC-AP02-09** — Given I open the avatar menu,
When it renders,
Then it shows my display name, my `@username`, my role as a labelled badge, **My account**, and **Sign out**; `Esc` closes it and focus returns to the avatar button.

**AC-AP02-10** — Given I choose **Sign out**,
When sign-out completes,
Then the token and all cached identity are cleared, polling stops, I am on `/login`, and pressing browser Back does not restore any authenticated page or its data.

**AC-AP02-11** — Given the network is down or `POST /auth/logout` returns an error,
When I choose **Sign out**,
Then I am still signed out locally and still land on `/login` — the server call never blocks or reverses the sign-out.

**AC-AP02-12** — Given my account is disabled by an administrator while I am working on `/requests?status=PENDING_REVIEW`,
When the next request (including a background poll) returns `401`,
Then my session is cleared once, I am redirected to `/login?returnUrl=%2Frequests%3Fstatus%3DPENDING_REVIEW`, and I see "Your session is no longer valid. Please sign in again." exactly once regardless of how many requests were in flight.

**AC-AP02-13** — Given I navigate to a URL that matches no route,
When the not-found page renders,
Then it appears inside the shell with a heading, the attempted path shown as plain text, and a link to a page my role can open.

**AC-AP02-14** — Given I am a `TECHNICIAN` who types `/settings` directly,
When the forbidden page renders,
Then it names my role, explains that the page is not available to it, offers a link to `/jobs`, and displays no settings data.

**AC-AP02-15** — Given I load any authenticated page and press Tab once,
When focus lands,
Then the first tab stop is a visible **Skip to content** link that moves focus into the main content region, past the entire sidebar.

**AC-AP02-16** — Given I navigate from Jobs to Customers,
When the new page finishes rendering,
Then focus moves to the new page's `<h1>`, the browser tab title becomes "Customers · RepairDesk", the breadcrumb reflects the new location, and the content region is scrolled to the top.

**AC-AP02-17** — Given a viewport between 768px and 1023px,
When the shell renders,
Then the sidebar is the 64px icon rail; and below 768px it is an overlay drawer that traps focus, closes on `Esc`, on backdrop click, and on selecting an item, returning focus to the button that opened it.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| No persistent navigation; the dashboard is a list of tiles and every task is a `Navigator.push` that must be popped back to the dashboard | Persistent sidebar with grouped sections, reachable from every page | Back-office work is a sequence of tasks across sections; returning to a hub between each one is pure overhead ([README §1](../README.md)). |
| Navigation state is implicit — no way to tell which area you are in | Explicit active-item indication with `aria-current` | Orientation in a tool with fifteen destinations is a requirement, not a nicety. |
| Only `/login`, `/register`, `/dashboard` are real routes; everything else is an un-addressable pushed screen | Every page has a real, deep-linkable URL; Back/Forward and Ctrl-click work | Sharing "look at this job" is a daily need. |
| Bell and account icon live on the dashboard's `AppBar`, so they disappear on pushed screens | Bell and avatar menu are in a persistent top bar, present on every page | Notification counts are worthless if they are only visible on one screen. |
| Bell renders for every role and polls the customer inbox endpoint, which returns 0 for staff and admins | Bell is specified for all internal roles, but its audience is unresolved (§10 Q1) | Only customers and newly-assigned technicians ever have notification rows, so the mobile bell is decorative for the exact roles this panel serves. Copying it unexamined would ship a badge that can never light up. |
| Account is a pushed screen from an icon with no name, role, or username shown | Avatar menu showing name, `@username`, and role badge before any action | On a shared back-office machine, "who am I signed in as" must be answerable at a glance. |
| No 404 or 403 surface — unreachable states simply cannot be typed | Dedicated not-found and forbidden pages inside the shell | URLs exist now, so typing, bookmarking, and sharing wrong ones is inevitable. |
| Logout has no visible session-expiry handling; a rejected token surfaces as a generic error | Central `401` interception with `returnUrl` and an explanatory notice | Disabled accounts fail immediately by design ([API.md §2](../../API.md)); the user deserves to be told why. |

## 10. Open questions

1. **The bell is empty by construction for the panel's main audience.** [API.md §15](../../API.md) scopes `/me/notifications` to `CUSTOMER`, and [gap-analysis.md](../../gap-analysis.md) states staff get badge counters instead of an inbox in V1. The shipped endpoint is in fact open to any authenticated user, so it returns `200 { count: 0 }` rather than `403` — but notification rows are only ever written for customers and, in exactly one case, for a technician on job assignment (`JOB_ASSIGNED`). So the bell is meaningful for `TECHNICIAN`, and permanently reads zero for `ADMIN` and `STAFF`. Options: (a) show the bell only for technicians, (b) keep it for everyone and accept a badge that never lights up for most users, or (c) start writing staff notifications for queue events — a backend change, out of scope for this panel. **Blocking for element #16 and for the scope of [AP-16](AP-16-notifications.md).**
2. **Does a technician see Dashboard?** [README §3](../README.md) says technicians see Jobs and Appointments only; [README §4](../README.md) and the route map list `/dashboard` for all internal roles. This spec hides it and lands technicians on `/jobs`, matching the mobile app. Confirm, and align whichever README section is wrong.
3. **Appointments for technicians.** `GET /appointments` is `STAFF`/`ADMIN` only; technicians read their own schedule via `GET /me/schedule` ([API.md §14](../../API.md)). So the Appointments nav item as specified in [README §4](../README.md) would 403 for exactly the role most likely to click it. Options: point technicians at a personal-schedule view backed by `GET /me/schedule` (preferred), or hide the item for them. **Blocking for element #7.**
4. **Staff access to the request queue.** Inherited from [README §3](../README.md) — the panel hides Repair Requests from `STAFF` per shipped behavior, while four other documents grant staff approve/reject. Product decision; only nav visibility and the route guard change either way.
5. **Global search.** The shell sketch in [README §4](../README.md) includes one, but no endpoint searches across requests, jobs, and customers together. Either add such an endpoint, define it as a client-side fan-out across the three `q`-capable list endpoints (three requests per keystroke — not recommended), or accept its absence in V1. Element #21 is currently deferred.
6. **Brand name source.** The top bar hardcodes "ACE Telecom" because `GET /settings` is admin-only and `GET /settings/scheduling-config` does not include the business name. If the panel should reflect a configured business name for all roles, the name needs to be exposed on an endpoint every role can read.
7. **Notification deep links.** [AP-04](AP-04-repair-requests.md) lists "notification deep link" as an entry point. The shipped notification payload does carry `type`, `referenceNumber`, and `relatedEntityId`, which is enough to route to a request or job, but [API.md §15](../../API.md) documents neither field. Add them to the contract before any page relies on them — and note the target audience problem in Q1 applies here too.
8. **Collapsed-state persistence key.** The preference is per browser; confirm it is not expected to follow the user across machines, since no user-preferences endpoint exists.
9. **Technician default landing.** [PRD §6](../../PRD.md) says a technician lands on their personal schedule (today's appointments); the mobile app lands them on the jobs list. This spec follows the mobile app. Resolving this also resolves Q3, since both hinge on whether a technician schedule view exists in V1.

---

*Spec status: draft. Verified against `frontend/lib/features/dashboard/presentation/dashboard_screen.dart`, `frontend/lib/features/notifications/data/unread_count_provider.dart`, `frontend/lib/features/notifications/data/notifications_repository.dart`, the shipped `NotificationController`, `NotificationService`, and `NotificationResponse` in `backend/`, [API.md](../../API.md) §2, §6, §13–15, [README.md](../README.md) §3–4 and §6, and [Design-System.md](../Design-System.md) §4, §7–8.*
