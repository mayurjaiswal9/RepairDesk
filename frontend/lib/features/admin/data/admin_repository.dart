import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'admin_repository.g.dart';

@riverpod
AdminRepository adminRepository(Ref ref) =>
    AdminRepository(ref.watch(apiClientProvider));

// ── Device categories ──────────────────────────────────────────────────────

/// Whether a category offers a curated, searchable brand list ([configured])
/// or falls back to "Others"/free-text entry ([freeText]) on the New
/// Request screen (PROD-004).
enum BrandMode {
  configured,
  freeText;

  String get apiValue => this == configured ? 'CONFIGURED' : 'FREE_TEXT';

  static BrandMode fromApi(String? raw) =>
      raw == 'CONFIGURED' ? BrandMode.configured : BrandMode.freeText;
}

class AdminCategory {
  final String id;
  final String name;
  final String? description;
  final bool archived;
  final BrandMode brandMode;

  const AdminCategory({
    required this.id,
    required this.name,
    this.description,
    required this.archived,
    this.brandMode = BrandMode.freeText,
  });

  factory AdminCategory.fromJson(Map<String, dynamic> json) => AdminCategory(
        id: json['id'] as String,
        name: json['name'] as String,
        description: json['description'] as String?,
        archived: json['archived'] as bool? ?? false,
        brandMode: BrandMode.fromApi(json['brandMode'] as String?),
      );
}

/// A brand curated by an admin under a device category.
class AdminBrand {
  final String id;
  final String deviceCategoryId;
  final String name;
  final bool archived;

  const AdminBrand({
    required this.id,
    required this.deviceCategoryId,
    required this.name,
    required this.archived,
  });

  factory AdminBrand.fromJson(Map<String, dynamic> json) => AdminBrand(
        id: json['id'] as String,
        deviceCategoryId: json['deviceCategoryId'] as String,
        name: json['name'] as String,
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

  // Backend `dayOfWeek` is 0=Monday .. 6=Sunday (see WorkingHours.java).
  // A stray leading '' entry here previously shifted every label by one day
  // (Sunday showed as "Saturday", Monday showed blank) — this is what
  // actually caused the "misaligned" Working Hours row that looked like a
  // spacing bug but was really a day-name mapping bug.
  static const dayNames = [
    'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'
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
      String name, String? description, BrandMode brandMode) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/device-categories',
          data: {
            'name': name,
            'description': description,
            'brandMode': brandMode.apiValue,
          });
      return AdminCategory.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AdminCategory> updateCategory(
      String id, String name, String? description, BrandMode brandMode) async {
    try {
      final res = await _dio.put<Map<String, dynamic>>(
          '/device-categories/$id',
          data: {
            'name': name,
            'description': description,
            'brandMode': brandMode.apiValue,
          });
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

  // ── Brands (per category) ────────────────────────────────────────────────

  Future<List<AdminBrand>> allBrands(String categoryId) async {
    try {
      final res = await _dio
          .get<List<dynamic>>('/device-categories/$categoryId/brands/all');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AdminBrand.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AdminBrand> createBrand(String categoryId, String name) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/device-categories/$categoryId/brands', data: {'name': name});
      return AdminBrand.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AdminBrand> updateBrand(
      String categoryId, String brandId, String name) async {
    try {
      final res = await _dio.put<Map<String, dynamic>>(
          '/device-categories/$categoryId/brands/$brandId',
          data: {'name': name});
      return AdminBrand.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> archiveBrand(String categoryId, String brandId) async {
    try {
      await _dio
          .post<void>('/device-categories/$categoryId/brands/$brandId/archive');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> unarchiveBrand(String categoryId, String brandId) async {
    try {
      await _dio.post<void>(
          '/device-categories/$categoryId/brands/$brandId/unarchive');
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

  Future<void> updateSettings({
    int? slotDurationMinutes,
    int? bookingHorizonDays,
    String? businessName,
  }) async {
    try {
      final body = <String, dynamic>{};
      if (slotDurationMinutes != null) body['slotDurationMinutes'] = slotDurationMinutes;
      if (bookingHorizonDays != null) body['bookingHorizonDays'] = bookingHorizonDays;
      if (businessName != null) body['businessName'] = businessName;
      await _dio.put<void>('/settings', data: body);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> updateWorkingHours(List<Map<String, dynamic>> hours) async {
    try {
      await _dio.put<void>('/settings/working-hours', data: hours);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
