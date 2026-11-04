# Job Trips and Mileage — API Contract

Implements [Technician-Trip-Distance-and-Live-Location-Proposal.md](Technician-Trip-Distance-and-Live-Location-Proposal.md). Base path `/api/v1`. JSON is camelCase, times are ISO-8601 UTC instants, dates are `YYYY-MM-DD` India dates (`Asia/Kolkata`). Distances are whole metres, money is whole paise.

## 1. Switches

| Setting (existing / new) | JSON | Default | Meaning |
| --- | --- | --- | --- |
| `travel_enabled` (existing) | `travelEnabled` | false | **Record job trips.** Off: no Start travel, `POST /jobs/{id}/trips` returns 422 "Job trips are not turned on", no strict mode. |
| `tracking_enabled` (existing) | `trackingEnabled` | false | **Live location on a job.** On: app sends an on-site update every `onSitePingMinutes` while the technician has a job In progress. |
| `strict_trip_mode` (new) | `strictTripMode` | true | Only applies when `travelEnabled`. Technician `POST /jobs/{id}/start` without a trip for that job needs a reason. |
| `trip_open_maps` (new) | `tripOpenMaps` | true | App opens Google Maps directions after Start travel. |
| `travel_ping_seconds` (new) | `travelPingSeconds` | 120 | App uploads queued trip points this often (30–600). |
| `on_site_ping_minutes` (new) | `onSitePingMinutes` | 60 | On-site update interval (15–240). |

- `GET/PUT /settings/location` (Admin): response adds the four new fields. In the PUT body the four new fields are **optional**; `null` keeps the stored value, so older clients keep working.
- `GET /location/config` (any signed-in user): adds the four new fields.

**Rollout rule:** turn `travelEnabled` on only after technicians have the new APK. The old APK cannot send a no-trip reason.

## 2. Tables — `V21__job_trips.sql`

All three tables have `id UUID PK DEFAULT gen_random_uuid()`, `company_id UUID NOT NULL REFERENCES companies(id)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ`, `version BIGINT NOT NULL DEFAULT 0`. Entities extend `TenantEntity` with the company `@Filter`.

`job_trips`: `job_id` (FK jobs), `technician_id` (FK users), `status` (`ACTIVE` / `ENDED` / `CANCELLED`), `started_at`, `start_lat`, `start_lng`, `start_accuracy_m`, `start_kind` (`SHOP` / `PREVIOUS_JOB` / `OTHER` / `UNKNOWN`), `start_job_id` (nullable FK jobs), `ended_at`, `end_lat`, `end_lng`, `end_reason` (`ARRIVED` / `GEOFENCE` / `JOB_STARTED` / `REPLACED` / `TIMEOUT` / `CANCELLED`), `tracked_distance_m INT NOT NULL DEFAULT 0`, `estimated_gap_m INT NOT NULL DEFAULT 0`, `planned_distance_m INT`, `planned_duration_s INT`, `planned_estimated BOOLEAN NOT NULL DEFAULT FALSE`, `point_count INT NOT NULL DEFAULT 0`, `gap_count INT NOT NULL DEFAULT 0`, `mocked_points INT NOT NULL DEFAULT 0`, `flags VARCHAR(200)` (comma-separated), `vehicle_type VARCHAR(20)`, `rate_paise INT`, `adjusted_distance_m INT`, `adjust_reason VARCHAR(500)`, `adjusted_by UUID` (FK users), `adjusted_at TIMESTAMPTZ`. Indexes: `(company_id, technician_id, started_at)`, `(job_id)`, and unique `(technician_id) WHERE status = 'ACTIVE'`.

`trip_points`: `trip_id` (FK job_trips ON DELETE CASCADE), `client_point_id VARCHAR(64) NOT NULL`, `recorded_at TIMESTAMPTZ NOT NULL`, `lat`, `lng` (NOT NULL), `accuracy_m`, `speed_mps`, `mocked BOOLEAN NOT NULL DEFAULT FALSE`. Unique `(trip_id, client_point_id)`; index `(trip_id, recorded_at)`.

