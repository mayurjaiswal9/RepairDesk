import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../error/app_exception.dart';
import '../network/api_client.dart';
import 'location_service.dart';
import 'trip_models.dart';

final tripRepositoryProvider = Provider<TripRepository>(
  (ref) => TripRepository(ref.watch(apiClientProvider)),
);

/// Job trips and mileage (`/jobs/{id}/trips`, `/trips/...`, `/me/trips/active`, `/mileage/...`).
class TripRepository {
  TripRepository(this._dio);
  final Dio _dio;

  /// Starts (or returns the already running) trip to [jobId]. A trip running
  /// for another job is ended by the server with `REPLACED`.
  Future<Trip> start(String jobId, LocationFix? fix) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/jobs/$jobId/trips',
        data: {
          if (fix != null) 'latitude': fix.latitude,
          if (fix != null) 'longitude': fix.longitude,
          if (fix != null) 'accuracyM': fix.accuracyM,
          'mocked': fix?.mocked ?? false,
        },
      );
      return Trip.fromJson(res.data!);
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<PointUploadResult> uploadPoints(String tripId, List<TripPoint> points) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/trips/$tripId/points',
        data: {'points': points.map((p) => p.toJson()).toList()},
      );
      return PointUploadResult.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<Trip> end(String tripId, TripEndReason reason, LocationFix? fix) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/trips/$tripId/end',
        data: {
          'reason': reason.code,
          if (fix != null) 'latitude': fix.latitude,
          if (fix != null) 'longitude': fix.longitude,
          if (fix != null) 'accuracyM': fix.accuracyM,
        },
      );
      return Trip.fromJson(res.data!);
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  /// The caller's running trip, or null (204) when there is none.
  Future<Trip?> active() async {
    try {
      final res = await _dio.get<dynamic>('/me/trips/active');
      final data = res.data;
      if (res.statusCode == 204 || data is! Map) return null;
      return Trip.fromJson(Map<String, dynamic>.from(data));
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<JobTrips> forJob(String jobId) async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/jobs/$jobId/trips');
      return JobTrips.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  /// Technicians always get their own rows; the server ignores other filters for them.
  Future<MileageDays> days({required String from, required String to}) async {
    try {
      final res = await _dio.get<Map<String, dynamic>>(
        '/mileage/days',
        queryParameters: {'from': from, 'to': to, 'view': 'DAY'},
      );
      return MileageDays.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<MileageDayDetail> day(String technicianId, String date) async {
    try {
      final res = await _dio
          .get<Map<String, dynamic>>('/mileage/days/$technicianId/$date');
      return MileageDayDetail.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  AppException _error(DioException error) {
    return error.error is AppException
        ? error.error as AppException
        : AppException.fromDioError(error);
  }
}
