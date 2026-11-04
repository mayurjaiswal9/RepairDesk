# Technician Trip Kilometres, Route, Live Location and Mileage — Proposal

**Date:** 1 November 2026 (decisions agreed the same day, section 10)  
**Applies to:** technician app (Android), admin portal, API  
**Status:** proposal agreed, nothing built yet

---

## 1. Short answers

| Question | Answer |
| --- | --- |
| Can I see the total km a technician drove for a job today? | **No.** The app sends one location when the technician opens a job, and the Navigate button only opens Google Maps. Nothing comes back from Google Maps. |
| Can we capture the km? | **Yes.** The technician taps **Start travel** on the job. Our app records the drive in the background until they arrive, and the km is saved on the job automatically. |
| What is the best way? | GPS trip recorded by **our app**, checked against Google's planned route distance. Staff and Admin can correct a recorded trip with a reason. No odometer photos. |
| Can we track the route if he uses Google Maps? | **Yes, but not from Google Maps.** Google Maps does not give its route or distance to other apps, and Google Timeline has no API. Our app records the route itself while Google Maps navigates. |
| Is it mandatory? | **Yes.** Start travel in the app is mandatory, and strict mode is on: no trip in the app, no km paid. The job itself is never blocked (section 5). |
| Which trips are paid? | Every trip that ends at a job: **shop → job, job → job, and any location → job** (for example, home → first job). Trips back to the shop or home are not recorded or paid. |
| Live location every 1 hour while on a job? | **Yes.** Every 60 minutes on site, and every 2 minutes while travelling. Nothing outside jobs or after working hours. |
| Where do Admin and Staff see mileage? | A separate **Mileage** page in the admin portal: km per technician per day, routes on a map, adjustments, and a payroll CSV (section 7). |
| What is the market standard? | Per-job trips with GPS distance and route, a trips report for travel allowance, and location shared only while working. While travelling, location updates every 10 seconds to 1 minute. See section 3. |

---

## 2. What exists today

| Area | Today | File |
| --- | --- | --- |
| Technician location | One reading when the technician opens a job (`POST /me/location`). Only the latest position per technician is kept (`technician_presence`). | `frontend/lib/features/jobs/presentation/job_detail_screen.dart` (`_PresencePing`), `TechnicianPresenceService.java` |
| Navigate | Opens Google Maps directions to the job pin (`maps/dir/?api=1&destination=…&travelmode=driving`). | `frontend/lib/core/location/service_location.dart` |
| Settings already stored | Tracking and travel switches (off, not used yet), ping every 5 min / 200 m, ₹5 / km two-wheeler and ₹10 / km four-wheeler, vehicle type per technician, 90-day retention, privacy notice version. | `LocationSettings.java` |
| Google route distance | Locations board already asks Google Routes for distance and ETA, with a daily call cap. | `MapsService.java`, `LocationBoardService.java` |
| Earlier plan | [GPS task list](GPS-Implementation-Tasks-App-Backend.md) Phases 4–5: all-day tracking from Start day to End day, then daily km and claims. Not built. | — |

This proposal **replaces Phases 4–5 with per-job trips.** Per-job trips record less, so they are easier to justify to technicians. The km lands on the job and in the Mileage page, and it is what comparable products do.

---

## 3. Market standard

| Product | How km is captured | Live location |
| --- | --- | --- |
| **Zoho FSM** | Technician taps **Start a Trip** on the appointment and **Stop** at the end. The trip saves travel time, GPS distance, and the route. Several trips per appointment are allowed. Office can add a manual trip. A **Trips report** is used for travel allowance. | Shared only between check-in and check-out, updated every 10–15 seconds on the dispatch map. |
| **Salesforce Field Service** | "Travelling" and "On site" statuses. Can switch to On site automatically when the technician enters a radius around the appointment. Location history is drawn as a line on the map. | Admin sets the minimum update interval for foreground and background. Updates stop when the phone is not moving. While travelling to a customer, updates every 60 seconds for the customer ETA. |
| **Mileage / field-force apps used for allowance** | GPS distance during working time. Some add an odometer photo for extra proof. | Every few minutes while on duty, nothing off duty. |

**The common pattern:**
1. Record only while working: a trip, or check-in to check-out.
2. GPS distance with filters for bad readings.
3. Compare with the planned route. Office corrections need a reason.
4. Frequent updates while travelling, much less often on site.
5. A permanent notification on the phone while recording.

