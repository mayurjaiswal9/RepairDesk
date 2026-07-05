import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';

part 'attachments_repository.g.dart';

@riverpod
AttachmentsRepository attachmentsRepository(Ref ref) =>
    AttachmentsRepository(ref.watch(apiClientProvider));

class AttachmentItem {
  final String id;
  final String ownerType;
  final String ownerId;
  final String contentType;
  final int fileSizeBytes;
  final String? originalFilename;
  final String storageKey;
  final String? createdAt;

  const AttachmentItem({
    required this.id,
    required this.ownerType,
    required this.ownerId,
    required this.contentType,
    required this.fileSizeBytes,
    this.originalFilename,
    required this.storageKey,
    this.createdAt,
  });

  factory AttachmentItem.fromJson(Map<String, dynamic> json) => AttachmentItem(
        id: json['id'] as String,
        ownerType: json['ownerType'] as String,
        ownerId: json['ownerId'] as String,
        contentType: json['contentType'] as String,
        fileSizeBytes: (json['fileSizeBytes'] as num).toInt(),
        originalFilename: json['originalFilename'] as String?,
        storageKey: json['storageKey'] as String,
        createdAt: json['createdAt'] as String?,
      );

  String get displayName =>
      originalFilename ?? storageKey.split('/').last;

  String get sizeLabel {
    if (fileSizeBytes < 1024) return '$fileSizeBytes B';
    if (fileSizeBytes < 1024 * 1024) {
      return '${(fileSizeBytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(fileSizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class AttachmentsRepository {
  AttachmentsRepository(this._dio);
  final Dio _dio;

  Future<List<AttachmentItem>> listForRequest(String requestId) async {
    try {
      final res = await _dio.get<List<dynamic>>('/requests/$requestId/attachments');
      return res.data!.cast<Map<String, dynamic>>().map(AttachmentItem.fromJson).toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<AttachmentItem>> listForJob(String jobId) async {
    try {
      final res = await _dio.get<List<dynamic>>('/jobs/$jobId/attachments');
      return res.data!.cast<Map<String, dynamic>>().map(AttachmentItem.fromJson).toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AttachmentItem> uploadToRequest(String requestId, File file) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          file.path,
          filename: file.path.split('/').last,
        ),
      });
      final res = await _dio.post<Map<String, dynamic>>(
        '/requests/$requestId/attachments',
        data: formData,
      );
      return AttachmentItem.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AttachmentItem> uploadToJob(String jobId, File file) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          file.path,
          filename: file.path.split('/').last,
        ),
      });
      final res = await _dio.post<Map<String, dynamic>>(
        '/jobs/$jobId/attachments',
        data: formData,
      );
      return AttachmentItem.fromJson(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<void> delete(String attachmentId) async {
    try {
      await _dio.delete<void>('/attachments/$attachmentId');
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  /// Downloads the raw image bytes for a given storageKey (requires auth).
  Future<Uint8List> fetchImageBytes(String storageKey) async {
    try {
      final res = await _dio.get<List<int>>(
        '/attachments/$storageKey',
        options: Options(responseType: ResponseType.bytes),
      );
      return Uint8List.fromList(res.data!);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }
}
