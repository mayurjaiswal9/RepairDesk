# RepairDesk V1 — Implementation Progress

**Last updated:** 2026-07-02  
**Source of truth:** [Tasks.md](Tasks.md)

---

## Status legend

| Symbol | Meaning |
|---|---|
| ✅ | Implemented and committed |
| 🔄 | In progress |
| ⏳ | Pending |
| ➖ | Deferred / out of scope for V1 |

---

## Phase 1 — Foundation

| ID | Task | Layer | Status |
|---|---|---|---|
| PROJ-001 | Spring Boot scaffold | BE | ✅ |
| PROJ-002 | Configuration & profiles | BE | ✅ |
| PROJ-003 | Actuator health | BE | ✅ |
| PROJ-010 | Flutter scaffold & folder structure | FE | ✅ |
| PROJ-011 | Flutter dependencies & codegen | FE | ✅ |
| DB-001 | PostgreSQL + Flyway wiring | INFRA | ✅ |
| DB-002 | Migration V1 — core accounts & settings | BE | ✅ |
| DB-003 | Migration V2 — repair_requests | BE | ✅ |
| DB-004 | Migration V3 — jobs | BE | ✅ |
| DB-005 | Migration V4 — appointments | BE | ✅ |
| DB-006 | Migration V5 — attachments, notifications, audit | BE | ✅ |
| DB-007 | Migration V6 — seed reference data | BE | ✅ |
| DB-008 | Migration V7 — seed default admin | BE | ✅ |
| CORE-001 | Standard error model & global exception handler | BE | ✅ |
| CORE-002 | Correlation-id filter & logging | BE | ✅ |
| CORE-003 | Base entity conventions (UUID, timestamps, version) | BE | ✅ |
| CORE-004 | Pagination & response conventions | BE | ✅ |
| AUDIT-001 | Audit event entity & AuditService | BE | ✅ |
| AUTH-002 | Password encoder | BE | ✅ |
| AUTH-003 | JWT service (mint/validate) | BE | ✅ |
| AUTH-004 | JWT auth filter + account-status check | BE | ✅ |
| AUTH-005 | Security config & CurrentUser accessor | BE | ✅ |
| THEME-001 | Theme & responsive foundations | FE | ✅ |
| CORE-010 | Dio client & interceptors | FE | ✅ |
| CORE-011 | Failure types & error mapping | FE | ✅ |
| CORE-012 | Secure token storage | FE | ✅ |
| CORE-013 | Session/auth state provider | FE | ✅ |
| SHARED-001 | Shared UI components | FE | ✅ |
| NAV-001 | GoRouter configuration | FE | ✅ |
| NAV-002 | Route guards (auth + role) | FE | ✅ |
| NAV-003 | Role-based landing resolution | FE | ✅ |

---

## Phase 2 — Core Features

### Authentication

| ID | Task | Layer | Status |
|---|---|---|---|
| AUTH-010 | Register endpoint | BE | ✅ |
| AUTH-011 | Login endpoint | BE | ✅ |
| AUTH-012 | Logout endpoint | BE | ✅ |
| AUTH-013 | Self profile & account settings endpoints | BE | ✅ |
| AUTH-020 | Login screen | FE | ✅ |
| AUTH-021 | Register screen | FE | ✅ |
| AUTH-022 | Auto-login & logout wiring | FE | ✅ |
| AUTH-023 | Account settings screen | FE | ✅ |

### Shared Read Dependencies

| ID | Task | Layer | Status |
|---|---|---|---|
| PROD-001 | Device category entity, repo & active-list endpoint | BE | ✅ |
| SET-001 | Settings entity, repo & scheduling-config endpoint | BE | ✅ |

### Notifications Core

| ID | Task | Layer | Status |
|---|---|---|---|
| NOTIF-001 | Notification entity, repo & service | BE | ✅ |
| NOTIF-002 | Customer inbox endpoints | BE | ✅ |
| NOTIF-003 | Dashboard counters endpoint | BE | ✅ |
| NOTIF-020 | Notification inbox UI & bell badge | FE | ✅ |
| NOTIF-021 | Polling controller (30s foreground) | FE | ✅ |

