import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'notifications_repository.g.dart';

@riverpod
NotificationsRepository notificationsRepository(Ref ref) =>
    NotificationsRepository(ref.watch(apiClientProvider));

class AppNotification {
  final String id;
  final String type;
  final String message;
  final String? referenceNumber;
  final String? relatedType;
  final String? relatedEntityId;
  final bool read;
  final String createdAt;

  const AppNotification({
    required this.id,
    required this.type,
    required this.message,
    this.referenceNumber,
    this.relatedType,
    this.relatedEntityId,
    required this.read,
    required this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: json['id'] as String,
        type: json['type'] as String,
        message: json['message'] as String,
        referenceNumber: json['referenceNumber'] as String?,
        relatedType: json['relatedType'] as String?,
        relatedEntityId: json['relatedEntityId'] as String?,
        read: json['read'] as bool? ?? false,
        createdAt: json['createdAt'] as String,
      );
}

class NotificationsRepository {
  NotificationsRepository(this._dio);
  final Dio _dio;

  Future<List<AppNotification>> inbox() async {
    try {
      final res = await _dio.get<List<dynamic>>('/me/notifications');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AppNotification.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<int> unreadCount() async {
    try {
      final res =
          await _dio.get<Map<String, dynamic>>('/me/notifications/unread-count');
      return (res.data!['count'] as num).toInt();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> markRead(String id) async {
    try {
      await _dio.post<void>('/me/notifications/$id/read');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> markAllRead() async {
    try {
      await _dio.post<void>('/me/notifications/read-all');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
