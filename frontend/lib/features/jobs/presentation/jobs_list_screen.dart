import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/time/india_date.dart';
import '../../../shared/theme/app_theme.dart';
import '../../../shared/widgets/date_range_bar.dart';
import '../data/jobs_repository.dart';
import 'job_detail_screen.dart';
import 'open_jobs_banner.dart';

/// Status and India date range for the jobs family. Technicians are always
/// scoped to their own assignments via [technicianId].
typedef _JobsQuery = ({
  String? status,
  String? technicianId,
  String? from,
  String? to,
});

final _jobsFamily = FutureProvider.autoDispose
    .family<List<JobSummary>, _JobsQuery>((ref, query) =>
        ref.watch(jobsRepositoryProvider).list(
              status: query.status,
              technicianId: query.technicianId,
              from: query.from,
              to: query.to,
            ));

class JobsListScreen extends ConsumerStatefulWidget {
  const JobsListScreen({super.key, this.focusDate});

  /// Opens the list on one India date and the still-open statuses.
  final String? focusDate;

  @override
  ConsumerState<JobsListScreen> createState() => _JobsListScreenState();
}

class _JobsListScreenState extends ConsumerState<JobsListScreen> {
  String? _status;
  String? _from;
  String? _to;
  bool _allDates = false;

  @override
  void initState() {
    super.initState();
    final today = indiaToday();
    _from = widget.focusDate ?? today;
    _to = widget.focusDate ?? today;
    if (widget.focusDate != null) _status = openJobStatuses;
  }

