# RepairDesk Admin Panel — Design System

**Applies to:** the Angular Admin Panel (`admin-web/`)
**Companion to:** [README.md](README.md) (IA, routes, cross-cutting rules) and the page specs in [`pages/`](pages/)

> This is the **shared vocabulary** for the page specs. The admin panel uses a **warm modern** visual language (orange CTAs, cream surfaces, soft elevation). Components here are described by **behavior and content**, not implementation — a page spec saying "status chip" or "data table" means exactly what's defined below.

---

## 1. Brand & color tokens

Warm modern palette for `admin-web/` (orange primary on cream). **Do not invent new hues**; if a page needs a color that isn't here, add it to this file first.

| Token | Value | Use |
| --- | --- | --- |
| `--color-primary` | `#FF6B35` | Primary actions, active nav item, links, focus rings, accent highlights. |
| `--color-primary-dark` | `#E85A28` | Hover/pressed state of primary, emphasized sidebar marks. |
| `--color-primary-light` | `#FF8F66` | Selected-row tint, subtle highlights. |
| `--color-primary-soft` | `#FFF0EA` | Soft info banners, chip fills, icon wells. |
| `--color-secondary` | `#2BB673` | "Needs info" / positive secondary emphasis, success-adjacent. |
| `--color-error` | `#E53935` | Destructive actions, error states, rejected/cancelled status. |
| `--color-success` | `#2BB673` | Completed/approved status, success toasts. |
| `--color-warning` | `#F5A623` | Pending review, awaiting parts, unscheduled warnings. |
| `--color-surface` | `#FFFFFF` | Cards, tables, dialogs, top bar. |
| `--color-background` | `#FFF7F2` | App background behind cards (warm cream). |
| `--color-background-mid` | `#FFE8DC` | Gradient wash / hero atmosphere. |
| `--color-on-primary` | `#FFFFFF` | Text/icons on primary fills. |
| `--color-text-primary` | `#1A1A1A` | Body and heading text. |
| `--color-text-secondary` | `#666666` | Labels, metadata, helper text, placeholder. |
| `--color-divider` | `#EADFD8` | Borders, table rules, dividers. |

**Semantic pairs.** Status colors are always used as a **tinted background + darker text/icon + a text label**, never color alone (accessibility, §8).

---

## 2. Typography

Inherits the mobile scale, extended with the larger sizes a desktop layout needs.

| Role | Size / weight | Use |
| --- | --- | --- |
| `display` | 28px / 600 | Page titles (`<h1>`), one per page. |
| `title-large` | 20px / 600 | Section headings, dialog titles. |
| `title-medium` | 16px / 600 | Card headers, table column groups, key-value keys in emphasis. |
| `body-large` | 16px / 400 | Primary body copy, form input text. |
| `body-medium` | 14px / 400 | Table cells, most UI text (default). |
| `body-small` | 12px / 400, `text-secondary` | Metadata, timestamps, helper text, `correlationId`. |
| `label-large` | 14px / 600 | Buttons, tabs, chips, field labels. |
| `mono` | 13px monospace | Reference numbers, IDs, `correlationId`. |

Line length in prose areas (issue descriptions, notes, resolution summaries) is capped around 90 characters so long text stays readable on wide monitors.

---

## 3. Spacing, radius, elevation

| Token | Value | Token | Value |
| --- | --- | --- | --- |
| `--space-xs` | 4px | `--radius-s` | 8px |
| `--space-s` | 8px | `--radius-m` | 16px |
| `--space-m` | 16px | `--radius-l` | 24px |
| `--space-l` | 24px | `--radius-pill` | 999px (buttons, chips, badges) |
| `--space-xl` | 32px | | |

**Typography face:** Plus Jakarta Sans (400–700) for UI chrome and headings.

**Elevation:** soft diffused shadows on cards (`--shadow-card`) and primary CTAs (`--shadow-soft`). Dense tables may still use a 1px `--color-divider` border; overlays (dialogs, menus, toasts) cast a stronger shadow.

**Density:** table rows are 48px tall (comfortable) with a user-togglable 36px compact mode remembered per browser. Form controls are 40px tall, buttons 40px (36px for in-row/secondary actions).

---

## 4. Layout

### Shell geometry

| Region | Size |
| --- | --- |
| Sidebar (expanded) | 240px fixed |
| Sidebar (collapsed rail) | 64px, icons only with tooltips |
| Top bar | 56px, sticky |
| Content max width | 1440px, centered, `--space-l` gutters |
| Content padding | `--space-l` desktop, `--space-m` below 1024px |

### Page archetypes

Only three, so pages feel predictable:

1. **List page** — page header (title + count + primary action) → filter bar → data table → pagination. Used by Requests, Jobs, Customers, Staff, Categories, Brands.
2. **Detail page** — breadcrumb → record header (reference, status, key metadata, action bar) → two columns: main content left (~2/3), contextual panels right (~1/3, e.g. technician/appointment, attachments, notes). Collapses to one column below 1024px.
3. **Form page** — breadcrumb → grouped field sections in a single column (max 640px) → sticky footer with **Save** / **Cancel**. Used by Settings and Schedule Appointment.

