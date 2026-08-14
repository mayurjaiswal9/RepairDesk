import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import '../../../shared/theme/app_theme.dart';
import '../../../shared/utils/image_source_picker.dart';
import '../../attachments/data/attachments_repository.dart';
import '../../attachments/presentation/attachments_widget.dart';
import '../data/customer_home_repository.dart';

final _detailFamily = FutureProvider.autoDispose
    .family<RepairDetail, String>((ref, ref_) =>
        ref.watch(customerHomeRepositoryProvider).repairDetail(ref_));

class RepairDetailScreen extends ConsumerWidget {
  const RepairDetailScreen({super.key, required this.referenceNumber});
  final String referenceNumber;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detailAsync = ref.watch(_detailFamily(referenceNumber));

    return Scaffold(
      appBar: AppBar(title: Text(referenceNumber)),
      body: detailAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline,
                  size: 48, color: AppTheme.error),
              const SizedBox(height: AppTheme.spaceM),
              Text(e.toString(),
                  style: const TextStyle(color: AppTheme.error),
                  textAlign: TextAlign.center),
              FilledButton(
                  onPressed: () =>
                      ref.invalidate(_detailFamily(referenceNumber)),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (detail) {
          final showPanel =
              detail.canProvideInfo && detail.requestId != null;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () =>
                      ref.refresh(_detailFamily(referenceNumber).future),
                  child: ListView(
                    padding: EdgeInsets.only(
                      left: AppTheme.spaceM,
                      right: AppTheme.spaceM,
                      top: AppTheme.spaceM,
                      bottom:
                          showPanel ? AppTheme.spaceS : AppTheme.spaceM,
                    ),
                    children: [
                      // ── Status banner ──────────────────────────────
                      _StatusBanner(status: detail.friendlyStatus),
                      const SizedBox(height: AppTheme.spaceM),

                      // ── Alerts ─────────────────────────────────────
                      if (detail.infoRequestedMessage != null)
                        _AlertCard(
                          icon: Icons.help_outline,
                          color: AppTheme.secondary,
                          title: 'More information needed',
                          body: detail.infoRequestedMessage!,
                        ),
                      if (detail.rejectionMessage != null)
                        _AlertCard(
                          icon: Icons.cancel_outlined,
                          color: AppTheme.error,
                          title: 'Request not accepted',
                          body: detail.rejectionMessage!,
                        ),

                      // ── Device & Issue ─────────────────────────────
                      _Section(
                        title: 'Device',
                        children: [
                          _InfoRow(
                              label: 'Type',
                              value: detail.deviceCategoryName),
                          if (detail.deviceBrand != null ||
                              detail.deviceModel != null)
                            _InfoRow(
                                label: 'Model',
                                value: [
                                  detail.deviceBrand,
                                  detail.deviceModel
                                ].whereType<String>().join(' ')),
                        ],
                      ),
                      const SizedBox(height: AppTheme.spaceM),

                      if (detail.issueDescription != null)
                        _Section(
                          title: 'Issue Described',
                          children: [
                            Text(detail.issueDescription!,
                                style: const TextStyle(
                                    color: AppTheme.textPrimary)),
                          ],
                        ),
                      const SizedBox(height: AppTheme.spaceM),

                      // ── Photos ──────────────────────────────────────
                      // Previously there was no way to see photos you'd
                      // already attached (at submission or via "Add Photo"
                      // below) — this screen only ever let you add more.
                      if (detail.requestId != null) ...[
                        _Section(
                          title: 'Photos',
                          children: [
                            AttachmentsWidget(
                              ownerType: 'REPAIR_REQUEST',
                              ownerId: detail.requestId!,
                              canUpload: true,
                              canDelete: false,
                              showHeader: false,
                            ),
                          ],
                        ),
                        const SizedBox(height: AppTheme.spaceM),
                      ],

                      // ── Timeline ───────────────────────────────────
                      if (detail.statusHistory.isNotEmpty) ...[
                        Text('Timeline',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(color: AppTheme.primary)),
                        const Divider(),
                        ...detail.statusHistory.asMap().entries.map((e) =>
                            _TimelineItem(
                              item: e.value,
                              isLast:
                                  e.key == detail.statusHistory.length - 1,
                            )),
                      ],
                    ],
                  ),
                ),
              ),

