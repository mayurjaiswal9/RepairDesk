# RepairDesk — Bugs & Action Items Tracker

Tracks reported bugs, their root cause, fix, and status. Update this file whenever a bug is triaged, fixed, or verified.

**Status legend:** `Open` → `In Progress` → `Fixed (pending verify)` → `Verified` | `Won't Fix` | `Deferred`

---

## Summary

| # | Title | Priority | Status |
|---|-------|----------|--------|
| 1 | Product edit/delete missing in Admin | High | Fixed (pending verify) |
| 2/3 | Customer's info response ("Info Provided") not visible to Admin | High | Fixed (pending verify) |
| 4 | Admin cannot reset password / edit / delete accounts | High | Fixed (pending verify) |
| 5 | Working Days label/value misaligned in Business Settings | Medium | Fixed (pending verify) |
| 6 | Customers page throws exception after long load | High | Fixed (pending verify) |
| 7 | Job Details page missing Job Creation Date | Low | Fixed (pending verify) |
| 8 | Technician job notes not saved/shown in Admin; verify photos | Medium | Fixed (pending verify) |
| 9 | `GET /customers` 500s when search box is empty (default screen state) | High | Verified (fixed in prod) |
| 10 | Wrong HTTP method on a valid path returns 500 instead of 405 | Medium | Verified (fixed in prod) |
| 11 | Staff Management lists customers alongside staff | Medium | Fixed (pending verify) |
| 12 | Admin can't reset password / delete a customer from Customers screen | High | Fixed (pending verify) |
| 13 | Customer history's request/job rows aren't clickable | Medium | Fixed (pending verify) |
| 14 | Working Hours day names off by one (real root cause of bug 5) | High | Fixed (pending verify) |
| 15 | "Add brand from category" existed but was hard to find | Low | Fixed (pending verify) |
| 16 | Photo upload has no "take picture" option; customer can't see own photos | Medium | Fixed (pending verify) |
| 17 | Staff Management name text wraps vertically letter-by-letter | Medium | Fixed (pending verify) |
| 18 | Technician can open Customers | High | Fixed (pending verify) — API OK for TECHNICIAN; UI shows role |
| 19 | Opening customer-uploaded images throws AppException | High | Fixed — root cause ephemeral disk; Azure Files mounted |
| 20 | Technician sees jobs not assigned to them | High | Fixed (pending verify) — API returns only assigned jobs for TECHNICIAN |
| 21 | Staff Management display names truncated to a few characters | Medium | Fixed (pending verify) |
| 22 | Jobs overview shows contact person instead of customer name | Medium | Fixed (pending verify) |

---

## 1. Product edit/delete missing in Admin

