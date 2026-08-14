# AP-13 · Device Categories (Catalog)

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-13 |
| **Route** | `/catalog/categories` (`?includeArchived=true`, `page`, `size`, `sort` in query string) |
| **Roles** | `ADMIN` only |
| **Entry points** | Sidebar → Catalog → Device Categories · Dashboard "Device Categories" card |
| **Primary endpoints** | `GET /device-categories/all` · `POST /device-categories` · `PUT /device-categories/{id}` · `POST /device-categories/{id}/archive` · `POST /device-categories/{id}/unarchive` |
| **Archetype** | List page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-14 Brands](AP-14-brands.md) · [Database.md §3.2](../../Database.md) · [API.md §10](../../API.md) |

## 2. Purpose

The catalog of device types the shop accepts for repair. Categories drive the customer's request form, so this page controls what customers can choose — and, via **brand mode**, whether each category offers a curated brand list or free-text brand entry (PROD-004). Categories are never hard-deleted: archiving hides one from new requests while every historical request and job keeps its reference intact, and the page's copy has to make that distinction obvious so admins don't avoid archiving out of fear.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│ Device Categories                              [ + Add category ]  │  page header
│ 8 active · 2 archived                                              │
├────────────────────────────────────────────────────────────────────┤
│ [ 🔍 Search categories ]        [ ☐ Show archived ]                │  filter bar
├────────────────────────────────────────────────────────────────────┤
│ Name        │ Description   │ Brand mode │ Brands │ Status │  ⋮     │  data table
│ Mobile Phone│ Phones & …    │ Configured │ 12     │ Active │  ⋮     │
│ Tablet      │ —             │ Free text  │ —      │ Active │  ⋮     │
│ Camera      │ …             │ Free text  │ 3 ⚠    │Archived│  ⋮     │
├────────────────────────────────────────────────────────────────────┤
│ Page 1 of 1 · 10 categories        [20 ▾]     ‹ Prev   Next ›       │
└────────────────────────────────────────────────────────────────────┘
```

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "Device Categories" | Static. |
| 2 | Result summary | Text, `body-small` | Active and archived counts | "8 active · 2 archived". |
| 3 | Add category | Primary button | — | Opens the create dialog (§5). The page's single primary action. |
| 4 | Search | Debounced text input (350ms) | Category name | Client-side filter over the loaded page if the endpoint has no `q` param (see §10). |
| 5 | Show archived | Checkbox | — | Off by default. Off ⇒ show only `archived = false`; on ⇒ show all, with archived rows visually de-emphasized. State reflected in the URL. |
| 6 | Column: Name | Table cell, `title-medium` | `name` | Archived rows render the name in `text-secondary` with a strikethrough. |
| 7 | Column: Description | Table cell | `description` | Truncated to one line with a tooltip; "—" when absent. |
| 8 | Column: Brand mode | Chip | `brandMode` | **Configured** (primary chip) or **Free text** (neutral chip). Tooltip explains what the customer sees for each. |
| 9 | Column: Brands | Table cell, link | Count of active brands for the category | Links to [AP-14](AP-14-brands.md). Renders "—" for `FREE_TEXT` categories with no brands. Shows a ⚠ marker when brands exist but `brandMode = FREE_TEXT` (curated brands invisible to customers — see §6). |
| 10 | Column: Status | Status chip | `archived` | **Active** or **Archived**. |
| 11 | Manage brands | Row menu item + Brands-cell link | — | Navigates to `/catalog/categories/{id}/brands`. Offered for both modes, since brands can be curated before switching a category to `CONFIGURED`. |
| 12 | Row action menu | Overflow (⋮) menu | Edit · Manage brands · Archive **or** Restore | Archive shown for active rows, Restore for archived rows — never both. |
| 13 | Row click | — | — | Opens the **Edit** dialog. (Categories have no detail page; there's nothing to show beyond their editable fields and brands.) |
| 14 | Pagination footer | Data-table footer | `page`, `totalPages`, `totalElements` | 20/50/100 page sizes. |
| 15 | Empty / filtered-empty / error / forbidden | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Create / Edit category dialog

Same dialog for both, differing only in title ("Add category" / "Edit category"), prefill, and endpoint.

| Field | Type | Rules |
| --- | --- | --- |
| **Name** * | Text | Required, max 100 chars. Must be unique among **active** categories — a `409` binds to this field as "A category named 'Tablet' already exists." |
| **Description** | Textarea, 2 rows | Optional, max 500 chars, with a character counter near the limit. |
| **Brand mode** * | Radio group, 2 options | **Free text (default)** — "Customers pick 'Others' or type their own brand." · **Configured list** — "Customers pick from brands you curate, plus 'Others'." Defaults to Free text on create; prefilled on edit. |
| *(hidden)* `version` | — | Round-tripped on edit for optimistic concurrency; a `409` offers **Reload**. |

- Actions: **Cancel** · **Save** (busy while in flight, disabled while pristine or invalid).
- Choosing **Configured list** shows an inline hint: "You'll need to add at least one brand for customers to choose from." When saving an edit that switches a category to Configured while it has **zero active brands**, the success toast includes a **Manage brands** action.
- Endpoints: `POST /device-categories` with `{ name, description?, brandMode }` · `PUT /device-categories/{id}` with `{ name?, description?, brandMode?, version }`.

### 5.2 Archive dialog

| Aspect | Content |
| --- | --- |
| Title | "Archive 'Mobile Phone'?" |
| Body | "Customers will no longer be able to choose this category for new requests. **Existing requests and jobs keep it** and are not affected. You can restore it at any time." |
| Actions | **Cancel** · **Archive** (error-colored) |
| Endpoint | `POST /device-categories/{id}/archive` |
| Result | Toast "Mobile Phone archived" with an **Undo** action that calls unarchive. Row moves out of the default view (or de-emphasizes if Show archived is on). |

The word "delete" is deliberately avoided everywhere on this page — nothing here is deleted, and calling it deletion misrepresents the soft-delete behavior the backend actually implements ([Database.md §4](../../Database.md)).

### 5.3 Restore

Archived rows offer **Restore** → `POST /device-categories/{id}/unarchive`, no confirmation needed (non-destructive). If restoring collides with a now-active category of the same name, the `409` surfaces as "A different active category is already named 'Tablet'. Rename it first."

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | 8 skeleton rows. |
| **Empty** (none at all) | Devices icon, "No device categories yet", "Categories are what customers choose from when submitting a repair request." Primary action **Add category**. |
| **Filtered-empty** | "No categories match 'came'", **Clear search**. |
| **Empty-active-only** | Categories exist but all are archived: "All categories are archived — customers can't submit requests." with **Show archived** pre-offered. This is a genuine operational alarm, not an empty list. |
| **Error / Forbidden** | Standard panels; forbidden text: "Catalog management is limited to administrators." |
| **Brand-mode mismatch warning** | A category with `brandMode = FREE_TEXT` **and** ≥1 active brand shows a ⚠ in the Brands cell, tooltip: "These brands aren't shown to customers because this category uses free-text entry." Surfaces a silent misconfiguration that is otherwise invisible until a customer complains. |

## 7. Permissions & visibility

- `ADMIN` only, for the route and the sidebar item; a `403` renders the forbidden panel.
- No record-state gating beyond archive/restore mutual exclusivity.
- Read access to categories is broad server-side (`GET /device-categories` is open to any authenticated user, since the request form needs it), but this **management** page and every mutation are admin-only.

## 8. Acceptance criteria

**AC-AP13-01** — Given I open `/catalog/categories`,
When the list loads,
Then only active categories are listed by default, and the summary reports both active and archived counts.

**AC-AP13-02** — Given I tick **Show archived**,
When the list refreshes,
Then archived categories also appear, visually de-emphasized with a strikethrough name and an Archived status chip, and the URL records the choice.

**AC-AP13-03** — Given I open **Add category**,
When I submit with an empty name,
Then submission is blocked with a field-level message and no request is sent.

**AC-AP13-04** — Given an active category named "Tablet" exists,
When I create another category named "tablet",
Then the server's `409` is shown as a field-level message on the Name field, the dialog stays open, and my input is preserved.

**AC-AP13-05** — Given I create a category without choosing a brand mode,
When it is saved,
Then it is created with brand mode **Free text**, matching the backend default.

**AC-AP13-06** — Given a category using **Free text**,
When I edit it to **Configured list** and save,
Then the change persists, the row's Brand mode chip reads "Configured", and — if it has no active brands — the success toast offers a **Manage brands** action.

**AC-AP13-07** — Given a category with `brandMode = FREE_TEXT` that has active brands,
When its row renders,
Then a warning marker explains that those brands are not visible to customers.

**AC-AP13-08** — Given I archive a category,
When the confirmation dialog is shown,
Then it states that existing requests and jobs are unaffected and that the category can be restored, and it does **not** use the word "delete".

**AC-AP13-09** — Given I have archived a category,
When the success toast appears,
Then it offers **Undo**, and choosing it restores the category.

**AC-AP13-10** — Given an archived category,
When I open its row action menu,
Then **Restore** is offered and **Archive** is absent.

**AC-AP13-11** — Given another admin edited a category after my list loaded,
When I save my edit of that category,
Then I see "This record changed since you opened it" with **Reload**, and my stale write is not applied.

**AC-AP13-12** — Given I click a row,
When the edit dialog opens,
Then its fields are prefilled with that category's current name, description, and brand mode.

**AC-AP13-13** — Given I follow the Brands link or the **Manage brands** menu item,
When navigation completes,
Then I am on `/catalog/categories/{id}/brands` for that category, with the category name shown in the page header and breadcrumb.

**AC-AP13-14** — Given I am signed in as `STAFF` or `TECHNICIAN`,
When I navigate directly to `/catalog/categories`,
Then I see the forbidden panel and no catalog data.

**AC-AP13-15** — Given every category in the catalog is archived,
When the list renders,
Then I see the "All categories are archived — customers can't submit requests" state rather than a generic empty state.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Cards with an inline "Manage Brands" button and two trailing icons | Table with a single overflow menu per row | Consistent row-action pattern; the mobile card needed a prominent button because its icon was undiscoverable ([Bugs.md](../../Bugs.md) #15) — a table's named menu items solve that structurally. |
| Archive action labeled "Delete" with a "Restore" counterpart | Labeled **Archive** / **Restore** throughout | The operation is a soft archive; naming it "Delete" misdescribes it and makes admins hesitant. |
| No brand count visible | Brands column with count + mismatch warning | Brand mode and brands are two halves of one setting; seeing them apart is what lets a misconfiguration hide. |
| Brand-mode warning only appears inside the Brands screen | Warning also surfaces on this list | The admin should notice the misconfiguration without drilling in. |
| No archived/active filter (always shows all) | Active-only by default with a Show archived toggle | Day-to-day work concerns active categories; archived ones are historical. |
| No undo after archiving | Undo action on the success toast | Cheap safety for a reversible operation. |

## 10. Open questions

1. **Brand counts.** `GET /device-categories/all` returns no brand count, so populating the Brands column needs either (a) a per-category `GET .../brands` call per row (N+1 — unacceptable), (b) an added `activeBrandCount` field on the category response (**preferred**, small change), or (c) dropping the column. **Blocking for elements #9 and the §6 mismatch warning.**
2. **Search support.** No `q` param on `GET /device-categories/all`. Client-side filtering over the loaded page is acceptable at the expected catalog size (tens of rows) but breaks once paginated; confirm the expected catalog size or add `q`.
3. **Pagination.** Is the catalog small enough to always fit one page? If yes, keep the footer but expect a single page; if it may grow, confirm the endpoint honors `page`/`size`/`sort`.
4. **Brand mode change with existing requests.** Switching a category to `CONFIGURED` doesn't retroactively validate the free-text brands on historical requests (by design — they're snapshots). Confirm no warning is expected for that case.
5. **`brand_mode` has no DB `CHECK` constraint** ([Database.md §3.2](../../Database.md) note). Not a UI issue, but if an invalid value ever reaches the client, the Brand mode chip must degrade gracefully rather than break the row — confirm the fallback ("Unknown" chip).

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/admin/presentation/categories_screen.dart`, [API.md §10](../../API.md), and [Database.md §3.2](../../Database.md).*
