import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'admin_repository.g.dart';

@riverpod
AdminRepository adminRepository(Ref ref) =>
    AdminRepository(ref.watch(apiClientProvider));

// ── Device categories ──────────────────────────────────────────────────────

class AdminCategory {
  final String id;
  final String name;
  final String? description;
  final bool archived;

  const AdminCategory({
    required this.id,
    required this.name,
    this.description,
    required this.archived,
  });

  factory AdminCategory.fromJson(Map<String, dynamic> json) => AdminCategory(
        id: json['id'] as String,
        name: json['name'] as String,
        description: json['description'] as String?,
        archived: json['archived'] as bool? ?? false,
      );
}

// ── Settings ───────────────────────────────────────────────────────────────

class WorkingHoursConfig {
  final int dayOfWeek;
  final bool isClosed;
  final String? openTime;
  final String? closeTime;

  const WorkingHoursConfig({
    required this.dayOfWeek,
    required this.isClosed,
    this.openTime,
    this.closeTime,
  });

  factory WorkingHoursConfig.fromJson(Map<String, dynamic> json) =>
      WorkingHoursConfig(
        dayOfWeek: (json['dayOfWeek'] as num).toInt(),
        isClosed: json['isClosed'] as bool? ?? false,
        openTime: json['openTime'] as String?,
        closeTime: json['closeTime'] as String?,
      );

  static const dayNames = [
    '', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'
  ];

  String get dayName => dayOfWeek < dayNames.length ? dayNames[dayOfWeek] : 'Day $dayOfWeek';
}

class SchedulingConfig {
  final int slotDurationMinutes;
  final int bookingHorizonDays;
  final List<WorkingHoursConfig> workingHours;

  const SchedulingConfig({
    required this.slotDurationMinutes,
    required this.bookingHorizonDays,
    required this.workingHours,
  });

  factory SchedulingConfig.fromJson(Map<String, dynamic> json) =>
      SchedulingConfig(
        slotDurationMinutes: (json['slotDurationMinutes'] as num).toInt(),
        bookingHorizonDays: (json['bookingHorizonDays'] as num).toInt(),
        workingHours: (json['workingHours'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>()
            .map(WorkingHoursConfig.fromJson)
            .toList(),
      );
}

// ── Repository ──────────────────────────────────────────────────────────────

class AdminRepository {
  AdminRepository(this._dio);
  final Dio _dio;

  // ── Categories ────────────────────────────────────────────────────────────

  Future<List<AdminCategory>> allCategories() async {
    try {
      final res = await _dio.get<List<dynamic>>('/device-categories/all');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AdminCategory.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AdminCategory> createCategory(
      String name, String? description) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/device-categories',
          data: {'name': name, 'description': description});
      return AdminCategory.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> archiveCategory(String id) async {
    try {
      await _dio.post<void>('/device-categories/$id/archive');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> unarchiveCategory(String id) async {
    try {
      await _dio.post<void>('/device-categories/$id/unarchive');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  // ── Settings ──────────────────────────────────────────────────────────────

  Future<SchedulingConfig> schedulingConfig() async {
    try {
      final res = await _dio
          .get<Map<String, dynamic>>('/settings/scheduling-config');
      return SchedulingConfig.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
