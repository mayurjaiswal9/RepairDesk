# RepairDesk Multi-Tenant Migration — Task List

**Status:** Code is in the API, the portal, and the phone. V19 and V20 were applied to the dev database on 30 October 2026 with image `1.1.16-dev`, and `1.1.17-dev` is live. Only ACE Telecom exists on the dev database.  
**Date:** 19 October 2026  
**Model:** one database, one API, one portal codebase, one phone codebase. A company that pays for its own website gets another hostname only. It does not get another database.

**Task IDs:** `DB` database · `BE` API · `WEB` portal · `APP` phone · `OPS` Azure · `QA` proof that today’s ACE Telecom data still works.

Live Flyway is **V18** (`attendance_days`). The next free versions are **V19** and **V20**. The old location plan’s `V19__eta_push.sql` is not this work. Do not reuse V16, V17, or V18.

---

## 1. Keep the existing data as it is

V19 may add a `company_id` and fill it with ACE Telecom. It must not change any value that is already stored.

| Leave unchanged | What that covers |
| --- | --- |
| User rows | Same id, username, mobile, name, password hash, role, and status. `admin`, staff, technicians, and customers stay signed in to ACE Telecom when no company code is sent. |
| Catalog | Category and brand names, descriptions, and archived flags. |
| Requests and jobs | Same ids and reference numbers, including `RD-2026000005` and `RD-2026000006`. Addresses, pins, statuses, and assignments stay. |
| Appointments, notes, notifications, audit rows | Same text, times, and links. |
| Attendance days | Same technician, India date, start and end times, flags, and coordinates. |
| Last technician position | The `technician_presence` point stays. |
| Office | Infinity Mall, Andheri West: 19.1412127, 72.8309064, radius 150 m. Every location switch stays off, including Attendance. Auto-end stays 19:00. |
| Working hours | The seven existing rows stay. |
| Reference counter | `repair_reference_seq` is not reset. The next ACE number continues from the current value. |
| Photos | Blob paths stay. New uploads may use a company prefix later. Old files are not moved or renamed. |
| Map cache and nightly-run memory | `geocode_cache` and `scheduled_runs` get no `company_id`. |

Do not truncate, copy the database, or insert a second company in V19. Do not run `provision-dev.ps1`. Do not replace Container App environment variables. A later image update uses `az containerapp update --image` only.

Hibernate is `ddl-auto: none`. A new column the current API does not map is ignored, so V19 can be applied before the new API is deployed. Prove the current API still opens an ACE job before the code that reads `company_id` is deployed.

---

## 2. Order

Do these in order. Start the next phase only after the previous phase’s check has passed on the dev database.

| Phase | Tasks | Ships |
| --- | --- | --- |
| 1 | DB-1 | `V19__companies.sql` |
| 2 | BE-1, BE-2 | API that puts the company in the token and filters by it |
| 3 | DB-2, BE-3 | `V20__company_settings.sql`, then numbers and files |
| 4 | WEB-1, APP-1 | Optional company code. Blank still means ACE Telecom |
| 5 | DB-3, OPS-1 | A second company, only after section 10 passes |

---

## 3. Phase 1 — Stamp ACE Telecom

### DB-1 — `V19__companies.sql`

- [ ] Take a restorable backup of the dev database the same day. Record row counts for users, categories, brands, requests, jobs, appointments, attachments, notifications, audit events, presence, attendance days, and working hours.
- [ ] Create `companies`: `id` uuid, `code` unique lower case, `name`, `status` (`ACTIVE` or `DISABLED`), `created_at`.
- [ ] Insert one row: code `ace`, name `ACE Telecom`, status `ACTIVE`. Do not insert a second company.
- [ ] On each table below, add `company_id uuid` nullable, set every existing row to the ACE id, then set the column `NOT NULL` and add a foreign key to `companies(id)`.

