import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'secure_storage.g.dart';

/// Keys used in secure storage — centralised to avoid typos across the app.
abstract class StorageKeys {
  static const authToken = 'auth_token';
  static const userId    = 'user_id';
  static const userRole  = 'user_role';
}

@riverpod
FlutterSecureStorage secureStorage(Ref ref) {
  return const FlutterSecureStorage();
}
