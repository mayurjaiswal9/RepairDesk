# AP-07 · Job Detail

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-07 |
| **Route** | `/jobs/:id` |
| **Roles** | All internal roles — `ADMIN`, `STAFF`, `TECHNICIAN` (technicians only for jobs assigned to them; see §7) |
| **Entry points** | [AP-06 Jobs](AP-06-jobs.md) row click · [AP-05 Request Detail](AP-05-request-detail.md) Job panel · [AP-09 Appointments](AP-09-appointments.md) event click · [AP-11 Customer Detail](AP-11-customer-detail.md) history row · Notification and technician-alert deep links |
| **Primary endpoints** | `GET /jobs/{id}` · `POST /jobs/{id}/assign` · `/unassign` · `/start` · `/awaiting-parts` · `/resume` · `/complete` · `/cancel` · `/priority` · `/warranty` · `/notes` (`GET` and `POST`) · `GET /users?role=TECHNICIAN` · `POST /jobs/{id}/attachments` · `GET /appointments/{appointmentId}` |
| **Archetype** | Detail page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-06 Jobs](AP-06-jobs.md) · [AP-05 Request Detail](AP-05-request-detail.md) · [AP-08 Schedule Appointment](AP-08-schedule-appointment.md) · [features/jobs.md](../../features/jobs.md) · [API.md §13, §14, §16](../../API.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The operational record for one repair, and the page where nearly all of the work in this product actually happens. It answers three questions at once: what is this repair, who is responsible for it, and what can be done to it right now. Staff and admins assign technicians, schedule appointments, adjust priority, confirm warranty, and cancel work that cannot proceed; technicians move their own jobs through the repair lifecycle, record what they did, and attach evidence. Because every status transition is audited and several are irreversible, the page's central discipline is to offer exactly the actions the job's current status and the viewer's role permit — and nothing else.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Jobs › RQ-1042                                                             │  breadcrumb
├────────────────────────────────────────────────────────────────────────────┤
│ RQ-1042                                       [ In progress ]  [ Urgent ]  │  record header
│ Created 4 Aug 2026, 9:20 am · Updated 6 Aug 2026, 2:14 pm                  │
│  [ Mark awaiting parts ]  [ Cancel job ]           [ Mark complete ]   ⋮   │  status action bar
├──────────────────────────────────────────┬─────────────────────────────────┤
│ Device & contact                         │ Technician                      │  right panel
│  Contact   A. Kumar   ·  @akumar         │ Priya Sharma                    │
│  Mobile    +91 98765 43210               │ [ Change ]  [ Unassign ]        │
│  Category  Mobile Phone                  │                                 │
│  Device    Samsung Galaxy S21            │ Appointment                     │
│  Serial    •••• •••• 3456   [ Reveal ]   │ 7 Aug 2026 · 11:00 am           │
│  Purchased 12 Jan 2025                   │ [ Reschedule ]                  │
│  Warranty  In warranty · confirmed: Yes  │                                 │
│                                          │ Originating request             │
│ Issue description                        │ RQ-1042 · Approved              │
│  Screen cracked after a fall; touch …    │ [ Open request → ]              │
│                                          │                                 │
│ Resolution summary        (when present) │                                 │
│  Replaced digitiser; tested …            │                                 │
│                                          │                                 │
│ Internal notes                [ + Add ]  │                                 │
│  ─────────────────────────────────────── │                                 │
│  Parts ordered 5 Aug — ETA Thursday.     │                                 │
│                                          │                                 │
│ Job attachments · 2          [ + Upload ]│                                 │
│  [ img ] [ img ]                         │                                 │
│                                          │                                 │
│ ▸ Customer photos · 3        (read-only) │                                 │
└──────────────────────────────────────────┴─────────────────────────────────┘
```

Below 1024px the right panel moves under the main column in the order Technician → Appointment → Originating request, and the status action bar becomes a sticky footer.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Links | "Jobs › {referenceNumber}" | "Jobs" returns to [AP-06](AP-06-jobs.md) with its previous filter/sort/page restored. |
| 2 | Reference number | `<h1>`, `mono` | `referenceNumber` | Equal to the originating request's reference by design ([Database.md §1](../../Database.md), ADR-012) — one number follows the repair from submission to completion. Page's accessible name and document title. |
| 3 | Status chip | Status chip | `status` | Shared job mapping ([Design-System §5.2](../Design-System.md)): New / Assigned / Scheduled / In progress / Awaiting parts / Completed / Cancelled. |
| 4 | Priority chip | Status chip | `priority` | `HIGH` and `URGENT` only; `NORMAL` and `LOW` render nothing. Changing priority is an overflow action (#10), not a click on the chip — a status-looking element that mutates on click is a mis-click waiting to happen. |
| 5 | Created timestamp | Text, `body-small` | `createdAt` | "Created 4 Aug 2026, 9:20 am", local timezone. **Always shown** — its absence was [Bugs.md](../../Bugs.md) #7. Renders "—" only if the field is genuinely absent. |
| 6 | Updated timestamp | Text, `body-small` | `updatedAt` | "Updated 6 Aug 2026, 2:14 pm" beside Created, with a relative value in the tooltip. Must change after **any** mutation including adding a note ([Bugs.md](../../Bugs.md) #8, AC-AP07-13). |
| 7 | Status action bar | Button group | Derived from `status` | Contains only the transitions the current status allows (§5.1). Secondary actions left, the primary advancing action right. Buttons disable and show a spinner while in flight; while any transition is in flight all the others are disabled. For `COMPLETED` and `CANCELLED` the bar is absent and replaced by #8. |
| 8 | Terminal-state line | Text, `body-small` | `status`, `cancelReason` | Replaces the action bar for terminal jobs: "This job is completed — no further status changes are possible", or "This job was cancelled" followed by the recorded `cancelReason`. |
| 9 | Cancellation reason | Callout, error-tinted | `cancelReason` | Shown for `CANCELLED` jobs, above the main column, so the reason is not buried. |
| 10 | Action overflow (⋮) | Menu in the header | Set priority · Confirm warranty · View history | **Set priority** and **Confirm warranty** are `ADMIN`/`STAFF` only. **View history** links to the job's audit trail (`GET /jobs/{id}/history`) — see §10 item 7. The menu is omitted entirely when it would be empty. |
| 11 | Device & contact section | Key–value block | — | Fixed 160px label column; absent optional values render an em-dash ([Design-System §5.4](../Design-System.md)). |
| 12 | Contact / username | Key–value rows | `contactName`, `customerUsername` | Request-local snapshots taken at approval, not the live account contact ([features/jobs.md](../../features/jobs.md) §Job Creation); one line of helper text says so. `@username` links to [AP-11](AP-11-customer-detail.md) for `ADMIN`/`STAFF`; for `TECHNICIAN` it renders as plain text, since technicians have no customer-administration access. |
| 13 | Mobile | Key–value row | `contactMobile` | Rendered as a `tel:` link. |
| 14 | Category / Device | Key–value rows | `deviceCategoryName`, `deviceBrand` + `deviceModel` | Device line joins brand and model with a space; "—" when both are absent. |
| 15 | Serial (IMEI) | Key–value row + reveal action | `deviceSerial` | **Masked by default**, showing only the last four characters, with a **Reveal** control that unmasks for the current view and re-masks on navigation, reload, or tab blur. Never written to console, telemetry, or any client log, and excluded from copy-all/print — `device_serial` is sensitive ([Database.md §1](../../Database.md)). Row and control absent when null. Same treatment as [AP-05](AP-05-request-detail.md). |
| 16 | Purchased / Warranty | Key–value rows | `purchaseDate`, warranty fields | Purchase date as `d MMM yyyy`. The warranty row shows the customer-indicated status and, separately, the staff confirmation, because they are different claims — see §10 item 4 for the field-shape question. |
| 17 | Issue description | Prose block | `issueDescription` | Snapshot from the request; full text, never truncated, line breaks preserved, wrapped at ~90 characters. |
| 18 | Resolution summary | Prose block | `resolutionSummary` | Rendered only when present (i.e. after completion). Headed "Resolution summary" with the completion timestamp beneath it. Read-only here; it is captured in the Mark complete dialog. |
| 19 | Internal notes | Section with an **Add note** action | `GET /jobs/{id}/notes` | **Append-only** ([features/jobs.md](../../features/jobs.md) §Repair Notes): no edit or delete control exists anywhere in this section, for any role. Notes render newest-last in a scrollable block, each with its author and timestamp where the API provides them (see §10 item 3). Internal-only — never customer-visible. Empty state: "No internal notes yet." Adding a note is permitted for `ADMIN`, `STAFF`, and the assigned `TECHNICIAN`. |
| 20 | Add note | Secondary button in the notes header | — | Opens the Add note dialog (§5.3). Disabled while a note is saving. |
| 21 | Job attachments | Attachment gallery, upload permitted | Job attachments for this job | Internal-only repair and completion evidence; customers never see these ([features/jobs.md](../../features/jobs.md) §Attachments). Bytes fetched through the authenticated HTTP layer and rendered from object URLs ([README §6.6](../README.md)). **Upload** accepts images only, shows per-file progress, and surfaces the server's magic-byte rejection message per file without failing the others. Limits are server-enforced (≤10 MB per file, ≤10 files per job — [API.md §16](../../API.md)) and stated in the upload control's helper text. Deleting an attachment is specified but **blocked on §10 item 2**. Upload is available to `ADMIN`, `STAFF`, and the assigned `TECHNICIAN`. |
| 22 | Customer photos | Attachment gallery, read-only, collapsed by default | Request attachments for the originating request | The customer's original submission, carried over as job context. **No upload and no delete** — this is the customer's evidence. Collapsed by default so it does not compete with the job's own attachments; the heading shows the count. Visible to `ADMIN`, `STAFF`, and the assigned `TECHNICIAN` ([Bugs.md](../../Bugs.md) #8 fixed technician access to this list). |
| 23 | Technician panel | Right-panel card | `assignedTechnicianName`, `assignedTechnicianId` | Shows the assigned technician's name, or "Not yet assigned" in `text-secondary`. Actions: **Assign** (when unassigned) or **Change** (when assigned) plus **Unassign**, all `ADMIN`/`STAFF` only and offered only while `status` is `CREATED` or `ASSIGNED` (§5.2). For a `TECHNICIAN` viewer the panel is read-only with no buttons. |
| 24 | Appointment panel | Right-panel card | `currentAppointmentId` → `GET /appointments/{id}` | Shows the confirmed date, time, and technician when an appointment exists; otherwise "Not scheduled". Action: **Schedule appointment** or **Reschedule**, linking to `/jobs/{id}/schedule` ([AP-08](AP-08-schedule-appointment.md)), `ADMIN`/`STAFF` only and offered while `status` is `CREATED`, `ASSIGNED`, or `SCHEDULED`. The panel also restates the customer's original preferred date and time as context — subject to §10 item 5. |
| 25 | Originating request panel | Right-panel card | `requestId` | "Originating request" with the shared reference number and **Open request**, linking to `/requests/{requestId}` ([AP-05](AP-05-request-detail.md)). Rendered for `ADMIN` only, because request detail is admin-gated ([README §3](../README.md)); for `STAFF` and `TECHNICIAN` the reference is shown without a link. |
| 26 | Refresh | Icon button in the header | — | Refetches the job; content stays visible with a progress indicator during the refetch. |
| 27 | Loading / error / forbidden / not-found | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Status transitions

The action bar is derived **only** from the job's current status, mirroring what the API permits ([API.md §13](../../API.md), [features/jobs.md](../../features/jobs.md) §Job Lifecycle). Actions that the status does not allow are absent from the DOM, not disabled — a greyed-out "Mark complete" on a `CREATED` job invites a support ticket rather than preventing a mistake.

| Current status | Actions offered | Notes |
| --- | --- | --- |
| `CREATED` | **Cancel job** | Start is not offered: work cannot begin before a technician is assigned. The Technician panel carries the action that unblocks the job. |
| `ASSIGNED` | **Start repair** (primary) · **Cancel job** | Starting without a scheduled appointment is the documented same-day shortcut ([features/jobs.md](../../features/jobs.md) §Job Statuses). |
| `SCHEDULED` | **Start repair** (primary) · **Cancel job** | — |
| `IN_PROGRESS` | **Mark awaiting parts** · **Cancel job** · **Mark complete** (primary) | — |
| `AWAITING_PARTS` | **Resume** (primary) · **Cancel job** · **Mark complete** | Resume returns the job to `IN_PROGRESS`; the pair may repeat any number of times. |
| `COMPLETED` | none | Terminal. The bar is replaced by the terminal-state line. |
| `CANCELLED` | none | Terminal. |

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Start repair** | Action bar | Confirmation naming the reference: "Start work on RQ-1042?" | `POST /jobs/{id}/start` | Toast "RQ-1042 started". Status becomes In progress; the action bar re-derives; the customer is notified server-side. |
| **Mark awaiting parts** | Action bar | Optional **Parts needed** note (multi-line) describing what is being waited on. | `POST /jobs/{id}/awaiting-parts` with `{ partsNeededNote? }` | Toast "RQ-1042 marked awaiting parts". Status becomes Awaiting parts. |
| **Resume** | Action bar | Confirmation: "Resume work on RQ-1042?" | `POST /jobs/{id}/resume` | Toast "RQ-1042 resumed". Status returns to In progress. |
| **Mark complete** | Action bar | **Resolution summary** * (required, multi-line, 640px dialog) — what was actually done. The dialog states that the customer is notified on completion and that completion is final. | `POST /jobs/{id}/complete` with `{ resolutionSummary }` | Toast "RQ-1042 completed". Status becomes Completed, the Resolution summary section appears, and the action bar is replaced by the terminal-state line. |
| **Cancel job** | Action bar | Error-styled confirm naming the reference. **Reason** * (required, multi-line). The dialog states that any active appointment is cancelled with the job and the customer is notified once. | `POST /jobs/{id}/cancel` with `{ reason }` | Toast "RQ-1042 cancelled". Status becomes Cancelled, the reason callout appears, the Appointment panel clears. |

Every transition disables its button while in flight, and maps `409 CONFLICT` to "This job changed since you opened it — its status is now *X*" with a **Reload** action that refetches the record. `422` binds field errors back to the dialog's controls (a blank resolution summary or reason is caught client-side first, but the server remains the authority).

### 5.2 Assign, unassign, priority, warranty

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Assign / Change technician** | Technician panel → Assign or Change | **Technician** * — a searchable select listing active technicians from `GET /users?role=TECHNICIAN` (`[{ id, name, username, … }]`), showing the name with `@username` as secondary text, prefilled with the current assignee when changing. Disabled technicians never appear in the list. | `POST /jobs/{id}/assign` with `{ technicianId, version }` | Toast "RQ-1042 assigned to Priya Sharma". Status becomes Assigned (from `CREATED`); the panel and the action bar re-derive; the technician is notified server-side. A `409` "Active technician not found or job not assignable" refetches the roster and the job. |
| **Unassign** | Technician panel → Unassign | Confirmation explaining the job returns to **New** and the technician loses access to it. | `POST /jobs/{id}/unassign` | Toast "RQ-1042 unassigned". Status returns to New. |
| **Set priority** | Header overflow → Set priority | **Priority** * — select of `LOW` / `NORMAL` / `HIGH` / `URGENT`, prefilled with the current value. | `POST /jobs/{id}/priority` with `{ priority, version }` | Toast "RQ-1042 priority set to Urgent". The priority chip updates (or disappears, for `NORMAL`/`LOW`). |
| **Confirm warranty** | Header overflow → Confirm warranty | **Warranty confirmed by staff** * — `YES` / `NO` / `UNKNOWN`, prefilled from the job. Helper text distinguishes this staff confirmation from the customer's original claim. | `POST /jobs/{id}/warranty` with `{ warrantyConfirmedByStaff }` | Toast "Warranty confirmation updated". The Warranty row updates. Subject to §10 item 4. |

### 5.3 Notes and attachments

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Add note** | Notes section → Add note | **Note** * (required, multi-line, 640px dialog). Helper text: "Notes are internal, permanent, and attributed to you. Customers never see them." | `POST /jobs/{id}/notes` with `{ text }` | Toast "Note added to RQ-1042". The note appears at the end of the thread **and the displayed Updated timestamp changes** ([Bugs.md](../../Bugs.md) #8). |
| **Upload job attachment** | Job attachments → Upload | Native file picker, images only, multiple files allowed. | `POST /jobs/{id}/attachments` (`multipart/form-data`) | Per-file progress; each file's success or the server's rejection message is reported individually, and one rejected file never discards the others. |
| **Delete job attachment** | Attachment tile → Delete | Confirmation naming the file. | *Not documented* — see §10 item 2 | Specified but not implementable today; the control is omitted until the endpoint exists. |
| **Open attachment** | Click any thumbnail | — | `GET /jobs/{id}/attachments/{attachmentId}` or the request equivalent for customer photos | Full-screen viewer with zoom, arrow-key navigation, and `Esc` to close, returning focus to the tile. |
| **Schedule / Reschedule** | Appointment panel | — | — | Navigates to `/jobs/{id}/schedule` ([AP-08](AP-08-schedule-appointment.md)), which owns the scheduling form, its validation, and the reschedule endpoint. |
| **Open request** | Originating request panel | — | — | Navigates to `/requests/{requestId}` ([AP-05](AP-05-request-detail.md)). |
| **Reveal serial** | **Reveal** beside the masked serial | — | — | Unmasks in place for the current view; re-masks on navigation, reload, or tab blur. |

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Breadcrumb renders immediately; header, both columns, notes, and both galleries render as skeletons shaped like the final layout. |
| **Loading (refetch)** | Content stays visible with a top progress bar; the status action bar is disabled for the duration. |
| **Action in flight** | Only the invoked control spins; every other mutating control on the page is disabled, so two transitions can never race from the same tab. |
| **Appointment unavailable** | If the appointment lookup fails or is not permitted for the viewer, the panel shows "Appointment details unavailable" with a retry affordance — it never shows a blank card implying the job is unscheduled. See §10 item 5. |
| **Notes empty** | "No internal notes yet." with the **Add note** action still available to permitted roles. |
| **Attachments empty** | Job attachments: "No repair photos yet" plus the upload tile. Customer photos: the collapsed section shows a count of 0 and expands to "No photos were submitted with this request." |
| **Error** | Full-page error panel with the mapped message, **Retry**, and the `correlationId`. A single failed image or a failed notes fetch degrades that region only, with its own retry. |
| **Forbidden** | A `403` — a technician opening a job that is not theirs, or any user without access — renders "You don't have access to this job", the user's role, and a link to `/jobs`. No job data is rendered. |
| **Not found** | `404` renders "This job no longer exists or was never visible to you" with a link back to `/jobs`. |
| **Stale record** | After a `409`, the page refetches automatically and shows an inline "Updated elsewhere" marker beside the status chip for 5 seconds so the change is visible before the action is retried. |

## 7. Permissions & visibility

Server-side authorization is authoritative ([API.md §2](../../API.md)); the rules below hide controls a role cannot use, so the page does not offer work that will fail.

| Control | `ADMIN` | `STAFF` | `TECHNICIAN` (assigned) | `TECHNICIAN` (not assigned) |
| --- | --- | --- | --- | --- |
| View the job | Yes | Yes | Yes | **No** — `403`, forbidden state |
| Start · Mark awaiting parts · Resume · Mark complete | Yes | Yes | Yes | — |
| Cancel job | Yes | Yes | **Hidden** | — |
| Assign · Change · Unassign technician | Yes | Yes | **Hidden** | — |
| Set priority | Yes | Yes | **Hidden** | — |
| Schedule / Reschedule appointment | Yes | Yes | **Hidden** | — |
| Add internal note | Yes | Yes | Yes | — |
| Upload job attachment | Yes | Yes | Yes | — |
| View customer photos | Yes | Yes | Yes | — |
| Confirm warranty | Yes | Yes | **Hidden** — see §10 item 4 | — |
| Open the originating request | Yes | Reference shown, not linked | Reference shown, not linked | — |
| Open the customer record | Yes | Yes | **Not linked** | — |

- **Technicians act only on jobs assigned to them.** Both the read (`GET /jobs/{id}`) and every mutation are scoped server-side by assignment ([API.md §13](../../API.md), [features/jobs.md](../../features/jobs.md) §Technician Interaction), so a technician who is reassigned mid-task loses access on their next request and sees the forbidden state rather than a stale record.
- **Technicians never reassign, cancel, reprioritise, or reschedule.** Those are Staff/Admin decisions; the corresponding panels render read-only for technicians rather than showing disabled buttons.
- **Notes and job attachments are internal-only** and are never exposed to customers in V1 ([features/jobs.md](../../features/jobs.md) §Attachments).
- **Serial / IMEI** is least-exposure for every internal role: masked by default, revealed deliberately, never logged.
- **Record-state gating applies on top of role gating**: even an admin sees no transitions on a `COMPLETED` or `CANCELLED` job, and no assign control once work has started.

## 8. Acceptance criteria

**AC-AP07-01** — Given I open `/jobs/{id}` for a job I may view,
When the page loads,
Then the header shows the reference number, the current status chip, the priority chip when priority is `HIGH` or `URGENT`, the created timestamp, and the last-updated timestamp.

**AC-AP07-02** — Given any job,
When the header renders,
Then the job's creation date and time are displayed and not only its last-updated time.

**AC-AP07-03** — Given a job in status `CREATED`,
When the action bar renders,
Then **Cancel job** is the only transition offered, **Start repair** is absent, and the Technician panel offers **Assign**.

**AC-AP07-04** — Given a job in status `ASSIGNED` or `SCHEDULED`,
When the action bar renders,
Then **Start repair** and **Cancel job** are offered and no other transition is.

**AC-AP07-05** — Given a job in status `IN_PROGRESS`,
When the action bar renders,
Then **Mark awaiting parts**, **Mark complete**, and **Cancel job** are offered and no other transition is.

**AC-AP07-06** — Given a job in status `AWAITING_PARTS`,
When the action bar renders,
Then **Resume**, **Mark complete**, and **Cancel job** are offered, and choosing Resume returns the job to In progress.

**AC-AP07-07** — Given a job in status `COMPLETED` or `CANCELLED`,
When the page renders,
Then no status transition control exists anywhere on the page and a line explains that the job is terminal; for a cancelled job the recorded reason is displayed.

**AC-AP07-08** — Given I open the Mark complete dialog,
When I submit with an empty resolution summary,
Then submission is blocked with a field-level message, the dialog stays open with my input, and no request is sent.

**AC-AP07-09** — Given I open the Cancel job dialog,
When I submit without a reason,
Then submission is blocked with a field-level message, and the dialog states that any active appointment will be cancelled with the job.

**AC-AP07-10** — Given a job with no technician,
When I open the Assign dialog,
Then the technician list contains only active technicians returned by `GET /users?role=TECHNICIAN`, and no disabled account is selectable.

**AC-AP07-11** — Given I assign a technician to a `CREATED` job,
When the assignment succeeds,
Then a toast names the job and the technician, the status becomes Assigned, the Technician panel shows the new assignee, and the action bar re-derives to offer **Start repair**.

**AC-AP07-12** — Given an assigned job,
When a staff member unassigns the technician and confirms,
Then the status returns to New, the panel reads "Not yet assigned", and the confirmation stated in advance that the technician would lose access.

**AC-AP07-13** — Given a job whose last-updated time I have noted,
When I add an internal note,
Then the note appears in the thread and the displayed **Updated** timestamp changes to reflect the note.

**AC-AP07-14** — Given a job with existing internal notes,
When the notes section renders for any role,
Then no edit or delete control is offered for any note — the thread is append-only.

**AC-AP07-15** — Given I upload two images to the job and the server rejects one for failing content-type validation,
When the upload completes,
Then the accepted file appears in the gallery, the rejected file reports the server's reason against that file, and no false success is shown for it.

**AC-AP07-16** — Given a job whose originating request had customer photos,
When the Customer photos section is expanded,
Then the photos render through the authenticated HTTP layer, and no upload or delete control is offered for them.

**AC-AP07-17** — Given I am a technician viewing a job assigned to me,
When the page renders,
Then I can start, pause for parts, resume, complete, add notes, and upload attachments — and no assign, unassign, cancel, priority, or scheduling control is present anywhere on the page.

**AC-AP07-18** — Given I am a technician and the job is assigned to someone else,
When I navigate directly to its URL,
Then I see the forbidden panel with a link back to `/jobs`, and no job data — including the customer's contact details and the device serial — is rendered.

**AC-AP07-19** — Given another user changed this job after I loaded it,
When I attempt a status transition,
Then I see that the job changed and what its status now is, with a **Reload** action, and my transition is not applied.

**AC-AP07-20** — Given a job with a recorded device serial,
When the Device & contact section renders,
Then only the last four characters are visible, revealing is an explicit action, and the value returns to masked when I navigate away or reload.

**AC-AP07-21** — Given a job with a confirmed appointment,
When the right panel renders,
Then the appointment's date and time are shown with a **Reschedule** action for staff and admins that opens [AP-08](AP-08-schedule-appointment.md) for this job; a job with no appointment reads "Not scheduled" and offers **Schedule appointment**.

**AC-AP07-22** — Given I operate the page with a keyboard,
When I open any dialog,
Then focus is trapped inside it, `Esc` cancels without submitting, focus returns to the triggering control on close, and each status change is announced to assistive technology through the page's live region.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| One long scrolling column: device, issue, technician, actions, notes, then photos | Two columns — record and evidence left, responsibility and scheduling right — with the action bar in the header | The three questions this page answers (what, who, what next) should be answerable without scrolling. |
| Status actions render as a vertical stack of full-width outlined buttons mid-page | A single action bar in the record header, ordered secondary-to-primary | Transitions are the page's purpose and belong beside the status they change. |
| An `ASSIGNED` job offers **Start repair** only — its Cancel action is unreachable, because the status-to-actions switch matches `ASSIGNED` in an earlier branch than the one that pairs it with Cancel | `ASSIGNED` offers **Start repair** and **Cancel job** | The API permits cancelling from any non-terminal status ([features/jobs.md](../../features/jobs.md) §Job Lifecycle); a duplicated switch case silently removed a permitted action from the most common pre-work state. |
| Serial/IMEI is not displayed at all on job detail | Displayed, masked, with a deliberate reveal | Technicians legitimately need the IMEI to service a device; hiding it entirely pushes them back to the request page. Masking keeps exposure minimal ([Database.md §1](../../Database.md)). |
| Notes render as one undifferentiated text blob with no attribution | A thread with author and timestamp per entry | [features/jobs.md](../../features/jobs.md) §Repair Notes requires attributed, append-only notes; a blob cannot show who said what. Depends on §10 item 3. |
| The screen has no priority display or control, and no warranty confirmation | Priority chip in the header plus **Set priority** and **Confirm warranty** in the overflow | Both endpoints already exist ([API.md §13](../../API.md)); priority drives queue ordering on [AP-06](AP-06-jobs.md) and is meaningless if it can never be set. |
| No unassign action | **Unassign** in the Technician panel | `POST /jobs/{id}/unassign` exists and is the documented way to hand a job back to the pool. |
| Job creation date was missing entirely until a late fix | Created timestamp always in the header | [Bugs.md](../../Bugs.md) #7. |
| Adding a note left the job's last-updated time unchanged | Adding a note bumps and re-renders **Updated** | [Bugs.md](../../Bugs.md) #8; asserted by AC-AP07-13. |
| No link back to the originating request | Originating-request panel with a link (admin) or the reference (staff, technician) | The request holds the customer's own words and photos; tracing back is a normal review step. |
| Errors render the raw exception string | Mapped human message, **Retry**, `correlationId` | Hard rule from [README §6.2](../README.md). |

## 10. Open questions

1. **API.md §8 misdescribes the technician roster — a documentation defect, not a backend gap.** API.md documents a dedicated `GET /technicians` endpoint returning `[{ id, name, username }]` for `STAFF` and `ADMIN`; the backend has never implemented it, and there is no `/technicians` route anywhere in the deployed service. API.md also marks `GET /users` as `ADMIN`-only, while the deployed `UserManagementController` allows both `ROLE_STAFF` and `ROLE_ADMIN`. This spec therefore builds the assign dialog (§5.2) on `GET /users?role=TECHNICIAN`, which the server filters to active users of that role and which staff may call — so nothing here is blocked. **Not blocking**; the correction belongs in API.md, and the same applies to the technician filter on [AP-06](AP-06-jobs.md).
2. **No delete endpoint for job attachments.** [API.md §16](../../API.md) documents `DELETE /requests/{id}/attachments/{attachmentId}` but nothing equivalent for jobs, while the mobile app renders a delete control on job attachments. Either add `DELETE /jobs/{id}/attachments/{attachmentId}` or accept that job evidence is permanent once uploaded. **Blocking for the delete action in element #21**; the control is omitted until it is resolved.
3. **Note attribution and the notes response shape.** [features/jobs.md](../../features/jobs.md) requires attributed, timestamped, append-only notes, and API.md §13 documents `GET /jobs/{id}/notes`, but its response shape is never specified — and `GET /jobs/{id}` returns `internalNotes` as a single concatenated text field, which cannot carry per-note authors or timestamps. Document the notes list shape as an array of `{ author, createdAt, text }`. **Blocking for element #19's per-note attribution**; until then the thread renders as one attributed-by-nobody block, which is a regression against the feature spec.
4. **Warranty field shape.** API.md §13 documents `POST /jobs/{id}/warranty` taking `{ warrantyConfirmedByStaff }` with `YES`/`NO`/`UNKNOWN`, and [Database.md §3](../../Database.md) has a matching three-value column — but the job response returns a boolean `warrantyConfirmed` and no customer-indicated `warrantyStatus` at all. The three-state confirmation and the customer's original claim therefore cannot both be displayed. Confirm the response fields. **Blocking for the second half of element #16.** Separately, API.md restricts this endpoint to `STAFF`/`ADMIN` while the deployed controller also allows `TECHNICIAN`; this spec follows API.md and hides the control from technicians — confirm which is intended.
5. **Appointment details on the job response.** `GET /jobs/{id}` returns only `currentAppointmentId`, so showing the date and time requires a second call to `GET /appointments/{id}`, which API.md §14 restricts to `STAFF`/`ADMIN` — meaning a technician cannot see the appointment on their own job (`GET /me/schedule` is a separate, date-ranged list, not a lookup by id). Options: embed `scheduledDate`, `startTime`, and the appointment status in the job response (preferred, and it removes a round trip for everyone), or widen `GET /appointments/{id}` to the assigned technician. **Blocking for element #24 for technicians.** The same gap means the customer's original preferred date and time are unavailable here, since the job response does not carry them either.
6. **Optimistic concurrency on transitions.** [README §6.3](../README.md) requires every mutation to round-trip the `version` it loaded, but API.md §13 documents `version` only on `assign` and `priority` — `start`, `awaiting-parts`, `resume`, `complete`, `cancel`, and `notes` show no `version` in their bodies. This spec sends what API.md documents and relies on the server's status guards to produce `409` on a stale transition. Confirm whether `version` should be added to the transition bodies for consistency.
7. **Audit history view.** `GET /jobs/{id}/history` is documented and the overflow menu links to it, but no page in this document set specifies how the history renders. Either add a history panel to this page (it belongs here — [features/jobs.md](../../features/jobs.md) §Job Details Screen lists it) or drop the menu item. Non-blocking for V1 only if the item is removed.
8. **Assignable statuses.** The deployed service permits assignment only while a job is `CREATED` or `ASSIGNED`, and this spec follows that, but [features/jobs.md](../../features/jobs.md) §Validation Rules says `Created`/`Assigned`/`Scheduled` are all assignable. A scheduled job whose technician calls in sick therefore cannot be reassigned without rescheduling. Confirm the intended rule.
9. **Notes payload key.** API.md §13 specifies `POST /jobs/{id}/notes` with `{ text }`; the mobile app sends `{ note }`. One of them is wrong. This spec follows API.md; confirm the server's expected key before build.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/jobs/presentation/job_detail_screen.dart`, `frontend/lib/features/jobs/data/jobs_repository.dart`, the `JobResponse` record in `backend/.../job/dto/JobResponse.java`, [API.md §13, §14 and §16](../../API.md), [features/jobs.md](../../features/jobs.md), [Database.md §1](../../Database.md), and [Bugs.md](../../Bugs.md) #7/#8.*
