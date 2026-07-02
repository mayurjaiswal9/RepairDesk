import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'jobs_repository.g.dart';

@riverpod
JobsRepository jobsRepository(Ref ref) =>
    JobsRepository(ref.watch(apiClientProvider));

class JobSummary {
  final String id;
  final String referenceNumber;
  final String customerUsername;
  final String deviceCategoryName;
  final String contactName;
  final String status;
  final String priority;
  final String? assignedTechnicianName;
  final String? updatedAt;

  const JobSummary({
    required this.id,
    required this.referenceNumber,
    required this.customerUsername,
    required this.deviceCategoryName,
    required this.contactName,
    required this.status,
    required this.priority,
    this.assignedTechnicianName,
    this.updatedAt,
  });

  factory JobSummary.fromJson(Map<String, dynamic> json) => JobSummary(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        customerUsername: json['customerUsername'] as String,
        deviceCategoryName: json['deviceCategoryName'] as String,
        contactName: json['contactName'] as String? ?? '',
        status: json['status'] as String,
        priority: json['priority'] as String,
        assignedTechnicianName: json['assignedTechnicianName'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}

class JobDetail {
  final String id;
  final String referenceNumber;
  final String requestId;
  final String customerId;
  final String customerUsername;
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
  final int version;

  const JobDetail({
    required this.id,
    required this.referenceNumber,
    required this.requestId,
    required this.customerId,
    required this.customerUsername,
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
    required this.version,
  });

  factory JobDetail.fromJson(Map<String, dynamic> json) => JobDetail(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        requestId: json['requestId'] as String,
        customerId: json['customerId'] as String,
        customerUsername: json['customerUsername'] as String,
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
        version: (json['version'] as num).toInt(),
      );
}

class JobsRepository {
  JobsRepository(this._dio);
  final Dio _dio;

  Future<List<JobSummary>> list({String? status, int page = 0}) async {
    try {
      final params = <String, dynamic>{'page': page, 'size': 20};
      if (status != null) params['status'] = status;
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

  Future<JobDetail> transition(String jobId, String action,
      {String? resolution, String? reason}) async {
    try {
      final data = <String, dynamic>{};
      if (resolution != null) data['resolutionSummary'] = resolution;
      if (reason != null) data['reason'] = reason;
      final res = await _dio.post<Map<String, dynamic>>(
          '/jobs/$jobId/$action', data: data.isEmpty ? null : data);
      return JobDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
