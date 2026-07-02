# RepairDesk — Admin & Developer User Guide

## 1. Default Admin Credentials

| Field    | Value        |
|----------|--------------|
| Username | `admin`      |
| Password | `Admin@1234` |
| Role     | ADMIN        |

> The admin account is seeded automatically the first time the backend starts against a fresh database.
> The password is kept static for now — it can be changed later via the **Forgot Password** flow once that screen is built.

---

## 2. Logging In (Admin)

1. Launch the app on the emulator or physical device.
2. On the **Sign In** screen enter:
   - **Username:** `admin`
   - **Password:** `Admin@1234`
3. Tap **Sign In**.
4. You will be taken to the **Dashboard** screen.

---

## 3. Configuring the Frontend for Different Environments

The Flutter app reads the backend URL at **build time** from the `API_BASE_URL` dart-define variable. The default value (used when nothing is specified) is the Android emulator loopback address.

### 3.1 Environment Files

Two pre-configured JSON files live in `frontend/`:

| File | Points to |
|------|-----------|
| `.env.local.json` | `http://10.0.2.2:8080/api/v1` — local backend via **Android emulator** |
| `.env.server.json` | `https://api.repairdesk.acetelecom.com/api/v1` — production server |

Update `.env.server.json` with the real server domain once deployed.

---

### 3.2 Running from VS Code / Cursor

Three launch configurations are defined in `frontend/.vscode/launch.json`:

| Configuration | Target |
|---------------|--------|
| **RepairDesk (Local BE — Emulator)** | Android emulator → `localhost:8080` |
| **RepairDesk (Local BE — Physical Device)** | Physical Android device on the same Wi-Fi |
| **RepairDesk (Server / Production)** | Release build → production server |

**To use:**
1. Open the `frontend/` folder in VS Code / Cursor.
2. Press `F5` (or open the **Run & Debug** panel).
3. Select the desired configuration from the dropdown and click the play button.

---

### 3.3 Running from the Command Line

**Android emulator (default — no extra flags needed):**
```bash
cd frontend
flutter run
```

**Android emulator with explicit env file:**
```bash
flutter run --dart-define-from-file=.env.local.json
```

**Physical Android device on your Wi-Fi:**

First find your PC's local IP:
```powershell
ipconfig | findstr "IPv4"
# e.g. 192.168.1.50
```

Then run:
```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:8080/api/v1
```

**Production / server build:**
```bash
flutter build apk --dart-define-from-file=.env.server.json
```

---

### 3.4 How It Works

In `lib/core/network/api_client.dart`:

```dart
const _baseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080/api/v1',
);
```

`String.fromEnvironment` is resolved at **compile time**, so the URL is baked into the binary. This means:
- A debug run and a release APK built with the same flag point to the same backend.
- No runtime file reading or environment variables are required on the device.

---

### 3.5 Starting the Local Backend

Before running with a local backend:

1. Start PostgreSQL:
   ```powershell
   docker start repairdesk-postgres
   ```

2. Start the Spring Boot backend from `C:\Codebase\backend`:
   ```powershell
   mvn spring-boot:run
   ```

3. Confirm it is healthy:
   ```powershell
   curl http://localhost:8080/api/v1/ping
   # {"status":"ok","service":"repairdesk"}
   ```

The backend runs on **port 8080**. The emulator address `10.0.2.2` automatically routes to your host machine's `localhost`, so no extra configuration is needed for emulator builds.

---

## 4. Quick Reference

| Item | Value |
|------|-------|
| Backend base path | `http://localhost:8080/api/v1` |
| Emulator → host loopback | `http://10.0.2.2:8080/api/v1` |
| Admin username | `admin` |
| Admin password | `Admin@1234` |
| Database host | `localhost:5432` |
| Database name | `repairdesk` |
| DB username / password | `repairdesk / repairdesk` |
