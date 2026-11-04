# Attendance monitoring — proposal

[Attendance-Monitoring-Proposal.pdf](Attendance-Monitoring-Proposal.pdf)

**Status:** Built in the API, the portal, and the phone. The Attendance switch stays off until an administrator turns it on. Jobs, opening a job, starting a repair, and Start Navigating do not check attendance or GPS.

**Visual copy:** the wireframes below, and the same screens on the canvas `attendance-monitoring-proposal.canvas.tsx` in the docs workspace canvases folder.

**Checked in code:** Angular `admin-web`, Flutter `frontend`, Spring Boot `backend`. The Staff manual is not changed by this proposal.

## Answer

Attendance is **not** present end to end. The product stores office settings and a technician’s last known position. It does not record a work day.

## What exists today

Administrators can open **Office location** on the web (`/settings/location`) and on the phone. Staff cannot. The screen saves the shop pin, a geofence radius (default 150 m), and six switches that default to off: Check-in, Attendance, On-duty tracking, Travel allowance, Customer ETA, and Allow start outside the office geofence. It also stores ping interval 5 minutes, ping distance 200 m, auto end 19:00, far-from-address 300 m, rates 500 and 1,000 paise (₹5/km and ₹10/km), retention 90 days, and privacy notice version 1.

Those switches are only stored and returned. `GET /location/config` exposes them to any signed-in user and omits the rates. Neither the phone nor the staff portal calls that endpoint. Turning a switch on does not add Start day, a trail, an allowance, or a customer countdown.

`POST /jobs/{id}/start` takes no location. When a technician opens an assigned job, the phone spends up to 8 seconds looking for a fix and then `POST /me/location`. That upserts one `technician_presence` row (latitude, longitude, accuracy, job id, time). If there is no fix, or the save fails, the job stays open. The server does not check the Attendance switch, the geofence, or that the job id belongs to that technician.

Staff and Admin open **Locations** on the web and on the phone: office pin, each technician’s last coordinates and time, open jobs with a travel time, and Start Navigating. Travel time uses the last position when one exists, otherwise the office pin. The page does not show who started a day.

There is no `attendance_days` table. Flyway V16 is `technician_presence`. The next free version is V18. `GeoMath` can measure a radius and is used for route fallback, not for a day. The scheduler only writes a heartbeat. Nothing deletes location rows after 90 days. `users.vehicle_type` exists and no screen sets it, so board routes stay two-wheeler.

## What Staff can already monitor

- Office pin on the Locations map.
- Last shared technician position, coordinates, and time. Blank until that person opens an assigned job with GPS available.
- Open jobs that have a service pin, travel minutes, distance, and Start Navigating.
- The same Locations page on the phone.

## What Staff cannot monitor

- Whether anyone started or ended today.
- Hours, an auto end at 19:00, or a previous day.
- Inside the shop, outside the shop, or a weak fix.
- Office location, the switches, the radius, or the rates.

## Recommended design

Add a work day and a staff list. Leave the other four feature switches unused. Keep the existing database and the single shop. Leave every switch off when the code ships.

### Technician phone

Show a duty card on the technician home only when `attendanceEnabled` is true. While it is false, the home stays Jobs and Appointments, and start/end write nothing.

Start day waits up to 8 seconds for a fix, then the server classifies it. The phone does not decide inside or outside. A second start on the same India date (`Asia/Kolkata`) returns the existing day.

Before the first start for privacy notice version 1, show one line: the time, and whether they were at the shop, are stored. Declining leaves the day unstarted. Jobs stay available.

End day is always allowed once a day is open. A missing or outside fix is recorded. It does not refuse the end.

### Geofence

Use the saved shop pin and `geofence_radius_m`. A fix is trustworthy when accuracy is present and no wider than that radius.

| What the phone has | Day | Flag on the staff list | Job |
| --- | --- | --- | --- |
| No fix in 8 seconds | Start | Location missing | Still opens and can be started |
| Accuracy wider than the radius | Start | Location uncertain | Still opens and can be started |
| Android reports a mock location | Start | Mocked location | Still opens and can be started |
| Trustworthy fix inside the radius | Start | Inside shop | Still opens and can be started |
| Trustworthy fix outside, and allow-start-outside is on | Start | Outside shop | Still opens and can be started |
| Trustworthy fix outside, and allow-start-outside is off | Do not start | Not started | Still opens and can be started |

The outside refusal returns the distance so the phone can explain it. It is not a job error.

### Staff portal

New **Attendance** item under Work, on the web and the phone, for Staff and Admin. Technicians do not get it. It does not replace Locations.

Default to today in India. Allow a from/to range of at most 31 days. One row per active technician per date in the range, including people with no row (Not started).

