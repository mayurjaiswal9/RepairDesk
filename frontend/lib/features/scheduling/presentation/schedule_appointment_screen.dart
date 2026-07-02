import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../shared/theme/app_theme.dart';
import '../../account/data/profile_repository.dart';
import '../data/scheduling_repository.dart';

class ScheduleAppointmentScreen extends ConsumerStatefulWidget {
  const ScheduleAppointmentScreen({
    super.key,
    required this.jobId,
    required this.referenceNumber,
    this.defaultTechnicianId,
    this.existingAppointmentId,
  });

  final String jobId;
  final String referenceNumber;
  final String? defaultTechnicianId;
  final String? existingAppointmentId;

  bool get isReschedule => existingAppointmentId != null;

  @override
  ConsumerState<ScheduleAppointmentScreen> createState() =>
      _ScheduleAppointmentScreenState();
}

class _ScheduleAppointmentScreenState
    extends ConsumerState<ScheduleAppointmentScreen> {
  final _formKey = GlobalKey<FormState>();
  final _notesCtrl = TextEditingController();

  DateTime? _selectedDate;
  TimeOfDay? _selectedTime;
  String? _selectedTechId;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _selectedTechId = widget.defaultTechnicianId;
    _selectedDate = DateTime.now().add(const Duration(days: 1));
  }

  @override
  void dispose() {
    _notesCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final techsAsync = ref.watch(
        FutureProvider.autoDispose((ref) =>
            ref.watch(profileRepositoryProvider)
                .listAllUsers()
                .then((users) => users
                    .where((u) => u.role == 'TECHNICIAN' && u.status == 'ACTIVE')
                    .toList())));

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.isReschedule
            ? 'Reschedule Appointment'
            : 'Schedule Appointment'),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(AppTheme.spaceM),
          children: [
            // Technician picker (only for new, not reschedule)
            if (!widget.isReschedule) ...[
              Text('Technician',
                  style: Theme.of(context)
                      .textTheme
                      .titleSmall
                      ?.copyWith(color: AppTheme.primary)),
              const Divider(),
              techsAsync.when(
                loading: () => const LinearProgressIndicator(),
                error: (e, _) => Text('Error: $e',
                    style: const TextStyle(color: AppTheme.error)),
                data: (techs) => DropdownButtonFormField<String>(
                  // ignore: deprecated_member_use
                  value: _selectedTechId,
                  decoration: const InputDecoration(
                      labelText: 'Assign technician *',
                      border: OutlineInputBorder()),
                  items: techs.map((t) {
                    return DropdownMenuItem(
                        value: t.id,
                        child: Text(t.name ?? t.username));
                  }).toList(),
                  onChanged: (v) => setState(() => _selectedTechId = v),
                  validator: (v) =>
                      v == null ? 'Select a technician' : null,
                ),
              ),
              const SizedBox(height: AppTheme.spaceM),
            ],

            // Date picker
            Text('Date & Time',
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.calendar_today_outlined,
                  color: AppTheme.primary),
              title: Text(_selectedDate != null
                  ? DateFormat('EEEE, MMMM d, yyyy').format(_selectedDate!)
                  : 'Select date'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _selectedDate ??
                      DateTime.now().add(const Duration(days: 1)),
                  firstDate: DateTime.now(),
                  lastDate: DateTime.now().add(const Duration(days: 90)),
                );
                if (picked != null) setState(() => _selectedDate = picked);
              },
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.access_time_outlined,
                  color: AppTheme.primary),
              title: Text(_selectedTime != null
                  ? _selectedTime!.format(context)
                  : 'Select start time'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () async {
                final picked = await showTimePicker(
                  context: context,
                  initialTime: _selectedTime ?? const TimeOfDay(hour: 9, minute: 0),
                );
                if (picked != null) setState(() => _selectedTime = picked);
              },
            ),
            const SizedBox(height: AppTheme.spaceM),

            // Notes
            Text('Notes (optional)',
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            TextFormField(
              controller: _notesCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                  hintText: 'Any notes for the technician...',
                  border: OutlineInputBorder()),
            ),
            const SizedBox(height: AppTheme.spaceL),

            FilledButton(
              onPressed: _submitting ? null : _submit,
              child: _submitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text(widget.isReschedule ? 'Reschedule' : 'Schedule'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select a date')));
      return;
    }
    if (_selectedTime == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select a start time')));
      return;
    }
    if (!widget.isReschedule && _selectedTechId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select a technician')));
      return;
    }

    setState(() => _submitting = true);
    try {
      final dateStr = DateFormat('yyyy-MM-dd').format(_selectedDate!);
      final timeStr =
          '${_selectedTime!.hour.toString().padLeft(2, '0')}:${_selectedTime!.minute.toString().padLeft(2, '0')}:00';

      if (widget.isReschedule) {
        await ref.read(schedulingRepositoryProvider).reschedule(
              jobId: widget.jobId,
              appointmentId: widget.existingAppointmentId!,
              scheduledDate: dateStr,
              startTime: timeStr,
              notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
            );
      } else {
        await ref.read(schedulingRepositoryProvider).create(
              jobId: widget.jobId,
              technicianId: _selectedTechId!,
              scheduledDate: dateStr,
              startTime: timeStr,
              notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
            );
      }

      if (mounted) {
        Navigator.of(context).pop(true);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(widget.isReschedule
                ? 'Appointment rescheduled'
                : 'Appointment scheduled')));
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
}
