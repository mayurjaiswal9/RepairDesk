import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/admin_repository.dart';
import 'brands_screen.dart';

final _categoriesProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(adminRepositoryProvider).allCategories());

class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cats = ref.watch(_categoriesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Device Categories')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateDialog(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Add Category'),
      ),
      body: cats.when(
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
                  onPressed: () => ref.invalidate(_categoriesProvider),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (items) => RefreshIndicator(
          onRefresh: () => ref.refresh(_categoriesProvider.future),
          child: ListView.separated(
            padding: const EdgeInsets.all(AppTheme.spaceM),
            itemCount: items.length,
            separatorBuilder: (_, sep) =>
                const SizedBox(height: AppTheme.spaceS),
            itemBuilder: (_, i) => _CategoryCard(
              cat: items[i],
              onRefresh: () => ref.invalidate(_categoriesProvider),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _showCreateDialog(BuildContext context, WidgetRef ref) =>
      _showCategoryDialog(context, ref, existing: null);
}

/// Shared dialog for both Create (existing == null) and Edit (existing != null).
Future<void> _showCategoryDialog(BuildContext context, WidgetRef ref,
    {required AdminCategory? existing}) async {
  final isEdit = existing != null;
  final nameCtrl = TextEditingController(text: existing?.name ?? '');
  final descCtrl = TextEditingController(text: existing?.description ?? '');
  final formKey = GlobalKey<FormState>();
  var brandMode = existing?.brandMode ?? BrandMode.freeText;

  final ok = await showDialog<bool>(
    context: context,
    builder: (dlg) => StatefulBuilder(
      builder: (ctx, setDlgState) => AlertDialog(
        title: Text(isEdit ? 'Edit Device Category' : 'New Device Category'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextFormField(
                controller: nameCtrl,
                decoration: const InputDecoration(
                    labelText: 'Name *', border: OutlineInputBorder()),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: AppTheme.spaceM),
              TextFormField(
                controller: descCtrl,
                maxLines: 2,
                decoration: const InputDecoration(
                    labelText: 'Description (optional)',
                    border: OutlineInputBorder()),
              ),
              const SizedBox(height: AppTheme.spaceM),
              const Text('Brand selection',
                  style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textSecondary)),
              const SizedBox(height: 4),
              RadioListTile<BrandMode>(
                contentPadding: EdgeInsets.zero,
                dense: true,
                value: BrandMode.freeText,
                groupValue: brandMode, // ignore: deprecated_member_use
                title: const Text('Others / Custom (free text)'),
                subtitle: const Text(
                    'No curated list — customer picks "Others" or types a brand.',
                    style: TextStyle(fontSize: 12)),
                onChanged: (v) => // ignore: deprecated_member_use
                    setDlgState(() => brandMode = v!),
              ),
              RadioListTile<BrandMode>(
                contentPadding: EdgeInsets.zero,
                dense: true,
                value: BrandMode.configured,
                groupValue: brandMode, // ignore: deprecated_member_use
                title: const Text('Configured brand list'),
                subtitle: const Text(
                    'Customer searches a curated list; "Others" is always included.',
                    style: TextStyle(fontSize: 12)),
                onChanged: (v) => // ignore: deprecated_member_use
                    setDlgState(() => brandMode = v!),
              ),
            ],
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
              child: Text(isEdit ? 'Save' : 'Create')),
        ],
      ),
    ),
  );
  if (ok != true || !context.mounted) return;
  try {
    final name = nameCtrl.text.trim();
    final desc = descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim();
    if (isEdit) {
      await ref
          .read(adminRepositoryProvider)
          .updateCategory(existing.id, name, desc, brandMode);
    } else {
      await ref
          .read(adminRepositoryProvider)
          .createCategory(name, desc, brandMode);
    }
    ref.invalidate(_categoriesProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(isEdit ? 'Category updated' : 'Category created')));
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e'), backgroundColor: AppTheme.error));
    }
  }
}

class _CategoryCard extends ConsumerWidget {
  const _CategoryCard({required this.cat, required this.onRefresh});
  final AdminCategory cat;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: cat.archived
              ? AppTheme.divider
              : AppTheme.primary.withValues(alpha: 0.12),
          child: Icon(
            Icons.devices_outlined,
            color: cat.archived ? AppTheme.textSecondary : AppTheme.primary,
            size: 20,
          ),
        ),
        title: Text(
          cat.name,
          style: TextStyle(
              fontWeight: FontWeight.w600,
              color:
                  cat.archived ? AppTheme.textSecondary : AppTheme.textPrimary,
              decoration:
                  cat.archived ? TextDecoration.lineThrough : null),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (cat.description != null)
              Text(cat.description!,
                  maxLines: 1, overflow: TextOverflow.ellipsis),
            Text(
              cat.brandMode == BrandMode.configured
                  ? 'Configured brand list'
                  : 'Others / Custom (free text)',
              style: const TextStyle(
                  fontSize: 11, color: AppTheme.textSecondary),
            ),
            // Full-width, clearly-labelled entry point for brand management —
            // previously this was only a small tag icon buried in the
            // trailing row, easy to miss.
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: OutlinedButton.icon(
                onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => BrandsScreen(category: cat),
                )),
                icon: const Icon(Icons.sell_outlined, size: 16),
                label: const Text('Manage Brands'),
                style: OutlinedButton.styleFrom(
                  visualDensity: VisualDensity.compact,
                  foregroundColor: AppTheme.secondary,
                  side: const BorderSide(color: AppTheme.secondary),
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                ),
              ),
            ),
          ],
        ),
        isThreeLine: true,
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Edit',
              icon: const Icon(Icons.edit_outlined,
                  color: AppTheme.primary, size: 20),
              onPressed: () =>
                  _showCategoryDialog(context, ref, existing: cat),
            ),
            _DeleteRestoreButton(cat: cat, onRefresh: onRefresh),
          ],
        ),
      ),
    );
  }
}

/// Archive is a soft-delete: categories referenced by past requests/jobs
/// can't be hard-deleted without breaking historical data, so "Delete"
/// hides it from new-request pickers while "Restore" brings it back.
class _DeleteRestoreButton extends ConsumerWidget {
  const _DeleteRestoreButton({required this.cat, required this.onRefresh});
  final AdminCategory cat;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return IconButton(
      tooltip: cat.archived ? 'Restore' : 'Delete',
      icon: Icon(
        cat.archived ? Icons.restore_outlined : Icons.delete_outline,
        color: cat.archived ? AppTheme.success : AppTheme.error,
        size: 20,
      ),
      onPressed: () async {
        if (!cat.archived) {
          final confirmed = await showDialog<bool>(
            context: context,
            builder: (dlg) => AlertDialog(
              title: const Text('Delete Category'),
              content: Text(
                  'Delete "${cat.name}"? It will be hidden from new '
                  'repair requests but kept for existing records.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(dlg, false),
                    child: const Text('Cancel')),
                FilledButton(
                  style:
                      FilledButton.styleFrom(backgroundColor: AppTheme.error),
                  onPressed: () => Navigator.pop(dlg, true),
                  child: const Text('Delete'),
                ),
              ],
            ),
          );
          if (confirmed != true) return;
        }
        try {
          if (cat.archived) {
            await ref.read(adminRepositoryProvider).unarchiveCategory(cat.id);
          } else {
            await ref.read(adminRepositoryProvider).archiveCategory(cat.id);
          }
          onRefresh();
        } catch (e) {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                content: Text('Error: $e'),
                backgroundColor: AppTheme.error));
          }
        }
      },
    );
  }
}
