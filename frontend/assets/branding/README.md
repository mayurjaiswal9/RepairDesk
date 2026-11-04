# Login branding

The bundled logo is a transparent PNG of the Ace Telecom mark. It sits on the
light login gradient, and the same artwork is the Android launcher icon.

Default logo on the Ace Telecom branch: `login-logo.png`.
The screen after launch is named Ace Smart Care - Customer Service APP.
The icon label is Ace Smart Care.

## Change for another customer

**Option A — replace the file**  
Overwrite `login-logo.png` with the customer's logo (PNG/SVG-as-PNG, preferably transparent).

**Option B — remote URL (no asset change)**  
Build with:

```bash
flutter build apk --release \
  --dart-define=BRAND_LOGO_URL=https://cdn.example.com/customer-logo.png \
  --dart-define=BRAND_NAME="Customer Name" \
  --dart-define=PRODUCT_NAME=RepairDesk
```

## Company code

The app has no company code field. Each customer APK is built with its own
code, and the default is `ace`:

```bash
flutter build apk --release --dart-define=COMPANY_CODE=ace
```

## Ace Telecom production build

Release builds are signed with the key named in `android/key.properties`
(git-ignored). Without that file the build falls back to the debug key, and
that APK must not be given to customers.

```bash
flutter build apk --release --build-name=1.1.0 --build-number=2 \
  --dart-define=COMPANY_CODE=ace \
  --dart-define=API_BASE_URL=https://repairdesk-api-dev.politeground-f86bf509.centralindia.azurecontainerapps.io/api/v1
```

Raise `--build-number` for every APK you hand out, or phones will not
accept it as an update.
