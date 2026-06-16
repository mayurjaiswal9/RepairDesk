import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../storage/secure_storage.dart';

part 'session_controller.g.dart';

/// Holds the current authentication state — watched by the router guard to
/// redirect between login and protected screens.
class SessionState {
  final String? token;
  final String? userId;
  final String? role;
  final bool isLoading;

  const SessionState({
    this.token,
    this.userId,
    this.role,
    this.isLoading = false,
  });

  bool get isAuthenticated => token != null;

  SessionState copyWith({
    String? token,
    String? userId,
    String? role,
    bool? isLoading,
  }) {
    return SessionState(
      token: token ?? this.token,
      userId: userId ?? this.userId,
      role: role ?? this.role,
      isLoading: isLoading ?? this.isLoading,
    );
  }

  static const unauthenticated = SessionState();
}

@riverpod
class SessionController extends _$SessionController {
  @override
  SessionState build() => const SessionState(isLoading: true);

  /// Called on app start to restore a persisted session from secure storage.
  Future<void> restore() async {
    final storage = ref.read(secureStorageProvider);
    final token  = await storage.read(key: StorageKeys.authToken);
    final userId = await storage.read(key: StorageKeys.userId);
    final role   = await storage.read(key: StorageKeys.userRole);

    if (token != null && userId != null && role != null) {
      state = SessionState(token: token, userId: userId, role: role);
    } else {
      state = SessionState.unauthenticated;
    }
  }

  /// Persists session after a successful login or register.
  Future<void> signIn({
    required String token,
    required String userId,
    required String role,
  }) async {
    final storage = ref.read(secureStorageProvider);
    await storage.write(key: StorageKeys.authToken, value: token);
    await storage.write(key: StorageKeys.userId,    value: userId);
    await storage.write(key: StorageKeys.userRole,  value: role);
    state = SessionState(token: token, userId: userId, role: role);
  }

  /// Clears session on logout or account disable.
  Future<void> signOut() async {
    final storage = ref.read(secureStorageProvider);
    await storage.deleteAll();
    state = SessionState.unauthenticated;
  }
}
