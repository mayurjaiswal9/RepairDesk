import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/time/india_date.dart';
import '../../../shared/theme/app_theme.dart';
import '../../../shared/widgets/date_range_bar.dart';
import '../../attachments/presentation/attachments_widget.dart';
import '../../jobs/presentation/jobs_list_screen.dart';
import '../../jobs/presentation/open_jobs_banner.dart';
import '../data/requests_repository.dart';
import 'job_status.dart';
import 'request_detail_screen.dart';

typedef _QueueQuery = ({String? status, String? from, String? to});

final _queueFamily = FutureProvider.autoDispose
    .family<List<RequestSummary>, _QueueQuery>((ref, query) =>
        ref.watch(requestsRepositoryProvider).queue(
              status: query.status,
              from: query.from,
              to: query.to,
            ));

class RequestQueueScreen extends ConsumerStatefulWidget {
  const RequestQueueScreen({super.key});

  @override
  ConsumerState<RequestQueueScreen> createState() =>
      _RequestQueueScreenState();
}

class _RequestQueueScreenState extends ConsumerState<RequestQueueScreen> {
  String? _status;
  late String _from = indiaToday();
  late String _to = indiaToday();
  bool _allDates = false;

  _QueueQuery get _query => (
        status: _status,
        from: _allDates ? null : _from,
        to: _allDates ? null : _to,
      );

  void _refresh() => ref.invalidate(_queueFamily(_query));

