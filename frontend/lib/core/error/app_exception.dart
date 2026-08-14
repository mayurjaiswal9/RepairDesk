import 'dart:convert';

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
  bool get isNetwork      => errorCode == 'NETWORK' || errorCode == 'TIMEOUT';

  /// A user-friendly message suitable for direct display in the UI.
  String get userMessage => switch (errorCode) {
    'UNAUTHORIZED' => 'Session expired. Please sign in again.',
    'FORBIDDEN' => 'You don\'t have permission to do that.',
    // Prefer the server message when it is specific (e.g. missing photo file).
    'NOT_FOUND' => message.trim().isEmpty ? 'Item not found.' : message,
    'CONFLICT' => message,
    'VALIDATION_ERROR' => fieldErrors.isNotEmpty
        ? fieldErrors.map((e) => e.message).join(', ')
        : message,
    'NETWORK' => 'No connection. Check your network.',
    'TIMEOUT' => 'Request timed out. Please try again.',
    _ => message,
  };

  /// Build from a Dio error, parsing the standard API error body when present.
  factory AppException.fromDioError(DioException error) {
    final data = error.response?.data;
    final map = _asJsonMap(data);
    if (map != null) {
      final fieldErrorsRaw = map['fieldErrors'] as List<dynamic>? ?? [];
      return AppException(
        errorCode: map['errorCode'] as String? ?? 'UNKNOWN',
        message:   map['message']   as String? ?? 'An unexpected error occurred',
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
    if (error.type == DioExceptionType.unknown && error.response == null) {
      final cause = error.error?.toString() ?? '';
      return AppException(
        errorCode: 'NETWORK',
        message: 'No connection. Check your network and try again.${ cause.isNotEmpty ? ' ($cause)' : ''}',
      );
    }

    return const AppException(
        errorCode: 'UNKNOWN', message: 'An unexpected error occurred.');
  }

  /// Binary downloads use [ResponseType.bytes], so error bodies arrive as
  /// `List<int>` rather than a decoded JSON map — decode them when possible.
  static Map<String, dynamic>? _asJsonMap(dynamic data) {
    if (data is Map<String, dynamic>) return data;
    if (data is Map) return Map<String, dynamic>.from(data);
    if (data is List<int>) {
      try {
        final text = String.fromCharCodes(data);
        final decoded = jsonDecode(text);
        if (decoded is Map<String, dynamic>) return decoded;
        if (decoded is Map) return Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    if (data is String) {
      try {
        final decoded = jsonDecode(data);
        if (decoded is Map<String, dynamic>) return decoded;
        if (decoded is Map) return Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    return null;
  }

  @override
  String toString() => 'AppException($errorCode): $message';
}

class FieldError {
  final String field;
  final String message;
  const FieldError({required this.field, required this.message});
}
