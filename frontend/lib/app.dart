import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/auth/roles.dart';
import 'core/auth/session_controller.dart';
import 'core/branding/branding_config.dart';
import 'core/push/push_registrar.dart';
import 'features/trips/data/on_site_tracker.dart';
import 'features/trips/data/presence_tracker.dart';
import 'features/trips/data/trip_recorder.dart';
import 'router/app_router.dart';
import 'shared/theme/app_theme.dart';

/// Root application widget. Wires together the router and theme.
/// Authentication state changes trigger automatic re-routing via GoRouter's
/// redirect (implemented in router/app_router.dart).
class RepairDeskApp extends ConsumerStatefulWidget {
  const RepairDeskApp({super.key});

  @override
  ConsumerState<RepairDeskApp> createState() => _RepairDeskAppState();
}

class _RepairDeskAppState extends ConsumerState<RepairDeskApp>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Restore any persisted session from secure storage on startup.
    // Called here (not main) so the Riverpod ProviderScope is already active
    // and the provider is kept alive by this widget's subscription.
    Future.microtask(() async {
      await ref.read(sessionControllerProvider.notifier).restore();
      unawaited(ref.read(tripRecorderProvider.notifier).restore());
      unawaited(ref.read(onSiteTrackerProvider.notifier).refresh());
      unawaited(ref.read(presenceTrackerProvider.notifier).refresh());
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) return;
    final session = ref.read(sessionControllerProvider);
    if (!session.isAuthenticated || !Roles.isTechnician(session.role)) return;
    unawaited(ref.read(tripRecorderProvider.notifier).restore());
    unawaited(ref.read(onSiteTrackerProvider.notifier).refresh());
    unawaited(ref.read(presenceTrackerProvider.notifier).refresh());
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: BrandingConfig.productName,
      theme: AppTheme.light(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
      builder: (context, child) {
        return Stack(
          children: [
            child ?? const SizedBox.shrink(),
            const _PushBinding(),
          ],
        );
      },
    );
  }
}

class _PushBinding extends ConsumerStatefulWidget {
  const _PushBinding();

  @override
  ConsumerState<_PushBinding> createState() => _PushBindingState();
}

class _PushBindingState extends ConsumerState<_PushBinding> {
  @override
  void initState() {
    super.initState();
    SessionHooks.beforeSignOut =
        () => ref.read(pushRegistrarProvider).unregister();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(sessionControllerProvider, (previous, next) {
      if (next.isAuthenticated) {
        unawaited(ref.read(pushRegistrarProvider).register());
      }
    });
    return const SizedBox.shrink();
  }
}
