import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/error/app_exception.dart';
import '../../../core/location/location_service.dart';
import '../../../core/location/maps_repository.dart';
import '../../../core/location/pin_picker_screen.dart';
import '../../../core/location/service_location.dart';
import '../../../core/location/service_location_card.dart';
import '../../../core/location/trip_repository.dart';
import '../../../shared/theme/app_theme.dart';
import '../../attachments/presentation/attachments_widget.dart';
import '../../scheduling/presentation/schedule_appointment_screen.dart';
import '../../trips/data/on_site_tracker.dart';
import '../../trips/data/trip_recorder.dart';
import '../../trips/presentation/job_travel_card.dart';
import '../../trips/presentation/no_trip_reason_sheet.dart';
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
    // Scheduling and cancelling are Staff/Admin-only on the server.
    final isTechnician =
        Roles.isTechnician(ref.watch(sessionControllerProvider).role);
    final actions = _availableActions(job.status, isTechnician: isTechnician);
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
          if (job.createdAt != null) ...[
            const SizedBox(height: 2),
            Text(
              'Created ${_formatCreatedAt(job.createdAt!)}',
              style: const TextStyle(
                  fontSize: 12, color: AppTheme.textSecondary),
            ),
          ],
          const SizedBox(height: AppTheme.spaceS),
          if (isTechnician) ...[
            _PresencePing(jobId: job.id),
            JobTravelCard(job: job),
          ],

          // Actions sit above the map so Start Repair is on the first screen
          // and the confirm dialog is not covered by the Google Map view.
          if (actions.isNotEmpty) ...[
            Text('Actions',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            ...actions.map((a) => _ActionTile(
                key: ValueKey('job-action-${a.name}-${job.id}'),
                action: a,
                job: job,
                onRefresh: onRefresh)),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Device & Contact ──────────────────────────────────────────
          _Section(
            title: 'Device & Contact',
            children: [
              _InfoRow(label: 'Customer', value: job.customerDisplayName),
              _InfoRow(label: 'Username', value: '@${job.customerUsername}'),
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

          if (job.serviceLocation != null || !isTechnician) ...[
            _Section(
              title: 'Service address',
              children: [
                if (job.serviceLocation != null)
                  ServiceLocationCard(location: job.serviceLocation)
                else
                  const Text(
                    'No address yet',
                    style: TextStyle(color: AppTheme.textSecondary),
                  ),
                if (!isTechnician) ...[
                  const SizedBox(height: AppTheme.spaceS),
                  _EditJobPinButton(job: job, onSaved: onRefresh),
                ],
              ],
            ),
            const SizedBox(height: AppTheme.spaceM),
          ],

          // ── Issue ─────────────────────────────────────────────────────
          _Section(
            title: 'Issue',
            children: [
              Text(job.issueDescription,
                  style: const TextStyle(color: AppTheme.textPrimary)),
            ],
          ),
          const SizedBox(height: AppTheme.spaceM),

          // ── Technician & Appointment ─────────────────────────────────
          _Section(
            title: 'Technician & Appointment',
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
              if (!isTechnician &&
                  (job.status == 'ASSIGNED' || job.status == 'SCHEDULED')) ...[
                const SizedBox(height: AppTheme.spaceS),
                OutlinedButton.icon(
                  icon: const Icon(Icons.calendar_today_outlined, size: 18),
                  label: Text(job.currentAppointmentId != null
                      ? 'Reschedule Appointment'
                      : 'Schedule Appointment'),
                  onPressed: () async {
                    final result = await Navigator.of(context).push<bool>(
                      MaterialPageRoute(
                          builder: (_) => ScheduleAppointmentScreen(
                                jobId: job.id,
                                referenceNumber: job.referenceNumber,
                                defaultTechnicianId: job.assignedTechnicianId,
                                existingAppointmentId:
                                    job.currentAppointmentId,
                              )),
                    );
                    if (result == true) onRefresh();
                  },
                ),
              ],
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

          // ── Internal Notes ────────────────────────────────────────────
          _NotesSection(job: job, onRefresh: onRefresh),
          const SizedBox(height: AppTheme.spaceM),

          // ── Customer Photos (from original request) ───────────────────
          Theme(
            data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
            child: ExpansionTile(
              tilePadding: EdgeInsets.zero,
              childrenPadding:
                  const EdgeInsets.only(bottom: AppTheme.spaceS),
              leading: const Icon(Icons.photo_library_outlined,
                  size: 20, color: AppTheme.secondary),
              title: const Text('Customer Photos',
                  style: TextStyle(
                      fontWeight: FontWeight.w600, fontSize: 14)),
              children: [
                AttachmentsWidget(
                  ownerType: 'REPAIR_REQUEST',
                  ownerId: job.requestId,
                  canUpload: false,
                  canDelete: false,
                  showHeader: false,
                ),
              ],
            ),
          ),
          const SizedBox(height: AppTheme.spaceM),

          // ── Job Attachments (internal) ─────────────────────────────────
          AttachmentsWidget(
            ownerType: 'JOB',
            ownerId: job.id,
            canUpload: true,
            canDelete: true,
          ),
          const SizedBox(height: AppTheme.spaceL),
        ],
      ),
    );
  }

  static String _formatCreatedAt(String iso) {
    try {
      return DateFormat('MMM d, yyyy • h:mm a')
          .format(DateTime.parse(iso).toLocal());
    } catch (_) {
      return iso;
    }
  }

  static List<_JobAction> _availableActions(String status,
      {required bool isTechnician}) {
    final actions = switch (status) {
      'ASSIGNED' || 'SCHEDULED' => [_JobAction.start],
      'IN_PROGRESS' => [_JobAction.awaitingParts, _JobAction.complete, _JobAction.cancel],
      'AWAITING_PARTS' => [_JobAction.resume, _JobAction.complete, _JobAction.cancel],
      'CREATED' || 'ASSIGNED' => [_JobAction.cancel],
      _ => <_JobAction>[],
    };
    return isTechnician
        ? actions.where((a) => a != _JobAction.cancel).toList()
        : actions;
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
    final role = ref.watch(sessionControllerProvider).role;
    // Technicians work assigned jobs; they cannot reassign the roster.
    if (Roles.isTechnician(role) || Roles.normalize(role) == Roles.customer) {
      return const SizedBox.shrink();
    }
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

class _ActionTile extends ConsumerStatefulWidget {
  const _ActionTile(
      {super.key,
      required this.action,
      required this.job,
      required this.onRefresh});
  final _JobAction action;
  final JobDetail job;
  final VoidCallback onRefresh;

  @override
  ConsumerState<_ActionTile> createState() => _ActionTileState();
}

class _ActionTileState extends ConsumerState<_ActionTile> {
  bool _busy = false;

  _JobAction get action => widget.action;
  JobDetail get job => widget.job;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: OutlinedButton.icon(
        icon: _busy
            ? SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: action.color),
              )
            : Icon(action.icon, color: action.color),
        label: Text(action.label, style: TextStyle(color: action.color)),
        style: OutlinedButton.styleFrom(
          side: BorderSide(color: action.color.withValues(alpha: 0.5)),
          alignment: Alignment.centerLeft,
          padding: const EdgeInsets.symmetric(
              horizontal: AppTheme.spaceM, vertical: 14),
        ),
        onPressed: _busy ? null : _run,
      ),
    );
  }

  Future<void> _run() async {
    if (_busy) return;
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);

    String? resolution;
    String? reason;
    String? noTripReason;
    String? noTripNote;

    try {
      if (action == _JobAction.start) {
        final started = await _confirmStart();
        if (started == null) return;
        noTripReason = started.$1;
        noTripNote = started.$2;
      }

      if (action == _JobAction.complete) {
        final ctrl = TextEditingController();
        final ok = await showDialog<bool>(
          context: context,
          useRootNavigator: true,
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
        if (ok != true || ctrl.text.trim().isEmpty) return;
        resolution = ctrl.text.trim();
      }

      if (action == _JobAction.cancel) {
        final ctrl = TextEditingController();
        final ok = await showDialog<bool>(
          context: context,
          useRootNavigator: true,
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
        if (ok != true || ctrl.text.trim().isEmpty) return;
        reason = ctrl.text.trim();
      }

      if (action == _JobAction.start) {
        await ref.read(tripRecorderProvider.notifier).prepareJobStart(job.id);
      }
      await ref.read(jobsRepositoryProvider).transition(
            job.id,
            action.apiAction,
            resolution: resolution,
            reason: reason,
            noTripReason: noTripReason,
            noTripNote: noTripNote,
          );
      if (action == _JobAction.start) {
        await ref.read(tripRecorderProvider.notifier).jobStarted(job.id);
      }
      await ref.read(onSiteTrackerProvider.notifier).refresh();
      widget.onRefresh();
      messenger.showSnackBar(SnackBar(content: Text('${action.label} — done')));
    } on AppException catch (e) {
      if (action == _JobAction.start && e.tripRequired) {
        final choice = await showNoTripReasonSheet(context);
        if (choice == null) return;
        try {
          await ref.read(jobsRepositoryProvider).transition(
                job.id,
                'start',
                noTripReason: choice.reason.code,
                noTripNote: choice.note,
              );
          await ref.read(onSiteTrackerProvider.notifier).refresh();
          widget.onRefresh();
          messenger.showSnackBar(
              const SnackBar(content: Text('Start Repair — done')));
          return;
        } on AppException catch (retry) {
          messenger.showSnackBar(SnackBar(
              content: Text(retry.userMessage),
              backgroundColor: AppTheme.error));
          return;
        }
      }
      messenger.showSnackBar(SnackBar(
          content: Text(e.userMessage), backgroundColor: AppTheme.error));
    } catch (e) {
      messenger.showSnackBar(SnackBar(
          content: Text('Error: $e'), backgroundColor: AppTheme.error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Confirm Start Repair. Returns `(reason, note)` when strict mode needs one,
  /// or `(null, null)` when a trip already covers the job. Null means cancelled.
  Future<(String?, String?)?> _confirmStart() async {
    final ok = await showDialog<bool>(
      context: context,
      useRootNavigator: true,
      builder: (dlg) => AlertDialog(
        title: const Text('Start Repair?'),
        content: Text(
            'Job ${job.referenceNumber} will move to In Progress and the '
            'customer may be notified.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Not now')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Start Repair')),
        ],
      ),
    );
    if (ok != true) return null;

    try {
      final cached = ref.read(locationConfigProvider).value;
      final config = cached ??
          await ref
              .read(locationConfigProvider.future)
              .timeout(const Duration(seconds: 5));
      final recorder = ref.read(tripRecorderProvider);
      if (!config.tripRequiredToStart || recorder.isFor(job.id)) {
        return (null, null);
      }
      try {
        final trips = await ref.read(tripRepositoryProvider).forJob(job.id);
        final userId = ref.read(sessionControllerProvider).userId;
        if (trips.hasTripBy(userId)) return (null, null);
      } catch (_) {
        // Offline: the server still asks if a reason is required.
      }
      if (!mounted) return null;
      final choice = await showNoTripReasonSheet(context);
      if (choice == null) return null;
      return (choice.reason.code, choice.note);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Could not start repair: $e'),
            backgroundColor: AppTheme.error));
      }
      return null;
    }
  }
}

