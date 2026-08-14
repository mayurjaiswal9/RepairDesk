import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'profile_repository.g.dart';

@riverpod
ProfileRepository profileRepository(Ref ref) =>
    ProfileRepository(ref.watch(apiClientProvider));

class UserProfileData {
  final String id;
  final String username;
  final String? name;
  final String mobile;
  final String role;
  final String status;
  final bool mustChangePassword;

  const UserProfileData({
    required this.id,
    required this.username,
    this.name,
    required this.mobile,
    required this.role,
    required this.status,
    required this.mustChangePassword,
  });

  factory UserProfileData.fromJson(Map<String, dynamic> json) =>
      UserProfileData(
        id: json['id'] as String,
        username: json['username'] as String,
        name: json['name'] as String?,
        mobile: json['mobile'] as String,
        role: json['role'] as String,
        status: json['status'] as String,
        mustChangePassword: json['mustChangePassword'] as bool? ?? false,
      );
}

class ProfileRepository {
  ProfileRepository(this._dio);
  final Dio _dio;

  Future<UserProfileData> getMe() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/me');
      return UserProfileData.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<UserProfileData> updateMobile(String mobile) async {
    try {
      final res = await _dio.patch<Map<String, dynamic>>('/me',
          data: {'mobile': mobile});
      return UserProfileData.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> changePassword(
      String currentPassword, String newPassword) async {
    try {
      await _dio.post<void>('/me/change-password', data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      });
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  // ── Admin: user management ──────────────────────────────────────────────

  Future<List<UserProfileData>> listAllUsers() async {
    try {
      final res = await _dio.get<List<dynamic>>('/users');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(UserProfileData.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> createUser({
    required String username,
    required String name,
    required String mobile,
    required String password,
    required String role,
  }) async {
    try {
      await _dio.post<void>('/users', data: {
        'username': username,
        'name': name,
        'mobile': mobile,
        'password': password,
        'role': role,
      });
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> setUserStatus(String userId, bool active) async {
    try {
      await _dio.post<void>(
          '/users/$userId/${active ? 'activate' : 'deactivate'}');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<UserProfileData> updateUser(
    String userId, {
    String? name,
    String? mobile,
    String? role,
  }) async {
    try {
      final res = await _dio.patch<Map<String, dynamic>>('/users/$userId',
          data: {'name': name, 'mobile': mobile, 'role': role});
      return UserProfileData.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> resetUserPassword(String userId, String newPassword) async {
    try {
      await _dio.post<void>('/users/$userId/reset-password',
          data: {'newPassword': newPassword});
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> deleteUser(String userId) async {
    try {
      await _dio.delete<void>('/users/$userId');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
