import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'catalog_repository.g.dart';

@riverpod
CatalogRepository catalogRepository(Ref ref) =>
    CatalogRepository(ref.watch(apiClientProvider));

class DeviceCategory {
  final String id;
  final String name;

  const DeviceCategory({required this.id, required this.name});

  factory DeviceCategory.fromJson(Map<String, dynamic> json) => DeviceCategory(
        id: json['id'] as String,
        name: json['name'] as String,
      );
}

class CatalogRepository {
  CatalogRepository(this._dio);
  final Dio _dio;

  Future<List<DeviceCategory>> activeCategories() async {
    try {
      final res = await _dio.get<List<dynamic>>('/device-categories');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(DeviceCategory.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}

@riverpod
Future<List<DeviceCategory>> activeCategories(Ref ref) =>
    ref.watch(catalogRepositoryProvider).activeCategories();
