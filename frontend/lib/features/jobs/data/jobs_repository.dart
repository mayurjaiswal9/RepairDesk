import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/service_location.dart';
import '../../../core/network/api_client.dart';

part 'jobs_repository.g.dart';

@riverpod
JobsRepository jobsRepository(Ref ref) =>
    JobsRepository(ref.watch(apiClientProvider));

class JobSummary {
  final String id;
  final String referenceNumber;
  final String customerUsername;
  final String? customerName;
  final String deviceCategoryName;
  final String contactName;
  final String status;
  final String priority;
  final String? assignedTechnicianId;
  final String? assignedTechnicianName;
  final String? updatedAt;

  const JobSummary({
    required this.id,
    required this.referenceNumber,
    required this.customerUsername,
    this.customerName,
    required this.deviceCategoryName,
    required this.contactName,
    required this.status,
    required this.priority,
    this.assignedTechnicianId,
    this.assignedTechnicianName,
    this.updatedAt,
  });

  /// Prefer account display name; fall back to username (never contact person).
  String get customerDisplayName {
    final name = customerName?.trim();
    if (name != null && name.isNotEmpty) return name;
    return customerUsername;
  }

  factory JobSummary.fromJson(Map<String, dynamic> json) => JobSummary(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        customerUsername: json['customerUsername'] as String,
        customerName: json['customerName'] as String?,
        deviceCategoryName: json['deviceCategoryName'] as String,
        contactName: json['contactName'] as String? ?? '',
        status: json['status'] as String,
        priority: json['priority'] as String,
        assignedTechnicianId: json['assignedTechnicianId']?.toString(),
        assignedTechnicianName: json['assignedTechnicianName'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}

class OpenJobDay {
  final String date;
  final int count;

  const OpenJobDay({required this.date, required this.count});

  factory OpenJobDay.fromJson(Map<String, dynamic> json) => OpenJobDay(
        date: json['date'] as String,
        count: (json['count'] as num).toInt(),
      );
}

class JobDetail {
  final String id;
  final String referenceNumber;
  final String requestId;
  final String customerId;
  final String customerUsername;
  final String? customerName;
  final String deviceCategoryName;
  final String contactName;
  final String contactMobile;
  final String? deviceBrand;
  final String? deviceModel;
  final String issueDescription;
  final String status;
  final String priority;
  final String? assignedTechnicianId;
  final String? assignedTechnicianName;
  final String? currentAppointmentId;
  final String? resolutionSummary;
  final String? cancelReason;
  final String? internalNotes;
  final String? createdAt;
  final int version;
  final ServiceLocation? serviceLocation;

  const JobDetail({
    required this.id,
    required this.referenceNumber,
    required this.requestId,
    required this.customerId,
    required this.customerUsername,
    this.customerName,
    required this.deviceCategoryName,
    required this.contactName,
    required this.contactMobile,
    this.deviceBrand,
    this.deviceModel,
    required this.issueDescription,
    required this.status,
    required this.priority,
    this.assignedTechnicianId,
    this.assignedTechnicianName,
    this.currentAppointmentId,
    this.resolutionSummary,
    this.cancelReason,
    this.internalNotes,
    this.createdAt,
    required this.version,
    this.serviceLocation,
  });

  String get customerDisplayName {
    final name = customerName?.trim();
    if (name != null && name.isNotEmpty) return name;
    return customerUsername;
  }

  factory JobDetail.fromJson(Map<String, dynamic> json) => JobDetail(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        requestId: json['requestId'] as String,
        customerId: json['customerId'] as String,
        customerUsername: json['customerUsername'] as String,
        customerName: json['customerName'] as String?,
        deviceCategoryName: json['deviceCategoryName'] as String,
        contactName: json['contactName'] as String? ?? '',
        contactMobile: json['contactMobile'] as String? ?? '',
        deviceBrand: json['deviceBrand'] as String?,
        deviceModel: json['deviceModel'] as String?,
        issueDescription: json['issueDescription'] as String? ?? '',
        status: json['status'] as String,
        priority: json['priority'] as String,
        assignedTechnicianId: json['assignedTechnicianId'] as String?,
        assignedTechnicianName: json['assignedTechnicianName'] as String?,
        currentAppointmentId: json['currentAppointmentId'] as String?,
        resolutionSummary: json['resolutionSummary'] as String?,
        cancelReason: json['cancelReason'] as String?,
        internalNotes: json['internalNotes'] as String?,
        createdAt: json['createdAt'] as String?,
        version: (json['version'] as num).toInt(),
        serviceLocation: ServiceLocation.fromJson(json['serviceLocation']),
      );
}

class JobsRepository {
  JobsRepository(this._dio);
  final Dio _dio;

  Future<List<JobSummary>> list({
    String? status,
    String? technicianId,
    String? from,
    String? to,
    int page = 0,
  }) async {
    try {
      final params = <String, dynamic>{'page': page, 'size': 20};
      if (status != null) params['status'] = status;
      if (technicianId != null) params['technicianId'] = technicianId;
      if (from != null) params['from'] = from;
      if (to != null) params['to'] = to;
      final res = await _dio.get<Map<String, dynamic>>('/jobs',
          queryParameters: params);
      final content = res.data!['content'] as List<dynamic>;
      return content
          .cast<Map<String, dynamic>>()
          .map(JobSummary.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<OpenJobDay>> openCarryover() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/jobs/carryover');
      final days = res.data?['days'] as List<dynamic>? ?? const [];
      return days.cast<Map<String, dynamic>>().map(OpenJobDay.fromJson).toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<JobDetail> get(String id) async {
    try {
      final res =
          await _dio.get<Map<String, dynamic>>('/jobs/$id');
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<JobDetail> assign(String jobId, String technicianId) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/jobs/$jobId/assign', data: {'technicianId': technicianId});
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<Map<String, dynamic>>> technicians() async {
    try {
      final res = await _dio.get<List<dynamic>>('/users',
          queryParameters: {'role': 'TECHNICIAN'});
      return res.data!.cast<Map<String, dynamic>>();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  /// [noTripReason] / [noTripNote] go with `start` when strict trip mode asks why no trip was recorded.
  Future<JobDetail> transition(String jobId, String action,
      {String? resolution,
      String? reason,
      String? noTripReason,
      String? noTripNote}) async {
    try {
      final data = <String, dynamic>{};
      if (resolution != null) data['resolutionSummary'] = resolution;
      if (reason != null) data['reason'] = reason;
      if (noTripReason != null) data['noTripReason'] = noTripReason;
      if (noTripNote != null) data['noTripNote'] = noTripNote;
      final res = await _dio.post<Map<String, dynamic>>(
          '/jobs/$jobId/$action', data: data.isEmpty ? null : data);
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<JobDetail> appendNote(String jobId, String note) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/jobs/$jobId/notes', data: {'note': note});
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<JobDetail> updateServiceLocation(String jobId, ServiceLocation location) async {
    try {
      final res = await _dio.put<Map<String, dynamic>>(
        '/jobs/$jobId/service-location',
        data: location.toJson(),
      );
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<JobDetail> setPriority(String jobId, String priority) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
          '/jobs/$jobId/priority', data: {'priority': priority});
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
