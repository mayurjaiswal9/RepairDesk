# AP-10 · Customers

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-10 |
| **Route** | `/customers` (search/page/size in query string, e.g. `/customers?q=kumar&page=0&size=20`) |
| **Roles** | `ADMIN`, `STAFF` — read-only for `STAFF`; account actions are `ADMIN`-only (see §7) |
| **Entry points** | Sidebar → People → Customers · Dashboard "Customers" card · global search result |
| **Primary endpoints** | `GET /customers?q=` — the only supported parameter, returning a plain JSON array rather than a paged envelope · `POST /users/{id}/reset-password` (`ADMIN`) · `DELETE /users/{id}` (`ADMIN`) |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-11 Customer Detail & History](AP-11-customer-detail.md) · [AP-12 Staff Management](AP-12-staff.md) · [features/customer-management.md](../../features/customer-management.md) · [API.md §9](../../API.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The lookup desk for customer accounts. When someone calls or walks in, staff need to identify them in seconds and get to their repair history, so this page is a fast, forgiving search over name, username, and mobile number with a result table that leads straight into [AP-11](AP-11-customer-detail.md). Customer Management is a **read-only** view of the account record — nothing here creates or edits a customer — with one deliberate exception: an administrator can perform the two account-lifecycle actions (reset password, delete) that would otherwise require hunting the same person down in [AP-12](AP-12-staff.md). Those actions call the `/users/{id}` endpoints, not `/customers`, and are hidden entirely from `STAFF`.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Customers                                                          │  page header
│ 1,284 customers                                                    │
├────────────────────────────────────────────────────────────────────┤
│ [ 🔍 Search name, username, or mobile ]                     ✕      │  filter bar
├────────────────────────────────────────────────────────────────────┤
│ Name          │ Username  │ Mobile       │ Status  │ Repairs │  ⋮   │  data table
│ Anand Kumar   │ @akumar   │ 98765 43210  │ Active  │    3    │  ⋮   │
│ Priya S.      │ @priyas   │ 91234 56780  │ Disabled│    1    │  ⋮   │
│ …                                                                   │
├────────────────────────────────────────────────────────────────────┤
│ Page 1 of 65 · 1,284 customers      [20 ▾]      ‹ Prev   Next ›     │  pagination
└────────────────────────────────────────────────────────────────────┘
```

The ⋮ column renders only for `ADMIN`. For `STAFF` the table ends at the last data column and rows are purely navigational.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Customers" | Static. No primary action — customers self-register ([features/customer-management.md](../../features/customer-management.md) §Business Rules 1), so the panel never creates one. |
| 2 | Result summary | Text, `body-small` | Length of the returned array (see element #12) | "1,284 customers", or "12 customers match 'kumar'" while a search is active. Hidden during first load. |
| 3 | Search | Debounced text input (350ms) | Maps to the `q` param of `GET /customers`, which the API searches over name, username, and mobile ([API.md §9](../../API.md)) | Trimmed before sending. A clear (✕) button appears when non-empty and returns the page to the unfiltered list. The 350ms debounce is the fix from [Bugs.md](../../Bugs.md) #6, where a per-keystroke request made the page feel permanently loading. |
| 4 | Empty-search default | — | `GET /customers` with **no** `q` param | The unfiltered list is the page's normal resting state and must load successfully. A null `q` reaching the repository caused a production 500 on every visit ([Bugs.md](../../Bugs.md) #9); the client sends no `q` at all when the box is empty, and the page treats a failure here as a hard error state, not an empty list. |
| 5 | Column: Name | Table cell, `title-medium` | `name` | Truncates to one line with an ellipsis and exposes the full value in a tooltip. It must never wrap one character per line, the failure mode in [Bugs.md](../../Bugs.md) #17. Renders the username when `name` is absent, since `name` is optional on `users` ([Database.md §3.1](../../Database.md)). |
| 6 | Column: Username | Table cell, `body-medium` | `username`, rendered `@akumar` | Truncates with a tooltip. Immutable identifier; never editable anywhere in the panel. |
| 7 | Column: Mobile | Table cell | `mobile` | The **authoritative** account contact, not a request-local contact snapshot ([Database.md §1](../../Database.md)). |
| 8 | Column: Status | Status chip | `status` | Active / Disabled / Deleted per the shared mapping ([Design-System §5.2](../Design-System.md)). Deleted accounts are normally filtered out server-side; if one is ever returned, the chip must render rather than break the row. |
| 9 | Column: Repairs | Table cell, numeric | `repairCount` | **Conditional.** `repairCount` is documented only on `GET /customers/{id}` ([API.md §9](../../API.md)); the list item shape does not document it. The column ships only if the field is added to the list payload — see open question 1. Until then it is omitted entirely rather than filled with a placeholder or an N+1 fetch per row. |
| 10 | Row click | — | — | Navigates to `/customers/{id}`. A real anchor, so middle-click and Ctrl-click open a new tab. |
| 11 | Row action menu | Overflow (⋮) menu | Reset password · Delete customer | **`ADMIN` only.** Absent from the DOM for `STAFF`, not merely disabled. Both items act on the account through `/users/{id}`, so their availability follows the Staff Management permission model, not the read-only Customer Management one (§7). |
| 12 | Pagination footer | Data-table footer | Position and totals derived from the returned array | Page-size selector 20/50/100; arrows disabled at bounds. **Client-side pending API support** — `GET /customers` accepts no `page` or `size` and returns a bare array, so the page slices what it has received ([README §6.4](../README.md) describes the server-side behavior this footer should have once the endpoint supports it). See open question 3. |
| 13 | Empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Search** | Typing in element #3 | — | `GET /customers?q=…` | One request 350ms after the last keystroke; paging resets to page 0; `q` is written to the URL. |
| **Clear search** | ✕ in the search box | — | `GET /customers` (no `q`) | Returns to the unfiltered list; `q` removed from the URL. |
| **Open customer** | Row click | — | — | Navigates to [AP-11](AP-11-customer-detail.md). |
| **Reset password** | Row menu → Reset password (`ADMIN`) | §5.1 | `POST /users/{id}/reset-password` with `{ newPassword }` | Toast "Password reset for Anand Kumar". The list is not refetched — nothing visible changed. |
| **Delete customer** | Row menu → Delete customer (`ADMIN`) | §5.2 | `DELETE /users/{id}` | Toast "Anand Kumar's account deleted". The current page is refetched and the row disappears. |
| **Page / page size** | Pagination footer | — | — | No request: the page slices the array it already holds (element #12). Footer and URL update. |

### 5.1 Reset password dialog

| Aspect | Content |
| --- | --- |
| Title | "Reset password — Anand Kumar" |
| Body | One line of explanation: "Set a new password for this account. The customer will need this password to sign in, so share it with them directly." |
| Field | **New password** * — masked input with a show/hide toggle, minimum 8 characters (mirrors the server rule in [API.md §8](../../API.md) and [features/staff-management.md](../../features/staff-management.md) §Validation). Never pre-filled. |
| Actions | **Cancel** · **Reset password** (busy while in flight, disabled while empty or invalid) |
| Endpoint | `POST /users/{id}/reset-password` with `{ newPassword }` |
| Result | Dialog closes, field is cleared from memory, success toast names the customer. |

The password is **write-only**: after submission it is never re-displayed, never echoed into the toast or the URL, never written to telemetry or the console, and the input is not restored if the dialog is reopened ([Design-System §9](../Design-System.md), [features/staff-management.md](../../features/staff-management.md) Business Rule 15). A `422` binds its `fieldErrors[].field` message to the password control and leaves the dialog open.

### 5.2 Delete customer dialog

| Aspect | Content |
| --- | --- |
| Title | "Delete Anand Kumar's account?" |
| Body | "This is a **logical delete**. Their access is revoked immediately and any active session ends, but their repair requests and jobs are kept so history and reporting stay intact. Their username and mobile become available for a new account." |
| Actions | **Cancel** · **Delete account** (error-colored, busy while in flight) |
| Endpoint | `DELETE /users/{id}` |
| Result | Toast "Anand Kumar's account deleted". The current page is refetched so the row leaves the list. |

The wording is not softening: `DELETE /users/{id}` sets `status = DELETED` and the partial unique indexes free the username and mobile for reuse, while requests and jobs are never deleted at all ([Database.md §4](../../Database.md)). Calling it a permanent erasure would be wrong in both directions — access really is gone, and the data really is retained. There is no undo, because the API exposes no un-delete; `POST /users/{id}/activate` restores a `DISABLED` account, not a `DELETED` one.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Page header and search box render immediately; the table shows 10 skeleton rows. |
| **Loading (refetch)** | Existing rows stay visible and dimmed with a top progress bar; the search box stays focused and usable. |
| **Empty** (no search) | People icon, "No customers yet", "Customers appear here once they register in the mobile app." No action button — the panel cannot create a customer. |
| **Filtered-empty** | "No customers match 'kumar'", with **Clear search**. Must never claim that no customers exist. |
| **Error** | Standard error panel with the mapped message, **Retry**, and the `correlationId`. This is the state for a failed unfiltered load — the page must not degrade a 500 into a friendly "no customers" message, which is how [Bugs.md](../../Bugs.md) #9 stayed invisible in the mobile app. |
| **Forbidden** | Shown to `TECHNICIAN` (and any role the server refuses): "Customer lookup is limited to staff and administrators", with a link to `/dashboard`. |
| **Stale row** | A reset or delete against an account another admin already deleted returns `404`/`409`; the row is refreshed in place and an inline "Updated elsewhere" marker shows for 5 seconds. |

## 7. Permissions & visibility

- **Route guard:** `ADMIN` and `STAFF`. `TECHNICIAN` and `CUSTOMER` get the forbidden state; the server enforces this independently ([API.md §9](../../API.md) — "`403` for Technician/Customer").
- **`STAFF` gets a read-only list.** Search, columns, pagination, and row navigation only. The row action menu (element #11) is not rendered for `STAFF`, so there is no reset-password or delete affordance anywhere on the page.
- **`ADMIN` additionally gets Reset password and Delete**, performed through `POST /users/{id}/reset-password` and `DELETE /users/{id}`. These are Staff Management endpoints applied to a customer account; the panel surfaces them here purely because this is where an admin is already standing when they need them ([Bugs.md](../../Bugs.md) #12).
- **This is a documented tension, resolved in favor of shipped behavior.** [features/customer-management.md](../../features/customer-management.md) (§Scope note, Business Rule 1, AC-5) and [API.md §9](../../API.md) both describe Customer Management as strictly read-only for Staff *and* Admin, directing all account changes to [AP-12](AP-12-staff.md). The mobile app was deliberately changed to contradict that for `ADMIN` ([Bugs.md](../../Bugs.md) #12). The panel matches the shipped app; the doc conflict is recorded in §10 and needs a product decision.
- **No edit path for customer profile fields.** Name, mobile, and role are not editable from this page for either role — the `PATCH /users/{id}` edit form lives only in [AP-12](AP-12-staff.md), which is `ADMIN`-only and scoped to internal users.
- **Server is authoritative.** A `403` on any action renders or toasts per [README §6.2](../README.md) regardless of what the client believed about the current user's role.

## 8. Acceptance criteria

**AC-AP10-01** — Given I open `/customers` with an empty search box,
When the page loads,
Then `GET /customers` is called with no `q` parameter, the unfiltered first page of customers renders, and no error state is shown.

**AC-AP10-02** — Given the customer list is loading for the first time,
When I wait for the response,
Then the table shows skeleton rows while the page header and search box are already interactive.

**AC-AP10-03** — Given I type continuously in the search box,
When I pause,
Then exactly one `GET /customers?q=…` request is issued 350ms after my last keystroke, and the query is trimmed of surrounding whitespace.

**AC-AP10-04** — Given I have searched for a term that matches nothing,
When the empty result renders,
Then it names my query and offers **Clear search**, and it does not state that no customers exist.

**AC-AP10-05** — Given I click a customer row,
When navigation completes,
Then I am on `/customers/{id}` for that customer, and Ctrl-clicking the same row instead opens it in a new tab.

**AC-AP10-06** — Given I am signed in as `STAFF`,
When the customer list renders,
Then no row action menu is present on any row, and there is no reset-password or delete control anywhere on the page.

**AC-AP10-07** — Given I am signed in as `ADMIN`,
When I open a row's action menu,
Then **Reset password** and **Delete customer** are offered.

**AC-AP10-08** — Given I am an admin in the reset-password dialog,
When I submit a password shorter than 8 characters,
Then submission is blocked with a field-level message and no request is sent.

**AC-AP10-09** — Given I successfully reset a customer's password,
When the dialog closes,
Then the success toast names the customer but not the password, the password appears in no log, telemetry event, or URL, and reopening the dialog shows an empty field.

**AC-AP10-10** — Given I am an admin opening the delete dialog,
When it renders,
Then it states that access is revoked immediately and that the customer's requests and jobs are retained for history, and it names the customer.

**AC-AP10-11** — Given I confirm deletion of a customer,
When the request succeeds,
Then a toast names the deleted customer, the current page is refetched, and that row no longer appears in the list.

**AC-AP10-12** — Given another admin deleted a customer after my list loaded,
When I attempt to reset that customer's password or delete them,
Then I see an explanation that the account is no longer available, the row is refreshed in place, and my action is not reported as successful.

**AC-AP10-13** — Given a customer whose name is long enough to overflow its column,
When the row renders at any supported viewport width,
Then the name truncates to a single line with an ellipsis and a tooltip carrying the full value — it never wraps one character per line.

**AC-AP10-14** — Given `GET /customers` returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** button that refetches the current view, and the `correlationId` in small text — never a raw exception string, and never an empty-list state.

**AC-AP10-15** — Given the result set is larger than the page size,
When I move to the next page or change the page size,
Then the already-loaded array is re-sliced client-side without a further request, the footer reports the correct position and total, and the URL records both.

**AC-AP10-16** — Given I am signed in as `TECHNICIAN`,
When I navigate directly to `/customers`,
Then I see the forbidden panel with a link to the dashboard, and no customer data is displayed.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Card list with avatar, name, username, and mobile stacked | Data table with discrete Name / Username / Mobile / Status columns | Identifying a caller means scanning one column at a time; a table does that and a card list does not. |
| No pagination — a single unbounded list | A paged table with a page-size selector, client-side until the endpoint supports paging (element #12) | Required for the 10k-customer target in [PRD §10](../../PRD.md) and [features/customer-management.md](../../features/customer-management.md) Business Rule 7 — which client-side paging cannot meet on its own, hence open question 3. |
| Search state is in-memory only | `q`, `page`, and `size` live in the URL | Deep links, refresh-safety, and a working browser Back button. |
| Reset password and Delete shown to any user who reaches the screen | Both gated to `ADMIN`, absent for `STAFF` | The mobile app has no role check on `_CustomerCard`'s menu; the intended split ([features/staff-management.md](../../features/staff-management.md) §Permissions) is that account administration is admin-only. |
| Delete dialog says "Their repair history is kept" | Dialog additionally states the delete is logical, that sessions end immediately, and that the username/mobile are freed | Matches what `DELETE /users/{id}` actually does ([Database.md §4](../../Database.md)) instead of leaving admins to guess. |
| Errors render as a centered message replacing the whole body | Standard error panel with **Retry** and `correlationId` | Consistent with [README §6.2](../README.md) and gives support a trace handle. |
| Row status shown as a bare check/block icon | Status chip with text | Color alone is not an accessible carrier of meaning ([Design-System §8](../Design-System.md)). |

## 10. Open questions

1. **Does the customer list payload include `repairCount`?** [API.md §9](../../API.md) documents `repairCount` on `GET /customers/{id}` only; the list item shape is not documented, and the mobile client parses just `{ id, username, name?, mobile, status }`. The Repairs column (element #9) needs the field on the list response. Options: add `repairCount` to the list item (preferred, and it is already computed for the detail endpoint), or drop the column. **Blocking for element #9.**
2. **Does the list payload include `name`?** The documented profile shape for `GET /customers/{id}` is `{ id, username, mobile, status, registeredAt, repairCount }` — no `name` — yet `q` is documented as searching name, and the mobile client reads `name` off the list items. Confirm `name` is present on both payloads, or the Name column and the AP-11 profile header have nothing to render. **Blocking for element #5.**
3. **`GET /customers` does not return the paged envelope — it returns a bare array.** Verified against the deployed controller, which accepts `q` alone: no `page`, no `size`, no `sort`, and no `totalElements` to report. [API.md §4](../../API.md) and §9 describe the standard envelope, and they are wrong; the mobile client's bare-array parsing is correct. Element #12 therefore pages client-side over the whole result, which cannot meet the 10k-customer target in [PRD §10](../../PRD.md) — every visit would transfer the entire customer base. Add `page`, `size`, and the standard envelope to `GET /customers`. **Blocking for server-side pagination (element #12).**
4. **Read-only Customer Management versus admin account actions.** [features/customer-management.md](../../features/customer-management.md) and [API.md §9](../../API.md) both state the feature is read-only for Staff and Admin, with all account changes routed to [AP-12](AP-12-staff.md); [Bugs.md](../../Bugs.md) #12 deliberately added reset-password and delete to this screen for admins. This spec follows the shipped behavior. Product must either bless the exception (and amend the feature spec and API.md §9's "strictly read-only" rule) or remove the actions from AP-10 and AP-11 and send admins to Staff Management — which today lists only internal users and would need a customer view added.
5. **Sorting.** The deployed `GET /customers` accepts `q` alone (item 3), so there is no `sort` param and column sorting is not offered in V1. Adding `sort` alongside the pagination params in item 3 would let the table enable it; confirm whether that is wanted.
6. **Default ordering.** With no `sort`, the server's natural ordering is unspecified. [features/customer-management.md](../../features/customer-management.md) suggests "most recently active, or alphabetical/registration-ordered (implementation choice)". Confirm the actual order so the empty-search list is at least predictable.
7. **Disabling a customer.** `POST /users/{id}/deactivate` exists and would be the reversible middle ground between doing nothing and deleting, but neither the mobile app nor this spec offers it for customers. Confirm whether admins want it here; it is deliberately omitted rather than added without a decision.
8. **Password handover.** There is no email or SMS channel in V1 ([features/staff-management.md](../../features/staff-management.md) §Notifications), so a reset password must be read out to the customer by the admin. Confirm that is acceptable, and whether the dialog should also show the reset password once before closing (currently specified as never displayed).

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/customer_management/presentation/customers_screen.dart`, `frontend/lib/features/customer_management/data/customer_management_repository.dart`, [API.md §8–9](../../API.md), [Database.md §1, §3.1, §4](../../Database.md), [features/customer-management.md](../../features/customer-management.md), and [Bugs.md](../../Bugs.md) #6, #9, #12, #17.*
