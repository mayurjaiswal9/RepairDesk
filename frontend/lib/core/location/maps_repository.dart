import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../error/app_exception.dart';
import '../network/api_client.dart';
import 'location_models.dart';

class GeocodeHit {
  const GeocodeHit({
    required this.latitude,
    required this.longitude,
    this.formattedAddress,
  });

  final double latitude;
  final double longitude;
  final String? formattedAddress;
}

class PlaceSuggestion {
  const PlaceSuggestion({
    required this.placeId,
    required this.primaryText,
    this.secondaryText,
  });

  final String placeId;
  final String primaryText;
  final String? secondaryText;
}

final mapsRepositoryProvider = Provider<MapsRepository>(
  (ref) => MapsRepository(ref.watch(apiClientProvider)),
);

/// Feature switches for the signed-in user. Refetched each time a screen needs it.
final locationConfigProvider = FutureProvider.autoDispose<LocationConfig>(
  (ref) => ref.watch(mapsRepositoryProvider).locationConfig(),
);

class MapsRepository {
  MapsRepository(this._dio);
  final Dio _dio;

  Future<GeocodeHit> geocode(String address) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/geocode',
        data: {'address': address},
      );
      return _hit(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<PlaceSuggestion>> suggest({
    required String input,
    required String sessionToken,
    double? latitude,
    double? longitude,
  }) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/location/suggestions',
        data: {
          'input': input,
          'sessionToken': sessionToken,
          if (latitude != null && longitude != null) 'latitude': latitude,
          if (latitude != null && longitude != null) 'longitude': longitude,
        },
      );
      final raw = res.data?['suggestions'] as List<dynamic>? ?? const [];
      return raw.cast<Map<String, dynamic>>().map((item) {
        return PlaceSuggestion(
          placeId: item['placeId'] as String,
          primaryText: item['primaryText'] as String? ?? '',
          secondaryText: item['secondaryText'] as String?,
        );
      }).toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<GeocodeHit> selectPlace({
    required String placeId,
    required String sessionToken,
  }) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/location/suggestions/select',
        data: {'placeId': placeId, 'sessionToken': sessionToken},
      );
      return _hit(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<GeocodeHit> customerPin(String text) async {
    try {
      final res = await _dio.post<Map<String, dynamic>>(
        '/location/customer-pin',
        data: {'text': text},
      );
      return _hit(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  GeocodeHit _hit(Map<String, dynamic> data) {
    return GeocodeHit(
      latitude: (data['latitude'] as num).toDouble(),
      longitude: (data['longitude'] as num).toDouble(),
      formattedAddress: data['formattedAddress'] as String?,
    );
  }

  /// Office pin, technician positions, and travel time. Staff and Admin.
  Future<LocationBoard> board() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/location/board');
      return LocationBoard.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  /// Shop pin and attendance switches. Admin only.
  Future<LocationSettings> locationSettings() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/settings/location');
      return LocationSettings.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<LocationSettings> saveLocationSettings(LocationSettings settings) async {
    try {
      final res = await _dio.put<Map<String, dynamic>>(
        '/settings/location',
        data: settings.toJson(),
      );
      return LocationSettings.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  /// Feature switches and intervals. Any signed-in user.
  Future<LocationConfig> locationConfig() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>('/location/config');
      return LocationConfig.fromJson(res.data ?? const {});
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  /// Stores this technician's latest position for the admin location board.
  /// [state] is `TRAVELLING` or `ON_SITE`; omitted for a one-off update.
  Future<void> sharePresence({
    required double latitude,
    required double longitude,
    double? accuracyM,
    String? jobId,
    String? state,
  }) async {
    try {
      await _dio.post<void>('/me/location', data: {
        'latitude': latitude,
        'longitude': longitude,
        'accuracyM': accuracyM,
        'jobId': jobId,
        if (state != null) 'state': state,
      });
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
