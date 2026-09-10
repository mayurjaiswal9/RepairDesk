import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';
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
      final res =
          await _dio.get<List<dynamic>>('/requests/$requestId/attachments');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AttachmentItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<List<AttachmentItem>> listForJob(String jobId) async {
    try {
      final res = await _dio.get<List<dynamic>>('/jobs/$jobId/attachments');
      return res.data!
          .cast<Map<String, dynamic>>()
          .map(AttachmentItem.fromJson)
          .toList();
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    }
  }

  Future<AttachmentItem> uploadToRequest(String requestId, XFile file) async {
    try {
      final formData = FormData.fromMap({
        'file': await _multipartImage(file),
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

  Future<AttachmentItem> uploadToJob(String jobId, XFile file) async {
    try {
      final formData = FormData.fromMap({
        'file': await _multipartImage(file),
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

  /// Camera cache paths can vanish before Dio reads them; send bytes instead.
  /// MIME is taken from magic bytes so a `.heic` name on JPEG data is accepted.
  Future<MultipartFile> _multipartImage(XFile file) async {
    final bytes = await file.readAsBytes();
    if (bytes.isEmpty) {
      throw const AppException(
        errorCode: 'VALIDATION_ERROR',
        message: 'The photo could not be read. Please try again.',
      );
    }
    final mediaType = _mediaTypeForBytes(bytes, file.name, file.path);
    final rawName = (file.name.isNotEmpty ? file.name : file.path)
        .split(RegExp(r'[\\/]'))
        .last;
    final filename = _ensureExtension(rawName, mediaType);
    return MultipartFile.fromBytes(
      bytes,
      filename: filename,
      contentType: mediaType,
    );
  }

  static MediaType _mediaTypeForBytes(
    List<int> bytes,
    String name,
    String path,
  ) {
    if (bytes.length >= 3 &&
        bytes[0] == 0xFF &&
        bytes[1] == 0xD8 &&
        bytes[2] == 0xFF) {
      return MediaType('image', 'jpeg');
    }
    if (bytes.length >= 4 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return MediaType('image', 'png');
    }
    if (bytes.length >= 3 &&
        bytes[0] == 0x47 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46) {
      return MediaType('image', 'gif');
    }
    if (bytes.length >= 12 &&
        bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50) {
      return MediaType('image', 'webp');
    }
    return _mediaTypeForPath(name.isNotEmpty ? name : path);
  }

  static MediaType _mediaTypeForPath(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.png')) return MediaType('image', 'png');
    if (lower.endsWith('.gif')) return MediaType('image', 'gif');
    if (lower.endsWith('.webp')) return MediaType('image', 'webp');
    if (lower.endsWith('.heic')) return MediaType('image', 'heic');
    if (lower.endsWith('.heif')) return MediaType('image', 'heif');
    // image_picker with imageQuality re-encodes as JPEG by default.
    return MediaType('image', 'jpeg');
  }

  static String _ensureExtension(String name, MediaType type) {
    final lower = name.toLowerCase();
    final hasExt = lower.contains('.') &&
        !lower.endsWith('.') &&
        lower.lastIndexOf('.') > 0;
    if (hasExt) return name;
    final ext = switch (type.subtype) {
      'png' => 'png',
      'gif' => 'gif',
      'webp' => 'webp',
      'heic' || 'heif' => 'heic',
      _ => 'jpg',
    };
    return '$name.$ext';
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

  /// Downloads image bytes by attachment id (preferred — no file-extension
  /// routing issues). Falls back to storage-key path for older APIs.
  Future<Uint8List> fetchImageBytes(AttachmentItem attachment) async {
    try {
      return await _downloadBytes('/attachments/by-id/${attachment.id}');
    } on AppException catch (e) {
      if (e.isNotFound || e.errorCode == 'UNKNOWN') {
        // Older backend without by-id route — try legacy key path.
        return _downloadBytes(
            '/attachments/${Uri.encodeComponent(attachment.storageKey)}');
      }
      rethrow;
    }
  }

  Future<Uint8List> _downloadBytes(String path) async {
    try {
      final res = await _dio.get<dynamic>(
        path,
        options: Options(
          responseType: ResponseType.bytes,
          contentType: null,
          headers: {
            Headers.acceptHeader: 'image/*,*/*',
          },
        ),
      );
      return _asImageBytes(res.data);
    } on DioException catch (e) {
      throw e.error is AppException
          ? e.error as AppException
          : AppException.fromDioError(e);
    } catch (e) {
      throw AppException(
        errorCode: 'UNKNOWN',
        message: 'Could not read image data ($e)',
      );
    }
  }

  static Uint8List _asImageBytes(dynamic data) {
    if (data == null) {
      throw const AppException(
          errorCode: 'NOT_FOUND', message: 'Image data was empty.');
    }
    late final Uint8List bytes;
    if (data is Uint8List) {
      bytes = data;
    } else if (data is List<int>) {
      bytes = Uint8List.fromList(data);
    } else {
      throw AppException(
        errorCode: 'UNKNOWN',
        message: 'Unexpected image payload type: ${data.runtimeType}',
      );
    }
    if (bytes.isEmpty) {
      throw const AppException(
          errorCode: 'NOT_FOUND', message: 'Image data was empty.');
    }
    // Error JSON sometimes arrives as bytes when responseType is bytes.
    if (bytes[0] == 0x7B) {
      throw AppException.fromDioError(
        DioException(
          requestOptions: RequestOptions(),
          response: Response(
            requestOptions: RequestOptions(),
            data: bytes,
            statusCode: 400,
          ),
        ),
      );
    }
    if (!_looksLikeImage(bytes)) {
      throw const AppException(
        errorCode: 'VALIDATION_ERROR',
        message: 'This photo is in a format the app cannot display. Please upload a JPEG or PNG.',
      );
    }
    return bytes;
  }

  static bool _looksLikeImage(Uint8List bytes) {
    if (bytes.length >= 3 &&
        bytes[0] == 0xFF &&
        bytes[1] == 0xD8 &&
        bytes[2] == 0xFF) {
      return true;
    }
    if (bytes.length >= 4 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return true;
    }
    if (bytes.length >= 3 &&
        bytes[0] == 0x47 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46) {
      return true;
    }
    if (bytes.length >= 12 &&
        bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50) {
      return true;
    }
    return false;
  }
}
