import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../shared/theme/app_theme.dart';
import '../../catalog/data/catalog_repository.dart';
import '../data/requests_repository.dart';

class NewRequestScreen extends ConsumerStatefulWidget {
  const NewRequestScreen({super.key});

  @override
  ConsumerState<NewRequestScreen> createState() => _NewRequestScreenState();
}

class _NewRequestScreenState extends ConsumerState<NewRequestScreen> {
  final _formKey = GlobalKey<FormState>();

  final _contactNameCtrl = TextEditingController();
  final _contactMobileCtrl = TextEditingController();
  final _deviceBrandCtrl = TextEditingController();
  final _deviceModelCtrl = TextEditingController();
  final _issueCtrl = TextEditingController();

  String? _selectedCategoryId;
  DateTime? _preferredDate;
  TimeOfDay? _preferredTime;
  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _contactNameCtrl.dispose();
    _contactMobileCtrl.dispose();
    _deviceBrandCtrl.dispose();
    _deviceModelCtrl.dispose();
    _issueCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit(List<DeviceCategory> categories) async {
    if (!_formKey.currentState!.validate()) return;
    if (_preferredDate == null || _preferredTime == null) {
      setState(() => _errorMessage = 'Please select a preferred date and time');
      return;
    }

    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    try {
      await ref.read(requestsRepositoryProvider).createRequest(
            categoryId: _selectedCategoryId!,
            contactName: _contactNameCtrl.text.trim(),
            contactMobile: _contactMobileCtrl.text.trim(),
            deviceBrand: _deviceBrandCtrl.text.trim(),
            deviceModel: _deviceModelCtrl.text.trim(),
            issueDescription: _issueCtrl.text.trim(),
            preferredDate: _preferredDate!,
            preferredTime: _preferredTime!,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Request submitted! We\'ll review it shortly.')),
      );
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now.add(const Duration(days: 1)),
      firstDate: now,
      lastDate: now.add(const Duration(days: 60)),
    );
    if (picked != null) setState(() => _preferredDate = picked);
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 9, minute: 0),
    );
    if (picked != null) setState(() => _preferredTime = picked);
  }

  @override
  Widget build(BuildContext context) {
    final categoriesAsync = ref.watch(activeCategoriesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('New Repair Request')),
      body: categoriesAsync.when(
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
              const SizedBox(height: AppTheme.spaceM),
              FilledButton(
                  onPressed: () => ref.invalidate(activeCategoriesProvider),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (categories) => Form(
          key: _formKey,
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppTheme.spaceM),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // ── Device section ───────────────────────────────────────
                _SectionHeader(title: 'Device'),
                const SizedBox(height: AppTheme.spaceS),
                DropdownButtonFormField<String>(
                  decoration: const InputDecoration(
                    labelText: 'Device type *',
                    border: OutlineInputBorder(),
                  ),
                  value: _selectedCategoryId, // ignore: deprecated_member_use
                  items: categories
                      .map((c) => DropdownMenuItem(
                          value: c.id, child: Text(c.name)))
                      .toList(),
                  onChanged: (v) =>
                      setState(() => _selectedCategoryId = v),
                  validator: (v) =>
                      v == null ? 'Please select a device type' : null,
                ),
                const SizedBox(height: AppTheme.spaceM),
                TextFormField(
                  controller: _deviceBrandCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Brand (e.g. Samsung)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: AppTheme.spaceM),
                TextFormField(
                  controller: _deviceModelCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Model (e.g. Galaxy S24)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: AppTheme.spaceL),

                // ── Issue section ─────────────────────────────────────────
                _SectionHeader(title: 'Issue'),
                const SizedBox(height: AppTheme.spaceS),
                TextFormField(
                  controller: _issueCtrl,
                  maxLines: 4,
                  decoration: const InputDecoration(
                    labelText: 'Describe the issue *',
                    alignLabelWithHint: true,
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.trim().length < 10)
                      ? 'Please describe the issue (min 10 characters)'
                      : null,
                ),
                const SizedBox(height: AppTheme.spaceL),

                // ── Contact section ───────────────────────────────────────
                _SectionHeader(title: 'Contact'),
                const SizedBox(height: AppTheme.spaceS),
                TextFormField(
                  controller: _contactNameCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Your name *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Name is required'
                      : null,
                ),
                const SizedBox(height: AppTheme.spaceM),
                TextFormField(
                  controller: _contactMobileCtrl,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: 'Mobile number *',
                    border: OutlineInputBorder(),
                  ),
                  validator: (v) => (v == null || v.trim().length < 7)
                      ? 'Valid mobile required'
                      : null,
                ),
                const SizedBox(height: AppTheme.spaceL),

                // ── Preferred appointment ─────────────────────────────────
                _SectionHeader(title: 'Preferred Appointment'),
                const SizedBox(height: AppTheme.spaceS),
                Row(
                  children: [
                    Expanded(
                      child: _DateTimeButton(
                        icon: Icons.calendar_today_outlined,
                        label: _preferredDate == null
                            ? 'Select date'
                            : DateFormat('EEE, MMM d').format(_preferredDate!),
                        onTap: _pickDate,
                      ),
                    ),
                    const SizedBox(width: AppTheme.spaceM),
                    Expanded(
                      child: _DateTimeButton(
                        icon: Icons.access_time_outlined,
                        label: _preferredTime == null
                            ? 'Select time'
                            : _preferredTime!.format(context),
                        onTap: _pickTime,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppTheme.spaceL),

                if (_errorMessage != null) ...[
                  Container(
                    padding: const EdgeInsets.all(AppTheme.spaceM),
                    decoration: BoxDecoration(
                      color: AppTheme.error.withValues(alpha: 0.1),
                      borderRadius:
                          BorderRadius.circular(AppTheme.radiusM),
                    ),
                    child: Text(
                      _errorMessage!,
                      style:
                          const TextStyle(color: AppTheme.error),
                    ),
                  ),
                  const SizedBox(height: AppTheme.spaceM),
                ],

                FilledButton(
                  onPressed: _submitting ? null : () => _submit(categories),
                  child: _submitting
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white))
                      : const Text('Submit Request'),
                ),
                const SizedBox(height: AppTheme.spaceL),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});
  final String title;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: AppTheme.primary,
                )),
        const Divider(),
      ],
    );
  }
}

class _DateTimeButton extends StatelessWidget {
  const _DateTimeButton(
      {required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      icon: Icon(icon, size: 18),
      label: Text(label, style: const TextStyle(fontSize: 13)),
      style: OutlinedButton.styleFrom(
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(
            horizontal: AppTheme.spaceM, vertical: AppTheme.spaceM),
      ),
      onPressed: onTap,
    );
  }
}
