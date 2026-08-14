# AP-05 · Request Detail

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-05 |
| **Route** | `/requests/:id` |
| **Roles** | `ADMIN` only — consistent with [AP-04](AP-04-repair-requests.md); see §7 and the conflict note in [README §3](../README.md) |
| **Entry points** | [AP-04](AP-04-repair-requests.md) row click or **Open detail** · [AP-11 Customer Detail](AP-11-customer-detail.md) history row · Notification deep link · Direct URL |
| **Primary endpoints** | `GET /requests/{id}` · `POST /requests/{id}/approve` · `POST /requests/{id}/reject` · `POST /requests/{id}/request-info` · `GET /requests/{id}/attachments/{attachmentId}` |
| **Archetype** | Detail page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-04 Repair Requests](AP-04-repair-requests.md) · [AP-07 Job Detail](AP-07-job-detail.md) · [features/request-review.md](../../features/request-review.md) · [API.md §12, §16](../../API.md) |

## 2. Purpose

The full read view of one customer repair request, and the place where the decision is actually made. An admin arrives here from the queue when a request needs more than a glance: to read the issue description in full, inspect the customer's photos, check the device's warranty and serial, and — when information was requested — read the customer's reply next to the question that prompted it. The page carries the same three decisions as the queue (approve, reject, request more information) but with all the evidence on screen, and once a request is approved it becomes the hand-off point to the job that approval created.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Requests › RQ-1042                                                         │  breadcrumb
├────────────────────────────────────────────────────────────────────────────┤
│ RQ-1042                                        [ Pending review ]  [High]  │  record header
│ Submitted 4 Aug 2026, 9:14 am                                              │
│                     [ Request info ]  [ Reject ]  [ Approve & create job ] │  action bar
├──────────────────────────────────────────┬─────────────────────────────────┤
│ Information exchange                     │ Job                             │  right panel
│  ? Requested from customer · 4 Aug       │ In progress · Priya S.          │
│    "Please send a photo of the screen."  │ [ Open job → ]                  │
│  ↩ Provided by customer · 5 Aug          │                                 │
│    "Photo attached, cracked top-right."  │ Customer & contact              │
│                                          │ Contact   A. Kumar              │
│ Device                                   │ Username  @akumar               │
│  Category   Mobile Phone                 │ Mobile    +91 98765 43210       │
│  Model      Samsung Galaxy S21           │ Email     —                     │
│  Serial     •••• •••• 3456   [ Reveal ]  │                                 │
│  Purchased  12 Jan 2025                  │ Preferred appointment           │
│  Warranty   In warranty                  │ 8 Aug 2026 · 11:00 am           │
│                                          │ Customer preference — not a     │
│ Issue description                        │ confirmed booking.              │
│  Screen cracked after a fall; touch …    │                                 │
│                                          │                                 │
│ Customer photos · 3                      │                                 │
│  [ img ] [ img ] [ img ]                 │                                 │
└──────────────────────────────────────────┴─────────────────────────────────┘
```

Below 1024px the right panel moves under the main column in the order Job → Customer & contact → Preferred appointment, and the action bar becomes a sticky footer so a decision is always reachable without scrolling back up.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Links | "Requests › {referenceNumber}" | "Requests" returns to [AP-04](AP-04-repair-requests.md) preserving the queue's filter/sort/page state from the URL it was opened from. |
| 2 | Reference number | `<h1>`, `mono` | `referenceNumber` | The page's accessible name and document title. This same reference is reused as the job number ([Database.md §1](../../Database.md)), so it also identifies the job on [AP-07](AP-07-job-detail.md). |
| 3 | Status chip | Status chip | `status` | Shared request mapping ([Design-System §5.2](../Design-System.md)): Pending review / Needs info / Approved / Rejected / Cancelled. |
| 4 | Priority chip | Status chip | `priority` | Shown only for `HIGH` and `URGENT`; `NORMAL` and `LOW` render nothing and no placeholder. |
| 5 | Submitted timestamp | Text, `body-small` | `submittedAt` | Absolute (`d MMM yyyy, h:mm a`) in the browser's local timezone. Renders "—" if absent. |
| 6 | Action bar | Button group | — | **Request info** · **Reject** · **Approve & create job** (primary, last). Present only when `status` is `PENDING_REVIEW` or `INFORMATION_REQUIRED`; otherwise the whole bar is absent and replaced by the resolved-state line (#7). Buttons disable and show a busy spinner while their request is in flight. |
| 7 | Resolved-state line | Text, `body-small` | `status` | Replaces the action bar for `APPROVED` / `REJECTED` / `CANCELLED`: "This request was approved — no further decisions are available." Prevents an empty bar reading as a broken page. |
| 8 | Information exchange block | Card, two stacked entries | `infoRequestedMessage` + `customerInfoResponse` | **One block, both sides.** The staff question renders first (secondary-tinted, "Requested from customer"), the customer's reply directly under it (success-tinted, "Provided by customer"). When `infoRequestedMessage` is present and `customerInfoResponse` is null, the reply slot renders "Awaiting the customer's reply" rather than disappearing. The block is absent only when `infoRequestedMessage` is null. Splitting these two into separate, independently-conditional cards is what produced [Bugs.md](../../Bugs.md) #2/#3 — they are specified as a single component so a missing reply is visible as a gap, not as silence. |
| 9 | Rejection message card | Card, error-tinted | `customerFacingMessage` | "Rejection message sent to customer". Rendered when the field is present (i.e. `REJECTED`). The internal reason is never rendered on this card; see §7. |
| 10 | Device section | Key–value block | — | Fixed 160px label column ([Design-System §5.4](../Design-System.md)); optional values render an em-dash. |
| 11 | Device › Category | Key–value row | `deviceCategoryName` | Always present. |
| 12 | Device › Model | Key–value row | `deviceBrand` + `deviceModel` | Joined with a space; "—" when both absent. |
| 13 | Device › Serial (IMEI) | Key–value row + reveal action | `deviceSerial` | **Masked by default**: all but the last four characters replaced with bullets. A **Reveal** text button shows the full value for the current view only; it re-masks on navigation, on reload, and when the tab loses focus. The value is never written to console, telemetry, analytics, or any client log, and is excluded from copy-all/print output — `device_serial` is classified sensitive in [Database.md §1](../../Database.md). The row and the reveal control are absent entirely when the field is null. |
| 14 | Device › Purchased | Key–value row | `purchaseDate` | `d MMM yyyy`. |
| 15 | Device › Warranty | Status chip | `warrantyStatus` | In warranty / Out of warranty / Unknown, per the shared mapping. Labelled as customer-indicated, since staff confirmation happens on the job ([AP-07](AP-07-job-detail.md)). |
| 16 | Issue description | Prose block | `issueDescription` | Full text, wrapped at ~90 characters, whitespace and line breaks preserved. Never truncated on this page — this is the primary evidence for the decision. |
| 17 | Customer photos | Attachment gallery, read-only | Request attachments for this request | Thumbnail grid with a count in the heading; bytes fetched through the authenticated HTTP layer and rendered from object URLs ([README §6.6](../README.md)). Clicking a tile opens the full-screen viewer with zoom, keyboard navigation, and `Esc` to close. **No upload and no delete controls** — staff do not alter customer evidence; corrections go through Request info ([features/request-review.md](../../features/request-review.md) §Attachment Review). Zero attachments renders "No photos were submitted with this request." A per-tile load failure shows a retry affordance on that tile only and never blocks a decision. |
| 18 | Job panel | Right-panel card | `jobStatus`, `assignedTechnicianName`, `jobId` | Rendered only when `status` is `APPROVED`. Shows the created job's status chip (job mapping) and assigned technician, or "Unassigned". **Open job** links to `/jobs/{jobId}` ([AP-07](AP-07-job-detail.md)). If `jobStatus` is present but `jobId` is not, the card degrades to status text without a link rather than to a dead link — see §10. |
| 19 | Customer & contact panel | Right-panel key–value card | `contactName`, `customerUsername`, `contactMobile`, `contactEmail` | Contact fields are request-local snapshots, not the account's authoritative contact ([Database.md §1](../../Database.md)); the panel says so in one line of helper text. `@username` links to the customer's record on [AP-11](AP-11-customer-detail.md). Missing email renders "—". |
| 20 | Preferred appointment panel | Right-panel card | `preferredDate`, `preferredTime` | `d MMM yyyy` · `h:mm a`, with the standing caption "Customer preference — not a confirmed booking." Absent when no preferred date exists. |
| 21 | Refresh | Icon button in the header | — | Refetches `GET /requests/{id}`; content stays visible with a progress indicator during the refetch. |
| 22 | Loading / error / forbidden / not-found | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

The three decision dialogs are **identical to [AP-04 §5](AP-04-repair-requests.md)** — same fields, same validation, same endpoints, same copy — so a decision feels the same whether it is taken from the queue or from the record. They are listed here by field only; AP-04 remains the authority on their wording.

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Approve** | Action bar → Approve & create job | Confirmation naming the reference and stating a job will be created and the customer notified. Optional **Priority** select (`LOW`/`NORMAL`/`HIGH`/`URGENT`, default `NORMAL`); optional internal **Note**. Confirm: "Approve & create job". | `POST /requests/{id}/approve` with `{ priority?, note?, version }` | Toast "RQ-1042 approved · job created". The page refetches in place (it does not navigate away): the status chip becomes Approved, the action bar is replaced by the resolved-state line, and the Job panel appears with a link to the new job. |
| **Reject** | Action bar → Reject | Error-styled confirm. **Reason code** (required select), **Message to customer** (required, multi-line — the customer reads this verbatim), optional **Internal reason**. | `POST /requests/{id}/reject` with `{ reasonCode, internalReason?, customerMessage, version }` | Toast "RQ-1042 rejected". Page refetches; the rejection message card (#9) appears with the customer-facing text. |
| **Request more info** | Action bar → Request info | **Message to customer** (required, multi-line) describing exactly what is needed. | `POST /requests/{id}/request-info` with `{ message, version }` | Toast "Information requested from A. Kumar". Page refetches; status becomes Needs info and the information-exchange block appears with the reply slot showing "Awaiting the customer's reply". |
| **Reveal serial** | **Reveal** next to the masked serial | — | — (no request; the value is already in the loaded record) | Unmasks the value in place for the current view; the control toggles to **Hide**. Re-masks automatically on navigation, reload, or tab blur. |
| **Open photo** | Click a thumbnail | — | `GET /requests/{id}/attachments/{attachmentId}` | Full-screen viewer; arrow keys move between photos, `Esc` closes and returns focus to the originating tile. |
| **Open job** | Job panel → Open job | — | — | Navigates to `/jobs/{jobId}` ([AP-07](AP-07-job-detail.md)). |
| **View customer** | `@username` link | — | — | Navigates to `/customers/{customerId}` ([AP-11](AP-11-customer-detail.md)). |
| **Back to requests** | Breadcrumb | — | — | Returns to [AP-04](AP-04-repair-requests.md) with its previous filter/sort/page restored from the URL. |

All three decisions are irreversible transitions: each confirms explicitly, disables its confirm button while in flight, and maps `409 CONFLICT` to "This request was already actioned by someone else" with a **Reload** action that refetches the record. `422` field errors bind to the offending dialog control and leave the dialog open with input preserved ([README §6.2](../README.md)).

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Breadcrumb renders immediately; the record header, both columns, and the photo grid render as skeletons matching the final layout, so nothing jumps when data lands. |
| **Loading (refetch)** | Existing content stays visible with a top progress bar; the action bar's buttons are disabled for the duration. |
| **Decision in flight** | Only the invoked button shows a spinner; the other two are disabled so a second decision cannot be started against the same record. |
| **Empty regions** | Individual sections state their own emptiness ("No photos were submitted with this request", "—" for absent key–value entries). The page itself is never empty — a request always has a header, device, and issue. |
| **Error** | Full-page error panel with the mapped message, **Retry**, and the `correlationId`. A failure loading a *single* photo is not a page error: it degrades that tile only. |
| **Forbidden** | Shown when a `STAFF` or `TECHNICIAN` user reaches the URL: "Repair request review is limited to administrators", with a link to `/dashboard`. No request data is rendered. |
| **Not found** | `404` renders "This request no longer exists or was never visible to you" with a link back to `/requests`. |
| **Stale decision** | On `409`, the page refetches automatically and shows an inline "Updated elsewhere" marker beside the status chip for 5 seconds, so the admin can see what changed before retrying. |

## 7. Permissions & visibility

- **Route guard**: `ADMIN` only, matching [AP-04](AP-04-repair-requests.md) and current shipped behavior. `STAFF` and `TECHNICIAN` get the forbidden state on direct navigation; the server is authoritative and a `403` from `GET /requests/{id}` renders the same state regardless of what the client believed.
- **Decision actions are gated by record state, not role**: the action bar exists only for `PENDING_REVIEW` and `INFORMATION_REQUIRED`. For every other status the transitions are absent from the DOM entirely, not merely disabled.
- **Serial / IMEI** is internal-only and least-exposure: masked by default, revealed deliberately, never logged ([Database.md §1](../../Database.md), [features/request-review.md](../../features/request-review.md) §Security Requirements).
- **Internal reason** captured on rejection is never rendered on this page or anywhere a customer could reach; only `customerFacingMessage` is displayed.
- See the unresolved Staff-access question in [README §3](../README.md). If resolved in Staff's favor, only the route guard and nav visibility change — no page content changes.

## 8. Acceptance criteria

**AC-AP05-01** — Given I am an admin opening `/requests/{id}` for a request that exists,
When the page finishes loading,
Then the header shows the reference number, the current status chip, and the submitted timestamp, and the breadcrumb returns me to the queue with the filters I left it on.

**AC-AP05-02** — Given a request where staff asked for more information and the customer has replied,
When the page renders,
Then the staff question and the customer's reply appear together in one information-exchange block, in that order, each labelled with who wrote it — the reply is never shown without the question and never omitted when present.

**AC-AP05-03** — Given a request in `INFORMATION_REQUIRED` where the customer has not yet replied,
When the page renders,
Then the information-exchange block shows the staff question and an explicit "Awaiting the customer's reply" slot rather than hiding the reply area.

**AC-AP05-04** — Given a request whose device serial is recorded,
When the Device section renders,
Then only the last four characters are visible, a **Reveal** control is offered, revealing shows the full value in place, and navigating away or reloading returns it to the masked form.

**AC-AP05-05** — Given a request with no device serial,
When the Device section renders,
Then no serial row and no reveal control are shown, and no placeholder implies a hidden value exists.

**AC-AP05-06** — Given a `PENDING_REVIEW` request,
When I approve it and confirm,
Then a success toast names the reference, the page updates in place to `APPROVED`, the action bar is replaced by an explanatory line, and a Job panel appears linking to the job that approval created.

**AC-AP05-07** — Given a request whose status is `APPROVED`, `REJECTED`, or `CANCELLED`,
When the page renders,
Then no approve, reject, or request-info control exists anywhere on the page, and a line explains why no decisions are available.

**AC-AP05-08** — Given I open the Reject dialog,
When I submit without a reason code or without a customer message,
Then submission is blocked with field-level messages, the dialog stays open with my input, and no request is sent.

**AC-AP05-09** — Given another admin decided this request after I opened it,
When I confirm a decision,
Then I see "This request was already actioned by someone else" with a **Reload** action, the page refetches to the true current status, and my decision is not applied.

**AC-AP05-10** — Given a request that was rejected,
When the page renders,
Then the message sent to the customer is displayed, and the internal reason recorded at rejection is not displayed anywhere on the page.

**AC-AP05-11** — Given a request with customer photos,
When the gallery renders,
Then each image is fetched with the authorization header and rendered from an object URL (never a bare `<img src>`), and no upload or delete control is offered for those photos.

**AC-AP05-12** — Given one of the customer's photos fails to load,
When the gallery renders,
Then that tile shows a retry affordance, every other section stays usable, and the approve, reject, and request-info actions remain available.

**AC-AP05-13** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/requests/{id}`,
Then I see the forbidden panel explaining the restriction with a link to the dashboard, and no request data is displayed.

