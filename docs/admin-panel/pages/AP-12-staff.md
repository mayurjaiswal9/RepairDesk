# AP-12 · Staff Management

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-12 |
| **Route** | `/staff` (role/status/search/page in query string, e.g. `/staff?role=TECHNICIAN&status=ACTIVE&page=0`) |
| **Roles** | `ADMIN` only |
| **Entry points** | Sidebar → People → Staff · Dashboard "Staff members" card |
| **Primary endpoints** | `GET /users?role=` — a **single optional `role` param**, returning a plain JSON array rather than a paged envelope · `POST /users` · `PATCH /users/{id}` · `POST /users/{id}/activate` · `POST /users/{id}/deactivate` · `POST /users/{id}/reset-password` · `DELETE /users/{id}` · `GET /me` (to identify the current user's own row). There is **no** `GET /users/{id}` on the deployed backend — see §10 item 2. |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-10 Customers](AP-10-customers.md) · [features/staff-management.md](../../features/staff-management.md) · [API.md §8](../../API.md) · [Database.md §3.1](../../Database.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The administrator's console for internal accounts — the people who work in the shop, not the people who bring devices to it. From here an admin onboards a technician, corrects a mobile number, promotes someone to staff, suspends an account while a person is away, resets a forgotten password, and offboards a leaver. The page lists only `ADMIN`, `STAFF`, and `TECHNICIAN` accounts; customers appearing in this list was a real defect ([Bugs.md](../../Bugs.md) #11) that made the roster unreadable and put account controls next to people who should be managed from [AP-10](AP-10-customers.md). Two business rules shape almost every interaction: the shop must never be left without an active administrator, and an admin cannot administer themselves out of existence. Both are enforced server-side, and this page's job is to make them visible before an admin hits them rather than after.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Staff                                             [ + Add user ]   │  page header
│ 11 internal users · 9 active                                       │
├────────────────────────────────────────────────────────────────────┤
│ [All internal][Admin][Staff][Technician]   [Status ▾]              │  filter bar
│ [ 🔍 Search name, username, or mobile ]                            │
├────────────────────────────────────────────────────────────────────┤
│ Name           │ Username │ Mobile      │ Role       │ Status │ ⋮   │  data table
│ Ravi Menon (you)│ @ravi   │ 98765 43210 │ [Admin]    │ Active │ ⋮   │
│ Priya Sharma   │ @priyas  │ 91234 56780 │ [Technician]│Active │ ⋮   │
│ Sunil Rao      │ @sunilr  │ 90000 11122 │ [Staff]    │Disabled│ ⋮   │
├────────────────────────────────────────────────────────────────────┤
│ Page 1 of 1 · 11 users             [20 ▾]     ‹ Prev   Next ›       │  pagination
└────────────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Staff" | Static. |
| 2 | Result summary | Text, `body-small` | Total internal users and active count | "11 internal users · 9 active". Reflects the current filters when any are active. |
| 3 | Add user | Primary button | — | Opens the create dialog (§5.1). The page's single primary action. |
| 4 | Role filter | Segmented control | **All internal** (default) · Admin · Staff · Technician | There is deliberately no "Customer" option, and no "All accounts" option — customers belong on [AP-10](AP-10-customers.md). How each option is fetched is described in element #8. |
| 5 | Status filter | Dropdown | All (default) · Active · Disabled | Applied **client-side** over the fetched array: the deployed `GET /users` accepts no `status` param, so the page filters the rows it already holds (open question 1). `DELETED` is not offered: deleted accounts are filtered out server-side by default ([Database.md §4](../../Database.md)) and the panel has no un-delete action to justify showing them. |
| 6 | Search | Debounced text input (350ms) | Matched **client-side** against `name`, `username`, and `mobile` on the fetched array | The deployed `GET /users` accepts no `q` param (open question 1). Trimmed before matching; clear (✕) button when non-empty. The 350ms debounce is kept so the table does not re-filter and re-render on every keystroke. |
| 7 | Active filter chips | Removable chips + **Clear all** | Current role, status, and search | Per [Design-System §5.8](../Design-System.md). Whole filter state lives in the URL. |
| 8 | Internal-only fetch strategy | — | `GET /users?role=` | `GET /users` accepts a **single** `role` value and nothing else — no `q`, no `status`, no `page`, `size`, or `sort` — and returns a plain array of the users holding that role ([Bugs.md](../../Bugs.md) #11: "`/users?role=X` only supports one role at a time"). So: selecting a specific role issues **one** call, and the page searches, filters, and pages the returned array itself. **All internal** issues **three parallel calls** — `role=ADMIN`, `role=STAFF`, `role=TECHNICIAN` — whose arrays are merged and then treated identically. Three role-scoped calls are used instead of one unfiltered call with client-side exclusion because the unfiltered response would carry the entire customer base in order to render a roster of eleven people; and because each response is an unpaged array, three calls reconcile no page boundaries and cost little more than one. See open question 1. |
| 9 | Column: Name | Table cell, `title-medium` | `name` | Truncates to one line with an ellipsis and a tooltip; never wraps one character per line — the exact defect in [Bugs.md](../../Bugs.md) #17, caused by an unbounded name competing with the role badge for width. Falls back to the username when `name` is absent ([Database.md §3.1](../../Database.md): `name` is nullable). |
| 10 | "(you)" marker | Text suffix, `text-secondary` | Current user id from `GET /me` | Appended to the current admin's own name so self-action guards (§5.6) have a visible cause. |
| 11 | Column: Username | Table cell | `username`, rendered `@ravi` | Truncates with a tooltip. **Never editable** — `PATCH /users/{id}` accepts name, mobile, and role only, and usernames are login identifiers. |
| 12 | Column: Mobile | Table cell | `mobile` | Login identifier and authoritative contact; unique among non-deleted accounts. |
| 13 | Column: Role | Role badge | `role` | Admin / Staff / Technician per [Design-System §5.3](../Design-System.md) — always the word, never colour alone. |
| 14 | Column: Status | Status chip | `status` | Active / Disabled per [Design-System §5.2](../Design-System.md). |
| 15 | Row action menu | Overflow (⋮) menu | Edit · Disable **or** Enable · Reset password · Delete | Disable is shown for `ACTIVE` rows and Enable for `DISABLED` rows, never both. Items that the current user is not permitted to perform on that row are rendered **disabled with an explanatory tooltip** rather than hidden (§5.6), so the reason is discoverable. |
| 16 | Row click | — | — | Opens the **Edit** dialog. Internal users have no detail page — there is nothing to show beyond the editable fields and the account actions already in the row menu, and the backend exposes no user-detail endpoint to populate one (§10 item 2). |
| 17 | Pagination footer | Data-table footer | Position and totals derived from the loaded array | Page-size selector 20/50/100. **Client-side throughout**, for every role option — `GET /users` has no `page` or `size` params, so the page slices the merged and filtered array itself. True server-side paging depends on open question 1. |
| 18 | Empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

| Action | Trigger | Dialog / form fields | Endpoint | Result |
| --- | --- | --- | --- | --- |
| **Create user** | Add user | §5.1 | `POST /users` with `{ username, mobile, name?, password, role }` | `201`; toast "Priya Sharma created as Technician"; list refetches; the new account is `ACTIVE` and not signed in. |
| **Edit user** | Row click or menu → Edit | §5.2 | `PATCH /users/{id}` with `{ name, mobile, role, version }`, prefilled from the loaded row | Toast "Priya Sharma updated"; the row refreshes in place. |
| **Disable** | Menu → Disable | §5.3 | `POST /users/{id}/deactivate` | `204`; toast "Sunil Rao disabled"; the status chip becomes Disabled. |
| **Enable** | Menu → Enable | Confirmation naming the user: "Sunil Rao will be able to sign in again." | `POST /users/{id}/activate` | `204`; toast "Sunil Rao enabled"; status chip becomes Active. |
| **Reset password** | Menu → Reset password | §5.4 | `POST /users/{id}/reset-password` with `{ newPassword }` | Toast naming the user; no list refetch — nothing visible changed. |
| **Delete** | Menu → Delete | §5.5 | `DELETE /users/{id}` | Toast "Sunil Rao's account deleted"; list refetches and the row disappears. |
| **Filter / search / page** | Filter bar, footer | — | `GET /users?role=` on a role change only | The URL updates for every filter. Changing the role refetches per element #8; search, status, and paging are applied to the already-loaded array without a request. |

### 5.1 Create user dialog

Title "Add user". Fields, all validated on blur and again on submit ([Design-System §9](../Design-System.md)):

| Field | Type | Rules |
| --- | --- | --- |
| **Full name** * | Text | Required by this form, max 100 characters ([Database.md §3.1](../../Database.md)). The API treats `name` as optional, but an unnamed internal user is unusable in a technician dropdown, so the panel requires it. |
| **Username** * | Text | Required, unique. Immutable after creation, and the dialog says so: "Usernames can't be changed later." A `409` binds here as "The username 'priyas' is already taken." |
| **Mobile** * | Text | Required, unique, valid mobile format. A `409` binds here as "That mobile number is already in use." |
| **Temporary password** * | Masked text with show/hide | Required, minimum 8 characters ([API.md §8](../../API.md), [features/staff-management.md](../../features/staff-management.md) GAP-04). Never pre-filled. Helper text: "Share this with the user directly — RepairDesk doesn't send it." |
| **Role** * | Select | Staff · Technician · Admin, defaulting to **Staff**. Customer is deliberately not offered: creating a customer from the staff console would place an account in this list that this page then filters out. |

Actions: **Cancel** · **Create user** (busy while in flight, disabled while pristine or invalid). On success the dialog closes and the list refetches; the created account is `ACTIVE` and is **not** auto-signed-in ([API.md §8](../../API.md)). The temporary password is never shown again after the dialog closes.

### 5.2 Edit user dialog

The backend exposes no user-detail endpoint, so the dialog is prefilled from the row already loaded in the list rather than from a fresh read, and it round-trips the `version` that row was fetched with ([README §6.3](../README.md)). That version is only as current as the last list load — see §10 item 2.

| Field | Type | Rules |
| --- | --- | --- |
| **Username** | Read-only text | Displayed for identification, not editable. `PATCH /users/{id}` does not accept it. |
| **Full name** * | Text | Required, max 100 characters. |
| **Mobile** * | Text | Required, unique, valid format. A `409` binds here. |
| **Role** * | Select | Staff · Technician · Admin. **Disabled on the current user's own row** with the tooltip "You can't change your own role." (§5.6). |
| *(hidden)* `version` | — | Carried from the loaded row and round-tripped; a `409` optimistic-lock conflict shows "This record changed since you opened it" with **Reload**, which refetches the list so the dialog can reopen on current data. |

Actions: **Cancel** · **Save** (disabled while pristine or invalid). Changing a technician's role away from Technician removes them from the assignable roster consumed by Jobs and Scheduling ([features/staff-management.md](../../features/staff-management.md) Business Rule 10); the dialog notes this inline when the edited user is currently a technician: "Existing jobs assigned to this technician stay assigned, but they won't be selectable for new assignments."

### 5.3 Disable dialog

| Aspect | Content |
| --- | --- |
| Title | "Disable Sunil Rao?" |
| Body | "They will be signed out immediately and won't be able to sign in until you enable the account again. Nothing is deleted — their record, and any jobs assigned to them, are kept." |
| Actions | **Cancel** · **Disable** |
| Endpoint | `POST /users/{id}/deactivate` |

The immediacy is not a figure of speech: tokens are non-expiring and revalidated against account status on every request (ADR-003, [API.md §2](../../API.md)), so a disabled user's very next action fails with a `401`. On the current admin's own row the body gains a first line in error styling: "This is your own account. You will be signed out of RepairDesk immediately."

### 5.4 Reset password dialog

Identical in content and confidentiality rules to [AP-10 §5.1](AP-10-customers.md): title "Reset password — {name}", one masked **New password** field with a show/hide toggle and a minimum of 8 characters, **Cancel** and **Reset password** actions, and a `422` bound back to the field. The password is write-only — never displayed after submission, never in the toast, URL, telemetry, or logs, never restored on reopen ([features/staff-management.md](../../features/staff-management.md) Business Rule 15).

### 5.5 Delete dialog

| Aspect | Content |
| --- | --- |
| Title | "Delete Sunil Rao's account?" |
| Body | "This is a **logical delete**. Their access is revoked immediately and they are signed out, but their history — the jobs they worked on and the audit trail — is retained and stays attributable to them. Their username and mobile become available for a new account." |
| Actions | **Cancel** · **Delete account** (error-colored) |
| Endpoint | `DELETE /users/{id}` |
| Result | Toast naming the user; the list refetches and the row disappears. |

The wording follows what actually happens: `status = DELETED`, foreign keys from jobs and audit events stay valid, and the partial unique indexes free the username and mobile ([Database.md §4](../../Database.md)). There is no undo — `POST /users/{id}/activate` restores a `DISABLED` account, not a `DELETED` one, and the API exposes no un-delete.

### 5.6 Business-rule guards

Two rules are enforced server-side and must not be discovered only by failing. The panel handles each in two layers — pre-emptive disabling where the client can be certain, and a clear presentation of the server's refusal where it cannot.

**Self-action guard — a user cannot delete or demote themselves.** The current user's id comes from `GET /me`, so this is fully knowable client-side and is applied pre-emptively:

| On the current admin's own row | Treatment |
| --- | --- |
| **Delete** | Menu item disabled, tooltip "You can't delete your own account. Ask another administrator to do it." |
| **Role** select in the edit dialog | Disabled, tooltip "You can't change your own role. Ask another administrator to do it." |
| **Disable** | Left enabled — an admin may legitimately suspend their own account — but the confirmation warns that they will be signed out immediately (§5.3). |
| **Edit name / mobile**, **Reset password** | Left enabled; both are ordinary self-service operations. |

If the server refuses a self-action anyway, the `409`/`422` is shown in the dialog and the confirm button stays disabled, because an identical retry will fail identically.

**Last-admin guard — at least one active administrator must always exist.** Disabling, deleting, or demoting the last `ACTIVE` `ADMIN` returns `409 CONFLICT` with `errorCode: LAST_ADMIN` ([API.md §8](../../API.md), [features/staff-management.md](../../features/staff-management.md) Business Rule 8). Presentation:

- The dialog stays open and swaps its body for an error-styled explanation: "RepairDesk must always have at least one active administrator, and this is the only one. Give another user the Admin role first, then try again." The confirm button becomes disabled and only **Close** remains.
- In the edit dialog, a `LAST_ADMIN` conflict raised by a role change is additionally bound to the **Role** control and the select reverts to Admin, so the form reflects reality.
- **Pre-emptive disabling is best-effort and only in "All internal" mode.** In that mode the page holds the complete internal roster, so it can count active admins; when the count is exactly one, Disable and Delete on that row and the Role select in its edit dialog are disabled with the tooltip "This is the only active administrator." Under a single-role filter or a search the loaded set is partial, so no client-side claim is made and the guard surfaces reactively. The server is the authority in both cases.

**Other error mappings.** `422 VALIDATION_ERROR` binds each `fieldErrors[].field` to its control with the form left open and input preserved; any unmatched field error appears in a form-level summary ([Design-System §9](../Design-System.md)). `409` for a duplicate username or mobile binds to the offending field when `fieldErrors` identifies it — see open question 4.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading (first)** | Header and filter bar render immediately; the table shows 8 skeleton rows. |
| **Loading (refetch)** | Rows stay visible and dimmed with a top progress bar; row action menus are suppressed while in flight. |
| **Empty** | People icon, "No internal users yet", "Add the technicians, staff, and administrators who work in the shop." Primary action **Add user**. In practice unreachable, since the seeded default admin always exists ([features/staff-management.md](../../features/staff-management.md) Business Rule 14) and is the account viewing the page. |
| **Filtered-empty** | "No users match your filters", naming the active role, status, and search, with **Clear filters**. Never claims that no users exist. |
| **Error** | Standard error panel with the mapped message, **Retry**, and the `correlationId`. In **All internal** mode, if one of the three role calls fails while the others succeed, the page shows the error panel rather than a silently short roster — a partial staff list is worse than an obvious failure. |
| **Forbidden** | Shown to `STAFF` and `TECHNICIAN` reaching the URL directly: "Staff management is limited to administrators", with a link to `/dashboard`. |
| **Sole-admin** | When the loaded internal set contains exactly one active administrator, that row shows an inline marker and its destructive actions are pre-emptively disabled (§5.6). Informational, not an error. |
| **Self-disabled** | If an admin disables or deletes their own account, the next API call returns `401`; the session clears and they are redirected to `/login` with the standard message ([README §6.1](../README.md)) rather than left on a page that has stopped working. |

## 7. Permissions & visibility

- **Route guard:** `ADMIN` only, matching [features/staff-management.md](../../features/staff-management.md) Business Rule 1 and the `ADMIN` role on every mutating `/users` endpoint in [API.md §8](../../API.md). `STAFF` and `TECHNICIAN` do not see the sidebar item and get the forbidden state on direct navigation; `CUSTOMER` cannot sign in to the panel at all ([README §3](../README.md)).
- **The read is not admin-gated server-side.** `GET /users` is available to `ROLE_STAFF` as well as `ROLE_ADMIN` on the deployed backend — which is what lets [AP-06](AP-06-jobs.md), [AP-07](AP-07-job-detail.md), and [AP-08](AP-08-schedule-appointment.md) populate technician dropdowns for staff — even though API.md §8 marks the endpoint admin-only. This page stays `ADMIN`-only by product decision, not because the server refuses staff; every **mutating** `/users` endpoint genuinely is admin-only.
- **Scope:** internal roles only — `ADMIN`, `STAFF`, `TECHNICIAN`. `CUSTOMER` accounts never appear, under any filter or search term ([Bugs.md](../../Bugs.md) #11). Customer accounts are administered from [AP-10](AP-10-customers.md) and [AP-11](AP-11-customer-detail.md).
- **Record-state gating:** Disable is offered only on `ACTIVE` rows, Enable only on `DISABLED` rows.
- **Self gating:** per §5.6 — Delete and the Role select are pre-emptively disabled on the current user's own row; everything else stays available.
- **Sole-admin gating:** best-effort pre-emptive disabling when the full internal roster is loaded; otherwise reactive on the server's `LAST_ADMIN` conflict.
- **Server is authoritative.** All guards above are UX affordances. Every mutation is re-validated server-side against the account's state at execution time ([features/staff-management.md](../../features/staff-management.md) §Validation, General), and a `403` renders per [README §6.2](../README.md).

## 8. Acceptance criteria

**AC-AP12-01** — Given the internal roster and customer accounts both exist,
When I open `/staff` with the default filters,
Then only `ADMIN`, `STAFF`, and `TECHNICIAN` accounts are listed, and no customer appears under any filter, search term, or page.

**AC-AP12-02** — Given I set the role filter to **Technician**,
When the list refreshes,
Then the request carries `role=TECHNICIAN`, only technicians are listed, the pagination footer reports counts for technicians only, and the URL records the filter.

**AC-AP12-03** — Given I type a search term that matches no internal user,
When the result renders,
Then the term is matched against the loaded roster 350ms after my last keystroke without issuing a request, and the empty result names my filters and offers **Clear filters** rather than claiming no users exist.

**AC-AP12-04** — Given I open **Add user** and enter a unique username, a unique valid mobile, a name, an 8-character password, and the Technician role,
When I save,
Then the account is created as `ACTIVE`, a toast names the new user and their role, the list refreshes to include them, and they are not signed in anywhere.

**AC-AP12-05** — Given I am in the create dialog,
When I submit with a password shorter than 8 characters or with any required field empty,
Then submission is blocked with field-level messages and no request is sent.

**AC-AP12-06** — Given a user named `priyas` already exists,
When I create another user with the username `priyas`,
Then the server's `409` is presented as a message on the Username field, the dialog stays open, and every value I entered is preserved.

**AC-AP12-07** — Given I open **Edit** on a user,
When the dialog renders,
Then it is prefilled from the row already loaded in the list, the username is shown but not editable, and saving issues `PATCH /users/{id}` with name, mobile, role, and the `version` carried by that row.

**AC-AP12-08** — Given another admin changed a user after my edit dialog opened,
When I save,
Then I see "This record changed since you opened it" with **Reload**, and my stale write is not applied.

**AC-AP12-09** — Given an active user,
When I open the Disable confirmation,
Then it states that the user is signed out immediately and cannot sign in until re-enabled; and after confirming, `POST /users/{id}/deactivate` is called, their status chip reads Disabled, and the Enable action replaces Disable in their row menu.

**AC-AP12-10** — Given exactly one active administrator exists,
When I attempt to disable, delete, or demote that account,
Then the action is not applied, the dialog explains that at least one active administrator must remain and that another user must be made an admin first, and the confirm button is disabled so an identical retry is not offered.

**AC-AP12-11** — Given I am viewing my own row,
When I open its action menu and its edit dialog,
Then **Delete** is disabled with a tooltip explaining I cannot delete my own account, the **Role** select is disabled with a tooltip explaining I cannot change my own role, and the Disable confirmation warns that I will be signed out immediately.

**AC-AP12-12** — Given I am resetting a user's password,
When I submit fewer than 8 characters,
Then it is blocked with a field-level message and no request is sent; and after a successful reset the password appears in no toast, URL, log, or telemetry event, and reopening the dialog shows an empty field.

**AC-AP12-13** — Given I open the Delete confirmation for a user,
When it renders,
Then it names the user, states that access is revoked immediately, and states that their history remains attributable to them; and after confirming, a toast names them and their row leaves the list.

**AC-AP12-14** — Given a user whose name is long enough to overflow its column next to the role badge,
When the row renders at any supported viewport width,
Then the name truncates to a single line with an ellipsis and a tooltip carrying the full value — it never wraps one character per line.

**AC-AP12-15** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/staff`,
Then I see the forbidden panel with a link to the dashboard, and no account data is displayed.

**AC-AP12-16** — Given a user list request returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** that refetches the current view, and the `correlationId` — never a raw exception string, and never a partially-populated roster presented as complete.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Fetches every account and filters out `CUSTOMER` client-side after the response | Fetches per internal role with server-side `role=` filtering (element #8) | The unfiltered call carries the whole customer base to render a roster of a dozen people, and it puts customer accounts one rendering bug away from a screen that can delete them ([Bugs.md](../../Bugs.md) #11). |
| No role filter, status filter, search, or pagination | All four, with state in the URL | The roster is the admin's working set; finding one technician among many should not require scrolling. Only the role filter is served by the API today; the rest run over the fetched array (§10 item 1). |
| Enable/disable is an always-live `Switch` in the row | Explicit Enable/Disable menu items with a confirmation | Ending someone's session is not a toggle-sized action, and a switch offers no room to explain that sessions end immediately. |
| Errors surface as `Error: <exception>` in a snackbar | Field-bound `422`s, dialog-level `409`s, page-level error panel with `correlationId` | [README §6.2](../README.md); "last active admin" arriving as a raw exception string is unreadable. |
| No self-action guards in the UI | Delete and Role pre-emptively disabled on the current user's own row, with tooltips | The server already refuses these; letting an admin open a dialog, fill it in, and then be refused wastes the attempt and teaches nothing. |
| No indication of which account is your own | "(you)" suffix on the current user's row | Makes the disabled actions self-explanatory. |
| Create dialog offers Staff / Technician / Admin | Unchanged | Correct as shipped — creating a `CUSTOMER` here would add a row this page then hides. |
| Role shown as a coloured chip containing the raw enum (`TECHNICIAN`) | Role badge with a sentence-case label (Technician) | [Design-System §5.3](../Design-System.md) and §11; raw enums are not user copy. |

## 10. Open questions

1. **`GET /users` accepts a single `role` param and nothing else.** Verified against the deployed controller: there is no `q`, no `status`, and no `page`, `size`, or `sort`, and the response is a plain JSON array rather than the paged envelope described in [API.md §4](../../API.md). Two consequences follow. First, the search box (element #6), the status filter (element #5), and the pagination footer (element #17) are all **client-side over the fetched array**, so the entire roster for the selected role crosses the wire on every role change and the totals in the footer are computed locally. Second, listing all three internal roles still needs three parallel calls — but that is cheaper than previously assumed, since each returns an unpaged array and there are no page boundaries to reconcile between them. Preferred fix: add `q`, `status`, `page`, `size`, and `sort` to `GET /users`, plus a repeated or comma-separated `role` (or an `excludeRole=CUSTOMER`) so the default view can be served by one server-paged call. **Blocking for genuine server-side search, filtering, and pagination**; the page is workable on a shop-sized roster until then, and degrades as the internal headcount grows.
2. **There is no `GET /users/{id}`.** Verified: the user-management controller exposes list, create, update, activate, deactivate, reset-password, and delete, and no user-detail read. The edit dialog (§5.2) therefore cannot load a fresh record before saving, and — the substantive problem — cannot obtain a fresh `version`. It sends the `version` carried by the list row, which is stale the moment another admin edits that user, so a legitimate edit can be refused with a `409` that only a full list reload clears. It is also unconfirmed that the list item carries `version` at all: `PATCH /users/{id}` takes one ([API.md §8](../../API.md)) and `users` has the column ([Database.md §3.1](../../Database.md)), but neither the list item shape nor the `PATCH` response is documented field-by-field, and if `version` is absent from the list then [README §6.3](../README.md)'s "never send a guessed version" rule cannot be satisfied at all. Resolve by adding `GET /users/{id}` (preferred — it also gives the dialog a fresh read) or by having `PATCH /users/{id}` tolerate a version-less update. **Blocking for §5.2.**
3. **API.md documents the wrong verb for editing a user.** [API.md §8](../../API.md) specifies `PUT /users/{id}`; the deployed controller implements `PATCH /users/{id}`, matching [Bugs.md](../../Bugs.md) #4. `PATCH` is verified correct and is what this spec now uses throughout — a `PUT` would return `405` ([Bugs.md](../../Bugs.md) #10). Nothing is blocked; this is a **documentation defect in API.md**, recorded in [API-Contract-Drift.md](../API-Contract-Drift.md). The same document also names the account-status endpoints `/enable` and `/disable`, where the backend implements `/activate` and `/deactivate`.
4. **Do duplicate `409`s populate `fieldErrors`?** [API.md §3](../../API.md) puts `fieldErrors[]` on the error envelope and §8 returns `409` for a duplicate username or mobile, but does not say whether the conflict identifies which field collided. Without it the dialog can only show a generic message and highlight both fields. Preferred: populate `fieldErrors` on duplicate conflicts, as `422` does.
5. **Is there a distinct `errorCode` for a self-action refusal?** `LAST_ADMIN` is documented for the last-admin guard; the "cannot delete yourself" rule is described in [Bugs.md](../../Bugs.md) #4 as already enforced server-side but appears in neither [API.md §8](../../API.md) nor [features/staff-management.md](../../features/staff-management.md) as a standalone rule — that spec's Business Rule 9 only blocks self-disable/delete when the admin is the *last* active admin. Confirm whether self-delete is unconditionally blocked and, if so, document its `errorCode` and status so the panel can distinguish it from `LAST_ADMIN`.
6. **Is `mustChangePassword` set by an admin reset?** `GET /me` returns `mustChangePassword` ([API.md §7](../../API.md)), and both the mobile create and reset flows tell the admin "user must change password on next login". Nothing in [API.md §8](../../API.md) says `POST /users` or `POST /users/{id}/reset-password` sets that flag. Confirm before repeating that promise in the panel's toast copy — the current dialogs deliberately say only "share this with them directly".
7. **Sorting.** The deployed `GET /users` honors no `sort` param (item 1), so column sorting is not offered in V1 and the server's ordering is unspecified. Rows are presented in the order the array arrives, merged role by role in **All internal** mode. Server-side sorting needs the same API change as item 1; confirm whether it is wanted.
8. **Should the roster show a technician's current workload?** Deciding whether to disable a technician is much easier when you can see they have four open jobs. No endpoint provides a per-user job count, so it is omitted rather than approximated. Recorded in case it is wanted later.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/admin/presentation/staff_screen.dart`, the deployed `UserManagementController`, [API.md §7–8](../../API.md), [Database.md §3.1, §4](../../Database.md), [features/staff-management.md](../../features/staff-management.md), and [Bugs.md](../../Bugs.md) #4, #10, #11, #17.*
