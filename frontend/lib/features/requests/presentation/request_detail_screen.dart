import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/location/service_location_card.dart';
import '../../../shared/theme/app_theme.dart';
import '../../attachments/presentation/attachments_widget.dart';
import '../data/requests_repository.dart';
import 'job_status.dart';

final _requestDetailFamily = FutureProvider.autoDispose
    .family<RequestDetail, String>((ref, id) =>
        ref.watch(requestsRepositoryProvider).getDetail(id));

class RequestDetailScreen extends ConsumerWidget {
  const RequestDetailScreen({
    super.key,
    required this.requestId,
    required this.onActionCompleted,
  });

  final String requestId;
  final VoidCallback onActionCompleted;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detailAsync = ref.watch(_requestDetailFamily(requestId));

    return Scaffold(
      appBar: AppBar(title: const Text('Request Detail')),
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
                      ref.invalidate(_requestDetailFamily(requestId)),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (detail) => _RequestDetailBody(
          detail: detail,
          onActionCompleted: () {
            ref.invalidate(_requestDetailFamily(requestId));
            onActionCompleted();
          },
        ),
      ),
    );
  }
}

class _RequestDetailBody extends ConsumerWidget {
  const _RequestDetailBody(
      {required this.detail, required this.onActionCompleted});
  final RequestDetail detail;
  final VoidCallback onActionCompleted;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final canAct = detail.status == 'PENDING_REVIEW' ||
        detail.status == 'INFORMATION_REQUIRED';