---

## 4. Design

### 4.1 Technician flow (app)

1. Job detail shows **Start travel** when the job is Assigned, Scheduled, or Awaiting parts (return visit).
2. On tap:
   - The app checks location permission.
   - It starts the trip from wherever the technician is (shop, previous job, home, anywhere).
   - It shows the notification **"Trip to RD-2026000005 — recording"**.
   - It opens Google Maps directions, as Navigate does today.
3. The technician drives with Google Maps or any other app. Our app keeps a point about every 10 seconds or 25 m.
4. The trip ends when any one of these happens:
   - The technician taps **Arrived**.
   - They are within 150 m of the job pin and stopped for 2 minutes. The app asks: "You're at the customer — end trip?"
   - They tap **Start job**.
5. The job card shows **Trip: 12.4 km · 36 min**.
6. For the next job, **Start travel** again records Job A → Job B.
7. There is no trip back to the shop or home. Those journeys are not paid.

**Start point label** (shown in the Mileage page):
- **Shop** — inside the shop geofence.
- **RD-…** — within 300 m of the previous job's pin.
- **Other location** — anywhere else. The map shows the exact point.

Job statuses do not change. A trip is a separate record linked to the job, so the existing Start job, Awaiting parts and Complete steps work as now.

### 4.2 How the km is calculated (server)

| Step | Rule |
| --- | --- |
| Clean points | Drop readings worse than 50 m accuracy, jumps faster than 120 km/h, and movement under 25 m (GPS jitter while parked). |
| Tracked km | Sum of straight-line distance between the cleaned points. With a point every 10 seconds this follows the road closely. Target: within 5% of the odometer on good signal. |
| Planned km | One Google Routes call when the trip starts (start point → job pin), through the existing `MapsService` cap. |
| Gaps | No points for over 5 minutes (tunnel, phone killed the app): fill that part with a Routes distance and mark the trip **partly estimated**. |
| Flags shown to Staff | Tracked over 1.5 × planned or under 0.5 × planned; fake-GPS app detected; gaps; trip ended more than 300 m from the job pin. |
| Payable km | Adjusted km if Staff or Admin changed it, otherwise tracked km (including estimated gaps). Flagged trips are still payable until someone adjusts them. |
| Amount | Payable km × rate for the technician's vehicle type. The rate and vehicle type are saved on the trip when it ends, so a later rate change does not rewrite past days. |

Snapping the route to roads (Google Roads API) is **not needed** for km. It can be added later for a nicer map line.

### 4.3 Live location

| Technician state | Update | Why |
| --- | --- | --- |
| Travelling (trip running) | Points every ~10 s stored on the phone, sent every **2 minutes** | Live map and accurate km |
| On site (job In progress) | Every **60 minutes**, plus at Start job, Awaiting parts and Complete | Almost no battery |
| No active job, off duty, or after the 7:00 PM auto end | **Nothing** | Privacy |

Portal **Locations** page labels:
- **"Travelling to RD-… · 1 min ago"**
- **"On site at RD-… · 42 min ago"**
- **"No update for 75 min"** (grey). A position counts as stale after the interval plus 15 minutes.

### 4.4 Android: how it keeps recording while Google Maps is on screen

- The trip runs as an Android **foreground service** with a permanent notification. The app starts it when the technician taps Start travel, while the app is on screen.
- Android then lets it keep reading location in the background with the normal **"While using the app"** permission.
- **No "Allow all the time" permission** is needed, so Google Play needs no background-location declaration.
- The same service stays on, at one reading per hour, while the job is In progress.
- **Limits:**
  - After a phone restart, or if the technician force-stops the app, Android does not allow the service to restart by itself. The app shows **"Tap to resume trip"**.
  - Some brands (Xiaomi, Oppo, Vivo, Realme) close background apps aggressively. The battery settings help sheet from the GPS plan applies.
- **Offline:** points are saved on the phone first and sent when the network returns. Each point has its own id, so sending twice is harmless.
- **Battery:** GPS runs only while driving to a job. On site it is one reading per hour.
- **Manifest additions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and a service declared with `foregroundServiceType="location"`.

---

## 5. Mandatory rule and strict mode