---

## 5. Shared components

### 5.1 Data table

The backbone of every list page.

- **Server-driven**: sorting, paging, and filtering all round-trip to the API (`page`, `size`, `sort`, plus per-page filters). Filter/sort/page state is reflected in the URL query string so views are shareable and survive refresh and browser back.
- **Row click** opens the record's detail page. Row-level actions live in a trailing overflow (⋮) menu; the whole row is never a link *and* a set of buttons competing for the same click.
- **Sticky header**, horizontal scroll only as a last resort — columns beyond the essential set collapse into a secondary line within the primary cell instead.
- **Pagination footer**: `page N of M`, total record count, page-size selector (20/50/100), prev/next.
- **Selection** is only present on pages that specify a bulk action; no decorative checkboxes.
- **States**: skeleton rows while loading (same row height/count as the last page), plus the empty / filtered-empty / error / forbidden states in §6.

### 5.2 Status chip

Fixed, shared mapping — a status must look identical everywhere it appears.

| Domain | Value | Label | Color token |
| --- | --- | --- | --- |
| Request | `PENDING_REVIEW` | Pending review | `warning` |
| Request | `INFORMATION_REQUIRED` | Needs info | `secondary` |
| Request | `APPROVED` | Approved | `success` |
| Request | `REJECTED` | Rejected | `error` |
| Request | `CANCELLED` | Cancelled | `text-secondary` |
| Job | `CREATED` | New | `primary-light` |
| Job | `ASSIGNED` | Assigned | `primary` |
| Job | `SCHEDULED` | Scheduled | `primary` |
| Job | `IN_PROGRESS` | In progress | `secondary` |
| Job | `AWAITING_PARTS` | Awaiting parts | `warning` |
| Job | `COMPLETED` | Completed | `success` |
| Job | `CANCELLED` | Cancelled | `text-secondary` |
| Appointment | `SCHEDULED` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED` | Same words | `primary` / `secondary` / `success` / `text-secondary` |
| Account | `ACTIVE` / `DISABLED` / `DELETED` | Active / Disabled / Deleted | `success` / `warning` / `error` |
| Priority | `LOW` / `NORMAL` / `HIGH` / `URGENT` | Same words | `text-secondary` / none shown / `warning` / `error` |
| Warranty | `IN_WARRANTY` / `OUT_OF_WARRANTY` / `UNKNOWN` | In warranty / Out of warranty / Unknown | `success` / `text-secondary` / `text-secondary` |
| Archive | `archived = true` | Archived | `text-secondary` |

`NORMAL` priority renders **nothing** — badging the default just adds noise (the mobile app already does this correctly).

### 5.3 Role badge

`ADMIN` (error red), `STAFF` (primary), `TECHNICIAN` (secondary), `CUSTOMER` (text-secondary). Always the word, never just a color.

### 5.4 Key–value block

Used across all detail pages. Label in `body-small`/`text-secondary` above or left of the value in `body-medium`. On desktop, a fixed 160px label column keeps values aligned in a column; missing optional values render an em-dash (`—`), never a blank gap or the string "null".

> The mobile app shipped two label-alignment bugs and a day-name off-by-one here ([Bugs.md](../Bugs.md) #5, #14). The fixed label column and a single shared component are the structural fix.

### 5.5 Dialog

Centered modal, 480px default (640px when it contains a multi-line text area), title in `title-large`, body, then right-aligned actions with the confirming action last. Focus traps on open, returns to the trigger on close, `Esc` cancels, the confirming button shows a busy spinner and disables while in flight. Destructive confirmations use an error-colored confirm button and name the record explicitly.

### 5.6 Toast

Bottom-right, auto-dismiss 5s (success/info) or sticky with a close button (error). Always names the record. Never used for `403`/`404` — those are page states.

### 5.7 Attachment gallery & viewer

- Thumbnail grid, each tile showing the image, filename, and size; an **Add photo** tile when uploads are permitted.
- Bytes are fetched through the authenticated HTTP layer (never a bare `<img src>`, see [README §6.6](README.md)) and rendered from an object URL that is revoked on destroy.
- Clicking a tile opens a full-screen viewer with zoom/pan, filename in the header, keyboard arrows to move between images, and `Esc` to close.
- Upload accepts image types only, shows per-file progress, and surfaces the server's magic-byte rejection message verbatim-but-friendly. Delete requires confirmation and is only offered where the page spec allows it.

### 5.8 Filter bar

A row of controls above a table: segmented status filter (with an **All** option), a debounced search input (**350ms**, matching the fix in [Bugs.md](../Bugs.md) #6), and any page-specific dropdowns. Active filters appear as removable chips with a **Clear all** action, and the whole filter state lives in the URL.

### 5.9 Page header

Breadcrumb (on detail/nested pages only) → `<h1>` page title → optional result count / record summary → right-aligned primary action. One primary action per page, maximum.

### 5.10 Empty / error / forbidden panels

Centered in the content area: icon, one-line heading, one sentence of explanation, and at most one action (**Retry**, **Clear filters**, the page's primary action, or **Back to dashboard**). Error panels additionally show the `correlationId` in `body-small`.

---

## 6. The five canonical states

Every page and every async region implements all five. Page specs list only their page-specific extras.

| State | Trigger | Presentation |
| --- | --- | --- |
| **Loading** | First load or refetch | Skeletons shaped like the real content. Refetches keep the old content visible with a subtle progress indicator instead of blanking the page. |
| **Empty** | Request succeeded, zero records, no filters active | Icon + "No X yet" + the primary action that creates one. |
| **Filtered-empty** | Zero records **with** filters/search active | Names the query/filter, offers **Clear filters**. Must never claim no records exist. |
| **Error** | Non-2xx (other than 403/404) | Message per [README §6.2](README.md), **Retry**, `correlationId`. |
| **Forbidden / Not found** | 403 / 404 | Page-level panel explaining the situation with a link to a page the user can access. |

---

## 7. Responsive behavior

| Breakpoint | Layout |
| --- | --- |
| ≥1440px | Full shell; detail pages two-column; tables show all columns. |
| 1024–1439px | Sidebar expanded; detail pages two-column with a narrower right panel; least-important table columns fold into their primary cell. |
| 768–1023px | Sidebar collapses to a 64px icon rail (toggleable); detail pages single-column with right-panel content moved below; tables drop to essential columns. |
| <768px | Sidebar becomes an overlay drawer; tables become stacked cards. **Not a primary target** — admins on phones should use the Flutter app; the panel stays usable but is not optimized here. |

---

## 8. Accessibility baseline

- **Keyboard**: every action reachable and operable; logical tab order; visible 2px `--color-primary` focus ring; skip-to-content link; `Esc` closes overlays.
- **Screen readers**: one `<h1>` per page, ordered headings, real `<label>`s tied to inputs, `<table>` semantics with `<th scope>` for data tables, `aria-live="polite"` for toasts and async result counts, `aria-busy` while loading.
- **Color**: never the sole carrier of meaning — every status chip and validation state includes text or an icon. AA contrast (4.5:1 text, 3:1 large text and UI boundaries).
- **Motion**: honor `prefers-reduced-motion`; no essential information conveyed only through animation.
- **Zoom**: usable at 200% zoom without loss of function.

---

## 9. Forms & validation

- **Labels** are always visible (no placeholder-only fields). Required fields are marked with `*` and the form explains the marker once.
- **Validation timing**: on blur for individual fields, on submit for the whole form. Never validate a field the user hasn't touched yet.
- **Server is the authority.** Client rules mirror the server's (e.g. password ≥8 characters) purely to save a round trip; `422 fieldErrors[]` are bound back to the matching controls by field name, and any unmatched field error is shown in a form-level summary so nothing is silently swallowed.
- **Dirty state**: navigating away from a dirty form prompts for confirmation. Save buttons are disabled while pristine or in flight.
- **Concurrency**: forms carry the `version` they loaded; a `409` shows "This record changed since you opened it" with **Reload**.
- **Passwords** are entered in a masked field with a show/hide toggle and never pre-filled, echoed back, or logged.

---

## 10. Date, time & number formatting

| Kind | Format | Example |
| --- | --- | --- |
| Date | `d MMM yyyy` | 6 Aug 2026 |
| Date + time | `d MMM yyyy, h:mm a` | 6 Aug 2026, 2:30 pm |
| Time only | `h:mm a` | 9:00 am |
| Relative (lists/notifications) | Relative under 7 days, absolute after, with the absolute value in a tooltip | "2 hours ago" |
| Weekday index | **`0 = Monday … 6 = Sunday`** | — |
| File size | Rounded with unit | 1.4 MB |

> ⚠️ The weekday convention is a **known trap**: the backend uses `0 = Monday`, and assuming `0 = Sunday` (or prepending a blank entry to a day-name array) is exactly what caused [Bugs.md](../Bugs.md) #14. Working-hours UI must be built against `0 = Monday` and covered by a test asserting Monday maps to index 0.

Timestamps arrive as UTC ISO-8601 and render in the **browser's local timezone**. Any screen showing shop working hours alongside timestamps must make clear which is which, since the shop's hours are local business hours.

---

## 11. Copy & tone

Plain, specific, no jargon. Name the record in confirmations and toasts. Describe consequences honestly — an "archive" says *hidden from new work, kept on existing records*, because that's what soft delete does. Errors say what happened and what to do next, never "an unexpected error occurred" alone if a specific cause is known. Sentence case for labels and buttons; buttons are verbs ("Add brand", not "New").

---

*Last updated: 2026-08-06 — initial version, tokens inherited from `frontend/lib/shared/theme/app_theme.dart`.*