`job_no_trip_reasons`: `job_id`, `technician_id`, `reason_code VARCHAR(30)` (`AT_SHOP` / `ALREADY_ON_SITE` / `PHONE_PROBLEM` / `OTHER`), `reason_text VARCHAR(500)`. Index `(company_id, technician_id, created_at)`.

`location_settings` adds the four columns in section 1. `technician_presence` adds `state VARCHAR(20)` (`TRAVELLING` / `ON_SITE`, nullable).

## 3. Trip object (`TripResponse`)

```json
{
  "id": "uuid", "jobId": "uuid", "jobReference": "RD-2026000005",
  "technicianId": "uuid", "technicianName": "GPS Technician",
  "status": "ACTIVE|ENDED|CANCELLED",
  "startedAt": "2026-11-01T09:12:00Z", "endedAt": null, "durationSeconds": 2160,
  "startKind": "SHOP|PREVIOUS_JOB|OTHER|UNKNOWN", "startJobReference": null,
  "start": {"latitude": 19.14, "longitude": 72.83}, "end": null,
  "destination": {"latitude": 19.1436, "longitude": 72.8246},
  "trackedDistanceM": 12400, "estimatedGapM": 0,
  "plannedDistanceM": 11800, "plannedDurationSeconds": 1980, "plannedEstimated": false,
  "adjustedDistanceM": null, "adjustReason": null, "adjustedByName": null, "adjustedAt": null,
  "payableDistanceM": 12400, "vehicleType": "TWO_WHEELER", "ratePaise": 500, "amountPaise": 6200,
  "flags": ["LONGER_THAN_ROUTE"], "pointCount": 214, "endReason": "ARRIVED",
  "route": [[19.1412, 72.8309], [19.1420, 72.8290]]
}
```

- `payableDistanceM` = `adjustedDistanceM` if set, else `trackedDistanceM + estimatedGapM`. `CANCELLED` → 0. `ACTIVE` → live tracked so far.
- `amountPaise` = round(`payableDistanceM` / 1000 × `ratePaise`); null while `ACTIVE`.
- `route` is the simplified line (at most 500 points, Douglas–Peucker 10 m). It is an empty array except in job travel and day detail responses.
- `durationSeconds`: ended − started, or now − started while active.

## 4. Endpoints

| Method and path | Roles | Body / query | Result |
| --- | --- | --- | --- |
| `POST /jobs/{jobId}/trips` | TECHNICIAN (assigned) | `{latitude?, longitude?, accuracyM?, mocked?}` | `TripResponse` (201). 422 if switch off, job not assigned to caller, or status not ASSIGNED / SCHEDULED / IN_PROGRESS / AWAITING_PARTS. Same job already active: returns it (200). Active trip on another job: that one ends with `REPLACED`, then the new one starts. |
| `POST /trips/{tripId}/points` | TECHNICIAN (owner) | `{points: [{clientPointId, recordedAt, latitude, longitude, accuracyM?, speedMps?, mocked?}]}`, max 500 | `{accepted, duplicates, trackedDistanceM}`. Accepted while `ACTIVE`, or up to 30 min after it ended (points at most 1 min after `endedAt`); distance is recalculated. Updates presence (state `TRAVELLING`, jobId) from the newest point while active. |
| `POST /trips/{tripId}/end` | TECHNICIAN (owner) | `{reason: "ARRIVED"\|"GEOFENCE"\|"CANCELLED", latitude?, longitude?, accuracyM?}` | `TripResponse`. Already ended: returns it unchanged. |
| `GET /me/trips/active` | TECHNICIAN | — | `TripResponse` or 204 |
| `GET /jobs/{jobId}/trips` | STAFF, ADMIN, assigned TECHNICIAN | — | `{trips: TripResponse[] (with route), noTrip: {reasonCode, reasonText, technicianName, createdAt} \| null}` |
| `POST /jobs/{jobId}/start` (changed) | as today | optional `{noTripReason?, noTripNote?}` | Technician, `travelEnabled` and `strictTripMode` on: an active trip for this job ends with `JOB_STARTED`; else an ended trip for this job by the caller is enough; else `noTripReason` is required, otherwise **422** with message "No trip recorded for this job" and `details.tripRequired = true`. `OTHER` needs `noTripNote`. The reason is stored. With `travelEnabled` on and strict off, an active trip for this job still ends with `JOB_STARTED`. Staff / Admin are never asked. |
| `PUT /trips/{tripId}/distance` | STAFF, ADMIN | `{distanceKm: 0–1000 (1 decimal), reason: 3–500 chars}` | `TripResponse`. Only `ENDED` trips. Audit `TRIP` / `DISTANCE_ADJUSTED` "12.4 km → 11.0 km: reason". |
| `GET /mileage/days` | STAFF, ADMIN; TECHNICIAN forced to self | `from`, `to` (default today; max 93 days), `technicianId?`, `vehicle?` (`TWO_WHEELER`/`FOUR_WHEELER`), `show?` (`ALL`/`FLAGGED`/`NO_TRIP`), `view?` (`DAY`/`MONTH`) | See 4.1 |
| `GET /mileage/days/{technicianId}/{date}` | STAFF, ADMIN; TECHNICIAN for self | — | See 4.2 |
| `GET /mileage/export.csv` | STAFF, ADMIN | same filters + `level=DAY\|TRIP` | UTF-8 CSV with BOM, like attendance export |
| `POST /me/location` (changed) | TECHNICIAN | adds optional `state` (`TRAVELLING`/`ON_SITE`) | stores state |
| `GET /location/board` (changed) | STAFF, ADMIN | — | each technician marker adds `state` |

