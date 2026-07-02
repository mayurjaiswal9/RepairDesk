import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'customer_management_repository.g.dart';

@riverpod
CustomerManagementRepository customerManagementRepository(Ref ref) =>
    CustomerManagementRepository(ref.watch(apiClientProvider));

class CustomerSummary {
  final String id;
  final String username;
  final String? name;
  final String mobile;
  final String status;

  const CustomerSummary({
    required this.id,
    required this.username,
    this.name,
    required this.mobile,
    required this.status,
  });

  factory CustomerSummary.fromJson(Map<String, dynamic> json) =>
      CustomerSummary(
        id: json['id'] as String,
        username: json['username'] as String,
        name: json['name'] as String?,
        mobile: json['mobile'] as String,
        status: json['status'] as String,
      );
}

class CustomerHistory {
  final CustomerSummary customer;
  final List<Map<String, dynamic>> requests;
  final List<Map<String, dynamic>> jobs;

  const CustomerHistory({
    required this.customer,
    required this.requests,
    required this.jobs,
  });

  factory CustomerHistory.fromJson(Map<String, dynamic> json) =>
      CustomerHistory(
        customer: CustomerSummary.fromJson(
            json['customer'] as Map<String, dynamic>),
        requests: (json['requests'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>(),
        jobs: (json['jobs'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>(),
      );
}

class CustomerManagementRepository {
  CustomerManagementRepository(this._dio);
  final Dio _dio;

  Future<List<CustomerSummary>> search(String? q) async {
    try {
      final res = await _dio.get<List<dynamic>>('/customers',
          queryParameters: q != null && q.isNotEmpty ? {'q': q} : null);
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(CustomerSummary.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<CustomerHistory> history(String customerId) async {
    try {
      final res = await _dio
          .get<Map<String, dynamic>>('/customers/$customerId/history');
      return CustomerHistory.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
