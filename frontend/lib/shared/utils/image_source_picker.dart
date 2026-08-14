import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../theme/app_theme.dart';

/// Shows a bottom sheet letting the user choose "Take Photo" or "Choose from
/// Gallery", then returns the picked image (or `null` if cancelled).
///
/// Every photo-upload entry point in the app should go through this instead
/// of calling `ImagePicker().pickImage(source: ImageSource.gallery)`
/// directly, so camera capture is available everywhere consistently.
Future<XFile?> pickImageWithSourceChoice(
  BuildContext context,
  ImagePicker picker, {
  int imageQuality = 85,
}) async {
  final source = await showModalBottomSheet<ImageSource>(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(AppTheme.radiusM)),
    ),
    builder: (sheetCtx) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: AppTheme.spaceM),
            child: Text('Add Photo',
                style: TextStyle(fontWeight: FontWeight.w600)),
          ),
          ListTile(
            leading: const Icon(Icons.photo_camera_outlined,
                color: AppTheme.primary),
            title: const Text('Take Photo'),
            onTap: () => Navigator.pop(sheetCtx, ImageSource.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined,
                color: AppTheme.primary),
            title: const Text('Choose from Gallery'),
            onTap: () => Navigator.pop(sheetCtx, ImageSource.gallery),
          ),
          const SizedBox(height: AppTheme.spaceS),
        ],
      ),
    ),
  );
  if (source == null) return null;
  // Downscale so camera JPEGs stay well under the API's 10 MB (and the
  // previous 1 MB Spring default that was rejecting technician uploads).
  return picker.pickImage(
    source: source,
    imageQuality: 80,
    maxWidth: 1920,
    maxHeight: 1920,
  );
}
