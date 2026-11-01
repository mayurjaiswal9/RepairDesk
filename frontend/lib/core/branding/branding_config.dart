/// Customer-facing brand shown on unauthenticated screens (login, etc.).
///
/// Swap for another customer by either:
/// 1. Replacing [logoAsset] (`assets/branding/login-logo.png`), or
/// 2. Passing `--dart-define=BRAND_LOGO_URL=https://…/logo.png` (and optional
///    `BRAND_NAME` / `PRODUCT_NAME`) at build time — no asset replace needed.
class BrandingConfig {
  const BrandingConfig._();

  /// Bundled logo used when [logoUrl] is empty.
  static const logoAsset = String.fromEnvironment(
    'BRAND_LOGO_ASSET',
    defaultValue: 'assets/branding/login-logo.png',
  );

  /// Optional remote logo URL. When set, takes precedence over [logoAsset].
  static const logoUrl = String.fromEnvironment('BRAND_LOGO_URL', defaultValue: '');

  static const brandName = String.fromEnvironment(
    'BRAND_NAME',
    defaultValue: 'ACE Telecom',
  );

  /// Short name under the Ace Telecom icon.
  static const appLabel = String.fromEnvironment(
    'APP_LABEL',
    defaultValue: 'Ace Smart Care',
  );

  /// Name shown on the screen after the app launches. Ace Telecom only.
  static const productName = String.fromEnvironment(
    'PRODUCT_NAME',
    defaultValue: 'Ace Smart Care - Customer Service APP',
  );

  /// Software partner credited at the foot of the auth screens. Set to an
  /// empty string to hide the line entirely.
  static const poweredBy = String.fromEnvironment(
    'POWERED_BY',
    defaultValue: 'Megagon Technologies Pvt Ltd',
  );

  static bool get hasRemoteLogo => logoUrl.trim().isNotEmpty;
}