Columns: name, date, status (Not started, On duty since, Ended, Auto-ended), start flag, end time, Manual or Auto, hours when both times exist. Omit coordinates from this payload. The web page can download the same columns as UTF-8 CSV (with a BOM) for Excel. Dates `dd-MM-yyyy`, times in India.

Staff still cannot open Office location or change a switch.

### Admin settings

Office location stays administrator-only on the web and the phone. This slice reads Attendance, the radius, allow-start-outside, auto-end time, and privacy notice version. It does not add switches and does not turn any on.

### Data

`attendance_days` via Flyway **V18** (not applied here):

- `technician_id`, `work_date` (India date), unique together
- `start_at`, `end_at`
- start and end flag: `INSIDE`, `OUTSIDE`, `UNCERTAIN`, `MISSING`, `MOCKED`
- `end_reason`: `MANUAL` or `AUTO`
- start and end coordinates and accuracy, kept for the flag, not shown on the staff list
- `privacy_notice_version` copied from settings at start

`technician_presence` stays the last position for Locations and keeps updating when a job is opened, including while Attendance is off. Do not treat that row as a day.

### Auto end

Every 15 minutes, claim the run with the existing scheduler guard. The scheduler clock is already `Asia/Kolkata`. At the stored `day_auto_end_time` (default 19:00), close open days for that India date with `end_reason = AUTO` and no end fix. Do not change the job. Send one in-app notification with the existing `NotificationService.send`. No SMS.

### Weak GPS

Opening a job, `POST /jobs/{id}/start`, and Start Navigating do not check attendance or GPS. The presence ping stays best-effort. A weak or missing fix on Start day still writes the day, with Uncertain or Missing, so Staff can see the attempt. The only refused action is Start day when a trustworthy fix is outside the shop and allow-start-outside is off.

### Retention

Keep attendance days. The stored 90 days applies later, to raw on-duty GPS points, when that switch is built. Deleting day rows at 90 days would remove the record Staff use to check attendance.

## Wireframes

Pictures of the design above, for someone who will not read the code. They are not a build. The duty card is on the technician home only while Attendance is on. That switch stays off when the code ships. While it is off, the home is Jobs and Appointments, and start and end write nothing.

Opening a job, starting a repair, and Start Navigating stay as they are. They do not wait on attendance or GPS, so they are not drawn here.

The sample day is 18-10-2026 (today in India). Names and times are sample rows so the columns are readable.

### Technician home — Start day

Shown only while Attendance is on. The phone waits up to 8 seconds for a fix. The server decides inside or outside the saved shop pin and radius. Not now is the decline: the day stays unstarted, and jobs stay available. A second Start day on the same India date returns the day already saved.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ Not started                        │
│ Your start time, and whether you   │
│ were at the shop, are stored.      │
│ [ Start day ]                      │
│ [ Not now ]                        │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

On the sample list, Arun Das is still on this screen.

### Blocked — trustworthy fix outside the shop

Allow start outside is off. The fix is trustworthy and outside the saved radius (150 m by default). Start day is refused. The server returns the distance so the phone can explain it. 420 m is a sample distance. The day stays unstarted, so the staff list shows Not started, the same as someone who has not tapped Start day. Jobs and Appointments stay available. This refusal is only for Start day.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ Not started                        │
│ You are 420 m from the shop.       │
│ Start day was not saved.           │
│ [ Start day ]                      │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

When allow-start-outside is on, that same outside fix starts the day. The list then shows Outside shop. That case is Ravi on the sample list, and the End day phone below.

### Flagged start — Missing, Uncertain, Mocked

No fix, a fix wider than the radius, or a mocked location still starts the day. Staff see the flag. Jobs and Appointments stay available. These three starts are saved even when allow-start-outside is off.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ On duty since 09:50                │
│ Start flag: Location missing       │
│ [ End day ]                        │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

No fix in 8 seconds. Sample list: Meera Shah. At 19:00 that day is Auto-ended. An open repair stays in progress.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ On duty since 09:15                │
│ Start flag: Location uncertain     │
│ [ End day ]                        │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

Accuracy wider than the saved radius. Sample list: Kiran Patel, ended 16:20, Manual, 7 h 05 m.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ On duty since 10:05                │
│ Start flag: Mocked location        │
│ [ End day ]                        │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

Android reports a mock location. Sample list: Neha Iyer, still on duty, so hours are blank.

### End day

End day is on the home once the day is open. It is always allowed. A missing or outside fix is recorded with the end.

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ On duty since 09:04                │
│ Start flag: Outside shop           │
│ [ End day ]                        │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

After End day the card reads:

```text
┌────────────────────────────────────┐
│ RepairDesk                    Home │
├────────────────────────────────────┤
│ Today                              │
│ Ended                              │
│ 17:40 · Manual                     │
│ Start flag: Outside shop           │
├────────────────────────────────────┤
│ Jobs                               │
│ Appointments                       │
└────────────────────────────────────┘
```

Sample list: Ravi Kumar, 8 h 36 m. Jobs and Appointments stay on the home. Ending the day leaves any open repair in progress.

Auto-end is separate from this button. At 19:00 IST the server closes an open day for that India date, with How ended = Auto and no end fix. The job stays in progress. The technician gets one in-app notice.

### Staff and Admin — Attendance, today

New item under Work, on the web and on the phone. It does not replace Locations. Staff and Admin can open it. The page lists every active technician for the India date, including people with no row. Default is today. From and To can cover at most 31 days. Coordinates stay off this list. The web page can download the same columns for Excel (UTF-8 CSV with a BOM, dates `dd-MM-yyyy`, times in India).

Web:

```text
Work / Attendance                         [ Download ]
Today · 18-10-2026     From 18-10-2026    To 18-10-2026
```

| Name | Date | Status | Start flag | End | How ended | Hours |
| --- | --- | --- | --- | --- | --- | --- |
| Asha Rao | 18-10-2026 | On duty since 09:12 | Inside shop | — | — | — |
| Ravi Kumar | 18-10-2026 | Ended | Outside shop | 17:40 | Manual | 8 h 36 m |
| Meera Shah | 18-10-2026 | Auto-ended | Location missing | 19:00 | Auto | 9 h 10 m |
| Kiran Patel | 18-10-2026 | Ended | Location uncertain | 16:20 | Manual | 7 h 05 m |
| Neha Iyer | 18-10-2026 | On duty since 10:05 | Mocked location | — | — | — |
| Arun Das | 18-10-2026 | Not started | — | — | — | — |

Hours appear when both a start and an end exist. Asha is the inside-shop start and is still on duty. A refused outside start looks like Arun’s row: Not started.

Phone (the same rows, stacked; download stays on the web):

```text
┌────────────────────────────────────┐
│ Attendance                         │
│ Today · 18-10-2026                 │
├────────────────────────────────────┤
│ Asha Rao                           │
│ On duty since 09:12                │
│ Inside shop                        │
├────────────────────────────────────┤
│ Ravi Kumar                         │
│ Ended · 17:40 · Manual             │
│ Outside shop · 8 h 36 m            │
├────────────────────────────────────┤
│ Meera Shah                         │
│ Auto-ended · 19:00 · Auto          │
│ Location missing · 9 h 10 m        │
├────────────────────────────────────┤
│ Kiran Patel                        │
│ Ended · 16:20 · Manual             │
│ Location uncertain · 7 h 05 m      │
├────────────────────────────────────┤
│ Neha Iyer                          │
│ On duty since 10:05                │
│ Mocked location                    │
├────────────────────────────────────┤
│ Arun Das                           │
│ Not started                        │
└────────────────────────────────────┘
```

### Office location

No new settings screen. Office location stays the administrator page already built, on the web (`/settings/location`) and on the phone. Only an administrator can open it. The shop pin, the radius, auto-end at 19:00, privacy notice version 1, the ₹5/km and ₹10/km rates, and the six switches stay on that screen. This slice does not add a switch and does not turn any on.

## Out of scope

- Check-in against the customer address and the 300 m far flag.
- On-duty points every 5 minutes or 200 m, and deletion of those points.
- Travel allowance and a vehicle-type control.
- A countdown on the customer’s repair screen.
- Turning any location switch on.
- A second database, or multi-tenant changes.
- Rewriting the Staff manual. Do that after the Attendance page exists.

## Risks

- Shipping the duty card without the staff list, or turning Attendance on in between, collects days nobody can check. Land both, then leave the switch off until someone chooses to enable it.
- Staff can mistake Locations for attendance. The last point has no start, no end, and no geofence result, and it is replaced on the next job open.
- A position from the morning still feeds travel time until the next open. This slice does not change that.
- A wrong office pin makes real starts look outside. The radius and pin stay admin-only for that reason.
- Auto end at 19:00 can close the day while a repair is still in progress. The job continues. The next India date is a new day.
- Counting a wide or mocked fix as Inside would show someone at the shop when the phone cannot say that.
- Applying the 90-day retention to `attendance_days` deletes the monitoring record.
- The older GPS task list named this table V16. That version is already `technician_presence`.

## Build order, when this is approved

1. V18 and the start, end, today, and list APIs. Attendance remains off, and start writes nothing while it is off.
2. Auto end at the stored India time.
3. Technician duty card, hidden while Attendance is off.
4. Staff and Admin list on the web and the phone, plus the spreadsheet download.
5. Staff manual, after the list is on screen.