| Table | Stamp it because |
| --- | --- |
| `users` | Every account belongs to one company |
| `device_categories` | The catalog is per company |
| `device_brands` | A brand follows its category’s company |
| `repair_requests` | Customer work |
| `jobs` | Job lists can stay inside the company without a join through the request |
| `appointments` | Visits follow the job’s company |
| `attachments` | Photos stay with the company that owns the request |
| `notifications` | The inbox is per company |
| `audit_events` | History is per company |
| `technician_presence` | The last point belongs to that technician’s company |
| `attendance_days` | A work day belongs to that technician’s company |
| `working_hours` | The office calendar is per company |
| `business_settings` | The existing id-1 row becomes ACE’s row |
| `location_settings` | The existing id-1 row becomes ACE’s row |

- [ ] Leave these constraints in place in V19. Changing them is DB-2 or DB-3.
  - `ux_users_username_active` and `ux_users_mobile_active` stay global.
  - `ux_device_categories_name_active` stays global.
  - `ux_repair_requests_reference` and `ux_jobs_reference` stay global.
  - `business_settings` and `location_settings` keep `CHECK (id = 1)`.
  - `working_hours` keeps `UNIQUE (day_of_week)`.
  - `ux_attendance_days_technician_date` stays `(technician_id, work_date)`.
- [ ] Do not add `company_id` to `geocode_cache` or `scheduled_runs`.
- [ ] Deploy no application change with this migration.

**Done when**

- [ ] The counts recorded before V19 match the counts after it.
- [ ] Spot-check: ACE users, both job reference numbers, both job pins, the office pin, radius 150, and all location switches still off.
- [ ] One attendance row, if any exists for the GPS technician on 19-10-2026, still shows the same start, end, and flags.
- [ ] `GET /api/v1/actuator/health` is 200 on the API image that is already running.
- [ ] An ACE admin can still open a job in the portal before BE-1 is deployed.

---

## 4. Phase 2 — The API learns the company

### BE-1 — Sign-in

Code: `AuthenticatedUser`, JWT issue and parse, login, `UserRepository.findActiveByUsername`.

- [ ] Add `companyId` to the signed-in user and to the token.
- [ ] Accept an optional `companyCode` on login.
- [ ] A missing or blank code looks the user up in company `ace` only.
- [ ] A present code looks `lower(username)` up inside that company.
- [ ] A wrong code returns the same “invalid username or password” as a wrong password.
- [ ] Tokens issued before this deploy have no `companyId`. Reject them. The user signs in again. No stored row changes.
- [ ] Ignore any `companyId` sent in a body or query. The token is the only source.

**Done when:** the current portal and the current APK, which send no company code, can sign in as admin, staff, technician, and customer, and the token contains the ACE company id.

### BE-2 — Every read and write stays in that company

- [ ] Pass `companyId` from `CurrentUser` into the repository methods for users, categories, brands, requests, jobs, appointments, attachments, notifications, presence, attendance, and the location board.
- [ ] A request for another company’s id returns 404.
- [ ] Office settings still load the ACE singleton until DB-2. Do not turn any location switch on.

**Done when:** a test that uses a thrown-away schema creates a second company, one job in each company, and shows that each token sees only its own job. The ACE path with no company code still returns the existing jobs. This second company is not created on the dev database.

---

## 5. Phase 3 — Settings, numbers, and files

### DB-2 — `V20__company_settings.sql`

Today both settings tables are singletons (`CHECK (id = 1)`), and `working_hours` is unique on `day_of_week` only. V19 has already stamped the ACE rows.

- [ ] Drop `CHECK (id = 1)` on `business_settings` and `location_settings` only after the ACE row is confirmed. Do not delete that row.
- [ ] Add `UNIQUE (company_id)` on each settings table.
- [ ] Drop `UNIQUE (day_of_week)` on `working_hours` and add `UNIQUE (company_id, day_of_week)`. The seven ACE rows stay.
- [ ] Add nullable `company_id` to `maps_api_usage`. Leave existing usage rows null. New ACE calls write the ACE id. Do not rewrite `call_count`.
- [ ] Load business settings, location settings, and working hours by the token’s company, not by id 1.
- [ ] A save of office location updates that company’s row only.

**Done when:** ACE still shows Infinity Mall, radius 150, switches off, and auto-end 19:00. A test company, in the test schema only, gets its own default row (switches off, no pin) and cannot overwrite ACE.

