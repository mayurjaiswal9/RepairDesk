import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';
import '../models/auth_models.dart';

part 'auth_repository.g.dart';

@riverpod
AuthRepository authRepository(Ref ref) {
  return AuthRepository(ref.watch(apiClientProvider));
}

class AuthRepository {
  AuthRepository(this._dio);

  final Dio _dio;

  Future<AuthResponse> login(String username, String password, {String? companyCode}) async {
    final code = companyCode?.trim() ?? '';
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {
          'username': username,
          'password': password,
          if (code.isNotEmpty) 'companyCode': code,
        },
      );
      return AuthResponse.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    } catch (e) {
      throw AppException(errorCode: 'UNKNOWN', message: e.toString());
    }
  }

  Future<AuthResponse> register({
    required String username,
    required String password,
    required String mobile,
    String? name,
  }) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/auth/register',
        data: {
          'username': username,
          'password': password,
          'mobile': mobile,
          if (name != null && name.isNotEmpty) 'name': name,
        },
      );
      return AuthResponse.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    } catch (e) {
      throw AppException(errorCode: 'UNKNOWN', message: e.toString());
    }
  }

  Future<void> logout() async {
    try {
      await _dio.post<void>('/auth/logout');
    } catch (_) {
      // Best-effort; local session is always cleared regardless.
    }
  }
}
