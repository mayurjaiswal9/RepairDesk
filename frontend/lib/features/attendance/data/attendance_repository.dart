import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/location_service.dart';
import '../../../core/network/api_client.dart';

class AttendanceConfig {
  const AttendanceConfig({required this.attendanceEnabled});

  final bool attendanceEnabled;

  factory AttendanceConfig.fromJson(Map<String, dynamic> json) => AttendanceConfig(
        attendanceEnabled: json['attendanceEnabled'] == true,
      );
}

class MyAttendance {
  const MyAttendance({
    required this.started,
    required this.workDate,
    this.startAt,
    this.endAt,
    this.startFlag,
    this.endFlag,
    this.endReason,
  });

  final bool started;
  final String? workDate;
  final String? startAt;
  final String? endAt;
  final String? startFlag;
  final String? endFlag;
  final String? endReason;

  bool get open => started && endAt == null;

  factory MyAttendance.fromJson(Map<String, dynamic> json) => MyAttendance(
        started: json['started'] == true,
        workDate: json['workDate'] as String?,
        startAt: json['startAt'] as String?,
        endAt: json['endAt'] as String?,
        startFlag: json['startFlag'] as String?,
        endFlag: json['endFlag'] as String?,
        endReason: json['endReason'] as String?,
      );
}

class AttendanceRow {
  const AttendanceRow({
    required this.technicianId,
    required this.technicianName,
    required this.workDate,
    required this.status,
    this.startAt,
    this.endAt,
    this.startFlag,
    this.endReason,
    this.durationMinutes,
  });

  final String technicianId;
  final String technicianName;
  final String workDate;
  final String status;
  final String? startAt;
  final String? endAt;
  final String? startFlag;
  final String? endReason;
  final int? durationMinutes;

  factory AttendanceRow.fromJson(Map<String, dynamic> json) => AttendanceRow(
        technicianId: json['technicianId'] as String? ?? '',
        technicianName: json['technicianName'] as String? ?? '',
        workDate: json['workDate'] as String? ?? '',
        status: json['status'] as String? ?? 'NOT_STARTED',
        startAt: json['startAt'] as String?,
        endAt: json['endAt'] as String?,
        startFlag: json['startFlag'] as String?,
        endReason: json['endReason'] as String?,
        durationMinutes: (json['durationMinutes'] as num?)?.toInt(),
      );
}

final attendanceRepositoryProvider = Provider<AttendanceRepository>(
  (ref) => AttendanceRepository(ref.watch(apiClientProvider)),
);

class AttendanceRepository {
  AttendanceRepository(this._dio);
  final Dio _dio;

  Future<AttendanceConfig> config() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/location/config');
      return AttendanceConfig.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<MyAttendance> today() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/attendance/today');
      return MyAttendance.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<MyAttendance> start(LocationFix? fix) => _post('/attendance/start', fix);

  Future<MyAttendance> end(LocationFix? fix) => _post('/attendance/end', fix);

  Future<List<AttendanceRow>> list({
    required String from,
    required String to,
  }) async {
    try {
      final res = await _dio.get<List<dynamic>>(
        '/attendance',
        queryParameters: {'from': from, 'to': to},
      );
      return (res.data ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(AttendanceRow.fromJson)
          .toList();
    } on DioException catch (e) {
      throw _error(e);
    }
  }

  Future<MyAttendance> _post(String path, LocationFix? fix) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        path,
        data: {
          if (fix != null) 'latitude': fix.latitude,
          if (fix != null) 'longitude': fix.longitude,
          if (fix != null) 'accuracyM': fix.accuracyM,
          'mocked': fix?.mocked ?? false,
        },
      );
      return MyAttendance.fromJson(res.data ?? const {});
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
