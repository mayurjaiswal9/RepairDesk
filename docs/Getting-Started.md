# RepairDesk — Getting Started: Required Tools & Next Steps

**Product:** RepairDesk (built for ACE Telecom)
**Document Type:** Developer Setup & Onboarding Guide
**Version:** 1.0
**Audience:** The developer setting up the V1 build environment (Windows).

> This guide lists the tools you must install to build and run RepairDesk V1 (Flutter mobile app + Spring Boot backend + PostgreSQL), how to set up an emulator to run the app, and the recommended next steps. It complements [Tasks.md](Tasks.md), which contains the actual implementation plan.

---

# 1. What you are building (recap)

- **Frontend:** a Flutter mobile app (Android first on Windows; iOS later on a Mac; Web later).
- **Backend:** a single Spring Boot REST API.
- **Database:** PostgreSQL.
- See [Architecture.md](Architecture.md) for the full picture.

**Important platform note (Windows):** You can build and run the **Android** app and the **backend** entirely on Windows. **iOS builds require a macOS machine with Xcode** — that is out of scope for your current setup and is a later/optional target.

---

# 2. Required Tools

Install these in order. Versions are "known-good" recommendations; newer stable releases are usually fine.

## 2.1 Core / shared

| Tool | Why you need it | Recommended version | Where to get it |
| --- | --- | --- | --- |
| **Git** | Version control for the codebase. | Latest stable | git-scm.com |
| **A code editor / IDE** | Writing code. Use **VS Code** (light, great for Flutter) and/or **IntelliJ IDEA Community** (great for Spring Boot). | Latest | code.visualstudio.com / jetbrains.com |

## 2.2 Frontend — Flutter (Android)