- **Report:** After adding a product (device category) through Admin, there is no way to edit or delete it.
- **Root cause:** `CategoriesScreen` (`frontend/lib/features/admin/presentation/categories_screen.dart`) only exposed **Create** and **Archive/Unarchive** — the backend already had a working `PUT /device-categories/{id}` update endpoint, but the Flutter repository never called it and the screen had no edit UI. Hard delete isn't offered by design: categories referenced by historical requests/jobs can't be hard-deleted without breaking those records, so "delete" is a soft-archive.
- **Fix:**
  - Added `updateCategory()` to `AdminRepository` (calls the existing `PUT` endpoint).
  - Added an **Edit** icon per category that opens a pre-filled dialog (shared with the create dialog) and saves via `PUT`.
  - Relabeled the archive action as **Delete** (with a confirmation dialog explaining it's hidden from new requests but kept for history) / **Restore** when already archived.
- **Files:** `frontend/lib/features/admin/data/admin_repository.dart`, `frontend/lib/features/admin/presentation/categories_screen.dart`
- **Status:** Fixed (pending verify) — needs on-device confirmation.

## 2 & 3. Customer's info response not visible to Admin ("Info Requested"/"Info Provided" missing)

These two reports turned out to share one root cause, confirmed by tracing the full path from the customer's submission to the admin screen — there was no actual casing bug in the code; the "one is caps, one is small letters" symptom was really the *absence* of the "Info Provided" data next to the (working) "Info Requested" text, which reads like a casing/rendering mismatch at a glance.

- **Root cause:** The customer's response text is correctly saved to `repair_requests.customer_info_response` (`RepairRequest.customerInfoResponse`, written by `TrackingService.provideInfo`), but the field was **never serialized** by `RepairRequestResponse` (the DTO backing `GET /requests/{id}`), so it never reached the Flutter `RequestDetail` model or the Admin Request Detail UI. Only the *original* "Information requested" message (staff → customer) was ever shown; the customer's reply had nowhere to render.
- **Fix:**
  - Backend: added `customerInfoResponse` to `RepairRequestResponse` and its `from()` mapping.
  - Frontend: added `customerInfoResponse` to the `RequestDetail` model and rendered a new **"Information provided by customer"** alert card (green, reply icon) directly under the existing "Information requested from customer" card — so staff now see both sides of the exchange together.
- **Files:** `backend/.../dto/RepairRequestResponse.java`, `frontend/lib/features/requests/data/requests_repository.dart`, `frontend/lib/features/requests/presentation/request_detail_screen.dart`
- **Status:** Fixed (pending verify) — backend compiles and full test suite passes; needs on-device confirmation with a real info-request → customer-response round trip.

## 4. Admin cannot reset password / edit / delete accounts

- **Report:** Admin should be able to reset a user's password, modify their data, and delete their account.
- **Root cause:** Backend endpoints already existed (`PATCH /users/{id}`, `POST /users/{id}/reset-password`, `DELETE /users/{id}` — with business rules like "can't delete/demote the last active admin" and "can't delete self" already enforced server-side) but the **Staff Management** screen (`frontend/lib/features/admin/presentation/staff_screen.dart`) only wired up **Create** and an activate/deactivate **Switch**; Edit, Reset Password, and Delete were never added to the UI or the `ProfileRepository`.
- **Fix:**
  - Added `updateUser()`, `resetUserPassword()`, and `deleteUser()` to `ProfileRepository`.
  - Added a per-member overflow menu (⋮) next to the existing Switch with **Edit** (name/mobile/role dialog), **Reset Password** (new password dialog), and **Delete** (confirmation dialog) — backend validation errors (e.g. "last active admin") surface via the existing snackbar-error pattern.
- **Files:** `frontend/lib/features/account/data/profile_repository.dart`, `frontend/lib/features/admin/presentation/staff_screen.dart`
- **Status:** Fixed (pending verify) — needs on-device confirmation.

---

## 5. Working Days label/value alignment not proper in Business Settings

- **Report:** In Business Settings, the "Working Days" label and its value are misaligned.
- **Root cause:** `_WorkingHourRow` (Business Settings → Working Hours) used a `SizedBox(width: 100)` label + an unconstrained value (`Text`/`Chip`) directly in a `Row`, with no `Expanded`/shared column width — inconsistent with the `_InfoRow` pattern used just above it in the same screen (`width: 130` + `Expanded`), so the two sections' columns didn't line up and the value could sit awkwardly next to the label.
- **Fix:** Rewrote `_WorkingHourRow` to match the `_InfoRow` pattern — `SizedBox(width: 130)` for the day label (same width as the Scheduling section above) and the value (time range or "Closed" chip) wrapped in `Expanded` + left-aligned, with explicit `crossAxisAlignment: CrossAxisAlignment.center` so the chip and plain-text rows have consistent vertical alignment.
- **Files:** `frontend/lib/features/admin/presentation/settings_screen.dart`
- **Status:** Superseded by **bug 14** — the spacing fix here was real but incomplete. The actual "misalignment" the user kept seeing (an unlabeled row, days one-off) was a separate day-name mapping bug, only found and fixed later.

## 6. Customers page fails with exception (slow load then crash)

- **Report:** Admin's Customers page takes a long time to load and then fails with an exception page.
- **Root cause:** Two issues:
  1. `CustomerController.history()` had no `@Transactional`, and the app runs with `spring.jpa.open-in-view: false`. `RepairRequestSummary.from()` / `JobSummary.from()` access LAZY `customer`/`deviceCategory` associations *after* the repository call's transaction already closed, so opening **any** customer with at least one request/job threw a `LazyInitializationException` → generic 500 → the Flutter screen rendered the raw `AppException(...).toString()` as an "exception page".
  2. The search box fired a new `/customers?q=...` request on **every keystroke** (no debounce), which made the list feel like it was "loading for a long time" under real network latency.
- **Fix:**
  - Backend: added `@Transactional(readOnly = true)` to `CustomerController.history()` so the lazy associations resolve within the same transaction.
  - Frontend: added a 350ms debounce to the customer search field so it only queries once typing pauses.
  - Frontend: error views now show `AppException.userMessage` (e.g. "Request timed out. Please try again.") instead of the raw `toString()` dump, for any error that does occur.
- **Files:** `backend/.../feature/customer/controller/CustomerController.java`, `frontend/lib/features/customer_management/presentation/customers_screen.dart`
- **Status:** Fixed (pending verify) — backend compiles and full test suite (44 tests) passes; needs on-device confirmation opening a customer with repair history.

## 7. Job Details page missing Job Creation Date

- **Report:** Job Details page should show when the job was created, not just last-updated.
- **Root cause:** `Job` (via `BaseEntity`) already tracks `createdAt`, but `JobResponse` never serialized it, so the Flutter `JobDetail` model and Job Detail screen had no way to display it.
- **Fix:** Added `createdAt` to `JobResponse` (+ `from()` mapping), to the Flutter `JobDetail` model/`fromJson`, and rendered a "Created <date/time>" line under the reference number/status header on the Job Detail screen.
- **Files:** `backend/.../feature/job/dto/JobResponse.java`, `frontend/lib/features/jobs/data/jobs_repository.dart`, `frontend/lib/features/jobs/presentation/job_detail_screen.dart`
- **Status:** Fixed (pending verify) — backend compiles and full test suite passes; needs on-device confirmation.

## 8. Technician job notes not saved/shown in Admin; verify photos

- **Report:** Notes a technician enters on the Job Details page aren't saved or don't show up in the Admin view. Double-check photo attachments too.
- **Root cause:** Admin and Technician actually share the **same** Job Detail screen and the **same** `internalNotes` field end-to-end (`POST /jobs/{id}/notes` → `job.internalNotes` → `GET /jobs/{id}`), so notes do persist and are visible to both roles once the screen is refreshed. Two real gaps found on inspection:
  1. `JobService.appendNote()` never bumped `job.updatedAt` (every other job mutation — assign, start, complete, cancel — does), so a note-only change didn't move the job's "last updated" timestamp, making it easy to miss that anything changed.
  2. The "Customer Photos" section on the Job Detail screen (`ownerType: 'REPAIR_REQUEST'`) calls `GET /requests/{id}/attachments`, which was `@Secured` for `ROLE_CUSTOMER`/`ROLE_STAFF`/`ROLE_ADMIN` only — **not** `ROLE_TECHNICIAN` — so a technician opening their assigned job's detail page got a silent 403 on that section while Admin saw the photos fine. Job-level attachments (`ownerType: 'JOB'`) were already correctly shared between both roles.
- **Fix:**
  - `JobService.appendNote()` now sets `updatedAt` like the other job mutations.
  - `AttachmentController.listForRequest()` now also allows `ROLE_TECHNICIAN`, so technicians can see the customer's original request photos on the Job Detail screen.
- **Files:** `backend/.../feature/job/service/JobService.java`, `backend/.../feature/attachment/controller/AttachmentController.java`
- **Status:** Fixed (pending verify) — backend compiles and full test suite passes; needs on-device confirmation with a technician account viewing an assigned job's photos and notes.

---

## 9. `GET /customers` throws 500 when the search box is empty

- **Report:** Found via post-deploy API smoke testing (not user-reported) — the Customers screen's default state (empty search) sends `GET /customers` with **no** `q` param at all.
- **Root cause:** `CustomerController.search()` forwarded a genuinely `null` `q` straight to `UserRepository.searchCustomers`, whose JPQL uses `:q` inside `concat('%', :q, '%')`. With a `null` bind value, Hibernate/the PostgreSQL driver can't infer a concrete parameter type for `:q` in that expression and defaults it to `bytea`, so `lower(u.name)` — a `varchar` — blew up with `org.postgresql.util.PSQLException: function lower(bytea) does not exist`, surfacing to the client as a generic 500. This meant **every load of the Admin Customers screen with an empty search box failed in the deployed environment** (confirmed live before the fix went out).
- **Fix:** `CustomerController.search()` now normalizes `null` → `""` before calling the repository. An empty string still matches everything via the existing `LIKE '%%'` pattern, so behavior for real searches is unchanged.
- **Files:** `backend/.../feature/customer/controller/CustomerController.java`
- **Tests:** New `CustomerControllerTest` (3 tests: missing/blank/populated `q`) — verifies the repository is always called with a non-null string.
- **Status:** Verified — reproduced live on the deployed API (`curl` returned 500), shipped fix as image `1.1.1-dev`, re-tested the same request live and confirmed 200.

## 10. Wrong HTTP method on a known path returns a misleading 500

- **Report:** Found while cleaning up smoke-test data — calling `DELETE` on `/device-categories/{id}` (which only supports `POST .../archive`) returned a generic 500 "unexpected error" instead of a proper "method not allowed".
- **Root cause:** `GlobalExceptionHandler` only had a catch-all `@ExceptionHandler(Exception.class)` alongside specific handlers for validation/not-found/conflict — `HttpRequestMethodNotSupportedException` (thrown by Spring MVC for a valid path + wrong verb) fell through to the generic handler, which logs it as an "unhandled exception" and returns `INTERNAL_ERROR`/500. This affects **any** endpoint in the API called with the wrong HTTP method, not just device categories — a client bug that hits the wrong verb gets a confusing 500 instead of an actionable 405.
- **Fix:** Added a dedicated `HttpRequestMethodNotSupportedException` → `405 METHOD_NOT_ALLOWED` handler (and `NoResourceFoundException` → `404 NOT_FOUND` for unmatched routes, same category of gap) to `GlobalExceptionHandler`, plus a new `METHOD_NOT_ALLOWED` `ErrorCode`.
- **Files:** `backend/.../common/error/GlobalExceptionHandler.java`, `backend/.../common/error/ErrorCode.java`
- **Tests:** New `GlobalExceptionHandlerTest` (2 tests).
- **Status:** Verified — reproduced live (`DELETE` → 500), shipped fix as image `1.1.2-dev`, re-tested the same request live and confirmed 405.

---

## 11. Staff Management lists customers alongside staff

- **Report:** Staff Management screen should not show customers.
- **Root cause:** `GET /users` (no `role` filter) intentionally returns every account so admins can browse everyone — `StaffScreen` called it with no filter and rendered whatever came back, including `CUSTOMER` accounts (the UI already special-cased `role != 'CUSTOMER'` for the Switch/menu, an unused hint that customers weren't supposed to be there).
- **Fix:** `StaffScreen`'s provider now filters the result to non-customer roles before rendering. Kept as a client-side filter (not a backend change) since `/users?role=X` only supports one role at a time and other screens may still want the unfiltered list.
- **Files:** `frontend/lib/features/admin/presentation/staff_screen.dart`

## 12. Admin can't reset password / delete a customer from the Customers screen

- **Report:** Admin should be able to reset a customer's password and delete their account from the customer search page.
- **Root cause:** `_CustomerCard` in `CustomersScreen` only ever showed a read-only active/inactive icon — no menu was wired up, even though the backend `/users/{id}/reset-password` and `DELETE /users/{id}` endpoints (added for bug 4) work for any role, including customers.
- **Fix:** Added a per-customer overflow menu (⋮) with **Reset Password** and **Delete**, reusing the existing `ProfileRepository` methods; errors now render via the same `_friendlyError` helper as the rest of the screen.
- **Files:** `frontend/lib/features/customer_management/presentation/customers_screen.dart`

## 13. Customer history's request/job rows aren't clickable

- **Report:** In Admin → Customer Details, tapping a request or job should open that request/job's detail page.
- **Root cause:** `_RepairRow` (used for both the Requests and Jobs lists on `CustomerHistoryScreen`) was a plain, non-interactive `ListTile` — it rendered the reference number/status but had no `onTap`, even though both `RepairRequestSummary` and `JobSummary` already include an `id`.
- **Fix:** `_RepairRow` now takes a `_RecordType` (request/job) alongside the record and navigates to the existing `RequestDetailScreen` / `JobDetailScreen` on tap, using the record's `id`.
- **Files:** `frontend/lib/features/customer_management/presentation/customers_screen.dart`

## 14. Working Hours day names off by one (the real root cause of bug 5)

- **Report:** Business Settings' Working Hours still looked wrong after bug 5's fix (screenshot showed an unlabeled row above "Monday", and "Saturday" showing what looked like Sunday's closed state).
- **Root cause:** The backend's `dayOfWeek` is `0=Monday .. 6=Sunday` (`WorkingHours.java`). The frontend's `WorkingHoursConfig.dayNames` lookup table had a stray leading `''` entry (`['', 'Monday', ..., 'Saturday', 'Sunday']`, 8 entries), shifting every day's label by one: Monday (0) rendered with a **blank** label, Tuesday (1) rendered labeled "Monday", ..., Sunday (6) rendered labeled "Saturday". This is what looked like a spacing/alignment bug in the screenshot but was actually every day showing the wrong name. A second, independent copy of the same off-by-one existed in `SettingsEditScreen`'s "no config yet" fallback (`dayOfWeek: i + 1`, `dayNames[i + 1]`), which would have started throwing a `RangeError` once `dayNames` was fixed to 7 entries.
- **Fix:** Removed the stray leading `''` from `dayNames` (now exactly 7 entries, 0-indexed Monday→Sunday, matching the backend). Fixed `SettingsEditScreen`'s fallback to generate `dayOfWeek: i` / `dayNames[i]` (0-indexed) instead of `i + 1`.
- **Files:** `frontend/lib/features/admin/data/admin_repository.dart`, `frontend/lib/features/admin/presentation/settings_edit_screen.dart`

## 15. "Add brand from category" existed but was hard to find

- **Report:** Admin should be able to add brands from a Device Category and have them saved to the DB.
- **Root cause:** This was already fully implemented and working end-to-end (`BrandsScreen`, `DeviceBrandService`/`DeviceBrandController` on the backend — verified live via API smoke test, see bug 9/10 investigation) but the only entry point was a small, unlabeled tag icon buried among three icons in the category card's trailing row — easy to miss entirely.
- **Fix:** Replaced the icon with a full-width, clearly labeled **"Manage Brands"** button directly under each category's mode line, so it's obvious rather than hidden.
- **Files:** `frontend/lib/features/admin/presentation/categories_screen.dart`

## 16. Photo upload has no "take picture" option; customer can't see their own photos

- **Report:** Upload photo should let you take a picture, not just pick from gallery, across all pages — and saved pictures should be visible on job/request detail pages (looked missing in some places).
- **Root cause:** Two issues:
  1. All three photo-upload entry points (`AttachmentsWidget`, `new_request_screen.dart`, and the customer's `repair_detail_screen.dart` respond panel) called `ImagePicker().pickImage(source: ImageSource.gallery)` directly — gallery only, no camera choice. The Android manifest also had no `CAMERA` permission declared, so even a direct code path to the camera would have failed at runtime.
  2. Staff/Admin/Technician-facing screens (Job Detail, Request Detail, Request Queue) already display uploaded photos correctly via `AttachmentsWidget`, but the **customer's own** "Repair Detail" screen (`repair_detail_screen.dart`) never rendered `AttachmentsWidget` at all — it let the customer add a photo but never showed what had already been uploaded (at submission time or since), so from the customer's side, photos were "missing" here specifically.
- **Fix:**
  - Added `android.permission.CAMERA` (+ optional `android.hardware.camera` feature) to `AndroidManifest.xml`.
  - Added a shared `pickImageWithSourceChoice()` helper (bottom sheet: "Take Photo" / "Choose from Gallery") and switched all three upload entry points to use it.
  - Added a "Photos" section (read `AttachmentsWidget`, upload-enabled) to the customer's Repair Detail screen so previously-invisible photos are now shown; made the widget's provider public so the respond panel's separate upload path can invalidate it after a successful upload.
- **Files:** `frontend/android/app/src/main/AndroidManifest.xml`, `frontend/lib/shared/utils/image_source_picker.dart` (new), `frontend/lib/features/attachments/presentation/attachments_widget.dart`, `frontend/lib/features/requests/presentation/new_request_screen.dart`, `frontend/lib/features/customer_home/presentation/repair_detail_screen.dart`

## 17. Staff Management name text wraps vertically letter-by-letter

- **Report:** Names in Staff Management aren't properly aligned — they render as one character per line, stacked vertically (screenshot).
- **Root cause:** The `ListTile.title` `Row` (`Expanded(Text(name)) + role-chip Container`) had no `maxLines`/`overflow` on the name `Text`. On a real device, once the role chip ate enough width (e.g. "TECHNICIAN"), the `Expanded` name got squeezed to a very narrow column; since `Text` wraps by default and a name like "Gangadhar" has no spaces to break on, Flutter wrapped it one or two characters per line instead of truncating.
- **Fix:** Added `maxLines: 1, overflow: TextOverflow.ellipsis` to the name `Text` (and defensively to the `@username` subtitle line, plus the equivalent name `Text` in the Customers screen's card, which had the same latent gap).
- **Files:** `frontend/lib/features/admin/presentation/staff_screen.dart`, `frontend/lib/features/customer_management/presentation/customers_screen.dart`
- **Follow-up:** See bug 21 — ellipsis alone left names nearly unreadable on phone layouts.

## 18. Technician can open Customers

- **Report:** Technician login should not see Customers; hide based on role.
- **Root cause:** Customers tile lived on the Staff/Admin dashboard without an explicit role guard (technicians already use a separate landing, but deep navigation / future layout changes could still expose the screen). Spec (`customer-management.md`) denies technicians access.
- **Fix:** Guard the Customers quick-action to `ADMIN`/`STAFF` only; `CustomersScreen` shows a permission message for `TECHNICIAN`/`CUSTOMER`.
- **Files:** `frontend/lib/features/dashboard/presentation/dashboard_screen.dart`, `frontend/lib/features/customer_management/presentation/customers_screen.dart`

## 19. Opening customer-uploaded images throws AppException

- **Report:** Technician/Admin unable to open uploaded customer images — App Exception.
- **Root cause:** Download path `GET /attachments/{storageKey}` could truncate keys that include a file extension; the Flutter client also rendered raw `AppException.toString()` and failed to parse JSON error bodies when `ResponseType.bytes` was used.
- **Fix:** Backend mapping uses `{storageKey:.+}`; Flutter encodes the key, clears JSON content-type on binary GET, parses byte error bodies, and shows `userMessage` with Retry in the image viewer.
- **Files:** `backend/.../attachment/controller/AttachmentController.java`, `frontend/lib/features/attachments/data/attachments_repository.dart`, `frontend/lib/features/attachments/presentation/attachments_widget.dart`, `frontend/lib/core/error/app_exception.dart`

## 20. Technician sees jobs not assigned to them

- **Report:** Technician should only see jobs assigned to them (any status).
- **Root cause:** Backend already scopes `GET /jobs` for `TECHNICIAN` via `listForTechnician`, but the Flutter list never passed `technicianId` and had no client-side guard if an older API returned the full board.
- **Fix:** Jobs list passes `technicianId` for technicians, titles the screen "My Jobs", filters client-side when `assignedTechnicianId` is present, and hides Assign Technician for the technician role. `JobSummary` now includes `assignedTechnicianId`.
- **Files:** `frontend/lib/features/jobs/presentation/jobs_list_screen.dart`, `frontend/lib/features/jobs/data/jobs_repository.dart`, `frontend/lib/features/jobs/presentation/job_detail_screen.dart`, `backend/.../job/dto/JobSummary.java`

## 21. Staff Management display names truncated to a few characters

- **Report:** Screenshot shows names like "Ad…", "Ma…", "G…" with large empty space before the toggle.
- **Root cause:** Follow-on of bug 17 — `ListTile` title row still shared width with the role chip while trailing held Switch + menu, so the name `Expanded` received only a few characters of width.
- **Fix:** Replaced `ListTile` with a custom `Row`: full-width name on its own line; role chip + `@username` below; Switch/menu outside the text column.
- **Files:** `frontend/lib/features/admin/presentation/staff_screen.dart`

## 22. Jobs overview shows contact person instead of customer name

- **Report:** Job ending in 05 showed "Singh" (contact person) instead of the customer account name.
- **Root cause:** `_JobCard` rendered `job.contactName`; API/`JobSummary` had no `customerName` field (only `customerUsername`).
- **Fix:** Added `customerName` to backend `JobSummary`/`JobResponse` and Flutter models; job cards (and job detail) show customer display name, keeping contact as a separate field on detail.
- **Files:** `backend/.../job/dto/JobSummary.java`, `backend/.../job/dto/JobResponse.java`, `frontend/lib/features/jobs/data/jobs_repository.dart`, `frontend/lib/features/jobs/presentation/jobs_list_screen.dart`, `frontend/lib/features/jobs/presentation/job_detail_screen.dart`

---

*Last updated: 2026-08-11 — bugs 18–22 fixed pending on-device verification (mobile + small backend DTO/path fixes).*
