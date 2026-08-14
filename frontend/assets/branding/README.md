# Login branding

The logo is composited with `BlendMode.multiply`, so a logo supplied on a white
plate blends into the gradient without needing a transparent PNG.

Default logo: `login-logo.png` (ACE Telecom).

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
