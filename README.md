# RepairDesk

Mobile-first repair-shop management application (V1) built for ACE Telecom.

- **Frontend (mobile):** Flutter (Android-first) — `frontend/`
- **Admin panel (web):** Angular — `admin-web/` *(specified, not yet scaffolded)*
- **Backend:** Spring Boot REST API — `backend/`
- **Database:** PostgreSQL
- **Docs:** product, architecture, database, API, tasks, and setup — `docs/`

## Repository layout

```
repairdesk/
  docs/        # PRD, Architecture, Database, API, Tasks, Getting-Started, admin-panel/
  backend/     # Spring Boot project
  frontend/    # Flutter app
  admin-web/   # Angular admin panel (planned)
```

## Getting started

See [`docs/Getting-Started.md`](docs/Getting-Started.md) for required tools, environment setup, and the ordered next steps. The implementation plan lives in [`docs/Tasks.md`](docs/Tasks.md).

**Admin panel (Angular):** the UI specification set lives in [`docs/admin-panel/`](docs/admin-panel/README.md) — information architecture and route map, design system, and one spec per page with element inventories and acceptance criteria. Read [`API-Contract-Gaps.md`](docs/admin-panel/API-Contract-Gaps.md) before implementing.

**Deployment:**

- VM + Docker Compose: [`docs/Deployment-Runbook.md`](docs/Deployment-Runbook.md)
- Azure Container Apps (backend): [`docs/Azure-Container-Apps-Deployment.md`](docs/Azure-Container-Apps-Deployment.md)

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
