import 'package:flutter/material.dart';

import '../../core/branding/branding_config.dart';
import '../theme/app_theme.dart';

/// Gradient + wave backdrop shared by every unauthenticated screen.
/// The admin web app renders the same gradient and wave shape, so the two
/// login pages read as one product.
class BrandBackdrop extends StatelessWidget {
  const BrandBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFF4F9FF), Color(0xFFFFFFFF), Color(0xFFF2F8FF)],
          stops: [0.0, 0.42, 1.0],
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 210,
            child: CustomPaint(painter: _WavePainter()),
          ),
          Positioned.fill(child: child),
        ],
      ),
    );
  }
}

class _WavePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;

    final back = Path()
      ..moveTo(0, h * 0.45)
      ..cubicTo(w * 0.18, h * 0.16, w * 0.40, h * 0.74, w * 0.63, h * 0.56)
      ..cubicTo(w * 0.82, h * 0.41, w * 0.92, h * 0.22, w, h * 0.34)
      ..lineTo(w, h)
      ..lineTo(0, h)
      ..close();
    canvas.drawPath(back, Paint()..color = AppTheme.waveLight);

    final front = Path()
      ..moveTo(0, h * 0.80)
      ..cubicTo(w * 0.22, h * 0.55, w * 0.44, h * 0.97, w * 0.69, h * 0.83)
      ..cubicTo(w * 0.85, h * 0.74, w * 0.93, h * 0.60, w, h * 0.67)
      ..lineTo(w, h)
      ..lineTo(0, h)
      ..close();
    canvas.drawPath(
      front,
      Paint()..color = AppTheme.waveMid.withValues(alpha: 0.75),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Logo, product name, brand name and tagline — the shared header block.
class BrandHeader extends StatelessWidget {
  const BrandHeader({super.key, this.tagline = 'Sign in to continue'});

  final String tagline;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const BrandLogo(),
        const SizedBox(height: 8),
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            BrandingConfig.productName,
            maxLines: 1,
            softWrap: false,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.4,
              height: 1.15,
              color: AppTheme.brandNavy,
            ),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          tagline,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 14, color: AppTheme.textSecondary),
        ),
      ],
    );
  }
}

/// Configurable customer logo — remote URL if set, otherwise bundled asset.
/// The bundled PNG is cropped with a transparent plate so it sits on the
/// gradient without a white box.
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.width = 168});

  final double width;

  @override
  Widget build(BuildContext context) {
    // Source art is landscape (~2.4:1). Cap both axes so it cannot blow up
    // to the PNG's intrinsic 640px width on device.
    final height = width * 0.42;
    return Center(
      child: SizedBox(
        width: width,
        height: height,
        child: BrandingConfig.hasRemoteLogo
            ? Image.network(
                BrandingConfig.logoUrl,
                width: width,
                height: height,
                fit: BoxFit.contain,
                alignment: Alignment.center,
                filterQuality: FilterQuality.high,
                errorBuilder: (_, _, _) => _AssetLogo(width: width, height: height),
              )
            : _AssetLogo(width: width, height: height),
      ),
    );
  }
}

class _AssetLogo extends StatelessWidget {
  const _AssetLogo({required this.width, required this.height});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      BrandingConfig.logoAsset,
      width: width,
      height: height,
      fit: BoxFit.contain,
      alignment: Alignment.center,
      filterQuality: FilterQuality.high,
      errorBuilder: (_, _, _) => Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          color: AppTheme.primary,
          borderRadius: BorderRadius.circular(AppTheme.radiusL),
        ),
        child: const Icon(Icons.build_circle, color: Colors.white, size: 40),
      ),
    );
  }
}

/// "— Powered by `partner` —" strip shown at the foot of auth screens.
class PoweredByFooter extends StatelessWidget {
  const PoweredByFooter({super.key});

  @override
  Widget build(BuildContext context) {
    if (BrandingConfig.poweredBy.trim().isEmpty) {
      return const SizedBox.shrink();
    }
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const _FooterDash(),
        const SizedBox(width: 10),
        Text(
          'Powered by ${BrandingConfig.poweredBy}',
          style: const TextStyle(fontSize: 12.5, color: AppTheme.textSecondary),
        ),
        const SizedBox(width: 10),
        const _FooterDash(),
      ],
    );
  }
}

class _FooterDash extends StatelessWidget {
  const _FooterDash();

  @override
  Widget build(BuildContext context) {
    return Container(width: 18, height: 1, color: AppTheme.divider);
  }
}
