import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../core/auth/session_controller.dart';
import '../features/authentication/presentation/login_screen.dart';
import '../features/authentication/presentation/register_screen.dart';
import '../features/dashboard/presentation/dashboard_screen.dart';

part 'app_router.g.dart';

/// Named route paths — single source of truth for navigation.
abstract class AppRoutes {
  static const login     = '/login';
  static const register  = '/register';
  static const dashboard = '/dashboard';
}

/// Lets app-wide prompts (for example the trip arrival question) open a
/// dialog over whatever screen is showing.
final rootNavigatorKey = GlobalKey<NavigatorState>();

@riverpod
GoRouter appRouter(Ref ref) {
  // Watch session so the router rebuilds (and redirects) on auth state changes.
  final sessionListenable = _SessionListenable(ref);

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: AppRoutes.login,
    refreshListenable: sessionListenable,
    redirect: (context, state) {
      final session = ref.read(sessionControllerProvider);

      // Still restoring session from storage — stay put.
      if (session.isLoading) return null;

      final isPublicRoute = state.matchedLocation == AppRoutes.login ||
          state.matchedLocation == AppRoutes.register;

      if (!session.isAuthenticated && !isPublicRoute) {
        return AppRoutes.login;
      }
      if (session.isAuthenticated && isPublicRoute) {
        return AppRoutes.dashboard;
      }
      return null;
    },
    routes: [
      GoRoute(
        path: AppRoutes.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: AppRoutes.register,
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: AppRoutes.dashboard,
        builder: (context, state) => const DashboardScreen(),
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(child: Text('Route not found: ${state.error}')),
    ),
  );
}

/// Bridges Riverpod session state changes to GoRouter's [Listenable] refresh.
class _SessionListenable extends ChangeNotifier {
  _SessionListenable(Ref ref) {
    ref.listen(sessionControllerProvider, (_, _) => notifyListeners());
  }
}
