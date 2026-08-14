import 'package:flutter/material.dart';

import '../../../shared/theme/app_theme.dart';

/// Friendly label + color for a Job's status, shown alongside an
/// Accepted/Approved request so staff can see whether work has actually
/// started — "Approved" only means the request was reviewed and a job was
/// created; it does not mean the repair is complete or even picked up yet.
class JobStatusInfo {
  const JobStatusInfo(this.label, this.color, this.icon);
  final String label;
  final Color color;
  final IconData icon;

  static const Map<String, JobStatusInfo> _byStatus = {
    'CREATED': JobStatusInfo(
        'Unassigned — yet to be picked', AppTheme.warning, Icons.inbox_outlined),
    'ASSIGNED': JobStatusInfo(
        'Assigned — not started', AppTheme.secondary, Icons.assignment_ind_outlined),
    'SCHEDULED': JobStatusInfo(
        'Scheduled', AppTheme.secondary, Icons.event_available_outlined),
    'IN_PROGRESS': JobStatusInfo(
        'In progress', AppTheme.primary, Icons.build_outlined),
    'AWAITING_PARTS': JobStatusInfo(
        'Awaiting parts', AppTheme.warning, Icons.hourglass_empty),
    'COMPLETED': JobStatusInfo(
        'Completed', AppTheme.success, Icons.check_circle_outline),
    'CANCELLED': JobStatusInfo(
        'Cancelled', AppTheme.textSecondary, Icons.block_outlined),
  };

  static JobStatusInfo? forStatus(String? jobStatus) =>
      jobStatus == null ? null : _byStatus[jobStatus];

  /// e.g. "Assigned to Priya" / "Unassigned — yet to be picked"
  String describe(String? technicianName) {
    if (label.startsWith('Assigned') &&
        technicianName != null &&
        technicianName.isNotEmpty) {
      return 'Assigned to $technicianName';
    }
    return label;
  }
}

/// Small inline row showing the linked job's progress — used on Accepted
/// requests so "Approved" doesn't read as if the repair is finished.
class JobProgressRow extends StatelessWidget {
  const JobProgressRow(
      {super.key, required this.jobStatus, this.assignedTechnicianName});

  final String? jobStatus;
  final String? assignedTechnicianName;

  @override
  Widget build(BuildContext context) {
    final info = JobStatusInfo.forStatus(jobStatus);
    if (info == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(info.icon, size: 14, color: info.color),
          const SizedBox(width: 4),
          Text(
            info.describe(assignedTechnicianName),
            style: TextStyle(
                fontSize: 11.5, color: info.color, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}
