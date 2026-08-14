# AP-15 · Business Settings

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-15 |
| **Route** | `/settings` |
| **Roles** | `ADMIN` only |
| **Entry points** | Sidebar → Configuration → Business Settings · Dashboard "Business Settings" card · "Change working hours" link from a scheduling validation error ([AP-08](AP-08-schedule-appointment.md)) |
| **Primary endpoints** | `GET /settings` · `GET /settings/scheduling-config` · `PUT /settings` · `PUT /settings/working-hours` |
| **Archetype** | Form page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-08 Schedule Appointment](AP-08-schedule-appointment.md) · [API.md §11](../../API.md) · [Database.md §3.9/§3.10](../../Database.md) · [Bugs.md #14](../../Bugs.md) |

## 2. Purpose

The single place an administrator configures how the shop runs: who the business is, how long an appointment slot lasts, how far ahead customers may book, and which hours the shop is open on each day of the week. These are not cosmetic preferences — the scheduling values are enforced when anyone books an appointment, so a change here immediately narrows or widens what customers can request and what staff can schedule ([API.md §14](../../API.md)). Everything on the page is both displayed and editable in place; unlike the mobile app there is no separate read-only view, because a web form has room to be its own display.

## 3. Layout anatomy

```
┌──────────────────────────────────────────────────────────────────────┐
│ Business Settings                                                    │  page header
│ Last saved 6 Aug 2026, 2:30 pm                                       │
├──────────────────────────────────────────────────────────────────────┤
│ Business information                                                 │  section 1
│  Business name *  [ ACE Telecom                                   ]  │
│  Address          [ 12 Market Street, …                           ]  │
│  Phone            [ 0712345678        ]  Email [ shop@example.com ]  │
├──────────────────────────────────────────────────────────────────────┤
│ Scheduling                                                           │  section 2
│  Appointment slot duration *   ( )30  (•)60  ( )90  ( )120 minutes   │
│  Booking horizon *             [ 60 ] days ahead   (1–365)           │
│  ℹ These limit what customers can request and what staff can book.   │
├──────────────────────────────────────────────────────────────────────┤
│ Working hours                        Times are local shop hours.     │  section 3
│  Monday     [ Open  ●— ]   [  9:00 am ▾ ] – [  6:00 pm ▾ ]           │
│  Tuesday    [ Open  ●— ]   [  9:00 am ▾ ] – [  6:00 pm ▾ ]           │
│  Wednesday  [ Open  ●— ]   [  9:00 am ▾ ] – [  6:00 pm ▾ ]           │
│  Thursday   [ Open  ●— ]   [  9:00 am ▾ ] – [  6:00 pm ▾ ]           │
│  Friday     [ Open  ●— ]   [  9:00 am ▾ ] – [  6:00 pm ▾ ]           │
│  Saturday   [ Open  ●— ]   [  9:00 am ▾ ] – [  1:00 pm ▾ ]           │
│  Sunday     [ —○ Closed ]  Closed all day                            │
├──────────────────────────────────────────────────────────────────────┤
│ Unsaved changes in Scheduling, Working hours   [ Cancel ] [ Save ]   │  sticky footer
└──────────────────────────────────────────────────────────────────────┘
```

Single column, max 640px for the field groups; the working-hours rows are allowed to run wider so the day name, toggle, and two time pickers sit on one line down to 1024px.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Business Settings" | Static. |
| 2 | Last saved | Text, `body-small` | `updatedAt` from `GET /settings` | "Last saved 6 Aug 2026, 2:30 pm" in the browser's local timezone ([Design-System §10](../Design-System.md)). Renders "—" when the field is absent. |
| 3 | Business name | Text input | `businessName` | Max 150 chars ([Database.md §3.9](../../Database.md)). Seeded as "ACE Telecom". Part of the **settings** save group. |
| 4 | Business address | Textarea, 2 rows | `businessAddress` | Optional, max 500 chars. |
| 5 | Business phone | Text input | `businessPhone` | Optional, max 20 chars. Display/reference only — not used for notifications in V1. |
| 6 | Business email | Text input | `businessEmail` | Optional, max 255 chars, email-shaped. Helper text: "Shown for reference only — RepairDesk does not send email in V1" ([Database.md §3.9](../../Database.md)). |
| 7 | Appointment slot duration | Radio group, 4 options | `slotDurationMinutes` | **30 / 60 / 90 / 120 minutes** — the only accepted values ([API.md §11](../../API.md)). A radio group rather than a dropdown because four fixed options are worth showing at once. Helper text: "An appointment's end time is derived from this." Part of the **settings** save group. |
| 8 | Booking horizon | Number input + suffix | `bookingHorizonDays` | Whole days, 1–365 (see open question 3). Suffix "days ahead"; helper text: "Customers can request a date up to this many days from today." |
| 9 | Scheduling impact note | Info note | Static | "These values are enforced when an appointment is booked" with a link to [AP-08](AP-08-schedule-appointment.md). See §5.4. |
| 10 | Working-hours rows | Seven rows | `workingHours[]` from `GET /settings/scheduling-config` | Always exactly seven, always ordered **Monday first, Sunday last**. The day label comes from the fixed mapping in §5.2 — never from array position in the response and never from a locale weekday function. |
| 11 | Open/closed toggle | Switch, per row | `isClosed` (inverted) | On = open, off = closed. Switching to closed hides and clears that row's time pickers; switching to open restores the last entered times, or 9:00 am – 6:00 pm if the row has never had any. Accessible name is the day: "Monday — open". |
| 12 | Open time / Close time | Two time pickers, per row | `openTime`, `closeTime` | Rendered and entered as `h:mm a`, sent as `HH:mm` ([API.md §1](../../API.md)). Required and enabled only when the row is open. Close must be **after** open (§5.3). |
| 13 | Closed-day text | Text, `body-medium`, `text-secondary` | — | "Closed all day" replaces the time pickers on a closed row, so a closed day reads as a deliberate state rather than two empty fields. |
| 14 | Dirty summary | Text, `body-small`, sticky footer | — | Names the sections with unsaved changes: "Unsaved changes in Scheduling, Working hours". |
| 15 | Save | Primary button, sticky footer | — | Disabled while pristine, invalid, or in flight. Saves only the dirty groups — see §5.5. |
| 16 | Cancel | Secondary button, sticky footer | — | Reverts every field to the last loaded values and clears the dirty state. Confirms first if anything is dirty. |
| 17 | Loading / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Loading the page

The page issues two reads:

- `GET /settings` (`ADMIN`) — business information plus `slotDurationMinutes`, `bookingHorizonDays`, and `updatedAt`.
- `GET /settings/scheduling-config` (any authenticated user) — `{ slotDurationMinutes, bookingHorizonDays, workingHours: [{ dayOfWeek, isClosed, openTime, closeTime }] }`.

Both are needed: [API.md §11](../../API.md) describes `GET /settings` as returning "full business settings + working hours", but the working-hours array is only contractually specified on `/settings/scheduling-config`, so the page reads hours from there and does not depend on the ambiguity. See open question 1. If the two responses disagree on `slotDurationMinutes` or `bookingHorizonDays`, `GET /settings` wins (it is the admin-facing record) and the discrepancy is logged to telemetry rather than shown.

If `workingHours` comes back with fewer than seven entries, the page still renders seven rows: the missing days are shown as **closed** with a form-level note "Some days had no saved hours and are shown as closed. Saving will store all seven days." It never renders only the days it received, because a five-row week silently misrepresents the shop's schedule.

### 5.2 Weekday mapping — `0 = Monday`

`dayOfWeek` is **`0 = Monday … 6 = Sunday`** ([Database.md §3.10](../../Database.md), [Design-System §10](../Design-System.md)). The page uses exactly this table and nothing else:

| `dayOfWeek` | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Label | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |

Rules that follow from it, all of them non-negotiable:

- The day-name lookup has **exactly seven** entries and is indexed directly by `dayOfWeek`. No leading blank entry, no `+1`, no `-1`, no `Date`/`Intl` weekday function (JavaScript's `Date.getDay()` is `0 = Sunday`, which is the opposite convention and must never be used here).
- Rows are ordered by ascending `dayOfWeek`, not by response order — the API is not required to return them sorted.
- Rows are keyed by `dayOfWeek`, not by array index, so a row's identity survives reordering.
- The payload sent to `PUT /settings/working-hours` echoes each row's original `dayOfWeek` back; the client never recomputes it from position.
- A unit test asserts that the first row is Monday with `dayOfWeek = 0` and the last is Sunday with `dayOfWeek = 6` (AC-AP15-06, AC-AP15-07).

This is a shipped-bug regression guard, not defensive pedantry: a stray leading `''` in the mobile app's day-name array shifted every label by one — Monday rendered blank, Tuesday rendered as "Monday", Sunday rendered as "Saturday" — and a second independent copy of the same off-by-one sat in the edit screen's fallback ([Bugs.md #14](../../Bugs.md), which supersedes #5). The panel keeps exactly one mapping, in one place, covered by a test.

### 5.3 Working-hours validation

| Rule | Message | Enforced by |
| --- | --- | --- |
| Open time required when the day is open | "Set an opening time for Monday" | Client + server (`422`) |
| Close time required when the day is open | "Set a closing time for Monday" | Client + server (`422`) |
| Close must be after open | "Monday's closing time must be after its opening time" | Client + server (`422`) ([API.md §11](../../API.md)) |
| Closed day | Times are cleared and sent as null | Client + server |

Validation runs on blur per field and again on submit for the whole form ([Design-System §9](../Design-System.md)). Messages name the day, because a form with seven near-identical rows and an unattributed error is unusable. A `422` from the server binds back to the offending day's control by matching `fieldErrors[].field`; anything unmatched goes to a form-level summary above the sticky footer so nothing is swallowed.

Every day may be closed. The page does not block it, but it warns before saving: "Every day is set to closed. Customers won't be able to request any appointment date." This is a confirmation, not a validation error — a shop may legitimately shut for a period.

### 5.4 Downstream impact

Both scheduling values are enforced at booking time, not just recorded here:

- **Slot duration** derives an appointment's `endTime` from its start ([API.md §14](../../API.md)), so changing it changes how many bookings fit in a day.
- **Booking horizon** bounds the dates a customer may request; a preferred date beyond it is rejected with `422` ([API.md §12.1](../../API.md)).
- **Working hours** are a hard block: booking outside them, or with an end time past close of business, returns `422` ([API.md §14](../../API.md)).

The Scheduling section carries an info note saying so and linking to [AP-08](AP-08-schedule-appointment.md), where those errors surface. Changing hours **does not** invalidate existing appointments — [API.md §11](../../API.md) states that existing appointments survive and are flagged for staff review instead — and the save confirmation says exactly that rather than implying bookings will be cancelled.

The panel does **not** claim to tell you how many appointments a newly closed day already holds. No endpoint answers "are there appointments on future Mondays": `GET /appointments` filters by `dateFrom`/`dateTo`, `technicianId`, and `status`, not by weekday ([API.md §14](../../API.md)). Rather than fake a count by fetching the whole horizon and filtering client-side, the confirmation states the rule honestly: "Existing appointments on days you're closing are not cancelled — review them in Appointments." See open question 4.

### 5.5 Saving — two endpoints, one page

The two save groups map to two independent endpoints:

| Group | Fields | Endpoint | Body |
| --- | --- | --- | --- |
| **Settings** | Business name, address, phone, email, slot duration, booking horizon (elements #3–#8) | `PUT /settings` | `{ businessName?, businessAddress?, businessPhone?, businessEmail?, slotDurationMinutes?, bookingHorizonDays?, version }` — see open questions 2 and 5 |
| **Working hours** | The seven rows (elements #10–#13) | `PUT /settings/working-hours` | An array of `{ dayOfWeek, isClosed, openTime?, closeTime? }`, all seven days, always ([API.md §11](../../API.md)) |

**One Save button, not two.** The page presents itself as one form because the sections are read together and often changed together, and two save buttons invite the user to forget one. Under the hood:

- Save issues a request only for the groups that are **dirty**. A change confined to business information never touches working hours.
- When both are dirty they are sent **sequentially**, `PUT /settings` first, then `PUT /settings/working-hours`. Sequential rather than parallel so a failure has an unambiguous "what got through" answer.
- There is no transaction across the two calls, and no endpoint that reverses either one, so the panel does not attempt a rollback. It reports the split state instead.

**Partial failure.** If the first call succeeds and the second fails, the page shows a persistent (non-dismissing) form-level banner, error-styled:

> **Business information and scheduling were saved. Working hours were not.**
> {mapped message from [README §6.2](../README.md)} · correlation ID `…`
> **[ Retry working hours ]**

and then:

- The **saved** group is marked clean, its inputs reset to the values the server returned, and a small "Saved" marker sits on its section heading for the rest of the visit.
- The **failed** group stays dirty with every edit intact and its section heading marked "Not saved".
- **Retry working hours** re-sends only the failed call. It never re-sends the group that already succeeded.
- The `Last saved` timestamp updates, because part of the record genuinely changed. Leaving the page while the failed group is still dirty triggers the standard unsaved-changes confirmation ([Design-System §9](../Design-System.md)).
- The reverse order fails the same way with the labels swapped, though it can only occur if working hours are saved first — which this design never does.

A full success shows one toast, "Business settings saved", and refreshes both reads so the page reflects the server's values rather than the client's optimistic ones.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Skeletons shaped like the real form: four input-sized blocks, a radio row, and seven working-hours rows. The sticky footer is present but disabled. |
| **Loaded** | The form, populated. There is no separate read-only mode — see §9. |
| **Empty** | Not applicable: `business_settings` is a seeded singleton ([Database.md §9](../../Database.md)). If `GET /settings` reports the singleton is missing, that is a server misconfiguration and renders the error state, not an empty state. |
| **No working hours configured** | Seven closed rows plus the note in §5.1. Never a bare "No working hours configured" message with nothing to act on. |
| **Error** | Standard error panel with the mapped message, **Retry**, and the `correlationId`. Shown when either read fails; retry refetches both. |
| **Partial save failure** | The persistent banner in §5.5, with the form still on screen and edits preserved. Never a toast — a toast that disappears after five seconds is the wrong carrier for "half your change is gone". |
| **Saving** | Save shows a spinner and disables; every field is read-only while in flight; no double submit. |
| **Forbidden** | "Business settings are limited to administrators", with a link to `/dashboard`. Rendered on a `403` from `GET /settings` regardless of what the client believed about the user's role. |

## 7. Permissions & visibility

- `ADMIN` only, for the route, the sidebar item, and every mutation. `PUT /settings` and `PUT /settings/working-hours` are admin-gated server-side ([API.md §11](../../API.md)).
- `GET /settings/scheduling-config` is readable by **any** authenticated user because the request form and scheduling UI need it — but it is only *this page* that is admin-only. A `STAFF` user who can read the config through another screen still gets the forbidden panel here.
- No record-state gating: there is exactly one settings record and it is always editable by an admin.
- The panel never renders a disabled, uneditable copy of the form to non-admins. If you cannot edit it, you do not see it here.

## 8. Acceptance criteria

**AC-AP15-01** — Given I am an admin opening `/settings`,
When the page loads,
Then business information, scheduling values, and seven working-hours rows are all shown as editable fields on one page, with no separate Edit action or mode.

**AC-AP15-02** — Given the page is loading,
When I wait for the response,
Then I see skeletons shaped like the form's sections rather than a bare centered spinner.

**AC-AP15-03** — Given the scheduling section,
When I inspect the appointment slot duration control,
Then exactly four options are offered — 30, 60, 90, and 120 minutes — and no other value can be selected or typed.

**AC-AP15-04** — Given the booking horizon field,
When I enter `0`, `366`, a negative number, or a non-integer,
Then submission is blocked with a field-level message stating the allowed range, and no request is sent.

**AC-AP15-05** — Given a valid change to slot duration and booking horizon,
When I save,
Then `PUT /settings` is called, a success toast confirms the save, and the `Last saved` timestamp updates.

**AC-AP15-06** — Given the working-hours section has rendered,
When I read the rows top to bottom,
Then they are Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday in that order, with the **first row carrying `dayOfWeek = 0`** and the **last row carrying `dayOfWeek = 6`**.

**AC-AP15-07** — Given working hours are returned in an arbitrary order, or with a day missing,
When the rows render,
Then each label is derived from the seven-entry `0 = Monday` mapping keyed by `dayOfWeek` — never from array position — no row renders with a blank or shifted label, and exactly seven rows are shown.

**AC-AP15-08** — Given a day is open,
When I set its closing time earlier than or equal to its opening time,
Then submission is blocked with a message naming that day, and no request is sent.

**AC-AP15-09** — Given a day is open with no times entered,
When I try to save,
Then submission is blocked with a message naming that day and the missing time.

**AC-AP15-10** — Given I switch a day to closed,
When the row re-renders,
Then its time pickers are replaced by "Closed all day", and the saved payload for that day carries `isClosed: true` with null times.

**AC-AP15-11** — Given I have changed only working hours,
When I save,
Then `PUT /settings/working-hours` is called with all seven days and `PUT /settings` is **not** called.

**AC-AP15-12** — Given I have changed both scheduling values and working hours,
When `PUT /settings` succeeds and `PUT /settings/working-hours` fails,
Then a persistent error banner states which section saved and which did not, the working-hours section stays dirty with my edits intact, a **Retry working hours** action re-sends only the failed call, and no toast claims the settings were saved.

**AC-AP15-13** — Given a save is in flight,
When I click Save again or edit a field,
Then the second submission is ignored and the fields are read-only until the request settles.

**AC-AP15-14** — Given I am closing a day that customers can currently book,
When the save confirmation appears,
Then it states that existing appointments on that day are not cancelled and points me to Appointments to review them.

**AC-AP15-15** — Given I set every day to closed,
When I save,
Then I am warned that customers will not be able to request any appointment date, and the save proceeds only after I confirm.

**AC-AP15-16** — Given I have unsaved changes,
When I navigate away or press Cancel,
Then I am asked to confirm, and choosing to discard restores every field to the last loaded values.

**AC-AP15-17** — Given the server rejects a working-hours entry with `422` and a `fieldErrors[]` entry,
When the error renders,
Then the message is bound to the offending day's control (or, if unmatched, shown in a form-level summary), and no raw exception text or JSON appears anywhere.

**AC-AP15-18** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/settings`,
Then I see the forbidden panel with a link to the dashboard, and no settings data or disabled form.

**AC-AP15-19** — Given I operate the working-hours section with a keyboard and a screen reader,
When I move through a row,
Then the toggle's accessible name includes the day, the time pickers have real labels tied to both the day and their role (open or close), and validation messages are announced.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Two screens: a read-only `SettingsScreen` and a separate `SettingsEditScreen` reached via a pencil icon | **One page** that displays and edits in place | The split exists because a phone cannot show a seven-row editable schedule and stay readable. A desktop form can, and round-tripping through a second screen to change one time is pure friction. |
| The view screen shows the banner "To change scheduling settings, contact your system administrator" **while also offering an Edit action** | No such banner | It is self-contradicting — the only people who reach this page are the administrators it tells to go find an administrator. The panel does not carry the contradiction forward; role gating is communicated by the forbidden state (§6), not by advice to the wrong audience. |
| The view screen reads `/settings/scheduling-config` only, so business information is never shown | Business information is shown and editable | `PUT /settings` accepts it and the columns exist ([Database.md §3.9](../../Database.md)); [PRD §6](../../PRD.md) lists business information as part of Settings. The mobile app simply never surfaced it. |
| Booking horizon is a free-text number field that silently ignores out-of-range input (values outside 1–365 are dropped without feedback) | Range is validated with a visible field-level message | Silently discarding a user's typed value is worse than rejecting it; the user believes it was accepted. |
| Save always calls `PUT /settings` **and** `PUT /settings/working-hours`, then reports a single "Settings saved" regardless of which one failed | Only dirty groups are sent; partial failure is reported explicitly per section with a targeted retry | Two endpoints have two outcomes. Reporting one outcome for two calls means a user can be told their hours were saved when they were not. |
| Errors surface as `Error: <exception>` in a snackbar | Standard error mapping and field binding ([README §6.2](../README.md)) | Hard rule; the raw-exception failure mode is [Bugs.md #6](../../Bugs.md). |
| Day names come from a lookup table that was off by one, and a second copy of the same off-by-one lived in the edit screen's fallback | One mapping, keyed by `dayOfWeek`, used by every render path, covered by a test | [Bugs.md #14](../../Bugs.md). Duplication was the root cause; a single mapping is the structural fix. |
| No dirty tracking — leaving the edit screen discards silently | Dirty summary in the sticky footer and an unsaved-changes prompt | [Design-System §9](../Design-System.md). |

## 10. Open questions

1. **What does `GET /settings` actually return?** [API.md §11](../../API.md) describes it as "full business settings + working hours", but the implementation returns the settings singleton alone with no `workingHours` array (verified against `backend/.../controller/SettingsController.java`). The page works around this by also reading `/settings/scheduling-config` (§5.1). Either add the array to `GET /settings` or correct API.md — right now the page makes two reads to avoid depending on which is true.
2. **Business information field names are not in the contract.** [API.md §11](../../API.md) says `PUT /settings` updates "business info" without naming the fields. The four columns exist in [Database.md §3.9](../../Database.md) (`business_name`, `business_address`, `business_phone`, `business_email`) and the request body accepts `businessName`, `businessAddress`, `businessPhone`, `businessEmail` (verified against `backend/.../controller/SettingsController.java`), which is what elements #3–#6 and §5.5 assume. API.md should enumerate them so the panel is coding against a contract rather than an implementation. **Blocking for elements #3–#6** if the names turn out to differ.
3. **Booking horizon range.** The panel validates 1–365 and the server enforces the same bounds with a `VALIDATION_ERROR` (verified against `backend/.../service/SettingsService.java`), but [API.md §11](../../API.md) documents no range and [Database.md §3.9](../../Database.md) has no `CHECK`. Confirm 1–365 is the intended contract and add it to API.md; otherwise element #8's client validation may reject values the server would accept.
4. **Warning when closing a day that has appointments.** No endpoint answers "which future appointments fall on this weekday" — `GET /appointments` filters by date range, technician, and status only ([API.md §14](../../API.md)). Options: (a) add a weekday or "affected by hours change" query, (b) return the affected count from `PUT /settings/working-hours` as a warning payload, mirroring the soft-warning pattern already used for technician overlap ([API.md §14](../../API.md)) — **preferred**, or (c) keep the generic advisory in §5.4. Also unresolved: API.md says such appointments are "flagged for staff review", but no field or endpoint exposes that flag, so no screen can show it. **Blocking for a count-bearing warning; the generic advisory ships either way.**
5. **`version` on `PUT /settings`.** [API.md §11](../../API.md) documents a `version` in the body, but the request record has no `version` field (verified against `backend/.../controller/SettingsController.java`) even though the entity is `@Version`-annotated — so two admins editing settings concurrently will last-write-wins rather than get the `409` that [README §6.3](../README.md) requires. Confirm which is correct and whether §5.5 should be sending it.
6. **Concurrency on working hours.** `PUT /settings/working-hours` takes no `version` at all in either the contract or the implementation, so the same last-write-wins question applies to the seven-row replace. Given the low edit frequency this may be acceptable, but it should be an explicit decision rather than an omission.
7. **Notification settings.** [PRD §6](../../PRD.md) lists "notification settings" and "notification preferences" as part of Settings. No endpoint, field, or column exists for them ([API.md §11](../../API.md), [Database.md §3.9](../../Database.md)), so nothing is specified here. Confirm they are out of scope for V1 rather than missing from this page.
8. **Seven-day guarantee.** `PUT /settings/working-hours` replaces the weekly hours from the array it is given, and the implementation updates only days that already have a row. The panel always sends all seven days to keep that safe. Confirm the endpoint creates a row for a `dayOfWeek` that has none, or the "no working hours configured" recovery path in §5.1 will appear to save and change nothing.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/admin/presentation/settings_screen.dart`, `frontend/lib/features/admin/presentation/settings_edit_screen.dart`, `frontend/lib/features/admin/data/admin_repository.dart`, [API.md §11](../../API.md), [Database.md §3.9/§3.10](../../Database.md), and [Bugs.md #14](../../Bugs.md).*
