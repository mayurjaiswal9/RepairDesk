import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/theme/app_theme.dart';
import '../../../shared/utils/image_source_picker.dart';
import '../data/attachments_repository.dart';

String _friendlyError(Object e) {
  if (e is AppException) {
    if (e.isNotFound ||
        e.message.toLowerCase().contains('missing from storage') ||
        e.message.toLowerCase().contains('file not found')) {
      return 'This photo is no longer on the server. Please upload it again.';
    }
    return e.userMessage;
  }
  return 'Something went wrong. Please try again.';
}

// ── Provider ────────────────────────────────────────────────────────────────

/// Public so screens that upload attachments through a code path other than
/// [AttachmentsWidget]'s own picker (e.g. the customer's "Add Photo" button
/// in the respond panel) can invalidate the matching list after uploading.
final attachmentsProvider = FutureProvider.autoDispose
    .family<List<AttachmentItem>, ({String ownerType, String ownerId})>(
        (ref, args) {
  final repo = ref.watch(attachmentsRepositoryProvider);
  if (args.ownerType == 'JOB') {
    return repo.listForJob(args.ownerId);
  }
  return repo.listForRequest(args.ownerId);
});

// ── Widget ───────────────────────────────────────────────────────────────────

/// Displays a list of attachments with upload and delete actions.
/// [ownerType] is 'REPAIR_REQUEST' or 'JOB'.
class AttachmentsWidget extends ConsumerStatefulWidget {
  const AttachmentsWidget({
    super.key,
    required this.ownerType,
    required this.ownerId,
    this.canUpload = true,
    this.canDelete = false,
    this.showHeader = true,
  });

  final String ownerType;
  final String ownerId;
  final bool canUpload;
  final bool canDelete;
  final bool showHeader;

  @override
  ConsumerState<AttachmentsWidget> createState() => _AttachmentsWidgetState();
}

class _AttachmentsWidgetState extends ConsumerState<AttachmentsWidget> {
  final _picker = ImagePicker();
  bool _uploading = false;

  @override
  Widget build(BuildContext context) {
    final key = (ownerType: widget.ownerType, ownerId: widget.ownerId);
    final attachmentsAsync = ref.watch(attachmentsProvider(key));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (widget.showHeader) ...[
          Row(
            children: [
              Text(
                'Attachments',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary),
              ),
              const Spacer(),
              if (widget.canUpload)
                _uploading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : IconButton(
                        icon: const Icon(Icons.add_photo_alternate_outlined),
                        tooltip: 'Add photo',
                        onPressed: () => _pickAndUpload(),
                      ),
            ],
          ),
          const Divider(),
        ],
        attachmentsAsync.when(
          loading: () =>
              const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('Failed to load: ${_friendlyError(e)}',
              style: const TextStyle(color: AppTheme.error)),
          data: (items) {
            if (items.isEmpty) {
              return const Padding(
                padding: EdgeInsets.symmetric(vertical: AppTheme.spaceS),
                child: Text('No attachments yet',
                    style: TextStyle(color: AppTheme.textSecondary)),
              );
            }
            return Column(
              children: items.map((a) => _AttachmentTile(
                attachment: a,
                canDelete: widget.canDelete,
                onDeleted: () =>
                    ref.invalidate(attachmentsProvider(key)),
              )).toList(),
            );
          },
        ),
      ],
    );
  }

  Future<void> _pickAndUpload() async {
    final XFile? picked = await pickImageWithSourceChoice(context, _picker);
    if (picked == null) return;

    setState(() => _uploading = true);
    try {
      final repo = ref.read(attachmentsRepositoryProvider);
      if (widget.ownerType == 'JOB') {
        await repo.uploadToJob(widget.ownerId, picked);
      } else {
        await repo.uploadToRequest(widget.ownerId, picked);
      }
      final key = (ownerType: widget.ownerType, ownerId: widget.ownerId);
      ref.invalidate(attachmentsProvider(key));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Photo uploaded')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Upload failed: ${_friendlyError(e)}'),
              backgroundColor: AppTheme.error));
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }
}

// ── Tile ─────────────────────────────────────────────────────────────────────