              // ── Respond panel (sticky bottom, outside ListView) ───────
              if (showPanel)
                _RespondPanel(
                  referenceNumber: referenceNumber,
                  requestId: detail.requestId!,
                  onSubmitted: () =>
                      ref.invalidate(_detailFamily(referenceNumber)),
                ),
            ],
          );
        },
      ),
    );
  }
}

// ── Respond panel ─────────────────────────────────────────────────────────────

class _RespondPanel extends ConsumerStatefulWidget {
  const _RespondPanel({
    required this.referenceNumber,
    required this.requestId,
    required this.onSubmitted,
  });

  final String referenceNumber;
  final String requestId;
  final VoidCallback onSubmitted;

  @override
  ConsumerState<_RespondPanel> createState() => _RespondPanelState();
}

class _RespondPanelState extends ConsumerState<_RespondPanel> {
  final _msgCtrl = TextEditingController();
  final _picker = ImagePicker();
  bool _submitting = false;
  bool _uploadingPhoto = false;

  @override
  void dispose() {
    _msgCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickPhoto() async {
    final picked = await pickImageWithSourceChoice(context, _picker);
    if (picked == null) return;

    setState(() => _uploadingPhoto = true);
    try {
      await ref
          .read(attachmentsRepositoryProvider)
          .uploadToRequest(widget.requestId, picked);
      // Refresh the read-only Photos section above, which uses its own
      // provider keyed by (ownerType, ownerId) — invalidating `onSubmitted`'s
      // target (the repair detail itself) doesn't touch it.
      ref.invalidate(attachmentsProvider(
          (ownerType: 'REPAIR_REQUEST', ownerId: widget.requestId)));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Photo added')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text('Upload failed: $e'),
                backgroundColor: AppTheme.error));
      }
    } finally {
      if (mounted) setState(() => _uploadingPhoto = false);
    }
  }

  Future<void> _submit() async {
    final msg = _msgCtrl.text.trim();
    if (msg.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please add a message')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await ref
          .read(customerHomeRepositoryProvider)
          .provideInfo(widget.referenceNumber, msg);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Response submitted — your request is back under review')));
        widget.onSubmitted();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      color: Theme.of(context).colorScheme.surface,
      child: SafeArea(
        top: false,
        child: Padding(
        padding: const EdgeInsets.fromLTRB(
            AppTheme.spaceM, AppTheme.spaceS, AppTheme.spaceM, AppTheme.spaceM),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.reply_outlined,
                    color: AppTheme.secondary, size: 18),
                const SizedBox(width: AppTheme.spaceS),
                Text('Your Response',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: AppTheme.secondary,
                        fontWeight: FontWeight.w600)),
              ],
            ),
            const SizedBox(height: AppTheme.spaceS),
            TextField(
              controller: _msgCtrl,
              maxLines: 4,
              minLines: 2,
              decoration: const InputDecoration(
                hintText: 'Describe the additional information requested…',
                border: OutlineInputBorder(),
                contentPadding:
                    EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),
            const SizedBox(height: AppTheme.spaceS),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: _uploadingPhoto
                        ? const SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.add_photo_alternate_outlined,
                            size: 16),
                    label: const Text('Add Photo'),
                    onPressed: _uploadingPhoto ? null : _pickPhoto,
                  ),
                ),
                const SizedBox(width: AppTheme.spaceM),
                Expanded(
                  child: FilledButton.icon(
                    icon: _submitting
                        ? const SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.send_outlined, size: 16),
                    label: const Text('Submit'),
                    onPressed: _submitting ? null : _submit,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
      ),
    );
  }
}