// ── Notes section ─────────────────────────────────────────────────────────────

class _NotesSection extends ConsumerStatefulWidget {
  const _NotesSection({required this.job, required this.onRefresh});
  final JobDetail job;
  final VoidCallback onRefresh;

  @override
  ConsumerState<_NotesSection> createState() => _NotesSectionState();
}

class _NotesSectionState extends ConsumerState<_NotesSection> {
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('Internal Notes',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Spacer(),
            _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : IconButton(
                    icon: const Icon(Icons.add_comment_outlined, size: 20),
                    tooltip: 'Add note',
                    onPressed: () => _showAddNoteDialog(context),
                  ),
          ],
        ),
        const Divider(),
        if (widget.job.internalNotes != null &&
            widget.job.internalNotes!.isNotEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(AppTheme.spaceS),
            decoration: BoxDecoration(
              color: Colors.grey.shade50,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.grey.shade200),
            ),
            child: Text(
              widget.job.internalNotes!,
              style: const TextStyle(
                  fontSize: 13, color: AppTheme.textPrimary, height: 1.5),
            ),
          )
        else
          const Padding(
            padding: EdgeInsets.symmetric(vertical: AppTheme.spaceS),
            child: Text('No internal notes',
                style: TextStyle(color: AppTheme.textSecondary)),
          ),
      ],
    );
  }

  Future<void> _showAddNoteDialog(BuildContext context) async {
    final ctrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Add Note'),
        content: TextField(
          controller: ctrl,
          maxLines: 4,
          decoration: const InputDecoration(
            hintText: 'Enter note...',
            border: OutlineInputBorder(),
          ),
          autofocus: true,
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || ctrl.text.trim().isEmpty) return;

    setState(() => _saving = true);
    try {
      await ref.read(jobsRepositoryProvider).appendNote(
            widget.job.id,
            ctrl.text.trim(),
          );
      widget.onRefresh();
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text('Error: $e'),
                backgroundColor: AppTheme.error));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

