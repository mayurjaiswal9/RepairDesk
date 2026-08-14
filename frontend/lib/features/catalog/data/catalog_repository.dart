import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'catalog_repository.g.dart';

@riverpod
CatalogRepository catalogRepository(Ref ref) =>
    CatalogRepository(ref.watch(apiClientProvider));

/// Whether a device category offers a curated brand list ([configured])
/// or falls back to free-text/"Others" entry ([freeText]) on the New
/// Request screen (PROD-004).
enum BrandMode { configured, freeText }

BrandMode _brandModeFromJson(String? raw) =>
    raw == 'CONFIGURED' ? BrandMode.configured : BrandMode.freeText;

class DeviceCategory {
  final String id;
  final String name;
  final BrandMode brandMode;

  const DeviceCategory(
      {required this.id, required this.name, this.brandMode = BrandMode.freeText});

  factory DeviceCategory.fromJson(Map<String, dynamic> json) => DeviceCategory(
        id: json['id'] as String,
        name: json['name'] as String,
        brandMode: _brandModeFromJson(json['brandMode'] as String?),
      );
}

/// A curated brand entry under a device category.
class DeviceBrand {
  final String id;
  final String name;

  const DeviceBrand({required this.id, required this.name});

  factory DeviceBrand.fromJson(Map<String, dynamic> json) => DeviceBrand(
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

  /// Active brands curated for [categoryId]. Only meaningful when the
  /// category's [BrandMode] is [BrandMode.configured].
  Future<List<DeviceBrand>> activeBrands(String categoryId) async {
    try {
      final res = await _dio
          .get<List<dynamic>>('/device-categories/$categoryId/brands');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(DeviceBrand.fromJson)
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
