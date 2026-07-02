import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/attachments_repository.dart';

// ── Provider ────────────────────────────────────────────────────────────────

final _attachmentsProvider = FutureProvider.autoDispose
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
  });

  final String ownerType;
  final String ownerId;
  final bool canUpload;
  final bool canDelete;

  @override
  ConsumerState<AttachmentsWidget> createState() => _AttachmentsWidgetState();
}

class _AttachmentsWidgetState extends ConsumerState<AttachmentsWidget> {
  final _picker = ImagePicker();
  bool _uploading = false;

  @override
  Widget build(BuildContext context) {
    final key = (ownerType: widget.ownerType, ownerId: widget.ownerId);
    final attachmentsAsync = ref.watch(_attachmentsProvider(key));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
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
        attachmentsAsync.when(
          loading: () =>
              const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('Failed to load: $e',
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
                    ref.invalidate(_attachmentsProvider(key)),
              )).toList(),
            );
          },
        ),
      ],
    );
  }

  Future<void> _pickAndUpload() async {
    final XFile? picked = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (picked == null) return;

    setState(() => _uploading = true);
    try {
      final repo = ref.read(attachmentsRepositoryProvider);
      final file = File(picked.path);
      if (widget.ownerType == 'JOB') {
        await repo.uploadToJob(widget.ownerId, file);
      } else {
        await repo.uploadToRequest(widget.ownerId, file);
      }
      final key = (ownerType: widget.ownerType, ownerId: widget.ownerId);
      ref.invalidate(_attachmentsProvider(key));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Photo uploaded')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Upload failed: $e'),
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
      leading: const Icon(Icons.image_outlined, color: AppTheme.secondary),
      title: Text(attachment.displayName,
          style: const TextStyle(fontSize: 13),
          overflow: TextOverflow.ellipsis),
      subtitle: Text(attachment.sizeLabel,
          style:
              const TextStyle(color: AppTheme.textSecondary, fontSize: 11)),
      trailing: canDelete
          ? IconButton(
              icon: const Icon(Icons.delete_outline,
                  size: 18, color: AppTheme.error),
              tooltip: 'Delete',
              onPressed: () => _confirmDelete(context, ref),
            )
          : null,
    );
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
