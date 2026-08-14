# AP-08 · Schedule / Reschedule Appointment

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-08 |
| **Route** | `/jobs/:id/schedule` — one route, two modes (§5.1). No query string; the mode is derived from the job's current appointment, not from the URL. |
| **Roles** | `ADMIN`, `STAFF`. `TECHNICIAN` is forbidden — technicians cannot create, reschedule, or cancel appointments ([features/scheduling.md](../../features/scheduling.md) §Technician Interaction). |
| **Entry points** | [AP-07 Job Detail](AP-07-job-detail.md) → **Schedule appointment** / **Reschedule** · [AP-09 Appointments Calendar](AP-09-appointments.md) → unscheduled jobs panel · [AP-06 Jobs](AP-06-jobs.md) row action menu |
| **Primary endpoints** | `GET /jobs/{id}` · `GET /jobs/{id}/appointments` · `GET /settings/scheduling-config` · `GET /users?role=TECHNICIAN` · `GET /requests/{requestId}` · `POST /jobs/{id}/appointments` · `PUT /jobs/{id}/appointments/{appointmentId}/reschedule` · `DELETE /jobs/{id}/appointments/{appointmentId}` |
| **Archetype** | Form page ([Design-System §4](../Design-System.md)) — single column, max 640px, sticky footer |
| **Related** | [features/scheduling.md](../../features/scheduling.md) · [API.md §14](../../API.md) · [AP-15 Business Settings](AP-15-business-settings.md) (owns the working hours this page obeys) · [Bugs.md](../../Bugs.md) #14 |

