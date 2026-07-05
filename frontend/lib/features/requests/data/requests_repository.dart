import 'package:dio/dio.dart';
import 'package:flutter/material.dart' show TimeOfDay;
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'requests_repository.g.dart';

@riverpod
RequestsRepository requestsRepository(Ref ref) =>
    RequestsRepository(ref.watch(apiClientProvider));

class RequestSummary {
  final String id;
  final String referenceNumber;
  final String customerUsername;
  final String deviceCategoryName;
  final String contactName;
  final String status;
  final String priority;
  final String? submittedAt;

  const RequestSummary({
    required this.id,
    required this.referenceNumber,
    required this.customerUsername,
    required this.deviceCategoryName,
    required this.contactName,
    required this.status,
    required this.priority,
    this.submittedAt,
  });

  factory RequestSummary.fromJson(Map<String, dynamic> json) => RequestSummary(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        customerUsername: json['customerUsername'] as String,
        deviceCategoryName: json['deviceCategoryName'] as String,
        contactName: json['contactName'] as String,
        status: json['status'] as String,
        priority: json['priority'] as String,
        submittedAt: json['submittedAt'] as String?,
      );
}

class RequestDetail {
  final String id;
  final String referenceNumber;
  final String customerUsername;
  final String contactName;
  final String? contactMobile;
  final String? contactEmail;
  final String deviceCategoryName;
  final String? deviceBrand;
  final String? deviceModel;
  final String? deviceSerial;
  final String? purchaseDate;
  final String? warrantyStatus;
  final String? issueDescription;
  final String? preferredDate;
  final String? preferredTime;
  final String status;
  final String priority;
  final String? infoRequestedMessage;
  final String? customerFacingMessage;
  final String? submittedAt;

  const RequestDetail({
    required this.id,
    required this.referenceNumber,
    required this.customerUsername,
    required this.contactName,
    this.contactMobile,
    this.contactEmail,
    required this.deviceCategoryName,
    this.deviceBrand,
    this.deviceModel,
    this.deviceSerial,
    this.purchaseDate,
    this.warrantyStatus,
    this.issueDescription,
    this.preferredDate,
    this.preferredTime,
    required this.status,
    required this.priority,
    this.infoRequestedMessage,
    this.customerFacingMessage,
    this.submittedAt,
  });

  factory RequestDetail.fromJson(Map<String, dynamic> json) => RequestDetail(
        id: json['id'] as String,
        referenceNumber: json['referenceNumber'] as String,
        customerUsername: json['customerUsername'] as String,
        contactName: json['contactName'] as String? ?? '',
        contactMobile: json['contactMobile'] as String?,
        contactEmail: json['contactEmail'] as String?,
        deviceCategoryName: json['deviceCategoryName'] as String,
        deviceBrand: json['deviceBrand'] as String?,
        deviceModel: json['deviceModel'] as String?,
        deviceSerial: json['deviceSerial'] as String?,
        purchaseDate: json['purchaseDate'] as String?,
        warrantyStatus: json['warrantyStatus'] as String?,
        issueDescription: json['issueDescription'] as String?,
        preferredDate: json['preferredDate'] as String?,
        preferredTime: json['preferredTime'] as String?,
        status: json['status'] as String,
        priority: json['priority'] as String,
        infoRequestedMessage: json['infoRequestedMessage'] as String?,
        customerFacingMessage: json['customerFacingMessage'] as String?,
        submittedAt: json['submittedAt'] as String?,
      );
}

class RequestsRepository {
  RequestsRepository(this._dio);
  final Dio _dio;

  Future<List<RequestSummary>> queue({String? status, int page = 0}) async {
    try {
      final params = <String, dynamic>{'page': page, 'size': 20};
      if (status != null) params['status'] = status;
      final res = await _dio.get<Map<String, dynamic>>(
        '/requests',
        queryParameters: params,
      );
      final content = (res.data!['content'] as List<dynamic>);
      return content
          .cast<Map<String, dynamic>>()
          .map(RequestSummary.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<void> approve(String requestId) async {
    try {
      await _dio.post<void>('/requests/$requestId/approve');
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<void> reject(
      String requestId, String reasonCode, String customerMessage) async {
    try {
      await _dio.post<void>('/requests/$requestId/reject', data: {
        'reasonCode': reasonCode,
        'customerMessage': customerMessage,
      });
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<void> requestInfo(String requestId, String message) async {
    try {
      await _dio.post<void>('/requests/$requestId/request-info',
          data: {'message': message});
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<RequestDetail> getDetail(String requestId) async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/requests/$requestId');
      return RequestDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<Map<String, dynamic>> createRequest({
    required String categoryId,
    required String contactName,
    required String contactMobile,
    String? deviceBrand,
    String? deviceModel,
    required String issueDescription,
    required DateTime preferredDate,
    required TimeOfDay preferredTime,
  }) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>('/requests', data: {
        'deviceCategoryId': categoryId,
        'contactName': contactName,
        'contactMobile': contactMobile,
        if (deviceBrand != null && deviceBrand.isNotEmpty)
          'deviceBrand': deviceBrand,
        if (deviceModel != null && deviceModel.isNotEmpty)
          'deviceModel': deviceModel,
        'issueDescription': issueDescription,
        'preferredDate':
            '${preferredDate.year.toString().padLeft(4, '0')}-'
            '${preferredDate.month.toString().padLeft(2, '0')}-'
            '${preferredDate.day.toString().padLeft(2, '0')}',
        'preferredTime':
            '${preferredTime.hour.toString().padLeft(2, '0')}:'
            '${preferredTime.minute.toString().padLeft(2, '0')}',
      });
      return res.data!;
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }
}