**AC-AP05-14** — Given a request id that does not exist,
When the page loads,
Then I see the not-found panel with a link back to `/requests`, not a raw error or an empty shell.

**AC-AP05-15** — Given I open any decision dialog with the keyboard,
When the dialog is open,
Then focus is trapped inside it, `Esc` cancels without submitting, and on close focus returns to the button that opened it.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Single scrolling column; the action row sits at the very bottom, below the photos | Two columns with the action bar in the record header (sticky footer below 1024px) | The decision is the point of the page; it should not require scrolling past every section to reach. |
| The "information requested" and "information provided" alerts are two independently rendered cards | One information-exchange block that always renders both slots | The reply rendering only when non-null is exactly the shape that let [Bugs.md](../../Bugs.md) #2/#3 ship invisibly. One component makes a missing reply visible. |
| Serial/IMEI displayed in full, in plain text | Masked to the last four characters with an explicit reveal | `device_serial` is classified sensitive in [Database.md §1](../../Database.md); full-time display is unnecessary exposure on a page that is usually open for reading, not for serial checking. |
| Approve dialog is a bare confirm; reject sends a hardcoded `reasonCode` of `UNABLE_TO_SERVICE` with an optional message | Dialogs identical to [AP-04](AP-04-repair-requests.md): approve takes optional priority and internal note; reject requires a reason code and a customer message | The endpoints already accept these fields ([API.md §12.2](../../API.md)); hardcoding the reason discards information the customer and the audit trail need. |
| After approving or rejecting, the screen pops back to the queue | Stays on the record and refetches in place | The admin usually wants to confirm the outcome — and, after approval, to follow the link straight into the new job. |
| No link from an approved request to its job (job status is shown as text only) | Job panel with a link to `/jobs/{jobId}` | The approved request and its job are one continuous piece of work sharing one reference number. |
| Errors render the raw exception string | Mapped human message with **Retry** and `correlationId` | Hard rule from [README §6.2](../README.md), after the regression in [Bugs.md](../../Bugs.md) #6. |
| Warranty, purchase date, and contact fields sit inline in one long list | Device facts left, people and scheduling facts right | Reviewing is a comparison task: device evidence beside the issue text, context beside it. |

