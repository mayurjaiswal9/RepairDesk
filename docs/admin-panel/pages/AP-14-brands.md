# AP-14 · Brands (per Device Category)

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-14 |
| **Route** | `/catalog/categories/:id/brands` (`?includeArchived=true`, `q` in query string) |
| **Roles** | `ADMIN` only |
| **Entry points** | [AP-13](AP-13-device-categories.md) → Brands cell link · [AP-13](AP-13-device-categories.md) → row menu → Manage brands · **Manage brands** action on the AP-13 success toast after switching a category to Configured |
| **Primary endpoints** | `GET /device-categories/{categoryId}/brands/all` · `POST /device-categories/{categoryId}/brands` · `PUT /device-categories/{categoryId}/brands/{brandId}` · `POST /device-categories/{categoryId}/brands/{brandId}/archive` · `POST /device-categories/{categoryId}/brands/{brandId}/unarchive` · `GET /device-categories/all` (to resolve the parent category) · `PUT /device-categories/{id}` (inline brand-mode switch, §5.4) |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-13 Device Categories](AP-13-device-categories.md) · [API.md §10a](../../API.md) · [Database.md §3.2a](../../Database.md) |

## 2. Purpose

The curated brand list for **one** device category (PROD-004). When a category's brand mode is `CONFIGURED`, these rows are exactly what the customer sees in the request form's brand picker, so this page is the shop's control over how device brands are recorded. It is deliberately scoped to a single category — brand names are unique per category, not globally, and "Samsung" under Mobile Phone is a different row from "Samsung" under Tablet. Like categories, brands are never hard-deleted: archiving removes a brand from the customer's picker while every existing request keeps the free-text brand string it was submitted with ([Database.md §3.2a](../../Database.md)). The page also has to make one thing unmissable: a curated list is invisible to customers while the parent category is still on `FREE_TEXT`, which is a silent misconfiguration the admin cannot otherwise see from here.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Device Categories › Mobile Phone › Brands                          │  breadcrumb
│ Brands · Mobile Phone                             [ + Add brand ]  │  page header
│ 12 active · 3 archived                                             │
├────────────────────────────────────────────────────────────────────┤
│ ⚠ Mobile Phone uses free-text brand entry, so these brands are not │  brand-mode
│   shown to customers.                                              │  banner
│   [ Switch to configured list ]   [ Edit category ]                │
├────────────────────────────────────────────────────────────────────┤
│ [ 🔍 Search brands ]              [ ☐ Show archived ]              │  filter bar
├────────────────────────────────────────────────────────────────────┤
│ Brand name                        │ Status    │  ⋮                 │  data table
│ Apple                             │ Active    │  ⋮                 │
│ Samsung                           │ Active    │  ⋮                 │
│ Nokia                             │ Archived  │  ⋮                 │
├────────────────────────────────────────────────────────────────────┤
│ 15 brands · "Others" is always offered to customers automatically  │  footer note
└────────────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Breadcrumb | Breadcrumb | "Device Categories › {Category name} › Brands" | First segment links to `/catalog/categories`. The middle segment is the category name, not its id, and is plain text (there is no category detail page — AP-13 edits categories in a dialog). While the category name is still resolving, the segment renders a short skeleton rather than the raw UUID. |
| 2 | Page title | `<h1>` | "Brands · {Category name}" | The category name is always in the title, so a deep-linked or bookmarked URL is self-describing. |
| 3 | Result summary | Text, `body-small` | Active and archived counts derived from the response | "12 active · 3 archived". Hidden while loading. |
| 4 | Add brand | Primary button | — | Opens the create dialog (§5.1). The page's single primary action. |
| 5 | Brand-mode banner | Warning banner | Parent category's `brandMode` | Rendered **only** when `brandMode = FREE_TEXT`. Full behavior in §5.4 and §6. |
| 6 | Search | Debounced text input (350ms) | Brand name | Client-side filter over the loaded list — `GET .../brands/all` documents no `q` param ([API.md §10a](../../API.md)), and the endpoint returns the whole list in one response, so filtering locally is exact rather than partial. Reflected in the URL as `q`. |
| 7 | Show archived | Checkbox | — | Off by default. Off ⇒ only `archived = false` rows; on ⇒ all rows, archived ones de-emphasized. Filtering is client-side for the same reason as #6: the endpoint always returns both. State reflected in the URL. |
| 8 | Column: Brand name | Table cell, `title-medium` | `name` | Archived rows render in `text-secondary` with a strikethrough, matching AP-13's treatment. |
| 9 | Column: Status | Status chip | `archived` | **Active** or **Archived** ([Design-System §5.2](../Design-System.md)). |
| 10 | Row action menu | Overflow (⋮) menu | Edit · Archive **or** Restore | Archive appears for active rows, Restore for archived rows — never both. **Edit** is offered for active rows only; to rename an archived brand, restore it first (`PUT` on an archived brand is not documented as rejected, but renaming a row the customer cannot see is not a task worth surfacing). |
| 11 | Row click | — | — | Opens the **Edit** dialog for active rows; no-op for archived rows. Brands have no detail page — a brand is one editable field. |
| 12 | "Others" footer note | Text, `body-small` | Static | "Customers can always choose **Others** and type their own brand, whether or not this list is configured. Don't add an 'Others' row here." Permanent, not dismissible. See §5.5. |
| 13 | Empty / filtered-empty / error / forbidden / not-found | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