### 4.1 `GET /mileage/days`

```json
{
  "from": "2026-11-01", "to": "2026-11-01", "view": "DAY",
  "summary": {"payableDistanceM": 342600, "trips": 41, "amountPaise": 218400, "flaggedTrips": 3, "noTripJobs": 2, "activeTrips": 1},
  "rows": [{
    "date": "2026-11-01", "month": null,
    "technicianId": "uuid", "technicianName": "GPS Technician", "vehicleType": "TWO_WHEELER",
    "trips": 4, "trackedDistanceM": 38200, "adjustmentM": -1500, "payableDistanceM": 36700,
    "ratePaise": 500, "amountPaise": 18350, "flaggedTrips": 1, "noTripJobs": 0, "activeTrips": 0
  }]
}
```

- Rows exist only for technician-days with at least one trip or no-trip reason (not every technician × day).
- `trips` counts `ENDED` trips only; `activeTrips` counts running trips. `CANCELLED` trips are ignored.
- `trackedDistanceM` = sum of tracked + estimated gap of ENDED trips; `adjustmentM` = sum of (adjusted − (tracked + gap)) for adjusted trips; `payableDistanceM` = tracked + adjustment.
- `ratePaise` is the rate when all ENDED trips in the row share one rate, otherwise null. `amountPaise` is the sum of trip amounts.
- `view=MONTH`: one row per technician per month, `date` null, `month` `"2026-11"`.
- Sort: date (or month) descending, then technician name.
- `show=FLAGGED` keeps rows with `flaggedTrips > 0`; `show=NO_TRIP` keeps rows with `noTripJobs > 0`. The summary covers the filtered rows.

### 4.2 `GET /mileage/days/{technicianId}/{date}`

```json
{
  "date": "2026-11-01", "technicianId": "uuid", "technicianName": "GPS Technician", "vehicleType": "TWO_WHEELER",
  "shop": {"latitude": 19.1412127, "longitude": 72.8309064},
  "summary": {"payableDistanceM": 36700, "trips": 4, "amountPaise": 18350, "flaggedTrips": 1, "noTripJobs": 0, "activeTrips": 0},
  "trips": [/* TripResponse with route, oldest first; CANCELLED included and marked */],
  "noTripJobs": [{"jobId": "uuid", "referenceNumber": "RD-…", "reasonCode": "AT_SHOP", "reasonText": null, "createdAt": "…"}]
}
```

## 5. Distance rules (server, on end and on late points)