// ── Status banner ────────────────────────────────────────────────────────────

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.status});
  final String status;

  static const _colors = {
    'Submitted — Under Review': AppTheme.warning,
    'More Information Needed': AppTheme.secondary,
    'Accepted — Being Prepared': AppTheme.primary,
    'Assigned to Technician': AppTheme.primary,
    'Appointment Scheduled': AppTheme.primary,
    'Repair In Progress': AppTheme.success,
    'Waiting for Parts': AppTheme.warning,
    'Repair Completed': AppTheme.success,
    'Not Accepted': AppTheme.error,
    'Cancelled': AppTheme.textSecondary,
  };

  @override
  Widget build(BuildContext context) {
    final color = _colors[status] ?? AppTheme.primary;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
          vertical: AppTheme.spaceM, horizontal: AppTheme.spaceL),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        border: Border.all(color: color.withValues(alpha: 0.30)),
      ),
      child: Column(
        children: [
          Icon(_iconFor(status), color: color, size: 32),
          const SizedBox(height: AppTheme.spaceS),
          Text(
            status,
            style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: color),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  static IconData _iconFor(String s) {
    if (s.contains('Completed')) return Icons.check_circle_outline;
    if (s.contains('Progress')) return Icons.build_outlined;
    if (s.contains('Waiting')) return Icons.inventory_2_outlined;
    if (s.contains('Accepted') || s.contains('Preparing')) {
      return Icons.thumb_up_outlined;
    }
    if (s.contains('Review') || s.contains('Submitted')) {
      return Icons.hourglass_top_outlined;
    }
    if (s.contains('Information')) return Icons.help_outline;
    if (s.contains('Not Accepted')) return Icons.cancel_outlined;
    if (s.contains('Cancelled')) return Icons.block_outlined;
    return Icons.info_outline;
  }
}

// ── Alert card ───────────────────────────────────────────────────────────────

class _AlertCard extends StatelessWidget {
  const _AlertCard(
      {required this.icon,
      required this.color,
      required this.title,
      required this.body});
  final IconData icon;
  final Color color;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceM),
      padding: const EdgeInsets.all(AppTheme.spaceM),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: AppTheme.spaceS),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: TextStyle(
                        color: color, fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text(body,
                    style: TextStyle(
                        color: color.withValues(alpha: 0.85),
                        fontSize: 13)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Timeline ─────────────────────────────────────────────────────────────────

class _TimelineItem extends StatelessWidget {
  const _TimelineItem({required this.item, required this.isLast});
  final StatusHistoryItem item;
  final bool isLast;

  static const _actionLabels = {
    'SUBMITTED': 'Request submitted',
    'APPROVED': 'Request accepted',
    'REJECTED': 'Request not accepted',
    'INFO_REQUESTED': 'More information requested',
    'UPDATED': 'Request updated',
    'CANCELLED': 'Request cancelled',
    'CREATED': 'Job created',
    'ASSIGNED': 'Technician assigned',
    'STARTED': 'Repair started',
    'AWAITING_PARTS': 'Waiting for parts',
    'RESUMED': 'Repair resumed',
    'COMPLETED': 'Repair completed',
  };

  @override
  Widget build(BuildContext context) {
    final dt = DateTime.tryParse(item.occurredAt)?.toLocal();
    final label = _actionLabels[item.action] ?? item.action;
    final timeStr = dt != null
        ? DateFormat('MMM d, h:mm a').format(dt)
        : item.occurredAt;

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Vertical line + dot
          Column(
            children: [
              Container(
                width: 12,
                height: 12,
                margin: const EdgeInsets.only(top: 4),
                decoration: const BoxDecoration(
                  color: AppTheme.primary,
                  shape: BoxShape.circle,
                ),
              ),
              if (!isLast)
                Expanded(
                  child: Container(
                      width: 2, color: AppTheme.primary.withValues(alpha: 0.2)),
                ),
            ],
          ),
          const SizedBox(width: AppTheme.spaceM),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(
                  bottom: isLast ? 0 : AppTheme.spaceM),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label,
                      style: const TextStyle(fontWeight: FontWeight.w500)),
                  Text(timeStr,
                      style: const TextStyle(
                          fontSize: 12,
                          color: AppTheme.textSecondary)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children});
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(color: AppTheme.primary)),
        const Divider(),
        ...children,
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceXS),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
              width: 90,
              child: Text(label,
                  style: const TextStyle(
                      color: AppTheme.textSecondary, fontSize: 13))),
          Expanded(
              child: Text(value,
                  style: const TextStyle(fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}