There is **no pagination footer**. `GET /device-categories/{categoryId}/brands/all` returns a plain array rather than the paged envelope, so the page loads the category's whole brand list at once. This is a deliberate, documented deviation from [README §6.4](../README.md); see §9 and the open question in §10 about the list size this remains safe at.

## 5. Interactions & dialogs

### 5.1 Add / Edit brand dialog

One dialog for both, differing only in title ("Add brand" / "Edit brand"), prefill, and endpoint.

| Field | Type | Rules |
| --- | --- | --- |
| **Brand name** * | Text, autofocused | Required, trimmed, max 100 chars ([Database.md §3.2a](../../Database.md)). Must be unique among **active** brands **within this category**, case-insensitively — see §5.2. |
| *(hidden)* `version` | — | Round-tripped on edit per [API.md §10a](../../API.md), which documents `PUT .../brands/{brandId}` as taking `{ name, version }`. Blocked today — see open question 1. |

- Actions: **Cancel** · **Save** (busy while in flight, disabled while pristine or invalid).
- Endpoints: `POST /device-categories/{categoryId}/brands` with `{ name }` · `PUT /device-categories/{categoryId}/brands/{brandId}` with `{ name, version }`.
- On success the dialog closes and a toast names the brand and its category: "Samsung added to Mobile Phone".
- After a successful **Add**, the dialog offers to stay open for the next entry ("Save and add another"), because brand lists are entered in runs of ten or twenty. This is a client-side convenience over the same single-create endpoint, not a bulk API — see open question 4.

### 5.2 Duplicate brand name (`409`)

Active brand names are unique **per category**, case-insensitively (`UNIQUE (device_category_id, lower(name)) WHERE archived = false`, [Database.md §3.2a](../../Database.md)). A `409 CONFLICT` from create or edit binds to the **Brand name** field as "Mobile Phone already has an active brand named 'Samsung'." — never a page-level error and never a toast. The dialog stays open with the typed value intact so the admin can correct it in place.

Because uniqueness is per category, the same name may legitimately exist under a different category. The message names the category for exactly that reason.

### 5.3 Archive and Restore

| Aspect | Content |
| --- | --- |
| Title | "Archive 'Samsung'?" |
| Body | "Customers will no longer see Samsung in the brand list for Mobile Phone. **Existing repair requests keep the brand they were submitted with** and are not affected. You can restore it at any time." |
| Actions | **Cancel** · **Archive** (error-colored) |
| Endpoint | `POST /device-categories/{categoryId}/brands/{brandId}/archive` |
| Result | Toast "Samsung archived" with an **Undo** action calling unarchive. The row leaves the default view, or de-emphasizes if Show archived is on. |

**Restore** (`POST .../brands/{brandId}/unarchive`) needs no confirmation — it is non-destructive. If restoring collides with an active brand of the same name, the `409` surfaces as "Mobile Phone already has an active brand named 'Samsung'. Rename or archive that one first."

The word **delete** is not used anywhere on this page. Nothing here is deleted: the row is retained, and the brand string already recorded on historical requests is a snapshot that archiving does not touch ([Database.md §3.2a](../../Database.md), [§4](../../Database.md)).

### 5.4 Brand-mode banner and the inline switch

Shown only when the parent category's `brandMode = FREE_TEXT`:

> ⚠ **Mobile Phone uses free-text brand entry.** The brands below are not shown to customers — they type their own brand instead. Switch this category to a configured list to start using them.
> **[ Switch to configured list ]  [ Edit category ]**

- **Switch to configured list** performs the change without leaving the page: `PUT /device-categories/{id}` with `{ brandMode: "CONFIGURED", version }` ([API.md §10](../../API.md)). This is the right call — the admin is already looking at the list they want to publish, and bouncing them back to AP-13 to reopen a dialog is the exact friction that let this misconfiguration persist. It is offered **only** when the category has at least one active brand; with zero active brands it is replaced by disabled state and the hint "Add at least one brand first — customers would otherwise have nothing to choose from."
- Confirmation dialog: "Switch Mobile Phone to a configured brand list? Customers submitting a Mobile Phone request will pick from your 12 active brands, plus **Others**." Confirm → success toast "Mobile Phone now uses a configured brand list", banner disappears, no navigation.
- **Edit category** is the fallback path: it navigates to `/catalog/categories` and opens that category's edit dialog ([AP-13 §5.1](AP-13-device-categories.md)). It stays even when the inline switch is available, because brand mode is one field among several an admin may want to change.
- The inline switch depends on the category's `version`, which the category payload does not currently expose — see open question 2. Until that is resolved the button is not shippable and the banner falls back to **Edit category** alone; the banner and its warning text ship regardless.

### 5.5 "Others" is not a row

"Others" is an **always-on client-side sentinel** on the customer's request form, not a record in `device_brands` ([API.md §10a](../../API.md), [Database.md §3.2a](../../Database.md)). It is offered to customers whatever the category's brand mode is and whatever this list contains, and choosing it lets the customer free-type a brand that is stored as the same plain `deviceBrand` string as a curated pick ([API.md §12.1](../../API.md)).