| Tool | Why you need it | Recommended version | Where to get it |
| --- | --- | --- | --- |
| **Flutter SDK** | The Flutter framework + CLI (`flutter` command). Includes the Dart SDK. | Latest **stable** channel | docs.flutter.dev (Windows install) |
| **Android Studio** | Provides the **Android SDK**, **device/emulator manager (AVD)**, and build tooling Flutter needs. | Latest | developer.android.com/studio |
| **Android SDK + Platform-Tools + Command-line Tools** | Compiling/running the Android app; `adb`. (Installed via Android Studio's SDK Manager.) | API level **34 or 35** | via Android Studio → SDK Manager |
| **Android Emulator (AVD)** *or* a **physical Android phone** | To actually run the app. | System image API 34/35 | via Android Studio → Device Manager |
| **VS Code Flutter & Dart extensions** (if using VS Code) | Editor support, run/debug, hot reload. | Latest | VS Code Marketplace |
| **Chrome** (optional, for later Web target) | Run Flutter Web during development. | Latest | google.com/chrome |

> Dart does **not** need a separate install — it ships inside the Flutter SDK.

## 2.3 Backend — Spring Boot

| Tool | Why you need it | Recommended version | Where to get it |
| --- | --- | --- | --- |
| **JDK (Java Development Kit)** | Compiles/runs Spring Boot. | **Java 21 (LTS)** | Eclipse Temurin (adoptium.net) |
| **Build tool** | Builds the backend. Use the **Maven or Gradle wrapper** generated with the project (no separate global install required). | Bundled wrapper | via Spring Initializr (start.spring.io) |
| **IntelliJ IDEA Community** (recommended) | Best Spring Boot DX (run, debug, inspect). | Latest | jetbrains.com/idea |

> You will generate the backend project from **Spring Initializr** (start.spring.io) with: Spring Web, Spring Security, Spring Data JPA, Validation, Flyway Migration, PostgreSQL Driver, Lombok (optional), Actuator.

## 2.4 Database & containers

| Tool | Why you need it | Recommended version | Where to get it |
| --- | --- | --- | --- |
| **PostgreSQL** | The database. Install the server **or** run it via Docker (below). | **16.x** | postgresql.org/download/windows |
| **Docker Desktop** (recommended) | Easiest way to run PostgreSQL locally; **required for Testcontainers** integration tests (Phase 4). | Latest | docker.com/products/docker-desktop |
| **pgAdmin** or **DBeaver** (optional) | GUI to browse the database. | Latest | pgadmin.org / dbeaver.io |

> **Simplest path:** install Docker Desktop and run PostgreSQL in a container — no manual DB install needed, and it doubles for the integration tests.

## 2.5 API testing (pick one)

| Tool | Why | Where |
| --- | --- | --- |
| **Postman** (recommended) | Manually call/test the REST API while building. | postman.com |
| **Insomnia** or **curl/httpie** | Lightweight alternatives. | insomnia.rest / curl.se |

---

# 3. Mobile Emulator / Device Setup (how to run the app)

You need **one** of the following to see the app run.

## Option A — Android Emulator (no physical phone needed)
1. Install **Android Studio**.
2. Open **Android Studio → More Actions → SDK Manager**, install:
   - **Android SDK Platform** (API 34 or 35),
   - **Android SDK Platform-Tools**,
   - **Android SDK Command-line Tools**,
   - an **Emulator** + a **system image** (API 34/35, x86_64).
3. Open **Device Manager → Create Device** → pick e.g. **Pixel 7** → choose the API 34/35 system image → Finish.
4. **Enable hardware acceleration** (required for a usable emulator):
   - Ensure **virtualization** is enabled in your PC's BIOS/UEFI.
   - On Windows, the emulator uses **WHPX/Hyper-V** or the **Android Emulator Hypervisor Driver** (Android Studio prompts/installs this).
5. Press **▶** on the virtual device to boot it.

## Option B — Physical Android phone (often faster/smoother)
1. On the phone: **Settings → About phone →** tap **Build number** 7 times to unlock **Developer options**.
2. In **Developer options**, enable **USB debugging**.
3. Connect via USB and **accept the debugging prompt** on the phone.
4. Verify it's detected (it will appear as a target in `flutter devices`).

## Option C — Windows desktop / Chrome (quick smoke checks only)
- You can run the Flutter app as a **Windows desktop** app or in **Chrome** for fast UI checks during development, but **Android is the primary V1 target** and should be your main test surface.

---

# 4. Verify Your Setup

After installing, run these (PowerShell) and resolve anything flagged:

```powershell
git --version
java -version            # should report Java 21
flutter --version        # Flutter stable + bundled Dart
flutter doctor           # MUST be green for Android toolchain + a device
flutter doctor --android-licenses   # accept all Android SDK licenses
flutter devices          # your emulator/phone should be listed
docker --version         # if using Docker for PostgreSQL/tests
```

**Goal:** `flutter doctor` shows checkmarks for **Flutter**, **Android toolchain**, and a connected **device/emulator**. (The "iOS toolchain" line will be unavailable on Windows — that's expected.)

---

# 5. Recommended Repository Layout

Keep one repository with clearly separated apps and the existing docs:

```
repairdesk/
  docs/            # already present (PRD, Architecture, Database, API, Tasks, this guide...)
  backend/         # Spring Boot project (generated from Spring Initializr)
  frontend/        # Flutter app (flutter create)
  README.md        # top-level: how to run backend + frontend
  .gitignore
```

---

# 6. Next Steps (do these in order)

These map directly to **Phase 1** of [Tasks.md](Tasks.md). Do not skip the verification step.

1. **Install all tools** in §2 and complete §3 (emulator or device).
2. **Verify** with §4 (`flutter doctor` green; a device listed; Java 21; Docker if used).
3. **Initialize the repo:** create the `repairdesk/` structure in §5, `git init`, commit the existing `docs/`.
4. **Provision PostgreSQL:** start a local Postgres (Docker recommended) and create an empty `repairdesk` database. Note the connection URL/credentials for backend config.
5. **Scaffold the backend** (Tasks **PROJ-001 → PROJ-003**): generate from Spring Initializr with the dependencies in §2.3; create the package-by-feature structure; confirm it starts and `/health` responds.
6. **Wire DB + Flyway and run migrations** (Tasks **DB-001 → DB-008**): point the app at PostgreSQL; author and apply the V1–V7 migrations from [Database.md](Database.md); confirm the default admin (`admin` / `Admin@1234`) is seeded.
7. **Scaffold the Flutter app** (Tasks **PROJ-010 → PROJ-011**): `flutter create` in `frontend/`, add Riverpod/GoRouter/Dio/Freezed/build_runner, set up the folder structure, and run it on your emulator/device.
8. **Build the frontend foundation** (Tasks **THEME-001, CORE-010–013, NAV-001–003, SHARED-001**): theme, Dio + interceptors, error/session handling, router + role guards.
9. **Build the auth infrastructure & feature** (Tasks **AUTH-002–005**, then **AUTH-010–023**): JWT, security, login/register/reset, auto-login. **Milestone:** log in as `admin` from the app end-to-end.
10. **Proceed through Tasks.md phases** in order: Phase 2 (core features) → Phase 3 (operational) → Phase 4 (quality/tests) → Phase 5 (release).

**Set up Postman** (or your chosen API tool) once the backend is running so you can test each endpoint as you build it.

---

# 7. Quick Reference

| Item | Value |
| --- | --- |
| **Default admin login** | username `admin`, password `Admin@1234` (must change on first login) |
| **Default admin seed mobile** | `0000000000` (placeholder; update after first login) |
| **API base path** | `/api/v1` (see [API.md](API.md)) |
| **DB name (suggested)** | `repairdesk` |
| **Java version** | 21 (LTS) |
| **Android API level** | 34 or 35 |
| **Primary run target (Windows)** | Android emulator or physical Android device |
| **iOS** | Requires macOS + Xcode (later/optional) |

---

# 8. Optional / Later Tools

| Tool | When |
| --- | --- |
| **Xcode on a Mac** | Only when you add the iOS target. |
| **Chrome + Flutter Web** | When you enable the future Web target. |
| **Nginx + Let's Encrypt (certbot)** | Phase 5 deployment (TLS reverse proxy) — see [Tasks.md](Tasks.md) REL-001. |
| **CI/CD (e.g., GitHub Actions)** | Post-V1 automation (not required to ship V1). |

---

*Once §6 steps 1–9 are done, you'll have a running app that can log in against the backend — the foundation for everything in [Tasks.md](Tasks.md).*
