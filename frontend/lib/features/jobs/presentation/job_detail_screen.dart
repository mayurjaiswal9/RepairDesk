import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/jobs_repository.dart';

final _jobDetailFamily = FutureProvider.autoDispose
    .family<JobDetail, String>((ref, id) =>
        ref.watch(jobsRepositoryProvider).get(id));

class JobDetailScreen extends ConsumerWidget {
  const JobDetailScreen({super.key, required this.jobId});
  final String jobId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final jobAsync = ref.watch(_jobDetailFamily(jobId));

    return Scaffold(
      appBar: AppBar(title: const Text('Job Detail')),
      body: jobAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: AppTheme.error),
              const SizedBox(height: AppTheme.spaceM),
              Text(e.toString(),
                  style: const TextStyle(color: AppTheme.error),
                  textAlign: TextAlign.center),
              FilledButton(
                  onPressed: () => ref.invalidate(_jobDetailFamily(jobId)),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (job) => _JobDetailBody(
          job: job,
          onRefresh: () => ref.invalidate(_jobDetailFamily(jobId)),
        ),
      ),
    );
  }
}

class _JobDetailBody extends ConsumerWidget {
  const _JobDetailBody({required this.job, required this.onRefresh});
  final JobDetail job;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: ListView(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        children: [
          // ── Header ──────────────────────────────────────────────────────
          Row(
            children: [
              Expanded(
                child: Text(job.referenceNumber,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: AppTheme.primary,
                          fontWeight: FontWeight.bold,
                        )),
              ),
              _StatusBadge(status: job.status),
            ],
          ),
          const SizedBox(height: AppTheme.spaceS),

          // ── Device & Contact ──────────────────────────────────────────
          _Section(
            title: 'Device & Contact',
            children: [
              _InfoRow(label: 'Customer', value: '@${job.customerUsername}'),
              _InfoRow(label: 'Contact', value: job.contactName),
              _InfoRow(label: 'Mobile', value: job.contactMobile),
              _InfoRow(label: 'Category', value: job.deviceCategoryName),
              if (job.deviceBrand != null)
                _InfoRow(
                    label: 'Device',
                    value: [job.deviceBrand, job.deviceModel]
                        .whereType<String>()
                        .join(' ')),
            ],
          ),
          const SizedBox(height: AppTheme.spaceM),

          // ── Issue ─────────────────────────────────────────────────────
          _Section(
            title: 'Issue',
            children: [
              Text(job.issueDescription,
                  style: const TextStyle(color: AppTheme.textPrimary)),
            ],
          ),
          const SizedBox(height: AppTheme.spaceM),

          // ── Technician ────────────────────────────────────────────────
          _Section(
            title: 'Technician',
            children: [
              if (job.assignedTechnicianName != null)
                _InfoRow(
                    label: 'Assigned to',
                    value: job.assignedTechnicianName!)
              else
                const Text('Not yet assigned',
                    style: TextStyle(color: AppTheme.textSecondary)),
              const SizedBox(height: AppTheme.spaceS),
              _AssignTechnicianButton(job: job, onRefresh: onRefresh),
            ],
          ),
          const SizedBox(height: AppTheme.spaceM),

          // ── Resolution ────────────────────────────────────────────────
          if (job.resolutionSummary != null) ...[
            _Section(
              title: 'Resolution',
              children: [
                Text(job.resolutionSummary!,
                    style:
                        const TextStyle(color: AppTheme.textPrimary)),
              ],
            ),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Actions ──────────────────────────────────────────────────
          if (_availableActions(job.status).isNotEmpty) ...[
            Text('Actions',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            ..._availableActions(job.status)
                .map((a) => _ActionTile(action: a, job: job, onRefresh: onRefresh)),
          ],
        ],
      ),
    );
  }

  static List<_JobAction> _availableActions(String status) {
    return switch (status) {
      'ASSIGNED' || 'SCHEDULED' => [_JobAction.start],
      'IN_PROGRESS' => [_JobAction.awaitingParts, _JobAction.complete, _JobAction.cancel],
      'AWAITING_PARTS' => [_JobAction.resume, _JobAction.complete, _JobAction.cancel],
      'CREATED' || 'ASSIGNED' => [_JobAction.cancel],
      _ => [],
    };
  }
}

// ── Assign technician widget ──────────────────────────────────────────────

class _AssignTechnicianButton extends ConsumerWidget {
  const _AssignTechnicianButton({required this.job, required this.onRefresh});
  final JobDetail job;
  final VoidCallback onRefresh;

  static const _assignableStatuses = {'CREATED', 'ASSIGNED'};

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!_assignableStatuses.contains(job.status)) return const SizedBox.shrink();

    return OutlinedButton.icon(
      icon: const Icon(Icons.person_add_outlined, size: 16),
      label: Text(job.assignedTechnicianId != null
          ? 'Change Technician'
          : 'Assign Technician'),
      onPressed: () => _showAssignDialog(context, ref),
    );
  }

  Future<void> _showAssignDialog(BuildContext context, WidgetRef ref) async {
    final techsAsync = await ref.read(jobsRepositoryProvider).technicians();

    if (!context.mounted) return;

    if (techsAsync.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No active technicians found')));
      return;
    }

    String? selectedId = job.assignedTechnicianId;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dlg) => StatefulBuilder(
        builder: (ctx, setDlgState) => AlertDialog(
          title: const Text('Assign Technician'),
          content: DropdownButtonFormField<String>(
            value: techsAsync // ignore: deprecated_member_use
                    .any((t) => t['id'] == selectedId)
                ? selectedId
                : null,
            items: techsAsync
                .map((t) => DropdownMenuItem<String>(
                    value: t['id'] as String,
                    child: Text(t['name'] as String? ?? t['username'] as String)))
                .toList(),
            onChanged: (v) => setDlgState(() => selectedId = v),
            decoration:
                const InputDecoration(labelText: 'Technician'),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dlg, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: selectedId == null
                    ? null
                    : () => Navigator.pop(dlg, true),
                child: const Text('Assign')),
          ],
        ),
      ),
    );
    if (confirmed != true || selectedId == null || !context.mounted) return;
    try {
      await ref.read(jobsRepositoryProvider).assign(job.id, selectedId!);
      onRefresh();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Technician assigned')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    }
  }
}


