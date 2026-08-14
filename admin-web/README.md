# RepairDesk Admin Panel (`admin-web`)

Angular web admin client for RepairDesk. Consumes the same `/api/v1` backend as the Flutter mobile app.

**Specs:** [`docs/admin-panel/`](../docs/admin-panel/README.md)

## Prerequisites

- Node.js 20+ and npm
- Backend running locally on `http://localhost:8080` (or set `environment.apiBaseUrl`)
- CORS must allow `http://localhost:4200` (included in backend `application.yml` defaults)

## Commands

```bash
cd admin-web
npm install
npm start          # http://localhost:4200
npm run build      # production build
```

## Current status (Batch 1)

| Area | Status |
| --- | --- |
| Project scaffold | Done |
| Sign in + forced change-password + forgot password | Done (backend-integrated) |
| App shell / Dashboard / Requests | Placeholder / not started |

Default admin (local seed): `admin` / `Admin@1234` (change on first login if `mustChangePassword` is set).

## Token storage

JWT is held in memory with `sessionStorage` as the reload fallback (see `SessionService`). Closing the browser ends the session.
