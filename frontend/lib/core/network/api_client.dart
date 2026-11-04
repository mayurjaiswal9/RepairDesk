import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../auth/session_controller.dart';
import '../error/app_exception.dart';

part 'api_client.g.dart';

/// Live API used by release builds when [API_BASE_URL] is not passed at
/// compile time. A plain `flutter build apk --release` used to keep the
/// emulator loopback below, so a phone that opened that APK waited out the
/// connect timeout and showed "Request timed out. Please try again."
const _liveApiBase =
    'https://repairdesk-api-dev.politeground-f86bf509.centralindia.azurecontainerapps.io/api/v1';

/// Android emulator → host loopback. Debug/profile only.
const _emulatorApiBase = 'http://10.0.2.2:8080/api/v1';

const _apiBaseFromDefine = String.fromEnvironment('API_BASE_URL');

/// `--dart-define` / `--dart-define-from-file` wins. Otherwise release
/// (`dart.vm.product`) talks to the live API, and debug keeps the emulator.
const _baseUrl = _apiBaseFromDefine != ''
    ? _apiBaseFromDefine
    : bool.fromEnvironment('dart.vm.product')
        ? _liveApiBase
        : _emulatorApiBase;

@Riverpod(keepAlive: true)
Dio apiClient(Ref ref) {
  final dio = Dio(BaseOptions(
    baseUrl: _baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 60),
    sendTimeout: const Duration(seconds: 60),
  ));

  // Auth interceptor: attaches bearer token and handles 401 by signing out.
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) {
        final session = ref.read(sessionControllerProvider);
        if (session.token != null) {
          options.headers['Authorization'] = 'Bearer ${session.token}';
        }
        // Dio defaults every request to application/json. That produces
        // "Unable to set different content type" on photo downloads (response
        // is image/jpeg) and breaks multipart uploads. Only JSON bodies need it.
        final binary = options.data is FormData
            || options.data == null
            || options.responseType == ResponseType.bytes
            || options.responseType == ResponseType.stream;
        if (binary) {
          options.headers.remove(Headers.contentTypeHeader);
          options.contentType = null;
        } else {
          options.headers.putIfAbsent(
            Headers.contentTypeHeader,
            () => Headers.jsonContentType,
          );
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401) {
          await ref.read(sessionControllerProvider.notifier).signOut();
        }
        handler.next(error);
      },
    ),
  );

  // Error-mapping interceptor: converts DioException → AppException.
  dio.interceptors.add(
    InterceptorsWrapper(
      onError: (error, handler) {
        handler.reject(
          DioException(
            requestOptions: error.requestOptions,
            error: AppException.fromDioError(error),
            type: error.type,
            response: error.response,
          ),
        );
      },
    ),
  );

  return dio;
}
