import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/admin_repository.dart';

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

  Future<void> _showCreateDialog(BuildContext context, WidgetRef ref) async {
    final nameCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final formKey = GlobalKey<FormState>();

    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('New Device Category'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
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
              child: const Text('Create')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref.read(adminRepositoryProvider).createCategory(
            nameCtrl.text.trim(),
            descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim(),
          );
      ref.invalidate(_categoriesProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Category created')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
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
        subtitle: cat.description != null
            ? Text(cat.description!,
                maxLines: 1, overflow: TextOverflow.ellipsis)
            : null,
        trailing: _ArchiveButton(cat: cat, onRefresh: onRefresh),
      ),
    );
  }
}

class _ArchiveButton extends ConsumerWidget {
  const _ArchiveButton({required this.cat, required this.onRefresh});
  final AdminCategory cat;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return IconButton(
      tooltip: cat.archived ? 'Unarchive' : 'Archive',
      icon: Icon(
        cat.archived ? Icons.unarchive_outlined : Icons.archive_outlined,
        color:
            cat.archived ? AppTheme.success : AppTheme.textSecondary,
      ),
      onPressed: () async {
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