The page therefore states this permanently (element #12) instead of leaving admins to infer it. If an admin types "Others", "Other", or "Others / Custom" into the Add brand dialog, the client blocks submission with the field message "Customers are always offered **Others** automatically — you don't need to add it." This is a client-side guard only; the server accepts the name, so it is a UX safeguard rather than a rule the panel can enforce.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Breadcrumb and page title render as soon as the category name resolves; 6 skeleton rows in the table. |
| **Empty** (no brands at all) | Tag icon, "No brands yet for Mobile Phone", "Add the brands customers should be able to choose from for this category." Primary action **Add brand**. |
| **Filtered-empty** | "No brands match 'sam'", **Clear search**. Never claims the category has no brands. |
| **Empty-active-only** | Brands exist but all are archived: "All brands for Mobile Phone are archived." If the category is `CONFIGURED`, this escalates to a warning — customers currently see only **Others** — with **Show archived** pre-offered so a brand can be restored. |
| **Free-text mode** | The §5.4 banner, above the filter bar, on every render while `brandMode = FREE_TEXT`. It is a warning, not an error: the page stays fully usable, because curating a list before flipping the mode is a supported workflow ([API.md §10a](../../API.md)). |
| **Error** | Standard error panel with the mapped message, **Retry**, and the `correlationId` ([README §6.2](../README.md)). |
| **Not found** | A `404` from `GET .../brands/all` means the category id in the URL doesn't exist: "That device category no longer exists", with a link back to `/catalog/categories`. |
| **Forbidden** | "Catalog management is limited to administrators", with a link to `/dashboard`. |
| **Archived parent category** | If the resolved category has `archived = true`, an informational note above the table: "Mobile Phone is archived, so customers can't submit requests for it at all. Restoring the category will bring these brands back with it." Editing brands stays enabled — curating an archived category's list ahead of restoring it is legitimate. |

## 7. Permissions & visibility

- `ADMIN` only. The page is reachable only from AP-13, which is itself admin-only, and a direct URL from any other role renders the forbidden panel.
- Every endpoint on this page is admin-gated server-side except `GET /device-categories/{categoryId}/brands` (the customer-facing active list), which this page does not use. The `/all` variant, create, edit, archive, and unarchive are all `ADMIN` ([API.md §10a](../../API.md)).
- No record-state gating beyond archive/restore mutual exclusivity and the active-only **Edit** rule (element #10).
- The parent category's `brandMode` gates nothing on this page — brands can be curated in either mode. It only changes what the banner says.

## 8. Acceptance criteria

**AC-AP14-01** — Given I open `/catalog/categories/{id}/brands` directly from a bookmark,
When the page loads,
Then the breadcrumb reads "Device Categories › {Category name} › Brands", the page title includes the category name, and at no point is a raw UUID shown in place of the name.

**AC-AP14-02** — Given a category with active and archived brands,
When the list loads,
Then only active brands are listed, and the summary reports both the active and archived counts.

**AC-AP14-03** — Given I tick **Show archived**,
When the list refreshes,
Then archived brands also appear with a strikethrough name and an Archived status chip, and the URL records the choice.

**AC-AP14-04** — Given I open **Add brand**,
When I submit with an empty or whitespace-only name,
Then submission is blocked with a field-level message and no request is sent.

**AC-AP14-05** — Given the Mobile Phone category already has an active brand named "Samsung",
When I add "samsung" to Mobile Phone,
Then the server's `409` is shown as a field-level message on the Brand name field naming the category, the dialog stays open, and my input is preserved.

**AC-AP14-06** — Given the Mobile Phone category has an active brand named "Samsung",
When I add "Samsung" to the **Tablet** category,
Then it is created successfully, because active brand names are unique per category rather than globally.

**AC-AP14-07** — Given I archive a brand,
When the confirmation dialog is shown,
Then it states that existing repair requests keep the brand they were submitted with and that the brand can be restored, and it does **not** use the word "delete".

**AC-AP14-08** — Given I have archived a brand,
When the success toast appears,
Then it offers **Undo**, and choosing it restores the brand.

**AC-AP14-09** — Given an archived brand,
When I open its row action menu,
Then **Restore** is offered and **Archive** and **Edit** are absent.

**AC-AP14-10** — Given the parent category's `brandMode` is `FREE_TEXT`,
When the page renders,
Then a warning banner states that the brands below are not shown to customers until the category is switched to a configured list, and it offers a way to make that change.

**AC-AP14-11** — Given the parent category's `brandMode` is `FREE_TEXT` and it has at least one active brand,
When I use **Switch to configured list** and confirm,
Then the category is updated via `PUT /device-categories/{id}`, the banner disappears without a page navigation, and a toast confirms the new mode.

**AC-AP14-12** — Given the parent category's `brandMode` is `CONFIGURED`,
When the page renders,
Then no brand-mode banner is shown.

**AC-AP14-13** — Given I am adding a brand,
When I type "Others" (in any casing) as the brand name,
Then submission is blocked with a message explaining that customers are always offered **Others** automatically, and no "Others" row is ever created from this page.

**AC-AP14-14** — Given any state of the list, including empty,
When the page renders,
Then a persistent note explains that customers can always choose **Others** and type their own brand.

**AC-AP14-15** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/catalog/categories/{id}/brands`,
Then I see the forbidden panel and no brand data.

**AC-AP14-16** — Given the category id in the URL does not exist,
When the request returns `404`,
Then I see a not-found panel with a link back to Device Categories, and no raw error text or JSON.

**AC-AP14-17** — Given I search for a string that matches no brand,
When the empty result renders,
Then it names my query and offers **Clear search**, and it does not claim the category has no brands.

**AC-AP14-18** — Given I operate the page with the keyboard only,
When I move through the table and open a row action menu and then a dialog,
Then focus is visible throughout, the dialog traps focus and returns it to the triggering control on close, and `Esc` cancels without saving.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Cards with two trailing icon buttons (edit, delete/restore) | Table with a single overflow menu per row | The same row-action pattern as every other list page; the mobile app's unlabeled trailing icons were undiscoverable enough to be filed as a bug ([Bugs.md](../../Bugs.md) #15). |
| Archive action labeled "Delete" | Labeled **Archive** / **Restore** throughout | The operation sets `archived = true`; calling it deletion misdescribes it and makes admins avoid a safe, reversible action. |
| No search, no archived filter — the full list always renders including archived rows | Search plus an active-by-default **Show archived** toggle | Matches [AP-13](AP-13-device-categories.md), and long brand lists are unusable without a filter. |
| Free-text banner is informational only ("switch it in Edit") | Banner offers an inline mode switch and a direct route to the category editor | The admin is already looking at the list they want to publish; sending them elsewhere to finish the job is what lets the misconfiguration survive. |
| Errors rendered as a raw exception string via `e.toString()` in a snackbar | Standard error panel and field-bound `409` messages | Hard rule in [README §6.2](../README.md); the raw-exception failure mode is [Bugs.md](../../Bugs.md) #6. |
| No indication that "Others" is automatic | Permanent footer note plus a client-side guard against creating an "Others" row | The sentinel is invisible from this screen, and an admin who doesn't know about it will add a duplicate "Others" that then appears twice to customers. |
| Page shows the category name only in the app bar | Breadcrumb, page title, toasts, and conflict messages all name the category | The page is one of several identical-looking lists; the category has to be unambiguous from any of them. |
| No undo after archiving | Undo action on the success toast | Cheap safety for a reversible operation, consistent with [AP-13](AP-13-device-categories.md). |
| *(cross-cutting)* [README §6.4](../README.md) requires server-side pagination on list pages | No pagination; the whole list loads in one request | `GET .../brands/all` returns a plain array, not the paged envelope ([API.md §10a](../../API.md)). Documented rather than faked with client-side paging. See open question 3. |

## 10. Open questions

1. **Brand `version` is not returned.** [API.md §10a](../../API.md) documents `PUT .../brands/{brandId}` as taking `{ name, version }`, but the brand payload returned by `GET .../brands/all` carries no `version` field (verified against `backend/.../dto/DeviceBrandResponse.java`), so the edit dialog has nothing to round-trip and cannot satisfy [README §6.3](../README.md). Either add `version` to the brand response or confirm that brand edits are exempt from optimistic concurrency. **Blocking for the edit dialog's concurrency handling (§5.1).**
2. **Category `version` is not returned either.** The inline **Switch to configured list** action needs `{ brandMode, version }` for `PUT /device-categories/{id}` ([API.md §10](../../API.md)), and the category payload does not expose `version` (verified against `backend/.../dto/DeviceCategoryResponse.java`). Same fix as question 1. **Blocking for the inline switch (§5.4)** — the banner ships without it, falling back to **Edit category**.
3. **Resolving the category name.** There is no documented `GET /device-categories/{id}`. The page currently resolves the name by reading `GET /device-categories/all` (admin, includes archived) and matching on the route id, which is one extra full-catalog fetch per page load and breaks if that endpoint ever becomes paged. Preferred fix: add a single-category read endpoint. Interim alternative — passing the name through router state — is rejected because it breaks deep links and refresh. **Blocking for elements #1 and #2** if the catalog is ever paginated.
4. **Bulk add.** Brand lists are long, and entering thirty phone manufacturers one dialog at a time is the main cost of this page. [API.md §10a](../../API.md) documents no bulk-create endpoint and no array body on `POST .../brands`, so bulk add is **not specified** here. A paste-a-list-of-names dialog issuing sequential `POST`s was considered and rejected: partial failure across thirty calls has no clean recovery story. Confirm whether a bulk endpoint accepting `{ names: [...] }` with a per-name result is worth adding; until then §5.1's "Save and add another" is the mitigation.
5. **List size and pagination.** How many brands is a category expected to hold? If the answer is "tens", the unpaginated array is fine and the client-side search and archived filter are exact. If it can reach hundreds, `GET .../brands/all` needs the paged envelope plus a `q` param, and elements #6 and #7 become server-driven.
6. **Renaming an archived brand.** The panel hides **Edit** on archived rows (element #10). Confirm the server actually permits `PUT` on an archived brand — if it rejects it, this becomes an enforced rule rather than a UI choice; if it permits it, confirm nobody needs it.
7. **Restoring into a name collision.** `unarchive` re-checks active-name uniqueness and returns `409` (verified against `backend/.../service/DeviceBrandService.java`), which §5.3 relies on, but [API.md §10a](../../API.md) documents the uniqueness `409` only for create and edit. Confirm and document the unarchive case so the message in §5.3 is contractually safe.
8. **Brand mode with existing requests.** Switching a category to `CONFIGURED` from this page does not retroactively reconcile the free-text brands already recorded on historical requests — they are snapshots by design ([Database.md §3.2a](../../Database.md)). Confirm no warning or migration is expected, matching [AP-13](AP-13-device-categories.md) open question 4.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/admin/presentation/brands_screen.dart`, `frontend/lib/features/admin/data/admin_repository.dart`, [API.md §10/§10a](../../API.md), and [Database.md §3.2/§3.2a](../../Database.md).*
