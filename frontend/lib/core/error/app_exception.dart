import 'package:dio/dio.dart';

/// Maps all error sources (API errors, network errors) to a single type
/// so UI and repositories handle one error shape throughout the app.
class AppException implements Exception {
  final String errorCode;
  final String message;
  final List<FieldError> fieldErrors;

  const AppException({
    required this.errorCode,
    required this.message,
    this.fieldErrors = const [],
  });

  bool get isUnauthorized => errorCode == 'UNAUTHORIZED';
  bool get isConflict     => errorCode == 'CONFLICT';
  bool get isValidation   => errorCode == 'VALIDATION_ERROR';
  bool get isNotFound     => errorCode == 'NOT_FOUND';
  bool get isForbidden    => errorCode == 'FORBIDDEN';

  /// Build from a Dio error, parsing the standard API error body when present.
  factory AppException.fromDioError(DioException error) {
    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      final fieldErrorsRaw = data['fieldErrors'] as List<dynamic>? ?? [];
      return AppException(
        errorCode: data['errorCode'] as String? ?? 'UNKNOWN',
        message:   data['message']   as String? ?? 'An unexpected error occurred',
        fieldErrors: fieldErrorsRaw
            .whereType<Map<String, dynamic>>()
            .map((e) => FieldError(
                  field:   e['field']   as String? ?? '',
                  message: e['message'] as String? ?? '',
                ))
            .toList(),
      );
    }

    // Network-level errors (no response body).
    if (error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout) {
      return const AppException(
          errorCode: 'TIMEOUT', message: 'Request timed out. Please try again.');
    }
    if (error.type == DioExceptionType.connectionError) {
      return const AppException(
          errorCode: 'NETWORK', message: 'No connection. Check your network and try again.');
    }

    return const AppException(
        errorCode: 'UNKNOWN', message: 'An unexpected error occurred.');
  }

  @override
  String toString() => 'AppException($errorCode): $message';
}

class FieldError {
  final String field;
  final String message;
  const FieldError({required this.field, required this.message});
}
