# Project Constitution

## Purpose

This document defines mandatory architectural and development rules for the RepairDesk application.

All AI-generated code must comply with these rules.

---

## Technology Stack

Frontend:

* Flutter

Backend:

* Spring Boot

Database:

* PostgreSQL

Authentication:

* JWT

---

## Frontend Principles

* Use Feature-First architecture.
* Use Riverpod for state management.
* Use GoRouter for navigation.
* Use Dio for HTTP communication.
* Separate UI from business logic.
* Do not place API calls directly in widgets.

---

## Backend Principles

* Follow Controller → Service → Repository architecture.
* Business logic belongs in Services.
* Controllers should remain thin.
* Repository layer handles persistence only.

---

## API Principles

* REST APIs only.
* JSON request/response format.
* Consistent error response structure.
* Version APIs under /api/v1.

---

## Database Principles

* Use Flyway migrations.
* No schema changes without migration scripts.
* Use UUID identifiers where appropriate.

---

## Security Principles

* JWT authentication required.
* Role-based authorization required.
* Never expose sensitive information in API responses.

---

## Testing Principles

* New features require tests.
* Critical business logic requires unit tests.

---

## Documentation Principles

Changes to architecture must update:

* PRD.md
* Architecture.md
* API.md

when applicable.