    return RefreshIndicator(
      onRefresh: () async => onActionCompleted(),
      child: ListView(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        children: [
          // ── Header ────────────────────────────────────────────────────
          Row(
            children: [
              Expanded(
                child: Text(detail.referenceNumber,
                    style: Theme.of(context)
                        .textTheme
                        .titleLarge
                        ?.copyWith(
                            color: AppTheme.primary,
                            fontWeight: FontWeight.bold)),
              ),
              _StatusChip(status: detail.status),
            ],
          ),
          if (detail.submittedAt != null) ...[
            const SizedBox(height: AppTheme.spaceXS),
            Text('Submitted: ${_fmtDate(detail.submittedAt!)}',
                style: const TextStyle(
                    color: AppTheme.textSecondary, fontSize: 12)),
          ],
          const SizedBox(height: AppTheme.spaceM),

          // ── Job progress (Accepted requests only) ────────────────────
          // "Accepted" only means the request was reviewed and a job was
          // created — it does not mean the repair is complete or even
          // picked up yet, so surface the linked job's real status here.
          if (detail.status == 'APPROVED') ...[
            _Section(title: 'Job Status', children: [
              _JobStatusInfoRow(
                jobStatus: detail.jobStatus,
                assignedTechnicianName: detail.assignedTechnicianName,
              ),
            ]),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Alerts ───────────────────────────────────────────────────
          if (detail.infoRequestedMessage != null)
            _AlertCard(
              icon: Icons.help_outline,
              color: AppTheme.secondary,
              title: 'Information requested from customer',
              body: detail.infoRequestedMessage!,
            ),
          if (detail.customerInfoResponse != null)
            _AlertCard(
              icon: Icons.reply_outlined,
              color: AppTheme.success,
              title: 'Information provided by customer',
              body: detail.customerInfoResponse!,
            ),
          if (detail.customerFacingMessage != null)
            _AlertCard(
              icon: Icons.cancel_outlined,
              color: AppTheme.error,
              title: 'Rejection message sent',
              body: detail.customerFacingMessage!,
            ),

          // ── Customer & Contact ─────────────────────────────────────
          _Section(title: 'Customer & Contact', children: [
            _InfoRow(label: 'Username', value: '@${detail.customerUsername}'),
            _InfoRow(label: 'Contact', value: detail.contactName),
            if (detail.contactMobile != null)
              _InfoRow(label: 'Mobile', value: detail.contactMobile!),
            if (detail.contactEmail != null &&
                detail.contactEmail!.isNotEmpty)
              _InfoRow(label: 'Email', value: detail.contactEmail!),
          ]),
          const SizedBox(height: AppTheme.spaceM),

          if (detail.serviceLocation != null) ...[
            _Section(title: 'Service address', children: [
              ServiceLocationCard(location: detail.serviceLocation),
            ]),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Device ────────────────────────────────────────────────
          _Section(title: 'Device', children: [
            _InfoRow(label: 'Category', value: detail.deviceCategoryName),
            if (detail.deviceBrand != null || detail.deviceModel != null)
              _InfoRow(
                  label: 'Model',
                  value: [detail.deviceBrand, detail.deviceModel]
                      .whereType<String>()
                      .join(' ')),
            if (detail.deviceSerial != null)
              _InfoRow(label: 'Serial', value: detail.deviceSerial!),
            if (detail.purchaseDate != null)
              _InfoRow(label: 'Purchased', value: detail.purchaseDate!),
            if (detail.warrantyStatus != null)
              _InfoRow(
                  label: 'Warranty',
                  value: _fmtWarranty(detail.warrantyStatus!)),
          ]),
          const SizedBox(height: AppTheme.spaceM),

          // ── Issue ────────────────────────────────────────────────
          if (detail.issueDescription != null) ...[
            _Section(title: 'Issue Description', children: [
              Text(detail.issueDescription!,
                  style: const TextStyle(
                      color: AppTheme.textPrimary, height: 1.5)),
            ]),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Preferred appointment ────────────────────────────────
          if (detail.preferredDate != null) ...[
            _Section(title: 'Preferred Appointment', children: [
              _InfoRow(label: 'Date', value: detail.preferredDate!),
              if (detail.preferredTime != null)
                _InfoRow(label: 'Time', value: detail.preferredTime!),
            ]),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Customer photos ───────────────────────────────────────
          _Section(title: 'Customer Photos', children: [
            AttachmentsWidget(
              ownerType: 'REPAIR_REQUEST',
              ownerId: detail.id,
              canUpload: false,
              canDelete: false,
              showHeader: false,
            ),
          ]),
          const SizedBox(height: AppTheme.spaceM),

          // ── Actions ───────────────────────────────────────────────
          if (canAct) ...[
            Text('Actions',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            const SizedBox(height: AppTheme.spaceS),
            _ActionButtons(
              detail: detail,
              onActionCompleted: onActionCompleted,
            ),
            const SizedBox(height: AppTheme.spaceL),
          ],
        ],
      ),
    );
  }

  static String _fmtDate(String iso) {
    try {
      final dt = DateTime.parse(iso);
      return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-'
          '${dt.day.toString().padLeft(2, '0')}';
    } catch (_) {
      return iso;
    }
  }

  static String _fmtWarranty(String raw) => switch (raw) {
        'IN_WARRANTY' => 'In warranty',
        'OUT_OF_WARRANTY' => 'Out of warranty',
        'UNKNOWN' => 'Unknown',
        _ => raw,
      };
}

// ── Action buttons ────────────────────────────────────────────────────────────

class _ActionButtons extends ConsumerWidget {
  const _ActionButtons(
      {required this.detail, required this.onActionCompleted});
  final RequestDetail detail;
  final VoidCallback onActionCompleted;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.check_circle_outline,
                size: 16, color: AppTheme.success),
            label: const Text('Approve',
                style: TextStyle(color: AppTheme.success)),
            style: OutlinedButton.styleFrom(
              side: BorderSide(
                  color: AppTheme.success.withValues(alpha: 0.5)),
              padding: const EdgeInsets.symmetric(vertical: 10),
            ),
            onPressed: () => _approve(context, ref),
          ),
        ),
        const SizedBox(width: AppTheme.spaceS),
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.info_outline,
                size: 16, color: AppTheme.secondary),
            label: const Text('Request Info',
                style: TextStyle(color: AppTheme.secondary)),
            style: OutlinedButton.styleFrom(
              side: BorderSide(
                  color: AppTheme.secondary.withValues(alpha: 0.5)),
              padding: const EdgeInsets.symmetric(vertical: 10),
            ),
            onPressed: () => _requestInfo(context, ref),
          ),
        ),
        const SizedBox(width: AppTheme.spaceS),
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.cancel_outlined,
                size: 16, color: AppTheme.error),
            label: const Text('Reject',
                style: TextStyle(color: AppTheme.error)),
            style: OutlinedButton.styleFrom(
              side:
                  BorderSide(color: AppTheme.error.withValues(alpha: 0.5)),
              padding: const EdgeInsets.symmetric(vertical: 10),
            ),
            onPressed: () => _reject(context, ref),
          ),
        ),
      ],
    );
  }

  Future<void> _approve(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Approve Request'),
        content: Text(
            'Approve ${detail.referenceNumber} and create a job?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Approve')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref.read(requestsRepositoryProvider).approve(detail.id);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text('Request approved — job created')));
        Navigator.of(context).pop();
        onActionCompleted();
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text('Error: $e'),
                backgroundColor: AppTheme.error));
      }
    }
  }

  Future<void> _requestInfo(BuildContext context, WidgetRef ref) async {
    final ctrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Request More Information'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('What do you need from the customer?'),
            const SizedBox(height: AppTheme.spaceM),
            TextField(
              controller: ctrl,
              maxLines: 3,
              autofocus: true,
              decoration: const InputDecoration(
                  labelText: 'Message to customer',
                  border: OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Send')),
        ],
      ),
    );
    if (ok != true || ctrl.text.trim().isEmpty || !context.mounted) return;
    try {
      await ref
          .read(requestsRepositoryProvider)
          .requestInfo(detail.id, ctrl.text.trim());
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Customer notified')));
        onActionCompleted();
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text('Error: $e'),
                backgroundColor: AppTheme.error));
      }
    }
  }

  Future<void> _reject(BuildContext context, WidgetRef ref) async {
    final ctrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Reject Request'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Reject ${detail.referenceNumber}?'),
            const SizedBox(height: AppTheme.spaceM),
            TextField(
              controller: ctrl,
              maxLines: 3,
              autofocus: true,
              decoration: const InputDecoration(
                  labelText: 'Customer message (optional)',
                  border: OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Cancel')),
          FilledButton(
            style:
                FilledButton.styleFrom(backgroundColor: AppTheme.error),
            onPressed: () => Navigator.pop(dlg, true),
            child: const Text('Reject'),
          ),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref.read(requestsRepositoryProvider).reject(
          detail.id, 'UNABLE_TO_SERVICE', ctrl.text.trim());
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Request rejected')));
        Navigator.of(context).pop();
        onActionCompleted();
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text('Error: $e'),
                backgroundColor: AppTheme.error));
      }
    }
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
                    color: AppTheme.textSecondary, fontSize: 13)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(fontWeight: FontWeight.w500)),
          ),
        ],
      ),
    );
  }
}