### File Attachments

| ID | Task | Layer | Status |
|---|---|---|---|
| FILE-001 | StorageService abstraction | BE | ✅ |
| FILE-002 | Request attachment upload & validation | BE | ✅ |
| FILE-003 | Authenticated attachment streaming | BE | ✅ |
| REQ-021 | Attachment picker & upload UI | FE | ✅ |

### Repair Request

| ID | Task | Layer | Status |
|---|---|---|---|
| REQ-001 | RepairRequest entity & repository | BE | ✅ |
| REQ-002 | Create request + reference generation | BE | ✅ |
| REQ-003 | Get/list own requests | BE | ✅ |
| REQ-004 | Edit/cancel request (editable window) | BE | ✅ |
| REQ-020 | Repair request form UI | FE | ✅ |
| REQ-022 | Submit, confirmation & error handling | FE | ✅ |

### Request Review

| ID | Task | Layer | Status |
|---|---|---|---|
| RV-001 | Review queue list & filters | BE | ✅ |
| RV-002 | Approve → auto-create job | BE | ✅ |
| RV-003 | Reject workflow | BE | ✅ |
| RV-004 | Request-info workflow | BE | ✅ |
| RV-005 | Priority, internal notes & history | BE | ✅ |
| RV-020 | Requests queue UI | FE | ✅ |
| RV-021 | Request detail + attachment review UI | FE | ✅ |
| RV-022 | Decision actions UI | FE | ✅ |

### Job Management

| ID | Task | Layer | Status |
|---|---|---|---|
| JOB-001 | Job entity & repository | BE | ✅ |
| JOB-002 | Job list & detail (role-scoped) | BE | ✅ |
| JOB-003 | Assign / unassign technician | BE | ✅ |
| JOB-004 | Status transition actions | BE | ✅ |
| JOB-005 | Cancel job | BE | ✅ |
| JOB-006 | Priority, warranty, notes & attachments | BE | ✅ |
| JOB-020 | Jobs list UI | FE | ✅ |
| JOB-021 | Job detail UI | FE | ✅ |
| JOB-022 | Assign & status action UI | FE | ✅ |
| JOB-023 | Notes & attachments UI (job) | FE | ✅ |

### Repair Status Tracking (Customer)

| ID | Task | Layer | Status |
|---|---|---|---|
| TRACK-001 | Customer Home list endpoint | BE | ✅ |
| TRACK-002 | Repair detail endpoint | BE | ✅ |
| TRACK-020 | Customer Home UI | FE | ✅ |
| TRACK-021 | Repair detail UI | FE | ✅ |

### Dashboard & Landings

| ID | Task | Layer | Status |
|---|---|---|---|
| DASH-001 | Counters endpoint | BE | ✅ |
| DASH-020 | Staff/Admin dashboard hub UI | FE | ✅ |
| DASH-021 | Technician landing (jobs list) | FE | ✅ |

---

## Phase 3 — Operational Features

### Scheduling

| ID | Task | Layer | Status |
|---|---|---|---|
| SCH-001 | Appointment entity & repository | BE | ✅ |
| SCH-002 | Create appointment + validation | BE | ✅ |
| SCH-003 | Reschedule appointment | BE | ✅ |
| SCH-004 | Cancel appointment | BE | ✅ |
| SCH-005 | Calendar list & technician schedule | BE | ✅ |
| SCH-020 | Calendar view UI (weekly) | FE | ✅ |
| SCH-021 | Create/reschedule/cancel UI | FE | ✅ |
| SCH-022 | Technician schedule view | FE | ✅ |
| SCH-023 | Unscheduled-jobs indicator | FE | ➖ |

### Customer Management

| ID | Task | Layer | Status |
|---|---|---|---|
| CUST-001 | Customer search & list | BE | ✅ |
| CUST-002 | Customer profile & history | BE | ✅ |
| CUST-020 | Customer search & profile UI | FE | ✅ |

### Product Management

| ID | Task | Layer | Status |
|---|---|---|---|
| PROD-002 | Category management endpoints | BE | ✅ |
| PROD-020 | Product management UI | FE | ✅ |

### Staff Management

