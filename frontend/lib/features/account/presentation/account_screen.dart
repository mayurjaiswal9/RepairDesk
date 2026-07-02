import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../shared/theme/app_theme.dart';
import '../data/profile_repository.dart';

final _profileProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(profileRepositoryProvider).getMe());

class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(_profileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: profileAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
            child: Text(e.toString(),
                style: const TextStyle(color: AppTheme.error))),
        data: (profile) => ListView(
          padding: const EdgeInsets.all(AppTheme.spaceM),
          children: [
            // ── Profile info ─────────────────────────────────────────────
            Center(
              child: CircleAvatar(
                radius: 36,
                backgroundColor: AppTheme.primary.withValues(alpha: 0.15),
                child: Text(
                  (profile.name?.isNotEmpty == true
                          ? profile.name![0]
                          : profile.username[0])
                      .toUpperCase(),
                  style: const TextStyle(
                      fontSize: 28,
                      color: AppTheme.primary,
                      fontWeight: FontWeight.bold),
                ),
              ),
            ),
            const SizedBox(height: AppTheme.spaceM),
            Center(
              child: Column(
                children: [
                  Text(profile.name ?? profile.username,
                      style: Theme.of(context).textTheme.titleLarge),
                  Text('@${profile.username}',
                      style: const TextStyle(color: AppTheme.textSecondary)),
                  const SizedBox(height: AppTheme.spaceXS),
                  _RoleBadge(role: profile.role),
                ],
              ),
            ),
            const SizedBox(height: AppTheme.spaceL),

            // ── Details ──────────────────────────────────────────────────
            Card(
              child: Column(
                children: [
                  _ProfileTile(
                      icon: Icons.phone_outlined,
                      label: 'Mobile',
                      value: profile.mobile),
                  const Divider(height: 0),
                  _ProfileTile(
                      icon: Icons.person_outline,
                      label: 'Username',
                      value: profile.username),
                ],
              ),
            ),
            const SizedBox(height: AppTheme.spaceM),

            // ── Actions ──────────────────────────────────────────────────
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.edit_outlined,
                        color: AppTheme.primary),
                    title: const Text('Change Mobile Number'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _showChangeMobileDialog(context, ref, profile.mobile),
                  ),
                  const Divider(height: 0),
                  ListTile(
                    leading: const Icon(Icons.lock_outline,
                        color: AppTheme.primary),
                    title: const Text('Change Password'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _showChangePasswordDialog(context, ref),
                  ),
                  const Divider(height: 0),
                  ListTile(
                    leading: const Icon(Icons.logout,
                        color: AppTheme.error),
                    title: const Text('Sign Out',
                        style: TextStyle(color: AppTheme.error)),
                    onTap: () => ref
                        .read(sessionControllerProvider.notifier)
                        .signOut(),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showChangeMobileDialog(
      BuildContext context, WidgetRef ref, String current) async {
    final ctrl = TextEditingController(text: current);
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Change Mobile'),
        content: TextField(
          controller: ctrl,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(
              labelText: 'Mobile number', border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref
          .read(profileRepositoryProvider)
          .updateMobile(ctrl.text.trim());
      ref.invalidate(_profileProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Mobile updated')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    }
  }

  Future<void> _showChangePasswordDialog(
      BuildContext context, WidgetRef ref) async {
    final currentCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    final formKey = GlobalKey<FormState>();

    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Change Password'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: currentCtrl,
                obscureText: true,
                decoration: const InputDecoration(
                    labelText: 'Current password',
                    border: OutlineInputBorder()),
                validator: (v) =>
                    (v == null || v.isEmpty) ? 'Required' : null,
              ),
              const SizedBox(height: AppTheme.spaceM),
              TextFormField(
                controller: newCtrl,
                obscureText: true,
                decoration: const InputDecoration(
                    labelText: 'New password (min 8 chars)',
                    border: OutlineInputBorder()),
                validator: (v) => (v == null || v.length < 8)
                    ? 'Min 8 characters'
                    : null,
              ),
              const SizedBox(height: AppTheme.spaceM),
              TextFormField(
                controller: confirmCtrl,
                obscureText: true,
                decoration: const InputDecoration(
                    labelText: 'Confirm new password',
                    border: OutlineInputBorder()),
                validator: (v) =>
                    v != newCtrl.text ? 'Passwords don\'t match' : null,
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
              child: const Text('Change')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref.read(profileRepositoryProvider).changePassword(
          currentCtrl.text, newCtrl.text);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Password changed successfully')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    }
  }
}

class _ProfileTile extends StatelessWidget {
  const _ProfileTile(
      {required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: AppTheme.primary),
      title: Text(label,
          style: const TextStyle(
              fontSize: 12, color: AppTheme.textSecondary)),
      subtitle: Text(value,
          style: const TextStyle(
              fontSize: 15, color: AppTheme.textPrimary)),
    );
  }
}

class _RoleBadge extends StatelessWidget {
  const _RoleBadge({required this.role});
  final String role;

  static const _labels = {
    'CUSTOMER': 'Customer',
    'TECHNICIAN': 'Technician',
    'STAFF': 'Staff',
    'ADMIN': 'Admin',
  };

  static const _colors = {
    'CUSTOMER': AppTheme.secondary,
    'TECHNICIAN': AppTheme.primary,
    'STAFF': AppTheme.primary,
    'ADMIN': AppTheme.error,
  };

  @override
  Widget build(BuildContext context) {
    final color = _colors[role] ?? AppTheme.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(_labels[role] ?? role,
          style: TextStyle(
              fontSize: 12,
              color: color,
              fontWeight: FontWeight.w600)),
    );
  }
}