### BE-3 — Numbers and files

- [ ] Keep `repair_reference_seq` as the ACE counter. Do not renumber any `RD-` value.
- [ ] Add `company_sequences (company_id, year, last_value)` for companies created later. A new company starts at 1 for the current year and still formats `RD-YYYYNNNNNN`.
- [ ] Leave existing blob paths where they are. New uploads use `companies/{companyId}/...`. A download checks the token’s company against the attachment row before returning the file.
- [ ] Keep `geocode_cache` shared. Keep the platform daily cap (default 2000) and the per-user geocode cap of 30 per hour. Add a per-company daily cap for new usage rows.
- [ ] Keep roles `ADMIN`, `STAFF`, `TECHNICIAN`, and `CUSTOMER` as company roles.
- [ ] Add a platform account, separate from the ACE `admin` user, that can create a company and that company’s first admin. The ACE admin cannot create companies and cannot see another company’s rows.

**Done when:** the next ACE reference number is the current sequence plus one, and an existing photo still opens.

---

## 6. Phase 4 — Sign-in on the portal and the phone

### WEB-1 and APP-1

- [x] Add an optional **Company code** field above the username on the portal. Leave it blank for ACE Telecom. Send `companyCode` only when the field is not blank.
- [x] The phone app has no company code field. Each customer APK is built with its code: `flutter build apk --release --dart-define=COMPANY_CODE=ace`. The default is `ace`.
- [ ] A company that paid for its own hostname gets that code baked into the portal build, and the field is hidden.
- [ ] Use the same Angular build and the same Flutter codebase. A separate store listing is a new package id, and only when that has been paid for.

**Done when:** a blank company code on both current portals, and the ACE APK built with `COMPANY_CODE=ace`, sign in to ACE and open the existing jobs.

---

## 7. Phase 5 — A second company, after ACE is proven

### DB-3

Do not apply this in the same release as V19.

- [ ] Confirm there is no duplicate `(company_id, username)` and no duplicate `(company_id, mobile)` among rows that are not `DELETED`.
- [ ] Replace `ux_users_username_active` with unique `(company_id, lower(username))` where status is not `DELETED`.
- [ ] Replace `ux_users_mobile_active` with unique `(company_id, mobile)` where status is not `DELETED`.
- [ ] Replace the global reference unique indexes with unique `(company_id, reference_number)` on requests and on jobs. Do not update any reference number.
- [ ] Replace the global category-name unique index with unique `(company_id, lower(name))` where the category is not archived.

### OPS-1

- [ ] Create the second company only through the platform account.
- [ ] For a paid website, add that hostname to `CORS_ALLOWED_ORIGINS` without replacing the other environment variables. Add the same hostname to the browser Maps key’s allowed referrers. No wildcard CORS.
- [ ] Point the extra site at the same API.

---

## 8. Checks before this is called done

- [ ] Dev backup taken, and the backup restores.
- [ ] User, catalog, request, job, appointment, attachment, notification, audit, presence, attendance, and working-hours counts match before and after V19.
- [ ] `RD-2026000005` and `RD-2026000006` keep their ids, numbers, and pins.
- [ ] Office pin is still 19.1412127, 72.8309064, radius 150. Every location switch is off.
- [ ] `admin`, staff, technician, and customer sign in with no company code.
- [ ] A token for a test company cannot read an ACE job (404). That test company is not left on the dev database.
- [ ] Health is 200 and both portals still load: `repairdesk-admin-portal.azurewebsites.net` and `black-cliff-0c84a7c00.7.azurestaticapps.net`.
- [ ] No task turns check-in, attendance, tracking, travel allowance, or customer ETA on.

---

## 9. Out of scope

- A second database or a second Container App.
- Moving or deleting blob files.
- Rewriting ACE reference numbers, passwords, or pins.
- Shipping a second company in the same release as V19.
- Turning location feature switches on.
- The old location-plan migrations (`V19__eta_push.sql` and anything after it in that table). Those numbers are already taken or reserved here.
