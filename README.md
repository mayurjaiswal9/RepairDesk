# RepairDesk

Mobile-first repair-shop management application (V1) built for ACE Telecom.

- **Frontend:** Flutter (Android-first) — `frontend/`
- **Backend:** Spring Boot REST API — `backend/`
- **Database:** PostgreSQL
- **Docs:** product, architecture, database, API, tasks, and setup — `docs/`

## Repository layout

```
repairdesk/
  docs/        # PRD, Architecture, Database, API, Tasks, Getting-Started
  backend/     # Spring Boot project
  frontend/    # Flutter app
```

## Getting started

See [`docs/Getting-Started.md`](docs/Getting-Started.md) for required tools, environment setup, and the ordered next steps. The implementation plan lives in [`docs/Tasks.md`](docs/Tasks.md).

## Local database (PostgreSQL via Docker)

```bash
docker run --name repairdesk-postgres \
  -e POSTGRES_USER=repairdesk \
  -e POSTGRES_PASSWORD=repairdesk \
  -e POSTGRES_DB=repairdesk \
  -p 5432:5432 \
  -v repairdesk_pgdata:/var/lib/postgresql/data \
  -d postgres:16
```

Connection URL: `jdbc:postgresql://localhost:5432/repairdesk` (user `repairdesk`, password `repairdesk`).
