# Login branding

Default logo: `login-logo.png` (ACE Telecom), served at `/branding/login-logo.png`.

## Change for another customer

**Option A — replace the file**  
Overwrite `login-logo.png`.

**Option B — point at another URL**  
In `src/environments/environment.prod.ts` (or `.ts` for local):

```ts
branding: {
  logoUrl: 'https://cdn.example.com/customer-logo.png',
  brandName: 'Customer Name',
  productName: 'RepairDesk Admin Panel',
  poweredBy: 'Megagon Technologies Pvt Ltd',
}
```

The logo is composited with `mix-blend-mode: multiply`, so a logo on a white
plate blends into the gradient without needing a transparent PNG.
```
