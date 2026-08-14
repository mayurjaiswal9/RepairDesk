import { environment } from '../../../environments/environment';

/**
 * Customer-facing brand for unauthenticated screens (login, change-password).
 *
 * Swap for another customer by editing `environment*.ts`:
 * - `branding.logoUrl` — path or absolute URL to the logo
 * - `branding.brandName` / `branding.productName` — display labels
 *
 * Or replace `public/branding/login-logo.png` and keep the default path.
 */
export const branding = {
  logoUrl: environment.branding?.logoUrl ?? '/branding/login-logo.png',
  brandName: environment.branding?.brandName ?? 'ACE Telecom',
  productName: environment.branding?.productName ?? 'Field Service Management',
  poweredBy: environment.branding?.poweredBy ?? 'Megagon Technologies Pvt Ltd',
} as const;
