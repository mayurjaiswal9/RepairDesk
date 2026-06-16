import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'router/app_router.dart';
import 'shared/theme/app_theme.dart';

/// Root application widget. Wires together the router and theme.
/// Authentication state changes trigger automatic re-routing via GoRouter's
/// redirect (implemented in router/app_router.dart).
class RepairDeskApp extends ConsumerWidget {
  const RepairDeskApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: 'RepairDesk',
      theme: AppTheme.light(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