| Item | Agreed rule |
| --- | --- |
| Start travel in the app | **Mandatory.** Km is stored automatically from the trip. |
| No trip | **No km paid** for that job. |
| Strict mode | **On.** Tapping **Start job** with no recorded trip for that job opens a sheet: "No trip recorded for this job. Km will not be paid." The technician picks a reason, then the job starts. |
| Strict mode reasons | "Customer came to the shop", "Already at this site", "Phone or GPS problem", "Other" (typed). The reason appears in the Mileage page. |
| Location permission | Required to start a trip. If refused, the app explains why and opens settings. The job can still start through the strict-mode sheet, with no km. |
| Using Google Maps | **Optional.** The app opens it by default, but the km comes from our app. |
| Adjust km | **Staff and Admin**, on recorded trips only, with a required reason. Every change is in the audit log. A job with no trip cannot be given km afterwards. |
| Odometer photo | **No.** |

Weak GPS never blocks a job. Strict mode only decides whether km is paid.

---

## 6. Privacy and consent

- Show a notice before the first trip, versioned with the existing `privacy_notice_version`. Record when it was accepted.
- **What is recorded:** only during trips and active jobs, inside working hours. The notification is always visible while recording.
- **Retention:** route points are kept for 90 days (existing setting), then deleted nightly. Trip totals (km, times, amount, adjustments) stay permanently with the job.
- **Access:** technicians can see their own trips. Staff and Admin can see all, and views of route history are written to the audit log.
- This follows the notice, purpose and retention principles of India's DPDP Act 2023. HR or legal should confirm the notice wording before release.

---

## 7. Mileage page for Admin and Staff (new)

A separate page in the admin portal, **Mileage**, in the left menu next to Locations and Attendance. Admin and Staff only.

### 7.1 Filters

- **Date:** Today, Yesterday, This week, This month, or a custom range.
- **Technician:** all, or one.
- **Vehicle:** all, two-wheeler, four-wheeler.
- **Show:** all, flagged only, or no trip only.
- **View:** **By day** (default) or **By month**.

### 7.2 Summary strip for the selected range

| Total km | Paid trips | Amount | Flagged trips | Jobs started with no trip |
| --- | --- | --- | --- | --- |
| 342.6 km | 41 | ₹2,184 | 3 | 2 |

### 7.3 Daily table (By day)

One row per technician per day, newest first, with a totals row at the bottom:

| Date | Technician | Vehicle | Trips | Tracked km | Adjusted | Payable km | Rate | Amount | Flags | No trip |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 Nov | GPS Technician | Two-wheeler | 4 | 38.2 | −1.5 | 36.7 | ₹5 | ₹184 | 1 | 0 |

**By month** shows one row per technician with the month's trips, payable km and amount.

### 7.4 Day detail

Clicking a row opens that technician's day:

- **Map:** every route of the day, numbered in order, with the shop pin and the job pins.
- **Timeline:** one line per trip:
  - start time
  - from (Shop / RD-… / Other location) and to RD-…
  - duration
  - tracked km and Google route km
  - payable km and flags
  - **Adjust km**
- **No trip:** jobs started without a trip, with the reason and 0 km.
- **Adjust km:** a dialog showing the tracked km, with fields for the new km and a required reason. The table then shows "Adjusted by Name, time".

### 7.5 Export

**Export CSV** for payroll, at two levels:
- One row per technician per day.
- One row per trip, with job, from, to, times, tracked, adjusted and payable km, rate, amount, flags, and who adjusted it.

### 7.6 Other screens

**Technician app**
- Job detail: **Start travel** button and a trip banner (live km, time, **Arrived**). After arrival the banner shows trip km and time.
- Strict-mode reason sheet on **Start job** when there is no trip.
- **My trips**: a list by day with km per trip, the day total and the amount.

**Admin portal**
- **Job detail → Travel:** the job's trips, a map with the route, and **Adjust km**.
- **Locations:** Travelling / On site / Stale labels (section 4.3).
- **Settings → Location:**
  - **Record job trips** (uses the existing travel switch)
  - **Strict mode** (on)
  - **Open Google Maps on Start travel** (on)
  - **Update while travelling** (2 min)
  - **Update while on site** (60 min; uses the existing ping interval)
  - Rates per km (existing)

---

## 8. Data and API (for developers)

**Migration `V21__job_trips.sql`** (`company_id` on every table, company filter, same-company checks):