> **Contract note.** [API.md §14](../../API.md) documents appointment mutations at `/appointments/{id}/reschedule` and `/appointments/{id}/cancel`. The shipped backend serves them at the **job-scoped** paths listed above, and the mobile client calls those. Since the backend is unchanged for this project, this spec is written against the shipped contract and the divergence is recorded as §10 Q1 for API.md to be corrected. Every endpoint below is likewise verified against the deployed controllers; see [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The single place where a job acquires a committed date, time, and technician. Scheduling is the step that turns approved backlog into a promise to a customer, so the page's job is to make the *valid* choices obvious and the invalid ones unreachable: it loads the shop's working hours, slot duration, and booking horizon from settings and drives both pickers from them, so a user cannot casually pick a closed Sunday, an 18:00 start against a 17:00 close, or a date four months out. The same page handles the first booking and every subsequent move, because the fields and rules are nearly identical — only the endpoint, the copy, and the presence of an appointment being replaced differ. Client-side constraints are normally a convenience over server enforcement; here they carry more weight than usual, because the server checks the date but **not** the time of day against working hours (§5.5), so this form is in practice the only thing standing between staff and a 3 a.m. appointment.

## 3. Layout anatomy

```
┌──────────────────────────────────────────────────────────────┐
│ Jobs › JOB-1042 › Schedule                                   │  breadcrumb
│ Schedule appointment                                          │  h1
│ JOB-1042 · A. Kumar · Mobile Phone · Assigned                 │  job summary strip
├──────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ Replacing this appointment      (reschedule mode only)   │ │
│ │ Fri 7 Aug 2026, 10:00 am – 11:00 am · Priya Sharma       │ │
│ │ Scheduled            [ Cancel appointment instead ]       │ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                              │
│ Customer preference                                          │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ 8 Aug 2026, 2:00 pm — advisory only     [ Use this ]      │ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                              │
│ Technician *                                                 │
│ [ Priya Sharma                                          ▾ ]  │
│                                                              │
│ Date & time                                                  │
│ [ 12 Aug 2026                              📅 ]              │
│ [ 10:00 am                                  ▾ ]              │
│ Ends 11:00 am · Wed hours 9:00 am – 5:00 pm (shop local)     │
│ ⚠ Priya already has JOB-1055 in this slot — you can proceed  │
│                                                              │
│ Notes (optional)                                             │
│ [                                                         ]  │
│ [                                                         ]  │
├──────────────────────────────────────────────────────────────┤
│                                    [ Cancel ]  [ Schedule ]  │  sticky footer
└──────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Link trail | Jobs › `referenceNumber` › Schedule | The middle crumb returns to `/jobs/{id}`. Guarded by the dirty-form prompt ([Design-System §9](../Design-System.md)). |
| 2 | Page title | `<h1>` | "Schedule appointment" or "Reschedule appointment" | Wording follows the mode (§5.1). |
| 3 | Job summary strip | Read-only key facts | `GET /jobs/{id}` — `referenceNumber`, `contactName`, `deviceCategoryName`, job status chip | Not editable here. The reference is a link to the job detail page. Gives the user proof they are scheduling the right job before they commit. |
| 4 | Replaced-appointment card | Card, reschedule mode only | `GET /jobs/{id}/appointments` — the appointment whose `id` matches the job's `currentAppointmentId`: `scheduledDate`, `startTime`–`endTime`, `technicianName`, status chip | Present only when `currentAppointmentId` is non-null. Makes explicit what is being moved, since the API returns no reschedule history to show afterwards (§10 Q3). Hosts the **Cancel appointment instead** action (#13). |
| 5 | Customer preference | Inline suggestion + **Use this** button | `GET /requests/{requestId}` (the job's originating request) → `preferredDate`, `preferredTime` | Advisory only, never binding ([features/scheduling.md](../../features/scheduling.md) §Scheduling Concepts). **Use this** copies the values into the date and time controls **only if** they are still valid under the current config; otherwise the button is disabled with a tooltip naming the reason (in the past, closed day, outside hours, beyond horizon, or not on a slot boundary). Renders "No preference recorded" when absent. The extra request is lazy and non-blocking — a failure hides the block rather than failing the page. |
| 6 | Technician select | Required dropdown | `GET /users?role=TECHNICIAN` → `[{ id, username, name, status, … }]`; the endpoint already returns **active** users of that role only | Displays `name`, falling back to `username`. Required in schedule mode; **read-only in reschedule mode** (§5.2). Prefilled from the job's `assignedTechnicianId` when set. Disabled accounts are never listed — an inactive technician is a hard `422` server-side. When the roster is empty the form shows the blocked state in §6. |
| 7 | Date picker | Required date input + calendar popover | Constrained by `GET /settings/scheduling-config` | Minimum selectable date is **today**; maximum is today + `bookingHorizonDays`. Any weekday whose `workingHours` entry has `isClosed = true` is disabled in the popover and rejected on typed input. Weekday lookup uses **`0 = Monday … 6 = Sunday`** (§8, [Design-System §10](../Design-System.md)). Disabled dates state the reason in their accessible name ("16 Aug 2026, closed"). Format `d MMM yyyy`. |
| 8 | Time select | Required dropdown of slot starts | Derived from the selected date's `openTime`, `closeTime`, and `slotDurationMinutes` | Options step from `openTime` by `slotDurationMinutes` while `start + slotDurationMinutes <= closeTime`, so the last slot always ends at or before close. When the selected date is today, slot starts already past are omitted — the server checks only the date, never the time of day (§5.5). Changing the date regenerates the options and clears a selection no longer offered, with an inline notice rather than a silent reset. Format `h:mm a`. |
| 9 | Derived end time & hours hint | Helper text, `body-small` | Computed start + `slotDurationMinutes`; the selected weekday's `openTime`/`closeTime` | Reads "Ends 11:00 am · Wed hours 9:00 am – 5:00 pm (shop local)". Explicitly labelled as shop-local business hours, since timestamps elsewhere render in the browser's timezone ([Design-System §10](../Design-System.md)). |
| 10 | Technician conflict warning | Inline warning strip | `GET /appointments?from={date}&to={date}`, filtered client-side to the selected technician | Refetched when technician or date changes. If a returned appointment overlaps the chosen slot, shows "Priya already has JOB-1055 in this slot — you can proceed." Advisory only: overlap is a **soft warning**, never a block ([features/scheduling.md](../../features/scheduling.md) §Schedule Conflict Rules). The server computes the same overlap but only writes it to the audit log, so this client check is the only way a user ever sees it (§10 Q4). A failed lookup degrades to no warning and never blocks submission. |
| 11 | Notes | Optional textarea, 3 rows | Free text | Sent as `notes`; returned as `appointmentNotes`. No server-side length limit — see §10 Q6. In reschedule mode the field starts empty rather than copying the previous note, because the reschedule creates a new appointment record and a stale note is worse than none. |
| 12 | Footer: Cancel | Secondary button, sticky footer | — | Returns to `/jobs/{id}`. Prompts if the form is dirty. |
| 13 | Cancel appointment instead | Secondary destructive action inside the card (#4) | — | Opens the cancellation dialog (§5.4). Deliberately separated from the footer's **Cancel**, which only abandons the form, and worded in full so the two can never be confused. |
| 14 | Footer: Save | Primary button, sticky footer | Label "Schedule" or "Reschedule" | Disabled while pristine, invalid, or in flight; shows a busy spinner while in flight. No double submits ([README §6.8](../README.md)). |
| 15 | Form-level error summary | Alert above the footer | Non-field validation messages and any `fieldErrors[]` that match no control | The server returns most scheduling validation failures as a plain message rather than `fieldErrors[]` (§5.3), so this element carries them and guarantees nothing is silently swallowed. |
| 16 | Loading / error / forbidden / not-found | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Mode resolution

The page loads `GET /jobs/{id}`, `GET /settings/scheduling-config`, and `GET /users?role=TECHNICIAN` in parallel, then picks a mode from the job's `currentAppointmentId`:

| Condition | Mode | Title / save label | Endpoint on save |
| --- | --- | --- | --- |
| `currentAppointmentId` is null, job is `CREATED` or `ASSIGNED` | **Schedule** | "Schedule appointment" / **Schedule** | `POST /jobs/{id}/appointments` |
| `currentAppointmentId` is set (job `SCHEDULED`, or `IN_PROGRESS` being moved mid-job) | **Reschedule** | "Reschedule appointment" / **Reschedule** | `PUT /jobs/{id}/appointments/{appointmentId}/reschedule` |
| Job is `COMPLETED` or `CANCELLED` | **Blocked** | — | None; see §6 |

In reschedule mode the page additionally calls `GET /jobs/{id}/appointments` to populate the replaced-appointment card, matching on `currentAppointmentId`.

Only one active appointment per job is permitted, and the panel never tries to create a second: when one exists it switches to reschedule rather than offering **Schedule**. Attempting a create anyway returns `409 CONFLICT` — "Job already has an active appointment. Reschedule or cancel it first."

### 5.2 Changing the technician while rescheduling — decision

**The panel does not allow it, and says why.** The shipped reschedule endpoint accepts only `{ scheduledDate, startTime, notes? }` and copies the technician from the appointment being replaced; there is no way to change the technician through it. [API.md §14](../../API.md) claims the body accepts `technicianId?` and [features/scheduling.md](../../features/scheduling.md) describes rescheduling as changing "the date/time **and optionally the technician**", but neither matches the implementation, and inventing the field would produce a request the server silently ignores — the worst possible outcome, because staff would believe they had reassigned the work.

So the technician select is rendered **read-only** in reschedule mode, showing the inherited technician with the helper text "Rescheduling keeps the same technician." Next to it, **Change technician** explains the only route the API actually supports: cancel this appointment and book a new one. Choosing it opens the cancellation dialog (§5.4) pre-filled with the reason "Reassigning to a different technician", and on success returns the user to this page in schedule mode with the technician select now editable. The dialog states plainly that the customer will receive a cancellation notice followed by a new booking notice, because two notifications instead of one is a real cost the user should choose knowingly.

This is worth fixing in the backend later — adding `technicianId?` to the reschedule body would collapse a three-step, two-notification workaround into one action. Recorded as §10 Q2.

### 5.3 Save

| Mode | Request | Success |
| --- | --- | --- |
| Schedule | `POST /jobs/{id}/appointments` with `{ technicianId, scheduledDate, startTime, notes? }`. `scheduledDate` is `YYYY-MM-DD`, `startTime` is `HH:mm`; `endTime` is derived server-side from `slotDurationMinutes`. | `201` with the appointment. Toast "JOB-1042 scheduled for 12 Aug 2026, 10:00 am with Priya Sharma". Navigate to `/jobs/{id}`. The job becomes `SCHEDULED`, gains the technician as its assignee if it had none, and the customer is notified. |
| Reschedule | `PUT /jobs/{id}/appointments/{appointmentId}/reschedule` with `{ scheduledDate, startTime, notes? }`. | `200` with the **new** appointment. Toast "JOB-1042 moved to 12 Aug 2026, 10:00 am". Navigate to `/jobs/{id}`. The old appointment is cancelled and superseded server-side, the job's current appointment now points at the new one, and the customer is notified. |

Rescheduling changes a commitment the customer has already been told about, so it confirms first: a dialog restating the old and new date and time, and the sentence "The customer will be notified of the new time." Creating a first appointment needs no confirmation step — the form itself is the deliberate act. Rescheduling a job that is `IN_PROGRESS` additionally warns that the job's status will roll back to `SCHEDULED`, which is a side effect the user is unlikely to predict.

**Response handling:**

- **`422 VALIDATION_ERROR`** — returned for a past date, a date beyond the booking horizon, and a non-technician or inactive technician. These arrive as a top-level `message` rather than `fieldErrors[]`, so the panel maps the known cases back onto the responsible control by matching on the error's field where present and otherwise rendering the message in the form-level summary (#15). The form stays open with all input preserved.
- **`409 CONFLICT`** — the job is `COMPLETED` or `CANCELLED`, the job already has an active appointment, or the appointment being rescheduled is already `COMPLETED` or `CANCELLED`. All four mean someone else changed things underneath the user, so the panel shows the server's message with a **Reload** action that refetches the job and re-resolves the mode.
- **`404`** — the job or appointment no longer exists, or the appointment does not belong to this job. Page-level not-found state.
- **`403`** — page-level forbidden state (§6), not a toast.

Appointment mutations accept **no `version`**, so the optimistic-concurrency rule in [README §6.3](../README.md) cannot be applied here; the `409`s above are the only concurrency protection available. See §10 Q5.

### 5.4 Cancel appointment dialog

| Aspect | Content |
| --- | --- |
| Title | "Cancel the appointment for JOB-1042?" |
| Body | "The appointment on Fri 7 Aug 2026, 10:00 am will be cancelled and the customer notified. **The job itself is not cancelled** — it returns to the unscheduled backlog so you can book it again." |
| Fields | **Reason** * — required, multi-line. Empty or whitespace-only input keeps the confirm button disabled; the server also rejects a blank reason with `422`. The reason text is included in the customer's notification, so the field's helper text says so. |
| Actions | **Keep appointment** · **Cancel appointment** (error-coloured) |
| Endpoint | `DELETE /jobs/{id}/appointments/{appointmentId}` with `{ reason }` → `204` |
| Result | Toast "Appointment for JOB-1042 cancelled". The job reverts to `ASSIGNED` if a technician is still assigned, otherwise `CREATED`. Navigate to `/jobs/{id}` — or, when the dialog was opened via **Change technician** (§5.2), back to this page in schedule mode. |

The reason is mandatory and audited ([features/scheduling.md](../../features/scheduling.md) §Cancellation Workflow), and the copy deliberately separates appointment cancellation from job cancellation, which is a different action with a very different meaning for the customer.

### 5.5 Config-driven picker behavior, and where the server stops enforcing

`GET /settings/scheduling-config` returns `{ slotDurationMinutes, bookingHorizonDays, workingHours: [{ dayOfWeek, isClosed, openTime, closeTime }] }` and is the single source for every picker constraint:

| Config value | Drives | Also enforced server-side? |
| --- | --- | --- |
| `bookingHorizonDays` | The date picker's maximum date | **Yes** — `422` beyond the horizon |
| *(implicit)* today | The date picker's minimum date | **Yes** — `422` for a date before today |
| `workingHours[].isClosed` | Which weekdays are disabled in the date picker | **No** |
| `workingHours[].openTime` / `closeTime` | The first and last slot offered for the selected date | **No** |
| `slotDurationMinutes` | The step between slot starts, and the derived end time | Partly — the server derives `endTime` from it but accepts any `startTime` |

The right-hand column is the uncomfortable part: the shipped validation checks the date against today and the horizon and nothing else. A closed day, a time outside opening hours, an end time past close, a slot that ignores the configured duration, and a time earlier today that has already passed are **all accepted** by the server. The client rules are therefore not a convenience here — they are the only enforcement, which is why every one of them is an acceptance criterion in §8 and why §10 Q7 asks for the server-side checks the feature spec already promises.

Consequently, if the config request fails the form does **not** fall back to an unconstrained picker; it renders the config-unavailable state in §6. An unconstrained picker against an unvalidating server would let staff book anything at all.

An existing appointment may legitimately sit outside the current hours, because changing working hours does not invalidate bookings already made ([API.md §11](../../API.md)). The replaced-appointment card therefore shows such a time normally, with a note that it falls outside the current hours, rather than treating it as invalid data.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Breadcrumb and title render immediately; the job summary strip and each field group show skeletons shaped like the final controls. The footer is present with **Save** disabled. |
| **Empty** | Not applicable — this page is a form, never a list. |
| **Error** | Error panel with the mapped message, **Retry**, and `correlationId`. Applies when the job, config, or technician roster fails to load. |
| **Forbidden** | `TECHNICIAN` reaching the URL directly, or any `403`: "Scheduling is handled by staff and administrators", with a link to `/appointments`, where technicians can see their own schedule, rather than a dead end. |
| **Not found** | `404` on the job: "That job no longer exists", with a link back to `/jobs`. |
| **Blocked — job not schedulable** | Job is `COMPLETED` or `CANCELLED`: the form is replaced by an explanation ("JOB-1042 is completed and can no longer be scheduled") and a link to the job. Prevents a guaranteed `409`. |
| **Blocked — no active technicians** | `GET /users?role=TECHNICIAN` returns an empty roster: "No active technicians are available to schedule", with a link to [AP-12 Staff](AP-12-staff.md) for admins and an "ask an administrator" sentence for staff. An appointment cannot exist without a technician. |
| **Blocked — config unavailable** | `GET /settings/scheduling-config` failed, or returned no working-hours entries: "Working hours aren't configured, so appointments can't be booked yet", with **Retry** and, for admins, a link to [AP-15 Business Settings](AP-15-business-settings.md). |
| **All days closed** | Every weekday has `isClosed = true`: the same blocked treatment, worded "The shop is marked closed every day of the week." An operational alarm, not an empty picker. |
| **Saving** | Save shows a spinner and disables; all fields become read-only; the dirty-navigation guard stays armed until the response resolves. |

## 7. Permissions & visibility

- **Route guard**: `ADMIN` and `STAFF`. `TECHNICIAN` and `CUSTOMER` get the forbidden state. The guard is a UX affordance only — the server independently rejects non-staff callers on all three mutations with `403`, and the page renders the same panel either way ([README §3](../README.md)).
- **Technician roster access**: `GET /users?role=TECHNICIAN` is available to `STAFF` and `ADMIN` on the shipped backend, so both roles can populate the dropdown. (API.md marks `GET /users` admin-only, which would break staff scheduling; see §10 Q8.)
- **Record-state gating** is independent of role: `CREATED` and `ASSIGNED` jobs can be scheduled; `SCHEDULED` and `IN_PROGRESS` jobs can be rescheduled; `COMPLETED` and `CANCELLED` jobs can be neither. A `COMPLETED` or `CANCELLED` appointment can be neither rescheduled nor cancelled.
- **Cancel appointment** is `STAFF` / `ADMIN` only and is offered only while an active appointment exists.

## 8. Acceptance criteria

**AC-AP08-01** — Given a job in `ASSIGNED` status whose `currentAppointmentId` is null,
When I open `/jobs/{id}/schedule`,
Then the page is titled "Schedule appointment", the save button reads **Schedule**, the technician select is editable and prefilled with the job's assigned technician, and no replaced-appointment card is shown.

**AC-AP08-02** — Given a job whose `currentAppointmentId` is set,
When I open `/jobs/{id}/schedule`,
Then the page is titled "Reschedule appointment", a card shows the existing appointment's date, time range, technician, and status, and the page never attempts to create a second appointment.

**AC-AP08-03** — Given `slotDurationMinutes = 60` and Wednesday hours of 9:00 am to 5:00 pm,
When I select a Wednesday,
Then the time select offers 9:00 am through 4:00 pm inclusive and no later start, because 4:00 pm is the last slot that ends at or before close.

**AC-AP08-04** — Given `workingHours` marks Sunday as closed,
When I open the date picker,
Then every Sunday in range is disabled and cannot be chosen by clicking or by typing, and its accessible name states that the day is closed.

**AC-AP08-05** — Given working hours use `dayOfWeek` where Monday is 0,
When the date picker evaluates a weekday,
Then it reads the entry at index 0 for Monday and index 6 for Sunday — so with a config in which only Monday is closed, Mondays are the only disabled weekday and Sundays remain bookable. This is the regression in [Bugs.md](../../Bugs.md) #14 and must be covered by an automated test asserting `0 = Monday`.

**AC-AP08-06** — Given today is 6 Aug 2026 and the shop opens at 9:00 am,
When I open the date picker,
Then no earlier date is selectable; and when I select 6 Aug 2026 at 2:00 pm local time, the time select offers only slots starting after 2:00 pm.

**AC-AP08-07** — Given `bookingHorizonDays = 30`,
When I open the date picker,
Then no date more than 30 days from today is selectable, and a typed out-of-horizon date is rejected client-side rather than submitted.

**AC-AP08-08** — Given I have chosen a technician and date, and that technician already has an appointment overlapping my chosen slot,
When the slot is selected,
Then an advisory warning names the conflicting job, the save button stays enabled, and submitting succeeds — technician overlap is a soft warning, never a block.

**AC-AP08-09** — Given the server rejects my submission with `422` because the date is beyond the booking horizon,
When the response is handled,
Then the message is shown against the date field or, failing a field match, in the form-level summary; the form stays open with my technician, date, time, and notes preserved; and no navigation occurs.

**AC-AP08-10** — Given I am rescheduling,
When the form renders,
Then the technician select is read-only, states that rescheduling keeps the same technician, and no `technicianId` is included in the reschedule request — the appointment keeps its existing technician.

**AC-AP08-11** — Given I am rescheduling and I need a different technician,
When I choose **Change technician**,
Then I am told this requires cancelling and rebooking and that the customer will receive both a cancellation and a new booking notice; and on confirming, the appointment is cancelled and I return to the page in schedule mode with the technician select editable.

**AC-AP08-12** — Given another user rescheduled or cancelled the same appointment after my page loaded,
When I save,
Then the server's `409` is shown with a **Reload** action that refetches the job and re-resolves the mode, and my stale write is not applied.

**AC-AP08-13** — Given a job that already has an active appointment,
When a create is attempted for it — for instance from a stale tab,
Then the `409` "Job already has an active appointment" is surfaced as a human-readable message with **Reload**, and no duplicate appointment is created.

**AC-AP08-14** — Given an active appointment,
When I choose **Cancel appointment instead** and leave the reason blank,
Then the confirm button stays disabled and no request is sent; and when I supply a reason and confirm, the appointment is cancelled, the dialog has stated that the job itself is not cancelled, and the job returns to `ASSIGNED` or `CREATED` so it can be booked again.

**AC-AP08-15** — Given I am signed in as a technician,
When I navigate directly to `/jobs/{id}/schedule`,
Then I see the forbidden panel explaining that scheduling is handled by staff and administrators, with a link to my own schedule, and no form is rendered.

**AC-AP08-16** — Given I am using only the keyboard,
When I move through the form,
Then I can open the date picker, move between dates with the arrow keys, and commit with Enter; the time select is operable with the arrow keys and type-ahead; disabled dates and unavailable slots are announced as unavailable with their reason; and focus returns to the triggering control when a picker closes.

**AC-AP08-17** — Given `GET /settings/scheduling-config` fails or returns no working hours,
When the page renders,
Then the form is replaced by an explanation that appointments cannot be booked until working hours are configured, with **Retry** — the pickers are never left unconstrained, because the server does not validate working hours.

**AC-AP08-18** — Given a job in `COMPLETED` status,
When I navigate to its schedule route,
Then the form is not rendered and I am told the job can no longer be scheduled, with a link back to the job.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Technician list comes from `GET /users` with a client-side filter to `role == 'TECHNICIAN' && status == 'ACTIVE'` | Uses `GET /users?role=TECHNICIAN` | The server already filters that parameter to **active** users of the role, so fetching every user — customers included — and filtering in the client is wasted payload that grows with the customer base. Same endpoint, correct parameter. (Note that API.md documents a `GET /technicians` roster endpoint that the backend does not implement — §10 Q8.) |
| Technician is hidden entirely in reschedule mode, with no explanation | Technician is shown read-only with an explanation and a **Change technician** path | The mobile form leaves the user guessing whether the technician is being kept, cleared, or reassigned. The panel states the inherited value and offers the only route the API supports. See §5.2. |
| Date picker allows any date from today to today + 90 days, hardcoded | Maximum date comes from `bookingHorizonDays` | 90 days is a client-side invention that does not match the configured horizon the server enforces — it produces `422`s on submit for dates the picker happily offered. |
| Closed days are selectable | Closed days are disabled in the picker | The server does not reject them either, so on mobile a booking on a day the shop is shut succeeds silently. This is the single most consequential fix on the page. |
| Free-form clock picker, any minute of any hour, defaulting to 9:00 am | Slot select generated from `openTime`, `closeTime`, and `slotDurationMinutes` | V1 uses **fixed slots** ([features/scheduling.md](../../features/scheduling.md) §Time Slot Management). A free clock invites 10:37 pm starts that the server accepts and the shop cannot honour. |
| Date defaults to tomorrow with no regard for whether the shop is open | No date is preselected; the picker opens on the next valid working day | Defaulting to a closed day pre-fills an invalid form. |
| The scheduling config is never fetched by this screen | Config is fetched and drives every constraint | Without it the client can mirror no rule at all, which is why the mobile form's only real validation is "a field is empty". |
| No customer preference shown | Preferred date and time shown, with **Use this** when still valid | The preference is captured at request submission specifically to seed scheduling ([features/scheduling.md](../../features/scheduling.md) AC-2); the mobile form discards it. |
| Nothing shown about the appointment being replaced | Replaced-appointment card | Rescheduling silently supersedes a real commitment; showing nothing means the user cannot check they are moving the right booking. |
| No technician conflict warning anywhere | Client-side overlap check against the technician's day | The server computes the overlap and writes it only to the audit log, so without this the warning the feature spec requires is invisible to everyone. |
| Errors surfaced as `SnackBar('Error: $e')` — the raw exception | Mapped messages, `409` reload path, form-level summary | Raw exception text to the user is prohibited ([README §6.2](../README.md), [Bugs.md](../../Bugs.md) #6). |
| Success `SnackBar` is shown *after* `Navigator.pop`, so it can be lost | Toast fires on the destination page | A confirmation the user may never see is not a confirmation. |
| No appointment cancellation from this screen | **Cancel appointment instead**, with a mandatory reason | The endpoint exists; without it, staff who need to un-book must cancel the whole job, which tells the customer their repair was cancelled. |

## 10. Open questions

1. **API.md documents the wrong paths for appointment mutations.** [API.md §14](../../API.md) specifies `POST /appointments/{id}/reschedule` and `POST /appointments/{id}/cancel`; the backend serves `PUT /jobs/{jobId}/appointments/{appointmentId}/reschedule` and `DELETE /jobs/{jobId}/appointments/{appointmentId}`, and API.md omits `GET /jobs/{jobId}/appointments` entirely. This spec follows the implementation because the backend is unchanged for this project. API.md should be corrected — it is the document the panel team will read first.
2. **Should reschedule accept a technician change?** API.md and [features/scheduling.md](../../features/scheduling.md) both say yes; the implementation copies the technician from the old appointment and accepts no `technicianId`. Until that is reconciled the panel uses the cancel-and-rebook path (§5.2), which costs the customer an extra notification. Adding `technicianId?` to the reschedule body is a small change with a disproportionate UX payoff. **Blocking for element #6 in reschedule mode.**
3. **Reschedule history is not retrievable.** Appointments record `supersededAppointmentId`, but `GET /jobs/{jobId}/appointments` excludes `CANCELLED` rows — and a superseded appointment is cancelled — so no endpoint returns the chain. The page can therefore show what is being replaced but cannot show what came before it, and neither can [AP-07](AP-07-job-detail.md). Options: add an `includeCancelled` parameter, or expose the history on job detail. **Blocking for any previous-appointments display.**
4. **The overlap warning never reaches the client.** The server computes technician overlap on create and writes it into the audit entry, but the response carries no warning field (API.md describes a `warnings: [...]` payload that does not exist). The panel therefore performs its own check via `GET /appointments` (#10), which is unavailable to a `STAFF` user only if that endpoint's role scope changes, and is not performed at all on reschedule by the server. Confirm whether the warning should be returned in the response body.
5. **No `version` on appointment mutations.** Create, reschedule, and cancel accept no `version`, so the optimistic-concurrency rule in [README §6.3](../README.md) does not apply to this page. Two staff members rescheduling the same appointment at once produce two appointments in sequence, with only the state checks in §5.3 catching the second. Confirm this is acceptable for V1 or add `version` to the reschedule and cancel bodies.
6. **Notes field: name and limits.** The request body field is `notes` and the response field is `appointmentNotes`; the asymmetry is easy to get wrong and should be documented in API.md. There is also no maximum length, so the textarea has no counter and a very long note is stored unchecked — confirm a limit.
7. **Working hours are not enforced server-side.** `validateDateTime` checks only past date, booking horizon, and end-after-start. Closed days, times outside opening hours, ends past close, and non-slot-aligned or already-passed times on today are all accepted. [features/scheduling.md](../../features/scheduling.md) §Validation Rules and [API.md §14](../../API.md) both describe these as hard blocks. Until the server implements them, this page is the only guard and any other client — including the existing mobile app — can create invalid appointments. **Recommended as a backend fix before the panel ships.**
8. **`GET /technicians` does not exist — verified, and it is an API.md defect rather than a backend gap.** API.md §8 documents `/technicians` as the roster endpoint for assignment dropdowns and marks `GET /users` as admin-only. Reading the deployed controllers confirms that no `/technicians` route exists anywhere in the backend, and that `GET /users?role=TECHNICIAN` is open to both `ROLE_STAFF` and `ROLE_ADMIN`. That single endpoint covers everything this page needs for both roles, so staff scheduling is not at risk and nothing here is blocked. API.md §8 needs correcting on both counts.
9. **Customer preference requires a second request.** `preferredDate` and `preferredTime` live on the request, not the job; the panel follows `job.requestId` to `GET /requests/{requestId}` for element #5. Surfacing them on the job response would remove a round trip on a page that already makes four.
10. **Timezone of `openTime` / `closeTime` and `startTime`.** All are bare `HH:mm` with no zone, compared against `LocalDate.now()` on the server. The panel treats them as shop-local wall-clock times and compares against the browser's local date, which is correct only while the server, staff, and shop share a timezone. Confirm before any remote-access or multi-site scenario.

---

*Spec status: draft. Verified against `frontend/lib/features/scheduling/presentation/schedule_appointment_screen.dart`, `frontend/lib/features/scheduling/data/scheduling_repository.dart`, the shipped `AppointmentController`, `AppointmentService`, `SettingsController`, and `UserManagementController`, [API.md](../../API.md) §8, §11, §13–14, [features/scheduling.md](../../features/scheduling.md), and [Bugs.md](../../Bugs.md) #14.*
