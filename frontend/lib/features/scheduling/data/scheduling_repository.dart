import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'scheduling_repository.g.dart';

@riverpod
SchedulingRepository schedulingRepository(Ref ref) =>
    SchedulingRepository(ref.watch(apiClientProvider));

class AppointmentItem {
  final String id;
  final String jobId;
  final String referenceNumber;
  final String technicianId;
  final String? technicianName;
  final String? customerName;
  final String? deviceCategoryName;
  final String scheduledDate;
  final String startTime;
  final String endTime;
  final String status;
  final String? appointmentNotes;

  const AppointmentItem({
    required this.id,
    required this.jobId,
    required this.referenceNumber,
    required this.technicianId,
    this.technicianName,
    this.customerName,
    this.deviceCategoryName,
    required this.scheduledDate,
    required this.startTime,
    required this.endTime,
    required this.status,
    this.appointmentNotes,
  });

  factory AppointmentItem.fromJson(Map<String, dynamic> json) =>
      AppointmentItem(
        id: json['id'] as String,
        jobId: json['jobId'] as String,
        referenceNumber: json['referenceNumber'] as String,
        technicianId: json['technicianId'] as String,
        technicianName: json['technicianName'] as String?,
        customerName: json['customerName'] as String?,
        deviceCategoryName: json['deviceCategoryName'] as String?,
        scheduledDate: json['scheduledDate'] as String,
        startTime: json['startTime'] as String,
        endTime: json['endTime'] as String,
        status: json['status'] as String,
        appointmentNotes: json['appointmentNotes'] as String?,
      );
}

class SchedulingRepository {
  SchedulingRepository(this._dio);
  final Dio _dio;

  Future<List<AppointmentItem>> calendar(String from, String to) async {
    try {
      final res = await _dio.get<List<dynamic>>('/appointments',
          queryParameters: {'from': from, 'to': to});
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AppointmentItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<AppointmentItem>> mySchedule(String from, String to) async {
    try {
      final res = await _dio.get<List<dynamic>>('/me/schedule',
          queryParameters: {'from': from, 'to': to});
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AppointmentItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<AppointmentItem>> forJob(String jobId) async {
    try {
      final res =
          await _dio.get<List<dynamic>>('/jobs/$jobId/appointments');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AppointmentItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AppointmentItem> create({
    required String jobId,
    required String technicianId,
    required String scheduledDate,
    required String startTime,
    String? notes,
  }) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/jobs/$jobId/appointments',
        data: {
          'technicianId': technicianId,
          'scheduledDate': scheduledDate,
          'startTime': startTime,
          if (notes != null && notes.isNotEmpty) 'notes': notes,
        },
      );
      return AppointmentItem.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AppointmentItem> reschedule({
    required String jobId,
    required String appointmentId,
    required String scheduledDate,
    required String startTime,
    String? notes,
  }) async {
    try {
      final res = await _dio.put<Map<String, dynamic>>(
        '/jobs/$jobId/appointments/$appointmentId/reschedule',
        data: {
          'scheduledDate': scheduledDate,
          'startTime': startTime,
          if (notes != null && notes.isNotEmpty) 'notes': notes,
        },
      );
      return AppointmentItem.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> cancel(
      String jobId, String appointmentId, String reason) async {
    try {
      await _dio.delete<void>(
          '/jobs/$jobId/appointments/$appointmentId',
          data: {'reason': reason});
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