class _EditJobPinButton extends ConsumerStatefulWidget {
  const _EditJobPinButton({required this.job, required this.onSaved});
  final JobDetail job;
  final VoidCallback onSaved;

  @override
  ConsumerState<_EditJobPinButton> createState() => _EditJobPinButtonState();
}

class _EditJobPinButtonState extends ConsumerState<_EditJobPinButton> {
  bool _saving = false;

  Future<void> _edit() async {
    final current = widget.job.serviceLocation;
    final picked = await Navigator.of(context).push<PinPick>(
      MaterialPageRoute(
        builder: (_) => PinPickerScreen(
          latitude: current?.latitude ?? 19.0760,
          longitude: current?.longitude ?? 72.8777,
          initialIsGeocode: current?.pinSource == 'GEOCODED',
        ),
      ),
    );
    if (picked == null || !mounted) return;
    setState(() => _saving = true);
    final address = picked.addressLine?.trim() ?? '';
    final location = ServiceLocation(
      addressLine: address.isNotEmpty ? address : current?.addressLine,
      landmark: current?.landmark,
      city: current?.city,
      pincode: current?.pincode,
      latitude: picked.latitude,
      longitude: picked.longitude,
      pinSource: picked.pinSource,
    );
    try {
      await ref.read(jobsRepositoryProvider).updateServiceLocation(widget.job.id, location);
      if (!mounted) return;
      widget.onSaved();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Job location saved')),
      );
    } on AppException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.userMessage)));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: OutlinedButton.icon(
        onPressed: _saving ? null : _edit,
        icon: _saving
            ? const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.edit_outlined, size: 18),
        label: Text(_saving ? 'Saving…' : 'Edit'),
      ),
    );
  }
}

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

/// Shares the technician's current position once when the job is opened.
class _PresencePing extends ConsumerStatefulWidget {
  const _PresencePing({required this.jobId});
  final String jobId;

  @override
  ConsumerState<_PresencePing> createState() => _PresencePingState();
}

class _PresencePingState extends ConsumerState<_PresencePing> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _send());
  }

  Future<void> _send() async {
    final fix = await LocationService().bestFix(timeout: const Duration(seconds: 8));
    if (!mounted || fix == null) return;
    try {
      await ref.read(mapsRepositoryProvider).sharePresence(
            latitude: fix.latitude,
            longitude: fix.longitude,
            accuracyM: fix.accuracyM,
            jobId: widget.jobId,
          );
    } catch (_) {
      // The job stays usable when location is off.
    }
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