| Table | Columns |
| --- | --- |
| `job_trips` | `id`, `company_id`, `job_id`, `technician_id`, `status` (`ACTIVE` / `ENDED` / `CANCELLED`), `started_at`, `start_lat`, `start_lng`, `start_kind` (`SHOP` / `PREVIOUS_JOB` / `OTHER`), `ended_at`, `end_lat`, `end_lng`, `end_reason` (`ARRIVED` / `GEOFENCE` / `JOB_STARTED` / `TIMEOUT` / `CANCELLED`), `tracked_distance_m`, `planned_distance_m`, `planned_duration_s`, `estimated_gap_m`, `point_count`, `gap_count`, `mocked_points`, `flags`, `vehicle_type`, `rate_paise`, `adjusted_distance_m`, `adjust_reason`, `adjusted_by`, `adjusted_at`, `created_at` |
| `trip_points` | `id`, `company_id`, `trip_id`, `client_point_id` (unique per trip), `recorded_at`, `lat`, `lng`, `accuracy_m`, `speed_mps`, `mocked` |
| `job_no_trip_reasons` | `id`, `company_id`, `job_id`, `technician_id`, `reason_code`, `reason_text`, `created_at` |
| `location_settings` (add) | `strict_trip_mode` (default true), `trip_open_maps` (default true), `travel_ping_seconds` (120) |

Mileage per day is calculated when asked, from `job_trips` grouped by technician and India date of `started_at` (`Asia/Kolkata`). There is no separate daily table. Index: `(company_id, technician_id, started_at)`.

| Endpoint | Who | Purpose |
| --- | --- | --- |
| `POST /jobs/{id}/trips` | Assigned technician | Start a trip. Only one active trip per technician. |
| `POST /trips/{id}/points` | Trip owner | Up to 500 points. Repeats are ignored. |
| `POST /trips/{id}/end` | Trip owner | Arrived, geofence, or cancel. `POST /jobs/{id}/start` also ends an active trip for that job. |
| `POST /jobs/{id}/start` (changed) | Assigned technician | With strict mode on and no trip, needs `noTripReason`. |
| `GET /jobs/{id}/trips` | Staff, Admin, assigned technician | Totals plus a simplified route line |
| `PUT /trips/{id}/distance` | Staff, Admin | Adjust km with a reason (audited) |
| `GET /mileage/days?from&to&technicianId&vehicle&show` | Staff, Admin | Daily or monthly rows plus the summary strip |
| `GET /mileage/days/{technicianId}/{date}` | Staff, Admin; technician for self | Day detail: trips, routes, no-trip jobs |
| `GET /mileage/export.csv?from&to&level=day\|trip` | Staff, Admin | Payroll CSV |
| `POST /me/location` (existing) | Technician | Hourly on-site update. Adds a `state` value (`TRAVELLING` / `ON_SITE`). |

---

## 9. Effort and phases

| Phase | Scope | API | App | Portal | Test | Days |
| --- | --- | --- | --- | --- | --- | --- |
| A | Trip km per job: Start travel, background recording, Arrived / geofence end, strict-mode reasons, km and flags, job Travel panel | 6 | 8 | 3 | 3 (budget phones) | 20 |
| B | Live location: 2 min while travelling, 60 min on site, Locations labels and stale state | 1.5 | 2 | 2 | — | 5.5 |
| C | **Mileage page:** daily and monthly tables, day detail with map, Adjust km, payroll CSV; **My trips** in the app | 2.5 | 1.5 | 5 | 1 | 10 |
| | **Total** | | | | | **35.5** |

That is about 7 weeks with one developer, or about 4 weeks with two (one on the app, one on the API and portal).

**Running cost:**
- About one Google Routes call per trip, plus rare gap fills. At ~30 trips a day that is about 1,000 calls a month, inside the existing daily cap.
- The Roads API is not used.
- Point storage is small: about 360 points per hour of driving, deleted after 90 days.

---

## 10. Decisions (agreed 1 November 2026)

| Topic | Decision |
| --- | --- |
| Paid trips | Shop → job, job → job, and any location → job. Return trips to the shop or home are not paid. |
| Start travel | Mandatory in the app, so km is stored automatically |
| No trip | No km paid |
| Strict mode | On |
| Intervals | 60 minutes on site, 2 minutes while travelling (kept as proposed) |
| Odometer photo | No |
| Who adjusts km | Staff and Admin |
| Customer live ETA link | Later (Phase 6 of the GPS plan) |
| Mileage view | Separate Mileage page for Admin and Staff, per technician per day (section 7) |