// ── Action tile ─────────────────────────────────────────────────────────────

enum _JobAction { start, awaitingParts, resume, complete, cancel }

extension on _JobAction {
  String get label => switch (this) {
        _JobAction.start => 'Start Repair',
        _JobAction.awaitingParts => 'Mark Awaiting Parts',
        _JobAction.resume => 'Resume',
        _JobAction.complete => 'Mark Complete',
        _JobAction.cancel => 'Cancel Job',
      };

  IconData get icon => switch (this) {
        _JobAction.start => Icons.play_arrow_outlined,
        _JobAction.awaitingParts => Icons.inventory_2_outlined,
        _JobAction.resume => Icons.replay_outlined,
        _JobAction.complete => Icons.check_circle_outline,
        _JobAction.cancel => Icons.cancel_outlined,
      };

  Color get color => switch (this) {
        _JobAction.start => AppTheme.success,
        _JobAction.resume => AppTheme.success,
        _JobAction.complete => AppTheme.success,
        _JobAction.awaitingParts => AppTheme.warning,
        _JobAction.cancel => AppTheme.error,
      };

  String get apiAction => switch (this) {
        _JobAction.start => 'start',
        _JobAction.awaitingParts => 'awaiting-parts',
        _JobAction.resume => 'resume',
        _JobAction.complete => 'complete',
        _JobAction.cancel => 'cancel',
      };
}

class _ActionTile extends ConsumerWidget {
  const _ActionTile(
      {required this.action, required this.job, required this.onRefresh});
  final _JobAction action;
  final JobDetail job;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: OutlinedButton.icon(
        icon: Icon(action.icon, color: action.color),
        label: Text(action.label,
            style: TextStyle(color: action.color)),
        style: OutlinedButton.styleFrom(
          side: BorderSide(color: action.color.withValues(alpha: 0.5)),
          alignment: Alignment.centerLeft,
          padding: const EdgeInsets.symmetric(
              horizontal: AppTheme.spaceM, vertical: 14),
        ),
        onPressed: () => _run(context, ref),
      ),
    );
  }

  Future<void> _run(BuildContext context, WidgetRef ref) async {
    String? resolution;
    String? reason;

    if (action == _JobAction.complete) {
      final ctrl = TextEditingController();
      final ok = await showDialog<bool>(
        context: context,
        builder: (dlg) => AlertDialog(
          title: const Text('Complete Job'),
          content: TextField(
            controller: ctrl,
            maxLines: 3,
            decoration: const InputDecoration(
                labelText: 'Resolution summary *',
                border: OutlineInputBorder()),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dlg, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(dlg, true),
                child: const Text('Complete')),
          ],
        ),
      );
      if (ok != true || ctrl.text.trim().isEmpty || !context.mounted) return;
      resolution = ctrl.text.trim();
    }

    if (action == _JobAction.cancel) {
      final ctrl = TextEditingController();
      final ok = await showDialog<bool>(
        context: context,
        builder: (dlg) => AlertDialog(
          title: const Text('Cancel Job'),
          content: TextField(
            controller: ctrl,
            maxLines: 3,
            decoration: const InputDecoration(
                labelText: 'Reason *', border: OutlineInputBorder()),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dlg, false),
                child: const Text('Cancel')),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppTheme.error),
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Cancel Job'),
            ),
          ],
        ),
      );
      if (ok != true || ctrl.text.trim().isEmpty || !context.mounted) return;
      reason = ctrl.text.trim();
    }

    try {
      await ref.read(jobsRepositoryProvider).transition(
            job.id,
            action.apiAction,
            resolution: resolution,
            reason: reason,
          );
      onRefresh();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('${action.label} — done')));
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
            width: 100,
            child: Text(label,
                style:
                    const TextStyle(color: AppTheme.textSecondary, fontSize: 13)),
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

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});
  final String status;

  static const _labels = {
    'CREATED': 'New',
    'ASSIGNED': 'Assigned',
    'SCHEDULED': 'Scheduled',
    'IN_PROGRESS': 'In Progress',
    'AWAITING_PARTS': 'Waiting Parts',
    'COMPLETED': 'Completed',
    'CANCELLED': 'Cancelled',
  };

  static const _colors = {
    'CREATED': AppTheme.textSecondary,
    'ASSIGNED': AppTheme.secondary,
    'SCHEDULED': AppTheme.primary,
    'IN_PROGRESS': AppTheme.success,
    'AWAITING_PARTS': AppTheme.warning,
    'COMPLETED': AppTheme.success,
    'CANCELLED': AppTheme.error,
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
              fontSize: 12, color: color, fontWeight: FontWeight.w600)),
    );
  }
}
