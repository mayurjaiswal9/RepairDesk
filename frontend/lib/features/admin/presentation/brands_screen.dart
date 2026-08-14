import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/admin_repository.dart';

final _brandsProvider = FutureProvider.autoDispose.family<List<AdminBrand>, String>(
    (ref, categoryId) =>
        ref.watch(adminRepositoryProvider).allBrands(categoryId));

/// Admin screen for curating the brand list under a single device category
/// (PROD-004). Only meaningful for customers when the category's brand mode
/// is "Configured", but brands can be prepared here ahead of switching modes.
class BrandsScreen extends ConsumerWidget {
  const BrandsScreen({super.key, required this.category});
  final AdminCategory category;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final brandsAsync = ref.watch(_brandsProvider(category.id));

    return Scaffold(
      appBar: AppBar(title: Text('${category.name} — Brands')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () =>
            _showBrandDialog(context, ref, categoryId: category.id, existing: null),
        icon: const Icon(Icons.add),
        label: const Text('Add Brand'),
      ),
      body: Column(
        children: [
          if (category.brandMode == BrandMode.freeText)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(AppTheme.spaceM),
              color: AppTheme.warning.withValues(alpha: 0.1),
              child: const Text(
                'This category is set to "Others / Custom (free text)" — '
                'brands added here won\'t be shown to customers until you '
                'switch it to "Configured brand list" in Edit.',
                style: TextStyle(fontSize: 12, color: AppTheme.textPrimary),
              ),
            ),
          Expanded(
            child: brandsAsync.when(
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
                        onPressed: () =>
                            ref.invalidate(_brandsProvider(category.id)),
                        child: const Text('Retry')),
                  ],
                ),
              ),
              data: (brands) => brands.isEmpty
                  ? const Center(
                      child: Text('No brands yet. Tap "Add Brand" to start.',
                          style: TextStyle(color: AppTheme.textSecondary)),
                    )
                  : RefreshIndicator(
                      onRefresh: () =>
                          ref.refresh(_brandsProvider(category.id).future),
                      child: ListView.separated(
                        padding: const EdgeInsets.all(AppTheme.spaceM),
                        itemCount: brands.length,
                        separatorBuilder: (_, sep) =>
                            const SizedBox(height: AppTheme.spaceS),
                        itemBuilder: (_, i) => _BrandCard(
                          categoryId: category.id,
                          brand: brands[i],
                        ),
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

Future<void> _showBrandDialog(BuildContext context, WidgetRef ref,
    {required String categoryId, required AdminBrand? existing}) async {
  final isEdit = existing != null;
  final nameCtrl = TextEditingController(text: existing?.name ?? '');
  final formKey = GlobalKey<FormState>();

  final ok = await showDialog<bool>(
    context: context,
    builder: (dlg) => AlertDialog(
      title: Text(isEdit ? 'Edit Brand' : 'New Brand'),
      content: Form(
        key: formKey,
        child: TextFormField(
          controller: nameCtrl,
          autofocus: true,
          decoration: const InputDecoration(
              labelText: 'Brand name *', border: OutlineInputBorder()),
          validator: (v) =>
              (v == null || v.trim().isEmpty) ? 'Required' : null,
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(dlg, false),
            child: const Text('Cancel')),
        FilledButton(
            onPressed: () {
              if (formKey.currentState!.validate()) {
                Navigator.pop(dlg, true);
              }
            },
            child: Text(isEdit ? 'Save' : 'Add')),
      ],
    ),
  );
  if (ok != true || !context.mounted) return;
  try {
    final name = nameCtrl.text.trim();
    if (isEdit) {
      await ref
          .read(adminRepositoryProvider)
          .updateBrand(categoryId, existing.id, name);
    } else {
      await ref.read(adminRepositoryProvider).createBrand(categoryId, name);
    }
    ref.invalidate(_brandsProvider(categoryId));
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(isEdit ? 'Brand updated' : 'Brand added')));
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e'), backgroundColor: AppTheme.error));
    }
  }
}

class _BrandCard extends ConsumerWidget {
  const _BrandCard({required this.categoryId, required this.brand});
  final String categoryId;
  final AdminBrand brand;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: brand.archived
              ? AppTheme.divider
              : AppTheme.secondary.withValues(alpha: 0.12),
          child: Icon(
            Icons.sell_outlined,
            color: brand.archived ? AppTheme.textSecondary : AppTheme.secondary,
            size: 18,
          ),
        ),
        title: Text(
          brand.name,
          style: TextStyle(
              fontWeight: FontWeight.w600,
              color: brand.archived
                  ? AppTheme.textSecondary
                  : AppTheme.textPrimary,
              decoration: brand.archived ? TextDecoration.lineThrough : null),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!brand.archived)
              IconButton(
                tooltip: 'Edit',
                icon: const Icon(Icons.edit_outlined,
                    color: AppTheme.primary, size: 20),
                onPressed: () => _showBrandDialog(context, ref,
                    categoryId: categoryId, existing: brand),
              ),
            IconButton(
              tooltip: brand.archived ? 'Restore' : 'Delete',
              icon: Icon(
                brand.archived ? Icons.restore_outlined : Icons.delete_outline,
                color: brand.archived ? AppTheme.success : AppTheme.error,
                size: 20,
              ),
              onPressed: () async {
                if (!brand.archived) {
                  final confirmed = await showDialog<bool>(
                    context: context,
                    builder: (dlg) => AlertDialog(
                      title: const Text('Delete Brand'),
                      content: Text(
                          'Delete "${brand.name}"? It will be hidden from new '
                          'repair requests but kept for existing records.'),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(dlg, false),
                            child: const Text('Cancel')),
                        FilledButton(
                          style: FilledButton.styleFrom(
                              backgroundColor: AppTheme.error),
                          onPressed: () => Navigator.pop(dlg, true),
                          child: const Text('Delete'),
                        ),
                      ],
                    ),
                  );
                  if (confirmed != true) return;
                }
                try {
                  if (brand.archived) {
                    await ref
                        .read(adminRepositoryProvider)
                        .unarchiveBrand(categoryId, brand.id);
                  } else {
                    await ref
                        .read(adminRepositoryProvider)
                        .archiveBrand(categoryId, brand.id);
                  }
                  ref.invalidate(_brandsProvider(categoryId));
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text('Error: $e'),
                        backgroundColor: AppTheme.error));
                  }
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
