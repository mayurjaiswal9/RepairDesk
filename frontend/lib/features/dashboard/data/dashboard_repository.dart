import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'dashboard_repository.g.dart';

@riverpod
DashboardRepository dashboardRepository(Ref ref) =>
    DashboardRepository(ref.watch(apiClientProvider));

class DashboardCounters {
  final int pendingReview;
  final int infoRequired;
  final int activeJobs;

  const DashboardCounters({
    required this.pendingReview,
    required this.infoRequired,
    required this.activeJobs,
  });

  factory DashboardCounters.fromJson(Map<String, dynamic> json) =>
      DashboardCounters(
        pendingReview: (json['pendingReview'] as num).toInt(),
        infoRequired: (json['infoRequired'] as num).toInt(),
        activeJobs: (json['activeJobs'] as num).toInt(),
      );
}

class DashboardRepository {
  DashboardRepository(this._dio);
  final Dio _dio;

  Future<DashboardCounters> counters() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/dashboard/counters');
      return DashboardCounters.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException ? e.error as AppException : AppException.fromDioError(e);
    }
  }
}
