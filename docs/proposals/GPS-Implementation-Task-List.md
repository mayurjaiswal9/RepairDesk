# RepairDesk Location Features — Implementation Task List (Google Maps)

**Related:** [Proposed app wireframes](GPS-Location-Features-Wireframes.pdf) (screen numbers below refer to that document)  
**Map provider:** Google Maps Platform, India pricing  
**Status:** Plan for review — 10 October 2026

**Effort:** S = up to 1 day · M = 2–3 days · L = about 1 week (one developer).
**Owner:** *You* = business owner / account holder · *Dev* = development.

The phases follow the rollout in the wireframes document. Each phase can ship on its own, and later phases build on the earlier ones.

---

## Phase 0 — Google Maps account and keys

This phase needs the business owner. Google requires the account holder to accept its terms and add a payment method, so it can't be done on your behalf.

| # | Task | Owner | Effort |
| --- | --- | --- | --- |
| 0.1 | Create (or choose) a **company-owned Google account** for Google Cloud, not a personal Gmail, so access survives staff changes | You | S |
| 0.2 | Open [Google Cloud Console](https://console.cloud.google.com) and create a **billing account** with an **Indian billing address** and GSTIN, so India pricing and its larger free limits apply. Add a card. | You | S |
| 0.3 | Create two projects: `repairdesk-dev` and `repairdesk-prod` | You or Dev | S |
| 0.4 | Enable APIs in both projects: **Maps SDK for Android**, **Maps JavaScript API**, **Geocoding API**, **Routes API**. Optional later: **Places API (New)** for address autocomplete | You or Dev | S |
| 0.5 | Create three restricted API keys per project: an **Android key** (restricted to app `com.acetelecom.repairdesk` and its signing SHA-1 fingerprints), a **web key** (restricted to the admin site domain), and a **server key** (restricted to Geocoding and Routes, used only by the backend) | Dev | S |
| 0.6 | Set **daily quotas**: Geocoding, Routes and Maps JavaScript at 2,250 requests per day each. Add a **budget alert** of ₹500 per month with emails at 50%, 90% and 100% | Dev | S |
| 0.7 | Give the developer access: add their Google account to both projects (role **Editor** on dev, **API Keys Admin** + **Viewer** on prod). Never send keys by chat or email, and never commit them to the repo | You | S |
| 0.8 | Store the server key as a secret in the Azure Container App (and the dev environment); store the Android key in the CI / build machine, not in git | Dev | S |

---

## Phase 1 — Foundations

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 1.1 | **Service address and map pin.** The backend currently stores no customer service address (only the shop address in settings). Add address line, landmark, city, PIN code, latitude, longitude and pin source to requests and jobs (Flyway migration `V12`) | Backend | Dev | M |
| 1.2 | **Shop location and settings.** Add shop latitude, longitude and geofence radius to business settings, plus a location-settings record: feature switches, ping interval, working-hours end, far-from-address threshold, allowance rates, retention days | Backend | Dev | M |
| 1.3 | **Maps service in the backend.** One class that calls Geocoding and Routes with the server key, caches results (the same address is never geocoded twice), counts calls per day, and falls back to straight-line distance when the daily limit is reached | Backend | Dev | M |
| 1.4 | Admin API: `GET/PUT /settings/location` | Backend | Dev | S |
| 1.5 | Flutter packages: `google_maps_flutter`, `geolocator`, `permission_handler`, `url_launcher`. Add location and notification permissions to `AndroidManifest.xml`; inject the Android key into the manifest at build time (Gradle placeholder), not in source | App | Dev | S |
| 1.6 | **Permission onboarding**: Screen 1A explanation, Android prompts (1B), notification prompt, and the three banners (location off, approximate, only this time). Brand-specific battery help for Xiaomi, Realme, Vivo, Oppo | App | Dev | M |
| 1.7 | Admin site: add `@angular/google-maps`; **address + pin** on the Staff "create request" form and request detail | Admin web | Dev | M |
| 1.8 | Admin site: **Location settings** page (Screen 8) with the shop pin, radius and feature switches | Admin web | Dev | M |

---

## Phase 2 — Geotagged check-in and check-out (feature 1)

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 2.1 | `job_visits` table: job, technician, type (On my way / Check-in / Check-out), latitude, longitude, accuracy, time, distance from customer pin, low-accuracy flag | Backend | Dev | S |
| 2.2 | Accept an optional location with **Start Repair** and **Mark Complete**; calculate distance from the pin and the far-from-address flag | Backend | Dev | M |
| 2.3 | App: capture the best location fix within 20 seconds in the Start Repair and Mark Complete confirmations; never block the job if GPS is weak | App | Dev | M |
| 2.4 | App: **Visit** section on Job Detail with map, distance, and **Navigate** (opens the Google Maps app); **Visit record** after completion (Screen 3) | App | Dev | M |
| 2.5 | Save the customer pin from the first check-in when the job has none | Backend + App | Dev | S |
| 2.6 | Staff and Admin: Visit record, far-from-address flag and "View on map" on job detail (Screen 6) | App + Admin web | Dev | M |
| 2.7 | Tests: distance and flag unit tests; emulator tests with simulated locations | All | Dev | M |

---

## Phase 3 — Attendance by geofence (feature 2)

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 3.1 | `attendance_days` table and **Start day / End day** endpoints; record whether each was inside the shop radius | Backend | Dev | M |
| 3.2 | Scheduled job: **auto-end** the day at the working-hours end time | Backend | Dev | S |
| 3.3 | App home: duty card with inside / outside shop area and the disabled state (Screen 2) | App | Dev | M |
| 3.4 | **Attendance & travel report** for Staff and Admin, with Excel (CSV) export (Screen 6) | App + Admin web | Dev | M |

---

## Phase 4 — On-duty tracking (feature 3)

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 4.1 | App: **background tracking** between Start day and End day — Android foreground service with the "sharing location while on duty" notification, background-location permission via Settings, ping every N minutes from settings | App | Dev | L |
| 4.2 | App: **offline queue** — store points on the phone and upload in batches when the network returns | App | Dev | M |
| 4.3 | `location_points` table (indexed by technician and time), batch upload endpoint, and a nightly job that deletes points older than the retention period | Backend | Dev | M |
| 4.4 | **Live technician map** for Staff (app) and Admin (web), with a warning after 15 minutes without a ping (Screen 5) | App + Admin web | Dev | L |
| 4.5 | Optional: sort technicians by distance in the Assign dialog | All | Dev | S |
| 4.6 | **Field test on budget phones** (Redmi, Realme, Vivo, Samsung M/A series): gaps, battery use per day, behaviour after reboot | QA | Dev + You | M |

---

## Phase 5 — Distance and travel allowance (feature 4)

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 5.1 | Daily distance from on-duty points: filter GPS jumps and jitter, split into legs at job check-ins | Backend | Dev | M |
| 5.2 | `travel_claims` table; submit, approve (single and bulk) and monthly export | Backend | Dev | M |
| 5.3 | App: **Travel & allowance** screen and Submit claim (Screen 4) | App | Dev | M |
| 5.4 | Staff / Admin: approve claims in the report | App + Admin web | Dev | S |
| 5.5 | Confirm allowance rates and approval rules (two-wheeler / four-wheeler per km) | — | You | S |

---

## Phase 6 — Customer live ETA (feature 5)

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 6.1 | **On my way** endpoint: start a tracking session with a private link; ETA from the Routes API every 2 minutes (cached); stop automatically at check-in | Backend | Dev | M |
| 6.2 | Push notification to the customer app; SMS with the tracking link | Backend | Dev | M |
| 6.3 | **SMS setup**: DLT registration of the sender ID and message template with an SMS provider (takes 1–2 weeks of lead time) | — | You | S |
| 6.4 | Customer app tracking screen (Screen 7) and a lightweight public tracking web page for customers without the app | App + Web | Dev | L |

---

## Phase 7 — Privacy, release and documentation

| # | Task | Area | Owner | Effort |
| --- | --- | --- | --- | --- |
| 7.1 | Privacy notice for technicians (DPDP Act 2023): what is collected, why, duty hours only, retention. Record each technician's acceptance with date and version | App + Backend | Dev + You | M |
| 7.2 | Technicians can view and download their own location history from Account | App | Dev | S |
| 7.3 | If publishing on the Play Store: background-location declaration, demo video, Data safety form | — | Dev + You | S |
| 7.4 | Update the Technician, Staff and Admin manuals and regenerate the PDFs | Docs | Dev | M |
| 7.5 | Monitoring: Google Cloud usage dashboard, budget alerts, backend count of map calls per day | Ops | Dev | S |

---

## Decisions needed before Phase 1

| Question | Proposed default |
| --- | --- |
| Shop geofence radius | 150 m |
| Can technicians start the day outside the shop? | No; Admin can enable per shop |
| Tracking interval | 5 minutes |
| Working hours / auto end of day | 9:00 AM – 7:00 PM |
| Allowance rates | ₹5 / km two-wheeler, ₹10 / km four-wheeler |
| Location history retention | 90 days |
| Technician phones | Personal phones (prompts) or company phones (pre-granted via `adb install -g` or device management) |
| Distribution | APK only, or Play Store (adds the background-location review) |
