import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import '../../../shared/theme/app_theme.dart';
import '../../../shared/utils/image_source_picker.dart';
import '../../attachments/data/attachments_repository.dart';
import '../../catalog/data/catalog_repository.dart';
import '../data/requests_repository.dart';

/// Sentinel values for [_BrandSelector]'s resolved selection, alongside real
/// catalog brand names (PROD-004).
const _othersSentinel = '__OTHERS__';
const _customSentinel = '__CUSTOM__';
const _othersLabel = 'Others';

final _brandsProvider = FutureProvider.autoDispose.family<List<DeviceBrand>, String>(
    (ref, categoryId) =>
        ref.watch(catalogRepositoryProvider).activeBrands(categoryId));

class NewRequestScreen extends ConsumerStatefulWidget {
  const NewRequestScreen({super.key});

  @override
  ConsumerState<NewRequestScreen> createState() => _NewRequestScreenState();
}

class _NewRequestScreenState extends ConsumerState<NewRequestScreen> {
  final _formKey = GlobalKey<FormState>();

  final _contactNameCtrl = TextEditingController();
  final _contactMobileCtrl = TextEditingController();
  final _customBrandCtrl = TextEditingController();
  final _deviceModelCtrl = TextEditingController();
  final _issueCtrl = TextEditingController();

  String? _selectedCategoryId;
  // A catalog brand name, [_othersSentinel], [_customSentinel], or null
  // (not yet chosen) — see [_BrandSelector].
  String? _brandSelection;
  DateTime? _preferredDate;
  TimeOfDay? _preferredTime;
  bool _submitting = false;
  String? _errorMessage;
  final List<XFile> _pickedPhotos = [];
  final _picker = ImagePicker();

  @override
  void dispose() {
    _contactNameCtrl.dispose();
    _contactMobileCtrl.dispose();
    _customBrandCtrl.dispose();
    _deviceModelCtrl.dispose();
    _issueCtrl.dispose();
    super.dispose();
  }