## 10. Open questions

1. **`jobId` on the request response.** The Job panel's link needs the created job's id. `GET /requests/{id}` does return `jobId` (verified in `backend/.../request/dto/RepairRequestResponse.java`), but [API.md §12.1](../../API.md) describes the endpoint only as "Request detail" and never enumerates its response fields, so the contract does not currently guarantee it. Document the response shape in API.md. **Blocking for element #18's link** (the panel degrades to unlinked status text without it).
2. **Attachment listing endpoint.** [API.md §16](../../API.md) documents upload, delete, and single-file download for request attachments but **no list endpoint**, while the mobile app and the fix for [Bugs.md](../../Bugs.md) #8 both rely on `GET /requests/{id}/attachments`. The gallery cannot be built without a documented way to enumerate attachments. **Blocking for element #17.**
3. **Attachment download path.** [README §6.6](../README.md) refers to `GET /attachments/{storageKey}` while [API.md §16](../../API.md) documents `GET /requests/{id}/attachments/{attachmentId}`. One of the two is wrong; this spec cites API.md. Reconcile before build.
4. **Request info from `INFORMATION_REQUIRED`.** [AP-04](AP-04-repair-requests.md) offers Request info for both `PENDING_REVIEW` and `INFORMATION_REQUIRED`, and this page follows it, but [features/request-review.md](../../features/request-review.md) §Review Actions permits the action only from `Pending Review`. If the server rejects a repeat info request with `409`, the control should be hidden in `INFORMATION_REQUIRED` on both pages. Confirm the server rule and align both specs.
5. **Reason codes.** The reject dialog needs the canonical `reasonCode` list. [Tasks.md](../../Tasks.md) decision 11 gives `DUPLICATE`, `UNSUPPORTED_DEVICE`, `INSUFFICIENT_INFORMATION`, `SPAM_INVALID`, `NON_REPAIRABLE`, which contradicts the `UNABLE_TO_SERVICE` value the mobile app sends. Confirm the backend enum and record it in API.md; tracked as the same question in [AP-04 §10](AP-04-repair-requests.md).
6. **Reveal auditing.** Revealing a serial is a sensitive-data access that [features/request-review.md](../../features/request-review.md) §Security suggests may need to be auditable, but there is no endpoint to record it and adding one would be a backend change. V1 treats reveal as a client-only action; confirm that is acceptable to the security review.
7. **Internal notes, priority, and decision history on this page.** The API already offers `POST /requests/{id}/notes`, `POST /requests/{id}/priority`, and `GET /requests/{id}/history` ([API.md §12.2](../../API.md)), none of which the mobile app surfaces and none of which are specified here. A decision history panel in particular would suit this page. Deferred pending a product decision rather than half-specified.
8. **Cancelled requests.** A customer can cancel while pre-review. Nothing on this page explains *when* or *why* a request was cancelled, because no cancellation reason is captured. Confirm whether the resolved-state line should say more than "This request was cancelled by the customer."

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/requests/presentation/request_detail_screen.dart`, `frontend/lib/features/requests/data/requests_repository.dart`, [API.md §12 and §16](../../API.md), [Database.md §1](../../Database.md), [Bugs.md](../../Bugs.md) #2/#3, and [features/request-review.md](../../features/request-review.md).*
