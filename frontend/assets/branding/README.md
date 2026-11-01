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
