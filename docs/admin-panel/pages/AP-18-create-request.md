# AP-18 · Create Request (Staff-Assisted Intake)

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-18 |
| **Route** | `/requests/new` (optional `?customerId=`) — **must be registered before** `/requests/:id` |
| **Roles** | `ADMIN`, `STAFF`. `TECHNICIAN` forbidden. `CUSTOMER` cannot sign in to the panel. |
| **Entry points** | [AP-04](AP-04-repair-requests.md) → **Create request** · [AP-11](AP-11-customer-detail.md) → **New request** · Dashboard quick link (admin) |
| **Primary endpoints** | `GET /customers?q=` · `GET /customers/{id}` · `POST /customers` · `GET /device-categories` · `GET /settings/scheduling-config` · `POST /requests` (assisted body) |
| **Archetype** | Progressive intake ([mockup](../mockups/AP-18-create-request.html)) — three numbered cards; contextual right rail **only after** a customer is selected; sticky footer |
| **Related** | [features/staff-assisted-intake.md](../../features/staff-assisted-intake.md) · [AP-04](AP-04-repair-requests.md) · [AP-05](AP-05-request-detail.md) · [AP-07](AP-07-job-detail.md) · [AP-11](AP-11-customer-detail.md) · [API.md](../../API.md) §9, §12.3 |

> **Backend required.** Shipped `POST /requests` always owns the request as the **caller**. Assisted intake needs `customerId` (and optional `approveOnCreate`). Walk-in accounts need `POST /customers` because `POST /users` is admin-only and meant for internal roles. Do not implement the page against today's create-as-self behaviour.

## 2. Purpose

The counter and phone intake desk. Staff identify (or register) the customer, capture request-local contact, describe the issue, optionally attach files and associate an asset, then **create the request**. Scheduling and technician assignment are the next stage — they are not on this screen.

One principle: **create the request first; schedule and dispatch later.** The form stays a single column until a customer is chosen. The right rail is contextual (snapshot, assets, history), not a second form, and it appears only after an existing customer is selected or a new one is created.

## 3. Layout anatomy