  void _showDay(String date) {
    setState(() {
      _allDates = false;
      _from = date;
      _to = date;
      _status = openJobStatuses;
    });
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    final isTech = Roles.isTechnician(session.role);
    final myId = session.userId;
    final today = indiaToday();
    final showingToday = !_allDates && _from == today && _to == today;
    final query = (
      status: _status,
      // Always send technicianId for TECHNICIAN so older APIs that honour
      // the query param still scope the list.
      technicianId: isTech ? myId : null,
      from: _allDates ? null : _from,
      to: _allDates ? null : _to,
    );
    final jobsAsync = ref.watch(_jobsFamily(query));

    return Scaffold(
      appBar: AppBar(
        title: Text(isTech ? 'My Jobs' : 'Jobs'),
      ),
      body: Column(
        children: [
          OpenJobsBanner(onShowDay: _showDay),
          DateRangeBar(
            from: _allDates ? null : _from,
            to: _allDates ? null : _to,
            allDates: _allDates,
            onFrom: (value) => setState(() {
              _allDates = false;
              _from = value;
              if (_to == null || _to!.compareTo(value) < 0) _to = value;
            }),
            onTo: (value) => setState(() {
              _allDates = false;
              _to = value;
              if (_from == null || _from!.compareTo(value) > 0) _from = value;
            }),
            onToday: () => setState(() {
              _allDates = false;
              _from = today;
              _to = today;
              if (_status == openJobStatuses) _status = null;
            }),
            onAll: () => setState(() {
              _allDates = true;
              _from = null;
              _to = null;
            }),
          ),
          _StatusFilterBar(
              selected: _status == openJobStatuses ? null : _status,
              onChanged: (s) => setState(() => _status = s)),
          Expanded(
            child: jobsAsync.when(
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
                    FilledButton(
                        onPressed: () => ref.invalidate(_jobsFamily(query)),
                        child: const Text('Retry')),
                  ],
                ),
              ),
              data: (jobs) {
                // Strict: technicians only ever see jobs assigned to them.
                // Do NOT keep rows with a null assignee — that previously
                // leaked the full board when the API omitted the field.
                final visible = isTech && myId != null
                    ? jobs
                        .where((j) => j.assignedTechnicianId == myId)
                        .toList()
                    : jobs;
                return visible.isEmpty
                    ? _EmptyJobs(
                        message: showingToday ? 'No jobs for today' : 'No jobs',
                      )
                    : RefreshIndicator(
                        onRefresh: () =>
                            ref.refresh(_jobsFamily(query).future),
                        child: ListView.separated(
                          padding: const EdgeInsets.all(AppTheme.spaceM),
                          itemCount: visible.length,
                          separatorBuilder: (_, sep) =>
                              const SizedBox(height: AppTheme.spaceS),
                          itemBuilder: (ctx, i) => _JobCard(
                            job: visible[i],
                            onTap: () async {
                              await Navigator.of(ctx).push(
                                MaterialPageRoute(
                                    builder: (_) => JobDetailScreen(
                                        jobId: visible[i].id)),
                              );
                              ref.invalidate(_jobsFamily(query));
                            },
                          ),
                        ),
                      );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusFilterBar extends StatelessWidget {
  const _StatusFilterBar(
      {required this.selected, required this.onChanged});
  final String? selected;
  final ValueChanged<String?> onChanged;

  static const _filters = [
    (null, 'All'),
    ('CREATED', 'New'),
    ('ASSIGNED', 'Assigned'),
    ('IN_PROGRESS', 'In Progress'),
    ('AWAITING_PARTS', 'Parts'),
    ('COMPLETED', 'Done'),
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

class _JobCard extends StatelessWidget {
  const _JobCard({required this.job, required this.onTap});
  final JobSummary job;
  final VoidCallback onTap;

  static const _statusColors = {
    'CREATED': AppTheme.textSecondary,
    'ASSIGNED': AppTheme.secondary,
    'SCHEDULED': AppTheme.primary,
    'IN_PROGRESS': AppTheme.success,
    'AWAITING_PARTS': AppTheme.warning,
    'COMPLETED': AppTheme.success,
    'CANCELLED': AppTheme.error,
  };

  static const _statusLabels = {
    'CREATED': 'New',
    'ASSIGNED': 'Assigned',
    'SCHEDULED': 'Scheduled',
    'IN_PROGRESS': 'In Progress',
    'AWAITING_PARTS': 'Waiting Parts',
    'COMPLETED': 'Completed',
    'CANCELLED': 'Cancelled',
  };

  @override
  Widget build(BuildContext context) {
    final color =
        _statusColors[job.status] ?? AppTheme.textSecondary;
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppTheme.spaceM),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(job.referenceNumber,
                        style: Theme.of(context)
                            .textTheme
                            .titleMedium
                            ?.copyWith(color: AppTheme.primary)),
                  ),
                  _PriorityBadge(priority: job.priority),
                  const SizedBox(width: AppTheme.spaceS),
                  _StatusBadge(
                      label: _statusLabels[job.status] ?? job.status,
                      color: color),
                ],
              ),
              const SizedBox(height: AppTheme.spaceXS),
              // Account customer name — not the per-request contact person.
              Text(job.customerDisplayName,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      )),
              Text(job.deviceCategoryName,
                  style: const TextStyle(color: AppTheme.textSecondary)),
              if (job.assignedTechnicianName != null) ...[
                const SizedBox(height: AppTheme.spaceXS),
                Row(
                  children: [
                    const Icon(Icons.engineering,
                        size: 14, color: AppTheme.textSecondary),
                    const SizedBox(width: 4),
                    Text(job.assignedTechnicianName!,
                        style: const TextStyle(
                            fontSize: 12,
                            color: AppTheme.textSecondary)),
                  ],
                ),
              ],
              const SizedBox(height: AppTheme.spaceXS),
              const Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Icon(Icons.chevron_right, color: AppTheme.textSecondary),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding:
          const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(label,
          style: TextStyle(
              fontSize: 11,
              color: color,
              fontWeight: FontWeight.w600)),
    );
  }
}

class _PriorityBadge extends StatelessWidget {
  const _PriorityBadge({required this.priority});
  final String priority;

  static const _icons = {
    'LOW': Icons.arrow_downward,
    'NORMAL': Icons.remove,
    'HIGH': Icons.arrow_upward,
    'URGENT': Icons.priority_high,
  };

  static const _colors = {
    'LOW': AppTheme.textSecondary,
    'NORMAL': AppTheme.textSecondary,
    'HIGH': AppTheme.warning,
    'URGENT': AppTheme.error,
  };

  @override
  Widget build(BuildContext context) {
    final icon = _icons[priority] ?? Icons.remove;
    final color = _colors[priority] ?? AppTheme.textSecondary;
    if (priority == 'NORMAL' || priority == 'LOW') {
      return const SizedBox.shrink();
    }
    return Icon(icon, size: 16, color: color);
  }
}

class _EmptyJobs extends StatelessWidget {
  const _EmptyJobs({required this.message});
  final String message;
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.build_circle_outlined,
              size: 64, color: AppTheme.textSecondary),
          const SizedBox(height: AppTheme.spaceM),
          Text(message,
              style: const TextStyle(
                  fontSize: 18, color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}
