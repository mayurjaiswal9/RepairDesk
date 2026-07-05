import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'customer_home_repository.g.dart';

@riverpod
CustomerHomeRepository customerHomeRepository(Ref ref) =>
    CustomerHomeRepository(ref.watch(apiClientProvider));

class RepairListItem {
  final String referenceNumber;
  final String deviceCategoryName;
  final String friendlyStatus;
  final String? submittedAt;
  final String? updatedAt;

  const RepairListItem({
    required this.referenceNumber,
    required this.deviceCategoryName,
    required this.friendlyStatus,
    this.submittedAt,
    this.updatedAt,
  });

  factory RepairListItem.fromJson(Map<String, dynamic> json) => RepairListItem(
        referenceNumber: json['referenceNumber'] as String,
        deviceCategoryName: json['deviceCategoryName'] as String,
        friendlyStatus: json['friendlyStatus'] as String,
        submittedAt: json['submittedAt'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}

class CustomerHomeRepository {
  CustomerHomeRepository(this._dio);
  final Dio _dio;

  Future<List<RepairListItem>> myRepairs() async {
    try {
      final res = await _dio.get<List<dynamic>>('/me/repairs');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(RepairListItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<RepairDetail> repairDetail(String referenceNumber) async {
    try {
      final res = await _dio
          .get<Map<String, dynamic>>('/me/repairs/$referenceNumber');
      return RepairDetail.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }

  Future<void> provideInfo(String referenceNumber, String message) async {
    try {
      await _dio.post<void>(
        '/me/repairs/$referenceNumber/provide-info',
        data: {'message': message},
      );
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }
}

class RepairDetail {
  final String? requestId;
  final String referenceNumber;
  final String deviceCategoryName;
  final String? deviceBrand;
  final String? deviceModel;
  final String? issueDescription;
  final String friendlyStatus;
  final bool canProvideInfo;
  final String? rejectionMessage;
  final String? infoRequestedMessage;
  final List<StatusHistoryItem> statusHistory;
  final String? submittedAt;
  final String? updatedAt;

  const RepairDetail({
    this.requestId,
    required this.referenceNumber,
    required this.deviceCategoryName,
    this.deviceBrand,
    this.deviceModel,
    this.issueDescription,
    required this.friendlyStatus,
    required this.canProvideInfo,
    this.rejectionMessage,
    this.infoRequestedMessage,
    required this.statusHistory,
    this.submittedAt,
    this.updatedAt,
  });

  factory RepairDetail.fromJson(Map<String, dynamic> json) => RepairDetail(
        requestId: json['requestId'] as String?,
        referenceNumber: json['referenceNumber'] as String,
        deviceCategoryName: json['deviceCategoryName'] as String,
        deviceBrand: json['deviceBrand'] as String?,
        deviceModel: json['deviceModel'] as String?,
        issueDescription: json['issueDescription'] as String?,
        friendlyStatus: json['friendlyStatus'] as String,
        canProvideInfo: json['canProvideInfo'] as bool? ?? false,
        rejectionMessage: json['rejectionMessage'] as String?,
        infoRequestedMessage: json['infoRequestedMessage'] as String?,
        statusHistory: (json['statusHistory'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>()
            .map(StatusHistoryItem.fromJson)
            .toList(),
        submittedAt: json['submittedAt'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}

class StatusHistoryItem {
  final String action;
  final String? detail;
  final String occurredAt;

  const StatusHistoryItem({
    required this.action,
    this.detail,
    required this.occurredAt,
  });

  factory StatusHistoryItem.fromJson(Map<String, dynamic> json) =>
      StatusHistoryItem(
        action: json['action'] as String,
        detail: json['detail'] as String?,
        occurredAt: json['occurredAt'] as String,
      );
}