| ID | Task | Layer | Status |
|---|---|---|---|
| STAFF-001 | User list & search | BE | ✅ |
| STAFF-002 | Create user | BE | ✅ |
| STAFF-003 | Edit user & assign role | BE | ✅ |
| STAFF-004 | Enable/disable & delete | BE | ✅ |
| STAFF-005 | Reset password & technician roster | BE | ✅ |
| STAFF-006 | Technician roster service hook | BE | ✅ |
| STAFF-020 | User list UI | FE | ✅ |
| STAFF-021 | Create/edit user UI | FE | ✅ |
| STAFF-022 | Enable/disable/delete UI | FE | ✅ |

### Settings

| ID | Task | Layer | Status |
|---|---|---|---|
| SET-002 | Settings & working-hours update | BE | ✅ |
| SET-020 | Settings UI (read + edit) | FE | ✅ |
| SET-021 | Working-hours editor UI | FE | ✅ |

---

## Phase 4 — Quality

### Backend Tests

| ID | Task | Status |
|---|---|---|
| TEST-001 | Auth & JWT unit tests | ✅ |
| TEST-002 | Request lifecycle unit tests | ✅ |
| TEST-003 | Job lifecycle unit tests | ✅ |
| TEST-004 | Scheduling validation unit tests | ⏳ |
| TEST-005 | Staff management unit tests | ⏳ |
| TEST-006 | Notification rules unit tests | ✅ |
| TEST-007 | Security integration tests | ⏳ |
| TEST-008 | Endpoint integration tests (happy path) | ⏳ |
| TEST-009 | Migration & repository tests | ⏳ |

### Frontend Tests

| ID | Task | Status |
|---|---|---|
| TEST-020 | Provider/repository/mapper unit tests | ⏳ |
| TEST-021 | Error-mapping & validation unit tests | ⏳ |
| TEST-022 | Auth & routing widget tests | ⏳ |
| TEST-023 | Customer experience widget tests | ⏳ |
| TEST-024 | Operational widget tests | ⏳ |

### Error Handling Hardening

| ID | Task | Status |
|---|---|---|
| ERR-001 | Frontend resilience polish | ⏳ |
| ERR-002 | Backend error-contract coverage | ⏳ |

---

## Phase 5 — Release Preparation

| ID | Task | Status |
|---|---|---|
| SEC-001 | RBAC & ownership review | ⏳ |
| SEC-002 | File upload security review | ⏳ |
| SEC-003 | Sensitive-data & logging review | ⏳ |
| SEC-004 | Default-admin & credential handover | ⏳ |
| PERF-001 | Performance review (load targets) | ⏳ |
| PERF-002 | Polling efficiency review | ⏳ |
| DOC-001 | Documentation consistency pass | ⏳ |
| DOC-002 | API documentation (OpenAPI/Swagger) | ✅ |
| DOC-003 | Deployment & operations runbook | ⏳ |
| REL-001 | Deployment setup (single env, TLS) | ⏳ |
| REL-002 | Backup & restore verification | ⏳ |
| REL-003 | Build artifacts (clients + server) | ⏳ |
| REL-004 | Release checklist & smoke tests | ⏳ |

---

## Summary

| Phase | Total Tasks | Done | Pending |
|---|---|---|---|
| Phase 1 — Foundation | 30 | 30 | 0 |
| Phase 2 — Core Features | 44 | 44 | 0 |
| Phase 3 — Operational | 21 | 20 | 1 |
| Phase 4 — Quality | 15 | 4 | 11 |
| Phase 5 — Release Prep | 13 | 1 | 12 |
| **Total** | **123** | **99** | **24** |

**Overall completion: ~80%**

### Remaining work to reach production-ready V1

1. **Automated tests** — 11 test tasks (backend unit + integration, frontend widget tests)
2. **Security review** — RBAC audit, file security, sensitive-data check, admin handover
3. **Deployment** — server setup, Nginx/TLS, backup, release build, smoke test
4. **Performance review** — load target validation, slow query check
5. **Docs consistency pass** — reconcile design docs with implementation
6. **SCH-023** — unscheduled-jobs indicator (minor)