class _JobStatusInfoRow extends StatelessWidget {
  const _JobStatusInfoRow(
      {required this.jobStatus, this.assignedTechnicianName});
  final String? jobStatus;
  final String? assignedTechnicianName;

  @override
  Widget build(BuildContext context) {
    final info = JobStatusInfo.forStatus(jobStatus);
    if (info == null) {
      return const Text('Job details unavailable',
          style: TextStyle(color: AppTheme.textSecondary, fontSize: 13));
    }
    return Padding(
      padding: const EdgeInsets.only(top: 2, bottom: AppTheme.spaceXS),
      child: Row(
        children: [
          Icon(info.icon, size: 18, color: info.color),
          const SizedBox(width: AppTheme.spaceS),
          Text(info.describe(assignedTechnicianName),
              style: TextStyle(
                  color: info.color,
                  fontWeight: FontWeight.w600,
                  fontSize: 13)),
        ],
      ),
    );
  }
}

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
          Icon(icon, color: color, size: 18),
          const SizedBox(width: AppTheme.spaceS),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w600,
                        fontSize: 13)),
                const SizedBox(height: 2),
                Text(body,
                    style: const TextStyle(
                        fontSize: 13, color: AppTheme.textPrimary)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});
  final String status;

  static const _colors = {
    'PENDING_REVIEW': AppTheme.warning,
    'INFORMATION_REQUIRED': AppTheme.secondary,
    'APPROVED': AppTheme.success,
    'REJECTED': AppTheme.error,
    'CANCELLED': AppTheme.textSecondary,
  };
  static const _labels = {
    'PENDING_REVIEW': 'Pending',
    'INFORMATION_REQUIRED': 'Info Needed',
    'APPROVED': 'Accepted',
    'REJECTED': 'Rejected',
    'CANCELLED': 'Cancelled',
  };

  @override
  Widget build(BuildContext context) {
    final color = _colors[status] ?? AppTheme.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(_labels[status] ?? status,
          style: TextStyle(
              fontSize: 12,
              color: color,
              fontWeight: FontWeight.w600)),
    );
  }
}