Visual source: Tezvo Flow wireframe 2026-09-02 (progressive rail). Mockup chrome uses Tezvo Flow naming; the route stays `/requests/new`. Category and preferred slot are **not on this screen** — silent defaults at submit (§4 #25).

```
┌────────────┬─────────────────────────────────────┬──────────────────┐
│ RepairDesk │ Work / Repair Requests / Create req │            🔔 3  │
│            ├─────────────────────────────────────┴──────────────────┤
│ Dashboard  │ Create request                                         │
│ Requests ● │ For a walk-in or phone customer. The request is        │
│ Jobs       │ owned by them, not by you.                             │
│ Appoint…   │ ┌ 1 Customer ─────────────────┐  ┌ About repair req ┐ │
│            │ │ Search name, username, or   │  │ Owned by the     │ │
│ Help       │ │ mobile                      │  │ customer         │ │
│ Aman Admin │ │ + New customer              │  │ Walk-in or phone │ │
│            │ └─────────────────────────────┘  │ Next steps       │ │
│            │ ┌ 2 Contact (this request only)┐  └──────────────────┘ │
│            │ │ Name *     Mobile * (+91)   │                       │
│            │ │ Email (optional)            │                       │
│            │ └─────────────────────────────┘                       │
│            │ ┌ 3 Request details ──────────┐                       │
│            │ │ Issue / Problem *           │                       │
│            │ └─────────────────────────────┘                       │
│            │          [Cancel] [Save for review] [Create and approve →] │
└────────────┴───────────────────────────────────────────────────────┘
```

When `?customerId=` is present, section 1 shows the Selected card and the rail loads with that customer.

**Initial load** is a single column: customer search, contact, request details (issue, channel, attachments). **No informational rail.** After a customer is selected, a compact right rail shows snapshot, equipment, previous requests, and a next-step note — not priority, date, or assign-technician.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Link trail | Work / Service Requests / **Create request** | First crumb returns to [AP-04](AP-04-repair-requests.md). Dirty-form prompt. STAFF without queue access: **Customers** or **Dashboard** (§7). |
| 2 | Page title | `<h1>` + icon | "Create request" | |
| 3 | Purpose line | Lede | Static | "Create a request for a walk-in or phone customer. The request is owned by them, not by you." |
| 4 | Section 1 — Customer | Numbered card | Step **1** | Heading "Customer". Hint: "Search or select the customer this request belongs to." |
| 5 | Customer search | Rounded field | `GET /customers?q=` | Required. Debounce 350ms; min 2 characters. Placeholder "Search name, username, or mobile number". Rows: name, `@username`, mobile, previous-request count. |
| 6 | No results | Inline empty | After a completed search | "No customer matches “{q}”. Create one and keep going." Primary **+ Create new customer** opens §5.1. |
| 7 | New customer | Text button | "+ New customer" | Always available until a customer is selected. Same dialog as #6. |
| 8 | Selected customer card | Card in section 1 | `name`, `@username`, mobile, Active | Replaces search. **Change** clears selection, `customerId`, and **hides the rail**. Disabled/deleted customers are not selectable. |
| 9 | Section 2 — Contact | Numbered card | Step **2** | "Contact (this request only)". Request-local; does not PATCH the account. |
| 10 | Contact name / mobile / email | Required / required / optional | Pre-fill name + mobile | Mobile prefix default **🇮🇳 +91**. Combined mobile `+?[0-9 -]{8,15}`. |
| 11 | Section 3 — Request details | Numbered card | Step **3** | Same device + issue fields as the customer app ([FEAT-RR-001](../../features/repair-request.md)). |
| 12 | Device type | Required select | `GET /device-categories` (active) | Label **Device type \*** — same as the customer app. Changing type reloads brands and clears a brand that no longer applies. |
| 13 | Brand | Optional select / text | `GET /device-categories/{id}/brands` when `CONFIGURED`, plus **Others**. Disabled until a device type is chosen ("Select a device type first"). | Snapshot string on submit. |
| 14 | Model | Optional text | | Placeholder "e.g. Galaxy S24". |
| 14a | Issue | Required textarea | | Label **Describe the issue \*** — min 10 characters, same as the app. |
| 14b | Intake channel | Single-select chips | Phone (default) · Walk-in · WhatsApp · Other | Desk-only. Persist as `intakeChannel` when the API supports it. |
| 14c | Photos | Drop zone + thumbs | Optional | Same limits as the app: max 5; 10 MB; JPG / PNG. Uploaded after `201`. |
| 15 | Contextual rail | Right column | **Absent** until a customer is selected or created | Not a form. No priority, preferred date, or assign-technician controls. Below 960px, stack under the form. |
| 16 | Rail — snapshot | Card | Name, mobile, previous-request count | **View customer →** opens [AP-11](AP-11-customer-detail.md). |
| 17 | Rail — equipment | Card | Customer assets, or empty | Select existing or **+ Add equipment** (§5.4). Optional on create. Maps to request device snapshot when the API has no asset entity. |
| 18 | Rail — previous requests | Card | Last few from customer history | Read-only. Empty copy: "No previous requests. This will be their first." |
| 19 | Rail — next | Card | Static | "After this request is created you can review it, then schedule and assign a technician." |
| 20 | Footer hint | Caption | Static | "Create the request first. Schedule and assign come next." |
| 21 | Footer: Cancel | Outlined | | Dirty prompt. |
| 22 | Footer: Save for review | Outlined | | Disabled with no customer, invalid form, or in flight. Parks `PENDING_REVIEW`. |
| 23 | Footer: Create request → | Primary | | Same enable rules. Creates the request and proceeds to the next stage (request detail). |
| 24 | Create and approve | Confirm dialog §5.2 | | Optional skip-review path. Not a footer field for scheduling. |
| 25 | Silent defaults | Submit payload only | Scheduling-config | `preferredDate` / `preferredTime` = next shop slot so `POST /requests` still validates. Device type is **on the form** (#12). Not shown: preferred slot (schedule later). |
| 26 | Form error / loading / forbidden | Panels | | See §6. |

## 5. Interactions & dialogs

| Action | Trigger | Dialog / form | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Select customer** | Typeahead row | — | `GET /customers?q=` then `GET /customers/{id}` (+ history) | Card #8; contact pre-fill; rail appears; `customerId` in the URL. |
| **Change customer** | Change on card | Confirm if dirty | — | Clears owner, contact pre-fill, rail, and `customerId`. |
| **No results → create** | Empty search | §5.1 | `POST /customers` | Same as New customer. |
| **New customer** | + New customer | §5.1 | `POST /customers` | Account created; selected; rail appears (0 history); password shown once if generated. |
| **Add equipment** | Rail + Add | §5.4 | Local / future asset API | Optional device snapshot on the request. |
| **Save for review** | Footer | — | `POST /requests` `customerId`, silent defaults (#25), no `approveOnCreate`; then attachments | Toast "SR-1042 created · pending review". Navigate to `/requests/{id}` if permitted, else `/customers/{customerId}`. |
| **Create request** | Footer primary | — | Same payload as Save for review | Toast "SR-1042 created". Navigate to `/requests/{id}` (next: review / schedule / assign). |
| **Create and approve** | Dialog §5.2 | §5.2 | `approveOnCreate: true`, `priority?`, `note?` | Toast "SR-1042 approved · job created". Navigate to `/jobs/{jobId}`. Scheduling is still on the job. |
| **Cancel** | Footer | Dirty prompt | — | Back to referrer. |

Both submit buttons disable and show a spinner while in flight. No double submit ([README §6.8](../README.md)).

### 5.1 New customer dialog

Title "New customer". Creates a `CUSTOMER` account for walk-in / phone intake.

| Field | Type | Rules |
| --- | --- | --- |
| Name | Required text | Max 100. |
| Mobile | Required | Same mobile pattern as registration. Unique among non-deleted users. |
| Username | Optional | If empty, the server derives one from the mobile. |
| Password | Optional | If empty, the server generates one and sets `mustChangePassword`. Min 8 if provided. Show/hide toggle. |

On `201`: close dialog, select the customer, toast "Anita Kumar added". If a password was generated, an **additional** dialog "Give this password to the customer" shows it once with **Copy**. It cannot be retrieved later.

On `409` duplicate mobile/username: field error plus **Use this customer** if the payload includes the existing id.

### 5.2 Create and approve dialog

Title "Create and approve". Copy: "This creates the request and the job in one step. {Name} will be notified that the repair was accepted — not that it is still under review."

| Field | Type | Rules |
| --- | --- | --- |
| Priority | Select | `LOW` / `NORMAL` / `HIGH` / `URGENT`. Default `NORMAL`. |
| Internal note | Optional textarea | Staff-only. |

Buttons: **Back** · **Create and approve** (primary, busy while in flight).

### 5.3 Prefill from `customerId`

On load, if `customerId` is in the query string, fetch that customer, lock the card as in #8, and show the rail. Invalid / forbidden / disabled ids show the inline error, hide the rail, and do not block filling the rest of the form after the operator picks someone else.

### 5.4 Add equipment dialog

Title "Add equipment". Optional. Associate an existing customer asset or capture a new one as the request device snapshot (category / brand / model). Does not schedule a visit.

Buttons: **Cancel** · **Add to request**.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Header + skeleton cards; no rail. |
| **Ready, no customer** | Single-column form; search empty; **no rail**; submits disabled. |
| **Search results** | Typeahead under section 1; still no rail. |
| **No customer found** | Empty search + **Create new customer**; no rail. |
| **Ready, customer selected** | Card #8 + rail; submits enabled when contact + issue are valid. |
| **New-customer / password / add-equipment / approve** | Modals §5.1–§5.4. |
| **Submitting** | Footer busy; form locked. |
| **Error (catalog/config)** | Error panel, **Retry**. |
| **Forbidden** | "Creating requests for customers is limited to staff and administrators" with a link to `/jobs`. |
| **Not found customerId** | Inline on the customer block; no rail; rest of the form usable. |

## 7. Permissions & visibility

- **Route guard:** `ADMIN` and `STAFF`. Technicians get the forbidden panel.
- **Repair Requests queue** remains `ADMIN`-only until the [README §3](../README.md) Staff-access question is resolved. STAFF still use this page:
  - Entry from [AP-11](AP-11-customer-detail.md) (**New request**) and a dashboard quick link.
  - After **Save for review** or **Create request**, navigate to `/customers/{id}` (not AP-04/AP-05).
  - After **Create and approve**, navigate to `/jobs/{id}` (STAFF already have jobs).
- **Recommended follow-up:** open AP-04 / AP-05 to `STAFF` so Save for review lands on the request. Tracked as [features/staff-assisted-intake.md](../../features/staff-assisted-intake.md) open question 1.
- Server is authoritative: `403` on `POST /requests` with `customerId` renders the form error summary, not a blank page.

## 8. Acceptance criteria

**AC-AP18-01** — Given I open `/requests/new` with no query,
When the page is ready,
Then no customer is selected, the contextual rail is not shown, both submit actions are disabled, and search is focused.

**AC-AP18-02** — Given I search "sharma" and select Anika Sharma,
When the card appears,
Then contact name and mobile are pre-filled, I can still edit them for this request only, and the rail shows her snapshot, assets, and previous requests.

**AC-AP18-03** — Given I opened `/requests/new?customerId={id}` from customer detail,
When the page loads,
Then that customer is selected and Change is the only way to pick someone else.

**AC-AP18-04** — Given a valid form and selected customer,
When I click Save for review,
Then `POST /requests` is sent with that `customerId` and without `approveOnCreate`, and on success I see a toast naming the new reference.

**AC-AP18-05** — Given a valid form,
When I click Create request,
Then `POST /requests` is sent without `approveOnCreate`, and on success I land on the new request detail (not a schedule or assign screen).

**AC-AP18-06** — Given no customer is selected,
When I click either submit,
Then no request is sent and the customer control shows a required error.

**AC-AP18-07** — Given I create a new customer with name and mobile only,
When the account is created,
Then they become the selected customer and, if a password was generated, I see it in a dialog I can copy.

**AC-AP18-08** — Given I am a technician,
When I navigate to `/requests/new`,
Then I see the forbidden panel and no customer search.

**AC-AP18-09** — Given a valid form and selected customer,
When I submit either finish,
Then the payload includes the selected `deviceCategoryId` and `preferredDate` / `preferredTime` of the next valid shop slot. Preferred slot is not on the page.

**AC-AP18-10** — Given there is no active device category (or no remaining shop slot),
When the page finishes loading,
Then I see an inline error and both submit actions stay disabled.

**AC-AP18-11** — Given no customer is selected,
When I view the page,
Then I see three numbered cards. Request details includes **Device type** (required), Brand, Model, issue, channel, and photos — the same device fields as the customer app. I do not see priority, preferred date, or assign-technician controls.

**AC-AP18-16** — Given a search that returns no customers,
When the results render,
Then I see a clear **Create new customer** action and the rail stays hidden.

**AC-AP18-17** — Given I confirm Create and approve,
When the call succeeds,
Then I land on the new job detail. Scheduling is not collected on this page.

**AC-AP18-12** — Given I type in search,
When I pause,
Then exactly one `GET /customers?q=` is issued 350ms after the last keystroke.

**AC-AP18-13** — Given the create call returns 422 field errors,
When the response arrives,
Then matching fields show errors, the footer is not left in a busy state, and no navigation occurs.

**AC-AP18-14** — Given I have unsaved field edits,
When I click Cancel or the breadcrumb,
Then I am prompted before leaving.

**AC-AP18-15** — Given `/requests/new` and `/requests/:id` are both routed,
When I open `/requests/new`,
Then this page loads — it is not treated as a request id of `"new"`.

## 9. Deviations from the mobile app

| Mobile behaviour | Panel behaviour | Why |
| --- | --- | --- |
| Customer submits as themselves; no owner picker | Staff must pick or create the customer | Walk-in / phone; request cannot be owned by staff. |
| Single Submit | **Save for review**, **Create request**, optional **Create and approve** | Create first; schedule later. Approve is skip-review only. |
| Full device, slot, and photo form | Customer + contact + issue + channel + optional files/asset | Category and next shop slot are silent defaults. No dispatch fields on this page. |
| Account already exists (registration) | **New customer** on this page | Walk-ins are often not registered; `POST /users` is the wrong tool for STAFF. |

## 10. Open questions

1. Staff access to AP-04 / AP-05 after Save for review — see §7 and FEAT-SAI-001 Q1. **Recommended: yes.**
2. `POST /customers` username/password generation scheme — confirm with backend (FEAT-SAI-001 Q3).
3. Should dashboard show a **Create request** quick link for STAFF even though they do not see Repair Requests in the sidebar? **Yes** (this spec).
4. `intakeChannel` and first-class assets are not in today's API. **This increment:** channel may be omitted; equipment maps to the request device snapshot.
5. First-active-category as the silent default is a placeholder. Confirm if a dedicated "Unspecified" catalog row is required.
6. Product chrome in the mockup is **Tezvo Flow / Service Requests**. Confirm whether the shipped panel keeps RepairDesk naming.

---

*Spec status: draft. Domain rules: [features/staff-assisted-intake.md](../../features/staff-assisted-intake.md). Mockup: [mockups/AP-18-create-request.html](../mockups/AP-18-create-request.html).*