class _AttachmentTile extends ConsumerWidget {
  const _AttachmentTile({
    required this.attachment,
    required this.canDelete,
    required this.onDeleted,
  });

  final AttachmentItem attachment;
  final bool canDelete;
  final VoidCallback onDeleted;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: _Thumbnail(attachment: attachment, ref: ref),
      title: Text(attachment.displayName,
          style: const TextStyle(fontSize: 13),
          overflow: TextOverflow.ellipsis),
      subtitle: Text(attachment.sizeLabel,
          style:
              const TextStyle(color: AppTheme.textSecondary, fontSize: 11)),
      onTap: () => _viewImage(context, ref),
      trailing: canDelete
          ? IconButton(
              icon: const Icon(Icons.delete_outline,
                  size: 18, color: AppTheme.error),
              tooltip: 'Delete',
              onPressed: () => _confirmDelete(context, ref),
            )
          : const Icon(Icons.chevron_right, size: 18,
              color: AppTheme.textSecondary),
    );
  }

  void _viewImage(BuildContext context, WidgetRef ref) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _ImageViewerScreen(
        attachment: attachment,
        repo: ref.read(attachmentsRepositoryProvider),
      ),
    ));
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete attachment?'),
        content: Text('Remove "${attachment.displayName}"?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Delete',
                  style: TextStyle(color: AppTheme.error))),
        ],
      ),
    );
    if (ok != true) return;

    try {
      await ref.read(attachmentsRepositoryProvider).delete(attachment.id);
      onDeleted();
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Delete failed: $e'),
              backgroundColor: AppTheme.error));
      }
    }
  }
}

// ── Thumbnail ─────────────────────────────────────────────────────────────────

class _Thumbnail extends StatefulWidget {
  const _Thumbnail({required this.attachment, required this.ref});
  final AttachmentItem attachment;
  final WidgetRef ref;

  @override
  State<_Thumbnail> createState() => _ThumbnailState();
}

class _ThumbnailState extends State<_Thumbnail> {
  Uint8List? _bytes;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final bytes = await widget.ref
          .read(attachmentsRepositoryProvider)
          .fetchImageBytes(widget.attachment);
      if (mounted) setState(() { _bytes = bytes; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const SizedBox(
          width: 40, height: 40,
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)));
    }
    if (_bytes == null) {
      return const Icon(Icons.image_outlined, color: AppTheme.secondary, size: 36);
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(6),
      child: Image.memory(
        _bytes!,
        width: 40,
        height: 40,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => const Icon(
          Icons.broken_image_outlined,
          color: AppTheme.error,
          size: 36,
        ),
      ),
    );
  }
}

// ── Full-screen image viewer ──────────────────────────────────────────────────

class _ImageViewerScreen extends StatefulWidget {
  const _ImageViewerScreen({required this.attachment, required this.repo});
  final AttachmentItem attachment;
  final AttachmentsRepository repo;

  @override
  State<_ImageViewerScreen> createState() => _ImageViewerScreenState();
}

class _ImageViewerScreenState extends State<_ImageViewerScreen> {
  Uint8List? _bytes;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final bytes = await widget.repo.fetchImageBytes(widget.attachment);
      if (mounted) setState(() { _bytes = bytes; _loading = false; });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = _friendlyError(e);
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Text(widget.attachment.displayName,
            style: const TextStyle(color: Colors.white, fontSize: 14)),
      ),
      body: Center(
        child: _loading
            ? const CircularProgressIndicator(color: Colors.white)
            : _error != null
                ? Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.broken_image_outlined,
                            color: Colors.white70, size: 48),
                        const SizedBox(height: 12),
                        Text(_error!,
                            style: const TextStyle(color: Colors.white),
                            textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        TextButton(
                          onPressed: () {
                            setState(() {
                              _loading = true;
                              _error = null;
                            });
                            _load();
                          },
                          child: const Text('Retry',
                              style: TextStyle(color: Colors.white)),
                        ),
                      ],
                    ),
                  )
                : InteractiveViewer(
                    child: Image.memory(
                      _bytes!,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => const Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'This photo could not be displayed. Please upload it again as JPEG or PNG.',
                          style: TextStyle(color: Colors.white),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                  ),
      ),
    );
  }
}
