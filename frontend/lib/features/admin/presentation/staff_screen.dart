import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../../account/data/profile_repository.dart';

final _staffProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(profileRepositoryProvider).listAllUsers());

class StaffScreen extends ConsumerWidget {
  const StaffScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final staffAsync = ref.watch(_staffProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Staff Management')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateDialog(context, ref),
        icon: const Icon(Icons.person_add_outlined),
        label: const Text('Add Member'),
      ),
      body: staffAsync.when(
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
                  onPressed: () => ref.invalidate(_staffProvider),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (users) => RefreshIndicator(
          onRefresh: () => ref.refresh(_staffProvider.future),
          child: users.isEmpty
              ? const Center(
                  child: Text('No team members yet.',
                      style: TextStyle(color: AppTheme.textSecondary)))
              : ListView.separated(
                  padding: const EdgeInsets.all(AppTheme.spaceM),
                  itemCount: users.length,
                  separatorBuilder: (_, sep) =>
                      const SizedBox(height: AppTheme.spaceS),
                  itemBuilder: (_, i) => _UserCard(
                    user: users[i],
                    onRefresh: () => ref.invalidate(_staffProvider),
                  ),
                ),
        ),
      ),
    );
  }

  Future<void> _showCreateDialog(BuildContext context, WidgetRef ref) async {
    final usernameCtrl = TextEditingController();
    final nameCtrl = TextEditingController();
    final mobileCtrl = TextEditingController();
    final passCtrl = TextEditingController();
    String selectedRole = 'STAFF';
    final formKey = GlobalKey<FormState>();

    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => StatefulBuilder(
        builder: (ctx, setDlgState) => AlertDialog(
          title: const Text('Add Team Member'),
          content: SingleChildScrollView(
            child: Form(
              key: formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextFormField(
                    controller: nameCtrl,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(
                        labelText: 'Full name *',
                        border: OutlineInputBorder()),
                    validator: (v) =>
                        (v == null || v.trim().isEmpty) ? 'Required' : null,
                  ),
                  const SizedBox(height: AppTheme.spaceM),
                  TextFormField(
                    controller: usernameCtrl,
                    decoration: const InputDecoration(
                        labelText: 'Username *',
                        border: OutlineInputBorder()),
                    validator: (v) =>
                        (v == null || v.trim().length < 3)
                            ? 'Min 3 characters'
                            : null,
                  ),
                  const SizedBox(height: AppTheme.spaceM),
                  TextFormField(
                    controller: mobileCtrl,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                        labelText: 'Mobile *',
                        border: OutlineInputBorder()),
                    validator: (v) =>
                        (v == null || v.trim().length < 7)
                            ? 'Enter a valid number'
                            : null,
                  ),
                  const SizedBox(height: AppTheme.spaceM),
                  TextFormField(
                    controller: passCtrl,
                    obscureText: true,
                    decoration: const InputDecoration(
                        labelText: 'Temporary password (min 8) *',
                        border: OutlineInputBorder()),
                    validator: (v) =>
                        (v == null || v.length < 8)
                            ? 'Minimum 8 characters'
                            : null,
                  ),
                  const SizedBox(height: AppTheme.spaceM),
                  DropdownButtonFormField<String>(
                    // ignore: deprecated_member_use
                    value: selectedRole,
                    decoration: const InputDecoration(
                        labelText: 'Role',
                        border: OutlineInputBorder()),
                    items: const [
                      DropdownMenuItem(value: 'STAFF', child: Text('Staff')),
                      DropdownMenuItem(
                          value: 'TECHNICIAN', child: Text('Technician')),
                      DropdownMenuItem(value: 'ADMIN', child: Text('Admin')),
                    ],
                    onChanged: (v) =>
                        setDlgState(() => selectedRole = v ?? 'STAFF'),
                  ),
                ],
              ),
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
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref.read(profileRepositoryProvider).createUser(
            username: usernameCtrl.text.trim(),
            name: nameCtrl.text.trim(),
            mobile: mobileCtrl.text.trim(),
            password: passCtrl.text,
            role: selectedRole,
          );
      ref.invalidate(_staffProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
              content: Text(
                  'Account created — user must change password on first login')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    }
  }
}

class _UserCard extends ConsumerWidget {
  const _UserCard({required this.user, required this.onRefresh});
  final UserProfileData user;
  final VoidCallback onRefresh;

  static const _roleColors = {
    'ADMIN': AppTheme.error,
    'STAFF': AppTheme.primary,
    'TECHNICIAN': AppTheme.secondary,
    'CUSTOMER': AppTheme.textSecondary,
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isActive = user.status == 'ACTIVE';
    final roleColor = _roleColors[user.role] ?? AppTheme.textSecondary;
    final initial = ((user.name?.isNotEmpty == true
                ? user.name![0]
                : user.username[0]))
        .toUpperCase();

    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: roleColor.withValues(alpha: 0.12),
          child: Text(initial,
              style:
                  TextStyle(color: roleColor, fontWeight: FontWeight.bold)),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(user.name ?? user.username,
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: roleColor.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(10),
                border:
                    Border.all(color: roleColor.withValues(alpha: 0.3)),
              ),
              child: Text(user.role,
                  style: TextStyle(
                      fontSize: 10,
                      color: roleColor,
                      fontWeight: FontWeight.w600)),
            ),
          ],
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('@${user.username}',
                style: const TextStyle(fontSize: 12)),
            Row(
              children: [
                Icon(
                  isActive
                      ? Icons.check_circle_outline
                      : Icons.block,
                  size: 12,
                  color:
                      isActive ? AppTheme.success : AppTheme.error,
                ),
                const SizedBox(width: 4),
                Text(
                  isActive ? 'Active' : 'Disabled',
                  style: TextStyle(
                      fontSize: 11,
                      color: isActive
                          ? AppTheme.success
                          : AppTheme.error),
                ),
              ],
            ),
          ],
        ),
        trailing: user.role != 'CUSTOMER'
            ? Switch(
                value: isActive,
                // ignore: deprecated_member_use
                activeColor: AppTheme.success,
                onChanged: (active) async {
                  try {
                    await ref
                        .read(profileRepositoryProvider)
                        .setUserStatus(user.id, active);
                    onRefresh();
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                          content: Text('Error: $e'),
                          backgroundColor: AppTheme.error));
                    }
                  }
                },
              )
            : null,
      ),
    );
  }
}