1. Points sorted by `recordedAt`; the start fix (if any) is the first point and the end fix (if any) the last.
2. Drop points with `accuracyM > 50`. Count `mocked` points.
3. Anchor = last accepted point. Skip a point closer than 25 m to the anchor (jitter). Drop a point implying more than 120 km/h from the anchor.
4. Segment with time gap over 5 minutes and distance over 500 m is a **gap**: add `MapsService.route(...)` distance (falls back to straight line × 1.3) to `estimatedGapM`, `gapCount++`. Other segments add haversine metres to `trackedDistanceM`.
5. Planned route at trip start: `MapsService.route(start → job pin)` with `DRIVE` for four-wheelers, `TWO_WHEELER` otherwise. Skipped when there is no start fix or no job pin.
6. Flags: `NO_POINTS` (fewer than 2 accepted points), `LONGER_THAN_ROUTE` (planned > 500 m and total > 1.5 × planned), `SHORTER_THAN_ROUTE` (planned > 1000 m and total < 0.5 × planned), `MOCKED`, `GAPS`, `FAR_FROM_JOB` (end point more than `farFromAddressM` from the job pin, for ARRIVED / GEOFENCE / JOB_STARTED).
7. Start kind: within shop geofence → `SHOP`; within `farFromAddressM` of another job pin assigned to the technician → `PREVIOUS_JOB` (+ `startJobReference`); other fix → `OTHER`; no fix → `UNKNOWN`.
8. On end, save the technician's vehicle type and the matching rate (`rate_two_wheeler_paise` / `rate_four_wheeler_paise`; no vehicle type → two-wheeler rate).
9. Scheduled task (every 15 min, `ScheduledTaskGuard`): end `ACTIVE` trips older than 6 hours with `TIMEOUT`. Nightly 02:30 India time: delete `trip_points` older than `retention_days`.

## 6. App behaviour

- **Start travel** on job detail (switch on; job ASSIGNED / SCHEDULED / IN_PROGRESS / AWAITING_PARTS; no ended trip for this job yet, or always allow a new trip for a return visit). Needs location permission and notification permission. Starts a foreground location stream (geolocator Android `foregroundNotificationConfig`, notification "Trip to RD-… — recording"), distance filter 25 m, about every 10 s. Points queue on the phone (persisted) and upload every `travelPingSeconds`; each point has a UUID `clientPointId`.
- Opens Google Maps directions when `tripOpenMaps`.
- Trip banner on job detail: live km, time, **Arrived**. Within 150 m of the job pin and nearly still for 2 minutes: prompt "You're at the customer — end trip?" (ends with `GEOFENCE`).
- **Start job**: if strict mode applies and there is no trip for this job, show the reason sheet first; send `noTripReason` / `noTripNote`. Cancel sends nothing.
- On-site updates: when `trackingEnabled` and the technician has an IN_PROGRESS job, and the time is before `dayAutoEndTime`: foreground stream at `onSitePingMinutes` → `POST /me/location` with `state: ON_SITE` and the job id. Stops on complete, awaiting parts, sign-out, or auto-end time.
- On app start / resume: `GET /me/trips/active` restores the banner and recording; upload anything queued.
- **My trips** tile for technicians: `GET /mileage/days` (self) and day detail.

## 7. Portal behaviour

- New left-menu page **Mileage** (`/mileage`, Staff and Admin) next to Locations and Attendance: filters, summary strip, daily / monthly table with totals row, day detail with map (all routes numbered, shop and job pins) and trip timeline, **Adjust km** dialog, no-trip list, **Export CSV** (day or trip level).
- Job detail: **Travel** panel from `GET /jobs/{id}/trips` with route map and **Adjust km**.
- Location settings: **Record job trips** (`travelEnabled`), **Live location on a job** (`trackingEnabled`), **Strict mode**, **Open Google Maps on Start travel**, **Upload while travelling (seconds)**, **Update while on site (minutes)**, existing rates.
- Locations board: Travelling / On site / stale labels from `state` and `recordedAt` (stale after the matching interval + 15 minutes).