  /// Resolves the brand chip/autocomplete selection to the string sent to
  /// the API: the catalog brand name, "Others", the typed custom text, or
  /// blank if the customer left brand unset entirely.
  String _resolveBrand() {
    switch (_brandSelection) {
      case _customSentinel:
        return _customBrandCtrl.text.trim();
      case _othersSentinel:
        return _othersLabel;
      case null:
        return '';
      default:
        return _brandSelection!;
    }
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
      final response = await ref.read(requestsRepositoryProvider).createRequest(
            categoryId: _selectedCategoryId!,
            contactName: _contactNameCtrl.text.trim(),
            contactMobile: _contactMobileCtrl.text.trim(),
            deviceBrand: _resolveBrand(),
            deviceModel: _deviceModelCtrl.text.trim(),
            issueDescription: _issueCtrl.text.trim(),
            preferredDate: _preferredDate!,
            preferredTime: _preferredTime!,
          );

      // Upload any picked photos
      if (_pickedPhotos.isNotEmpty) {
        final attRepo = ref.read(attachmentsRepositoryProvider);
        final requestId = response['id'] as String? ?? '';
        if (requestId.isNotEmpty) {
          for (final xfile in _pickedPhotos) {
            try {
              await attRepo.uploadToRequest(requestId, xfile);
            } catch (_) {
              // Non-fatal: continue on upload errors
            }
          }
        }
      }

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

  DeviceCategory? _selectedCategoryOrNull(List<DeviceCategory> categories) {
    for (final c in categories) {
      if (c.id == _selectedCategoryId) return c;
    }
    return null;
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
                  onChanged: (v) => setState(() {
                    _selectedCategoryId = v;
                    // Brand choices depend on the selected device type's
                    // brand mode, so reset when it changes.
                    _brandSelection = null;
                    _customBrandCtrl.clear();
                  }),
                  validator: (v) =>
                      v == null ? 'Please select a device type' : null,
                ),
                const SizedBox(height: AppTheme.spaceM),
                _BrandSelector(
                  category: _selectedCategoryOrNull(categories),
                  selection: _brandSelection,
                  customBrandCtrl: _customBrandCtrl,
                  onSelectionChanged: (v) =>
                      setState(() => _brandSelection = v),
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

                // ── Photos (optional) ─────────────────────────────────────
                _SectionHeader(title: 'Photos (Optional)'),
                const SizedBox(height: AppTheme.spaceS),
                const Text(
                  'Attach up to 5 photos of the device or damage.',
                  style: TextStyle(
                      color: AppTheme.textSecondary, fontSize: 13),
                ),
                const SizedBox(height: AppTheme.spaceS),
                if (_pickedPhotos.isNotEmpty) ...[
                  SizedBox(
                    height: 80,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: _pickedPhotos.length,
                      separatorBuilder: (_, separator) =>
                          const SizedBox(width: 8),
                      itemBuilder: (_, i) => Stack(
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.file(
                              File(_pickedPhotos[i].path),
                              width: 80,
                              height: 80,
                              fit: BoxFit.cover,
                            ),
                          ),
                          Positioned(
                            top: 2,
                            right: 2,
                            child: InkWell(
                              onTap: () => setState(
                                  () => _pickedPhotos.removeAt(i)),
                              child: Container(
                                decoration: const BoxDecoration(
                                  color: Colors.black54,
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.close,
                                    size: 14, color: Colors.white),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: AppTheme.spaceS),
                ],
                if (_pickedPhotos.length < 5)
                  OutlinedButton.icon(
                    icon: const Icon(Icons.add_a_photo_outlined, size: 18),
                    label: const Text('Add Photo'),
                    onPressed: () async {
                      final xfile =
                          await pickImageWithSourceChoice(context, _picker);
                      if (xfile != null) {
                        setState(() => _pickedPhotos.add(xfile));
                      }
                    },
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

/// Brand picker driven by the selected device type's [BrandMode] (PROD-004):
/// a searchable catalog + "Others" when [BrandMode.configured], or a choice
/// between "Others" and typed "Custom" text when [BrandMode.freeText].
class _BrandSelector extends ConsumerWidget {
  const _BrandSelector({
    required this.category,
    required this.selection,
    required this.customBrandCtrl,
    required this.onSelectionChanged,
  });

  final DeviceCategory? category;
  final String? selection;
  final TextEditingController customBrandCtrl;
  final ValueChanged<String?> onSelectionChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cat = category;
    if (cat == null) {
      return const InputDecorator(
        decoration: InputDecoration(
          labelText: 'Brand',
          border: OutlineInputBorder(),
          enabled: false,
        ),
        child: Text('Select a device type first',
            style: TextStyle(color: AppTheme.textSecondary, fontSize: 13)),
      );
    }

    if (cat.brandMode == BrandMode.freeText) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Brand',
              style: TextStyle(fontSize: 13, color: AppTheme.textSecondary)),
          const SizedBox(height: 6),
          Wrap(
            spacing: AppTheme.spaceS,
            children: [
              ChoiceChip(
                label: const Text(_othersLabel),
                selected: selection == _othersSentinel,
                onSelected: (_) => onSelectionChanged(_othersSentinel),
              ),
              ChoiceChip(
                label: const Text('Custom (Free Text)'),
                selected: selection == _customSentinel,
                onSelected: (_) => onSelectionChanged(_customSentinel),
              ),
            ],
          ),
          if (selection == _customSentinel) ...[
            const SizedBox(height: AppTheme.spaceM),
            TextFormField(
              controller: customBrandCtrl,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Brand (e.g. Samsung) *',
                border: OutlineInputBorder(),
              ),
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Please enter a brand, or choose Others'
                  : null,
            ),
          ],
        ],
      );
    }

    // BrandMode.configured — searchable list of curated brands + Others.
    final brandsAsync = ref.watch(_brandsProvider(cat.id));
    return brandsAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: AppTheme.spaceM),
        child: LinearProgressIndicator(),
      ),
      error: (e, _) => Text('Could not load brands: $e',
          style: const TextStyle(color: AppTheme.error, fontSize: 12)),
      data: (brands) {
        final options = [...brands.map((b) => b.name), _othersLabel];
        final currentLabel =
            selection == _othersSentinel ? _othersLabel : (selection ?? '');
        return Autocomplete<String>(
          key: ValueKey('brand-autocomplete-${cat.id}'),
          initialValue: TextEditingValue(text: currentLabel),
          optionsBuilder: (textEditingValue) {
            if (textEditingValue.text.isEmpty) return options;
            final query = textEditingValue.text.toLowerCase();
            return options.where((o) => o.toLowerCase().contains(query));
          },
          onSelected: (value) => onSelectionChanged(
              value == _othersLabel ? _othersSentinel : value),
          fieldViewBuilder: (context, controller, focusNode, onSubmitted) {
            return TextFormField(
              controller: controller,
              focusNode: focusNode,
              decoration: const InputDecoration(
                labelText: 'Brand',
                hintText: 'Search brand, or choose Others',
                border: OutlineInputBorder(),
                suffixIcon: Icon(Icons.search, size: 18),
              ),
            );
          },
        );
      },
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
