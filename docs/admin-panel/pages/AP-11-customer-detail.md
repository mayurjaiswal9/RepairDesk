# AP-11 · Customer Detail & History

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-11 |
| **Route** | `/customers/:id` |
| **Roles** | `ADMIN`, `STAFF` — read-only for `STAFF`; account actions are `ADMIN`-only (see §7) |
| **Entry points** | Row click from [AP-10](AP-10-customers.md) · customer link on [AP-05 Request Detail](AP-05-request-detail.md) and [AP-07 Job Detail](AP-07-job-detail.md) · global search result · direct URL |
| **Primary endpoints** | `GET /customers/{id}` · `GET /customers/{id}/history` · `POST /users/{id}/reset-password` (`ADMIN`) · `DELETE /users/{id}` (`ADMIN`) |
| **Archetype** | Detail page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-10 Customers](AP-10-customers.md) · [AP-05 Request Detail](AP-05-request-detail.md) · [AP-07 Job Detail](AP-07-job-detail.md) · [AP-18 Create Request](AP-18-create-request.md) · [features/customer-management.md](../../features/customer-management.md) · [API.md §9](../../API.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

Everything known about one customer on a single screen: the authoritative account profile on the left, and their complete repair history — every request and every job, regardless of status — on the right. This is the page staff open while a customer is on the phone, so its two jobs are to confirm who they are talking to and to get them into the right request or job in one click. Every history row is a live link into [AP-05](AP-05-request-detail.md) or [AP-07](AP-07-job-detail.md); in the mobile app those rows were dead ends, which forced staff back to a different screen to search for a reference number they were already looking at ([Bugs.md](../../Bugs.md) #13). Administrators additionally get the two account-lifecycle actions carried over from [AP-10](AP-10-customers.md).

## 3. Layout anatomy

```
Customers  ›  Anand Kumar                                              breadcrumb
┌────────────────────────────────────────────────────────────────────┐
│ Anand Kumar   [Active]         [ New request ] [ Account actions ▾ ]│  record header
│ @akumar                                                            │
├───────────────────────────────┬────────────────────────────────────┤
│ Repair requests (3)           │  Account profile                   │
│ ┌───────────────────────────┐ │  ┌──────────────────────────────┐  │
│ │ Ref     │Category│Status│ ›│ │  │ Name       Anand Kumar       │  │
│ │ RD-1042 │ Mobile │Pending│›│ │  │ Username   @akumar           │  │
│ │ RD-0997 │ Tablet │Approved│││  │ Mobile     98765 43210       │  │
│ └───────────────────────────┘ │  │ Status     [Active]          │  │
│                               │  │ Registered 4 Mar 2026        │  │
│ Jobs (2)                      │  │ Repairs    3                 │  │
│ ┌───────────────────────────┐ │  └──────────────────────────────┘  │
│ │ RD-0997 │ Tablet │Assigned│›│ │  Account contact — used for all  │
│ │ RD-0881 │ Mobile │Completed│││ signed-in activity. Individual   │
│ └───────────────────────────┘ │  requests may carry their own     │
│                               │  contact details.                 │
└───────────────────────────────┴────────────────────────────────────┘
```

Below 1024px the right panel moves beneath the history sections ([Design-System §7](../Design-System.md)). The **Account actions** menu renders only for `ADMIN`.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Navigation | "Customers › {name}" | "Customers" returns to [AP-10](AP-10-customers.md), preserving that page's search and paging state from the URL. |
| 2 | Record header — name | `<h1>` | `name` from `GET /customers/{id}` | Truncates with an ellipsis and a tooltip at narrow widths; never wraps one character per line ([Bugs.md](../../Bugs.md) #17). Falls back to the username when `name` is absent. |
| 3 | Record header — username | Text, `body-medium`, `text-secondary` | `username`, rendered `@akumar` | Static. Username is never editable in the panel. |
| 4 | Record header — status | Status chip | `status` | Active / Disabled / Deleted, shared mapping ([Design-System §5.2](../Design-System.md)). |
| 5 | Account actions | Menu button | Reset password · Delete customer | **`ADMIN` only** — absent from the DOM for `STAFF`. Same two actions, same endpoints, and same gating as [AP-10](AP-10-customers.md) element #11. |
| 5a | New request | Primary button | — | Visible to `ADMIN` and `STAFF`. Navigates to `/requests/new?customerId={id}` ([AP-18](AP-18-create-request.md)). Disabled with tooltip when status is `DISABLED` or `DELETED`. |
| 6 | Account profile panel | Key–value block ([Design-System §5.4](../Design-System.md)) | `GET /customers/{id}` → `{ username, mobile, status, registeredAt, repairCount }`, plus `name` | Fixed 160px label column; a missing optional value renders an em-dash, never a blank or "null". `registeredAt` uses `d MMM yyyy`; `repairCount` renders as a plain number. |
| 7 | Authoritative-contact note | Helper text, `body-small` | Static copy | Sits directly under the profile panel: "This is the account contact, used for sign-in and for anything not tied to one repair. An individual request may carry different contact details entered at submission time." Makes the distinction in [Database.md §1](../../Database.md) and [features/customer-management.md](../../features/customer-management.md) Business Rule 3 visible rather than assumed. |
| 8 | Requests section header | `<h2>` + count | "Repair requests (3)" | Count comes from the length of the `requests` array in `GET /customers/{id}/history`. |
| 9 | Requests table | Data table, compact | `requests[]` from `GET /customers/{id}/history` | Columns: Reference (`mono`, primary), Device category, Status chip, Date. Ordered most-recently-updated first, as returned by the API — the panel does not re-sort. No client-side status filtering: history is complete by design, including terminal records ([features/customer-management.md](../../features/customer-management.md) Business Rule 4). |
| 10 | Request row click | — | `requests[].id` | Navigates to `/requests/{id}` ([AP-05](AP-05-request-detail.md)). A real anchor, so Ctrl-click opens a new tab. This is the fix for [Bugs.md](../../Bugs.md) #13. |
| 11 | Jobs section header | `<h2>` + count | "Jobs (2)" | Count from the `jobs` array length. |
| 12 | Jobs table | Data table, compact | `jobs[]` from `GET /customers/{id}/history` | Columns: Reference, Device category, Status chip, Technician, Date. The jobs payload additionally carries technician and appointment information ([API.md §9](../../API.md)); "Unassigned" renders when no technician is set. |
| 13 | Job row click | — | `jobs[].id` | Navigates to `/jobs/{id}` ([AP-07](AP-07-job-detail.md)). Also the [Bugs.md](../../Bugs.md) #13 fix. |
| 14 | Reference numbers | Table cell, `mono` | `referenceNumber` | A request and its approved job share one reference ([Database.md §6](../../Database.md)), so the same string legitimately appears in both sections. The section heading is what disambiguates them, which is one reason the two sections stay separate (§9). |
| 15 | Per-section empty states | Inline panels | — | "No requests" and "No jobs" respectively, rendered inside the section rather than replacing the page. |
| 16 | Whole-history empty state | Panel | — | Replaces both sections when the customer has neither requests nor jobs; see §6. |
| 17 | Loading / error / forbidden / not-found | Panels | — | Per [Design-System §6](../Design-System.md); profile and history load independently, so each region has its own state (§6). |

## 5. Interactions & dialogs

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Open request** | Request row click | — | — | Navigates to `/requests/{id}` ([AP-05](AP-05-request-detail.md)). |
| **Open job** | Job row click | — | — | Navigates to `/jobs/{id}` ([AP-07](AP-07-job-detail.md)). |
| **New request** | Header primary | — | — | Navigates to `/requests/new?customerId={id}` ([AP-18](AP-18-create-request.md)). |
| **Back to list** | Breadcrumb "Customers" | — | — | Returns to [AP-10](AP-10-customers.md) with its previous query string intact. |
| **Reset password** | Account actions → Reset password (`ADMIN`) | **New password** * — masked, show/hide toggle, minimum 8 characters, never pre-filled | `POST /users/{id}/reset-password` with `{ newPassword }` | Toast "Password reset for Anand Kumar". Nothing on the page changes; no refetch. |
| **Delete customer** | Account actions → Delete customer (`ADMIN`) | Error-styled confirmation, wording per §5.2 | `DELETE /users/{id}` | Toast "Anand Kumar's account deleted", then navigates back to `/customers`, since the record being viewed no longer belongs in the list. |
| **Retry** | Retry button in an error panel | — | The failed call only (`GET /customers/{id}` or `GET /customers/{id}/history`) | Refetches just that region; a working region is never blanked to retry the other. |

### 5.1 Reset password dialog

Identical in content, validation, and confidentiality rules to [AP-10 §5.1](AP-10-customers.md): title "Reset password — {name}", one masked **New password** field with a show/hide toggle and a minimum of 8 characters (mirroring `POST /users/{id}/reset-password` in [API.md §8](../../API.md)), **Cancel** and **Reset password** actions, and a `422` bound back to the field. The password is write-only — never displayed after submission, never included in the toast, the URL, telemetry, or logs, and never restored if the dialog is reopened ([Design-System §9](../Design-System.md), [features/staff-management.md](../../features/staff-management.md) Business Rule 15).

### 5.2 Delete customer dialog

| Aspect | Content |
| --- | --- |
| Title | "Delete Anand Kumar's account?" |
| Body | "This is a **logical delete**. Their access is revoked immediately and any active session ends, but the 3 requests and 2 jobs shown on this page are kept so history stays intact. Their username and mobile become available for a new account." |
| Actions | **Cancel** · **Delete account** (error-colored, busy while in flight) |
| Endpoint | `DELETE /users/{id}` |
| Result | Toast naming the customer, then redirect to `/customers`. |

Because this page already shows the exact history that survives the delete, the dialog names those counts instead of speaking abstractly. The semantics are `status = DELETED` on `users` with requests and jobs never deleted at all ([Database.md §4](../../Database.md)). There is no undo — the API offers no un-delete, and `POST /users/{id}/activate` only restores a `DISABLED` account.

## 6. Page states

Profile and history are two independent requests, so they have independent states. A history failure must never blank the profile, and vice versa.

| State | Presentation |
| --- | --- |
| **Loading (profile)** | Skeleton key–value rows in the right panel; the breadcrumb and page frame render immediately. |
| **Loading (history)** | Two skeleton section headings with 3 skeleton rows each. |
| **Empty (whole history)** | Both sections are replaced by one panel: history icon, "No repairs yet", "This customer hasn't submitted a repair request." No action — the panel cannot create a request on a customer's behalf. |
| **Empty (per section)** | When one side is empty but the other is not, the empty side shows inline text — "No requests" or "No jobs" — and keeps its heading and count, so the structure of the page stays stable. |
| **Error (profile)** | Error panel in place of the profile card, with **Retry** and the `correlationId`. |
| **Error (history)** | Error panel in place of both history sections, with **Retry** and the `correlationId`. `GET /customers/{id}/history` is the endpoint that 500'd in production for every customer with at least one repair, because the controller resolved lazy associations outside a transaction ([Bugs.md](../../Bugs.md) #6). The server-side fix is a transaction-scoped read and is not a UI requirement, but this page is the one that shows the failure, so it must render the mapped message plus **Retry** and `correlationId` — never a raw exception string, which is precisely what the mobile app displayed. |
| **Not found** | A `404` on `GET /customers/{id}` renders "Customer not found", explaining the account may have been deleted, with **Back to customers**. |
| **Forbidden** | `TECHNICIAN` and any role the server refuses: "Customer lookup is limited to staff and administrators", with a link to `/dashboard`. |
| **Deleted account** | A customer whose `status` is `DELETED` still renders, with the Deleted chip in the header and a banner: "This account has been deleted. Its repair history is retained." Account actions are hidden. |

## 7. Permissions & visibility

- **Route guard:** `ADMIN` and `STAFF`, matching [AP-10](AP-10-customers.md) and the server's rule for `/customers/**` ([API.md §9](../../API.md) — "`403` for Technician/Customer").
- **`STAFF` gets a read-only page**: profile, history, and navigation into requests and jobs. The Account actions menu (element #5) is not rendered.
- **`ADMIN` additionally gets Reset password and Delete**, via `POST /users/{id}/reset-password` and `DELETE /users/{id}`. Gating is identical to [AP-10 §7](AP-10-customers.md) so the same person has the same powers in both places; a menu that appears on the list but not the detail page would be worse than either choice.
- **Nothing on this page is editable.** No profile field can be changed here for either role — `PATCH /users/{id}` is reachable only from [AP-12](AP-12-staff.md), which covers internal users.
- **Downstream permissions still apply.** Opening a request or job navigates to a page with its own guard; a `STAFF` user following a request link lands on [AP-05](AP-05-request-detail.md), which is currently `ADMIN`-only (see the unresolved conflict in [README §3](../README.md)) and will render its own forbidden state. This page does not pre-filter rows by what the user could open next — hiding a customer's repair from staff who can see the customer would be a worse lie than a forbidden page.
- **The read-only doc tension applies here too** and is recorded once, in [AP-10 §10](AP-10-customers.md) open question 4 and repeated in §10 below.

## 8. Acceptance criteria

**AC-AP11-01** — Given I open `/customers/{id}` for an existing customer,
When the page loads,
Then the profile panel shows the name, username, mobile, status, registered date, and repair count from `GET /customers/{id}`, with an em-dash for any absent optional value.

**AC-AP11-02** — Given the page is loading,
When I wait for both responses,
Then the profile panel and the history sections each show skeletons shaped like their final content, and the breadcrumb and header frame are already rendered.

**AC-AP11-03** — Given the customer has repair requests,
When I click a request row,
Then I navigate to `/requests/{id}` for that request, and Ctrl-clicking opens it in a new tab.

**AC-AP11-04** — Given the customer has jobs,
When I click a job row,
Then I navigate to `/jobs/{id}` for that job, and Ctrl-clicking opens it in a new tab.

**AC-AP11-05** — Given a customer with both requests and jobs,
When the history renders,
Then each section lists its records most-recently-updated first, with reference, device category, status, and date on every row, and no record is hidden because of a terminal status.

**AC-AP11-06** — Given a customer with requests but no jobs,
When the history renders,
Then the Requests section lists their requests and the Jobs section shows "No jobs" inline while keeping its heading — the page does not show a whole-page empty state.

**AC-AP11-07** — Given a customer with no requests and no jobs,
When the history renders,
Then a single "No repairs yet" panel replaces both sections.

**AC-AP11-08** — Given a customer who entered a different contact name and number on one of their requests,
When I read the profile panel,
Then the account's own mobile is shown, and the accompanying note states that individual requests may carry their own contact details.

**AC-AP11-09** — Given I am signed in as `STAFF`,
When the page renders,
Then no Account actions menu is present and no reset-password or delete control exists anywhere on the page.

**AC-AP11-10** — Given I am signed in as `ADMIN`,
When I open the Account actions menu,
Then **Reset password** and **Delete customer** are offered, matching the actions available on `/customers`.

**AC-AP11-11** — Given I am an admin resetting a customer's password,
When I submit a password shorter than 8 characters,
Then submission is blocked with a field-level message and no request is sent; and on a successful reset the password appears in no toast, URL, log, or telemetry event.

**AC-AP11-12** — Given I am an admin who confirms deletion,
When the request succeeds,
Then a toast names the customer and I am returned to `/customers`.

**AC-AP11-13** — Given another admin deleted this customer after my page loaded,
When I attempt to reset the password or delete the account,
Then I see an explanation that the account is no longer available, my action is not reported as successful, and I am offered a way back to `/customers`.

**AC-AP11-14** — Given `GET /customers/{id}/history` returns a 500,
When the error renders,
Then the history region shows a human-readable message, a **Retry** that refetches only the history, and the `correlationId` — while the profile panel stays visible, and no raw exception text or JSON is shown anywhere.

**AC-AP11-15** — Given I navigate to `/customers/{id}` for an id that does not exist,
When the response is a 404,
Then I see a "Customer not found" panel with a link back to `/customers`, not a generic error or a blank page.

**AC-AP11-16** — Given a customer whose name is long enough to overflow the header,
When the page renders at any supported viewport width,
Then the name truncates with an ellipsis and a tooltip carrying the full value — it never wraps one character per line.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| History rows are non-interactive `ListTile`s | Every request and job row navigates to its detail page | The single most-reported friction on this screen ([Bugs.md](../../Bugs.md) #13); the ids were already in the payload and simply unused. |
| Profile shows username, mobile, and status only | Adds name, registered date, and repair count | `GET /customers/{id}` documents `registeredAt` and `repairCount` ([API.md §9](../../API.md)) and [features/customer-management.md](../../features/customer-management.md) AC-2 requires the registered date; the mobile screen just never rendered them. |
| Profile is read from the `customer` object inside the history response | Profile loads from `GET /customers/{id}`, history from `GET /customers/{id}/history` | Two independent calls mean a history failure — the exact failure in [Bugs.md](../../Bugs.md) #6 — no longer takes the profile down with it. |
| No statement about which contact is authoritative | Explicit note under the profile panel | Requests carry their own editable contact snapshot ([Database.md §1](../../Database.md)); without the note, staff cannot tell which number to trust. |
| Single-column stack, profile card above the lists | Two-column detail layout, history left, profile right | History is the reason the page is open; the profile is reference material. Collapses to one column below 1024px. |
| Errors render `AppException.toString()` in the body | Standard error panel with **Retry** and `correlationId` | [README §6.2](../README.md) forbids raw exception text; this page is where that rule was broken twice. |
| Account actions are only on the list screen | Reset password and Delete also available here, `ADMIN`-only | An admin who has drilled into a customer should not have to go back to the list to act on them. |

## 10. Open questions

1. **Do the history rows carry an `id`?** [API.md §9](../../API.md) describes each history item as having reference, device category, status, date, and (for jobs) technician and appointment — it does not list `id`. [Bugs.md](../../Bugs.md) #13 confirms both `RepairRequestSummary` and `JobSummary` do include one, which is what makes row navigation possible. API.md should document it. **Blocking for elements #10 and #13 if it is ever removed.**
2. **Is `name` returned by `GET /customers/{id}`?** The documented shape is `{ id, username, mobile, status, registeredAt, repairCount }`, with no `name`, yet the header and profile panel both need it and `q` is documented as searching by name. Same question as [AP-10 §10](AP-10-customers.md) open question 2. **Blocking for elements #2 and #6.**
3. **Which date does each history row show?** [API.md §9](../../API.md) says the list is ordered most-recently-updated first and that each item carries "date", without naming the field. The mobile client reads `updatedAt`. Confirm whether the intended display is `updatedAt` (consistent with the ordering) or the request's `submittedAt` / the job's `createdAt`, and label the column accordingly — showing an unlabeled date of ambiguous meaning next to a "most recent first" ordering is how off-by-one confusion starts.
4. **Is the history paginated?** `GET /customers/{id}/history` documents no `page`/`size`, and the response is two plain arrays. That is fine for a typical customer and unbounded for a heavy one. Confirm the expected maximum, or add pagination; the page currently renders every row returned.
5. **Read-only Customer Management versus admin account actions.** Unresolved, and shared with [AP-10 §10](AP-10-customers.md) open question 4: [features/customer-management.md](../../features/customer-management.md) and [API.md §9](../../API.md) both call the feature strictly read-only for Staff *and* Admin, while [Bugs.md](../../Bugs.md) #12 deliberately added reset-password and delete for admins. This spec follows shipped behavior; product must confirm or reverse it for both pages together.
6. **Appointment context.** [API.md §9](../../API.md) says job history items include appointment information, and [features/customer-management.md](../../features/customer-management.md) §Screen Definitions expects the scheduled date/time to be visible. The Jobs table currently shows technician but not appointment date, to keep the row compact. Confirm whether the appointment date belongs in the table, in the row's tooltip, or nowhere.
7. **Transaction-scoped history read.** `GET /customers/{id}/history` must resolve its lazy associations inside the read transaction; without that it 500s for any customer with at least one repair ([Bugs.md](../../Bugs.md) #6). This is a server concern, not a UI requirement, but the panel is the visible consumer, so it should be covered by an API smoke test against a customer *with* history before this page is signed off.
8. **Staff access to request detail.** If [AP-05](AP-05-request-detail.md) stays `ADMIN`-only (the conflict in [README §3](../README.md)), `STAFF` will click a request row here and land on a forbidden page. Options: leave it (honest but jarring), or mark such rows with an explanatory tooltip. Deferred until the Staff-access decision is made, since it disappears entirely if Staff gain access.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/customer_management/presentation/customers_screen.dart` (`CustomerHistoryScreen`), `frontend/lib/features/customer_management/data/customer_management_repository.dart`, [API.md §8–9](../../API.md), [Database.md §1, §4, §6](../../Database.md), [features/customer-management.md](../../features/customer-management.md), and [Bugs.md](../../Bugs.md) #6, #12, #13, #17.*