  @override
  Widget build(BuildContext context) {
    final today = indiaToday();
    final showingToday = !_allDates && _from == today && _to == today;
    final queueAsync = ref.watch(_queueFamily(_query));

    return Scaffold(
      appBar: AppBar(title: const Text('Repair Requests')),
      body: Column(
        children: [
          OpenJobsBanner(
            onShowDay: (date) => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => JobsListScreen(focusDate: date),
              ),
            ),
          ),
          DateRangeBar(
            from: _allDates ? null : _from,
            to: _allDates ? null : _to,
            allDates: _allDates,
            onFrom: (value) => setState(() {
              _allDates = false;
              _from = value;
              if (_to.compareTo(value) < 0) _to = value;
            }),
            onTo: (value) => setState(() {
              _allDates = false;
              _to = value;
              if (_from.compareTo(value) > 0) _from = value;
            }),
            onToday: () => setState(() {
              _allDates = false;
              _from = today;
              _to = today;
            }),
            onAll: () => setState(() => _allDates = true),
          ),
          _StatusFilterBar(
            selected: _status,
            onChanged: (s) => setState(() => _status = s),
          ),
          Expanded(
            child: queueAsync.when(
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
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
                    const SizedBox(height: AppTheme.spaceM),
                    FilledButton(
                        onPressed: _refresh, child: const Text('Retry')),
                  ],
                ),
              ),
              data: (items) => items.isEmpty
                  ? _EmptyQueue(
                      message: showingToday
                          ? 'No requests for today'
                          : 'No requests',
                    )
                  : RefreshIndicator(
                      onRefresh: () =>
                          ref.refresh(_queueFamily(_query).future),
                      child: ListView.separated(
                        padding: const EdgeInsets.all(AppTheme.spaceM),
                        itemCount: items.length,
                        separatorBuilder: (_, sep) =>
                            const SizedBox(height: AppTheme.spaceS),
                        itemBuilder: (ctx, i) => _RequestCard(
                            item: items[i], onRefresh: _refresh),
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Filter bar ─────────────────────────────────────────────────────────────

class _StatusFilterBar extends StatelessWidget {
  const _StatusFilterBar(
      {required this.selected, required this.onChanged});
  final String? selected;
  final ValueChanged<String?> onChanged;

  static const _filters = [
    (null, 'All'),
    ('PENDING_REVIEW', 'Pending'),
    ('INFORMATION_REQUIRED', 'Info Needed'),
    ('APPROVED', 'Accepted'),
    ('REJECTED', 'Rejected'),
    ('CANCELLED', 'Cancelled'),
  ];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding:
            const EdgeInsets.symmetric(horizontal: AppTheme.spaceM),
        children: _filters.map((f) {
          final isSelected = selected == f.$1;
          return Padding(
            padding: const EdgeInsets.only(
                right: AppTheme.spaceS, top: 6, bottom: 6),
            child: ChoiceChip(
              label: Text(f.$2),
              selected: isSelected,
              onSelected: (sel) => onChanged(f.$1),
            ),
          );
        }).toList(),
      ),
    );
  }
}

// ── Request card with inline action buttons ─────────────────────────────

class _RequestCard extends ConsumerWidget {
  const _RequestCard({required this.item, required this.onRefresh});
  final RequestSummary item;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => RequestDetailScreen(
            requestId: item.id,
            onActionCompleted: onRefresh,
          ),
        )),
        child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(item.referenceNumber,
                      style: Theme.of(context)
                          .textTheme
                          .titleMedium
                          ?.copyWith(color: AppTheme.primary)),
                ),
                _StatusChip(status: item.status),
              ],
            ),
            if (item.status == 'APPROVED')
              JobProgressRow(
                jobStatus: item.jobStatus,
                assignedTechnicianName: item.assignedTechnicianName,
              ),
            const SizedBox(height: AppTheme.spaceXS),
            Text('${item.contactName} · @${item.customerUsername}',
                style: Theme.of(context).textTheme.bodyMedium),
            Text(item.deviceCategoryName,
                style:
                    const TextStyle(color: AppTheme.textSecondary)),
            const SizedBox(height: AppTheme.spaceXS),
            Theme(
              data: Theme.of(context)
                  .copyWith(dividerColor: Colors.transparent),
              child: ExpansionTile(
                tilePadding: EdgeInsets.zero,
                childrenPadding:
                    const EdgeInsets.only(bottom: AppTheme.spaceS),
                dense: true,
                leading: const Icon(Icons.photo_library_outlined,
                    size: 20, color: AppTheme.secondary),
                title: const Text('Photos & Attachments',
                    style: TextStyle(fontSize: 13)),
                children: [
                  AttachmentsWidget(
                    ownerType: 'REPAIR_REQUEST',
                    ownerId: item.id,
                    canUpload: false,
                    canDelete: false,
                    showHeader: false,
                  ),
                ],
              ),
            ),
            if (item.status == 'PENDING_REVIEW' ||
                item.status == 'INFORMATION_REQUIRED') ...[
              const SizedBox(height: AppTheme.spaceM),
              Row(
                children: [
                  _ActionButton(
                    label: 'Approve',
                    color: AppTheme.success,
                    icon: Icons.check_circle_outline,
                    onTap: () => _approve(context, ref),
                  ),
                  const SizedBox(width: AppTheme.spaceS),
                  _ActionButton(
                    label: 'Request Info',
                    color: AppTheme.secondary,
                    icon: Icons.info_outline,
                    onTap: () => _requestInfo(context, ref),
                  ),
                  const SizedBox(width: AppTheme.spaceS),
                  _ActionButton(
                    label: 'Reject',
                    color: AppTheme.error,
                    icon: Icons.cancel_outlined,
                    onTap: () => _reject(context, ref),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
      ),
    );
  }

  Future<void> _approve(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Approve Request'),
        content: Text('Approve ${item.referenceNumber} and create a job?'),
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
    if (confirmed != true || !context.mounted) return;
    try {
      await ref.read(requestsRepositoryProvider).approve(item.id);
      onRefresh();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text('Request approved — job created')));
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
    final msgCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Reject Request'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Reject ${item.referenceNumber}?'),
            const SizedBox(height: AppTheme.spaceM),
            TextField(
              controller: msgCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Customer message (optional)',
                border: OutlineInputBorder(),
              ),
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
    if (confirmed != true || !context.mounted) return;
    try {
      await ref.read(requestsRepositoryProvider).reject(
          item.id, 'UNABLE_TO_SERVICE', msgCtrl.text.trim());
      onRefresh();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Request rejected')));
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
    final msgCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Request More Information'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
                'What information do you need from the customer?'),
            const SizedBox(height: AppTheme.spaceM),
            TextField(
              controller: msgCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Message to customer',
                border: OutlineInputBorder(),
              ),
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
    if (confirmed != true || !context.mounted) return;
    if (msgCtrl.text.trim().isEmpty) return;
    try {
      await ref
          .read(requestsRepositoryProvider)
          .requestInfo(item.id, msgCtrl.text.trim());
      onRefresh();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Customer notified')));
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

// ── Status chip ───────────────────────────────────────────────────────────

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
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        _labels[status] ?? status,
        style: TextStyle(
            fontSize: 11,
            color: color,
            fontWeight: FontWeight.w600),
      ),
    );
  }
}

// ── Action button ─────────────────────────────────────────────────────────

class _ActionButton extends StatelessWidget {
  const _ActionButton(
      {required this.label,
      required this.color,
      required this.icon,
      required this.onTap});
  final String label;
  final Color color;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: OutlinedButton.icon(
        icon: Icon(icon, size: 14, color: color),
        label: Text(label,
            style: TextStyle(fontSize: 11, color: color)),
        style: OutlinedButton.styleFrom(
          side: BorderSide(color: color.withValues(alpha: 0.5)),
          padding: const EdgeInsets.symmetric(vertical: 6),
        ),
        onPressed: onTap,
      ),
    );
  }
}

// ── Empty state ───────────────────────────────────────────────────────────

class _EmptyQueue extends StatelessWidget {
  const _EmptyQueue({required this.message});
  final String message;
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.inbox_outlined,
              size: 64, color: AppTheme.textSecondary),
          const SizedBox(height: AppTheme.spaceM),
          Text(message, style: Theme.of(context).textTheme.titleMedium),
          const Text('Try another date, or choose All dates.',
              style: TextStyle(color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}
