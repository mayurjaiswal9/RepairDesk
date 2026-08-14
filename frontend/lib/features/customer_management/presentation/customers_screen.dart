import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/error/app_exception.dart';
import '../../../shared/theme/app_theme.dart';
import '../../account/data/profile_repository.dart';
import '../../jobs/presentation/job_detail_screen.dart';
import '../../requests/presentation/request_detail_screen.dart';
import '../data/customer_management_repository.dart';

/// Renders a user-friendly message for any error, instead of the raw
/// exception dump (BUG-6: customers page "fails with exception").
String _friendlyError(Object e) =>
    e is AppException ? e.userMessage : 'Something went wrong. Please try again.';

final _searchProvider =
    FutureProvider.autoDispose.family<List<CustomerSummary>, String>(
        (ref, q) =>
            ref.watch(customerManagementRepositoryProvider).search(q.isEmpty ? null : q));

final _historyProvider =
    FutureProvider.autoDispose.family<CustomerHistory, String>(
        (ref, id) =>
            ref.watch(customerManagementRepositoryProvider).history(id));

class CustomersScreen extends ConsumerStatefulWidget {
  const CustomersScreen({super.key});

  @override
  ConsumerState<CustomersScreen> createState() => _CustomersScreenState();
}

class _CustomersScreenState extends ConsumerState<CustomersScreen> {
  final _searchCtrl = TextEditingController();
  String _query = '';
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    super.dispose();
  }

  // Debounce keystrokes so we don't fire a search request per character,
  // which made the list feel like it was "loading for a long time" (BUG-6).
  void _onSearchChanged(String v) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () {
      if (mounted) setState(() => _query = v.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    final role = ref.watch(sessionControllerProvider).role;
    if (!Roles.canAccessCustomers(role)) {
      return Scaffold(
        appBar: AppBar(title: const Text('Customers')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(AppTheme.spaceL),
            child: Text(
              'Customer Management is only available to staff and administrators.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppTheme.textSecondary),
            ),
          ),
        ),
      );
    }

    final customersAsync = ref.watch(_searchProvider(_query));

    return Scaffold(
      appBar: AppBar(title: const Text('Customers')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(AppTheme.spaceM),
            child: TextField(
              controller: _searchCtrl,
              decoration: InputDecoration(
                hintText: 'Search by name, username, or mobile…',
                prefixIcon: const Icon(Icons.search),
                border: const OutlineInputBorder(),
                suffixIcon: _query.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _debounce?.cancel();
                          _searchCtrl.clear();
                          setState(() => _query = '');
                        })
                    : null,
              ),
              onChanged: _onSearchChanged,
            ),
          ),
          Expanded(
            child: customersAsync.when(
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline,
                        size: 48, color: AppTheme.error),
                    const SizedBox(height: AppTheme.spaceM),
                    Text(_friendlyError(e),
                        style: const TextStyle(color: AppTheme.error),
                        textAlign: TextAlign.center),
                    const SizedBox(height: AppTheme.spaceM),
                    FilledButton(
                        onPressed: () =>
                            ref.invalidate(_searchProvider(_query)),
                        child: const Text('Retry')),
                  ],
                ),
              ),
              data: (customers) => customers.isEmpty
                  ? Center(
                      child: Text(
                        _query.isEmpty
                            ? 'No customers registered yet.'
                            : 'No customers match "$_query".',
                        style: const TextStyle(
                            color: AppTheme.textSecondary),
                      ),
                    )
                  : ListView.separated(
                      padding: const EdgeInsets.symmetric(
                          horizontal: AppTheme.spaceM),
                      itemCount: customers.length,
                      separatorBuilder: (_, sep) =>
                          const SizedBox(height: AppTheme.spaceS),
                      itemBuilder: (_, i) => _CustomerCard(
                            customer: customers[i],
                            onChanged: () =>
                                ref.invalidate(_searchProvider(_query)),
                          ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomerCard extends ConsumerWidget {
  const _CustomerCard({required this.customer, required this.onChanged});
  final CustomerSummary customer;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final initial =
        ((customer.name?.isNotEmpty == true ? customer.name![0] : customer.username[0]))
            .toUpperCase();
    final isActive = customer.status == 'ACTIVE';

    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.primary.withValues(alpha: 0.12),
          child: Text(initial,
              style: const TextStyle(
                  color: AppTheme.primary, fontWeight: FontWeight.bold)),
        ),
        title: Text(customer.name ?? customer.username,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('@${customer.username}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12)),
            Text(customer.mobile,
                style: const TextStyle(fontSize: 12)),
          ],
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isActive ? Icons.check_circle_outline : Icons.block,
              size: 14,
              color: isActive ? AppTheme.success : AppTheme.error,
            ),
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert,
                  color: AppTheme.textSecondary),
              onSelected: (action) async {
                switch (action) {
                  case 'reset':
                    await _showResetPasswordDialog(context, ref, customer);
                    break;
                  case 'delete':
                    await _confirmDelete(context, ref, customer, onChanged);
                    break;
                }
              },
              itemBuilder: (_) => const [
                PopupMenuItem(
                    value: 'reset',
                    child: ListTile(
                        leading: Icon(Icons.lock_reset_outlined),
                        title: Text('Reset Password'))),
                PopupMenuItem(
                    value: 'delete',
                    child: ListTile(
                        leading:
                            Icon(Icons.delete_outline, color: AppTheme.error),
                        title: Text('Delete',
                            style: TextStyle(color: AppTheme.error)))),
              ],
            ),
            const Icon(Icons.chevron_right),
          ],
        ),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) =>
              CustomerHistoryScreen(customerId: customer.id, name: customer.name ?? customer.username),
        )),
      ),
    );
  }

  Future<void> _showResetPasswordDialog(
      BuildContext context, WidgetRef ref, CustomerSummary customer) async {
    final passCtrl = TextEditingController();
    final formKey = GlobalKey<FormState>();

    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: Text('Reset Password — ${customer.name ?? customer.username}'),
        content: Form(
          key: formKey,
          child: TextFormField(
            controller: passCtrl,
            obscureText: true,
            autofocus: true,
            decoration: const InputDecoration(
                labelText: 'New password (min 8) *',
                border: OutlineInputBorder()),
            validator: (v) =>
                (v == null || v.length < 8) ? 'Minimum 8 characters' : null,
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
            child: const Text('Reset'),
          ),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await ref
          .read(profileRepositoryProvider)
          .resetUserPassword(customer.id, passCtrl.text);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content:
                Text('Password reset — customer must change it on next login')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(_friendlyError(e)), backgroundColor: AppTheme.error));
      }
    }
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref,
      CustomerSummary customer, VoidCallback onChanged) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Delete Customer'),
        content: Text(
            'Delete ${customer.name ?? customer.username}\'s account? '
            'They will no longer be able to sign in. Their repair history is kept.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppTheme.error),
            onPressed: () => Navigator.pop(dlg, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(profileRepositoryProvider).deleteUser(customer.id);
      onChanged();
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Customer deleted')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(_friendlyError(e)), backgroundColor: AppTheme.error));
      }
    }
  }
}

class CustomerHistoryScreen extends ConsumerWidget {
  const CustomerHistoryScreen(
      {super.key, required this.customerId, required this.name});
  final String customerId;
  final String name;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final historyAsync = ref.watch(_historyProvider(customerId));

    return Scaffold(
      appBar: AppBar(title: Text(name)),
      body: historyAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline,
                  size: 48, color: AppTheme.error),
              const SizedBox(height: AppTheme.spaceM),
              Text(_friendlyError(e),
                  style: const TextStyle(color: AppTheme.error),
                  textAlign: TextAlign.center),
              const SizedBox(height: AppTheme.spaceM),
              FilledButton(
                  onPressed: () =>
                      ref.invalidate(_historyProvider(customerId)),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (history) => ListView(
          padding: const EdgeInsets.all(AppTheme.spaceM),
          children: [
            // Profile card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(AppTheme.spaceM),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Profile',
                        style: Theme.of(context)
                            .textTheme
                            .titleMedium
                            ?.copyWith(color: AppTheme.primary)),
                    const Divider(),
                    _Row('Username', '@${history.customer.username}'),
                    _Row('Mobile', history.customer.mobile),
                    _Row('Status', history.customer.status),
                  ],
                ),
              ),
            ),
            const SizedBox(height: AppTheme.spaceM),

            // Requests
            Text('Repair Requests (${history.requests.length})',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            if (history.requests.isEmpty)
              const Text('No requests',
                  style: TextStyle(color: AppTheme.textSecondary))
            else
              ...history.requests
                  .map((r) => _RepairRow(record: r, type: _RecordType.request)),
            const SizedBox(height: AppTheme.spaceM),

            // Jobs
            Text('Jobs (${history.jobs.length})',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
            const Divider(),
            if (history.jobs.isEmpty)
              const Text('No jobs',
                  style: TextStyle(color: AppTheme.textSecondary))
            else
              ...history.jobs
                  .map((j) => _RepairRow(record: j, type: _RecordType.job)),
          ],
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        children: [
          SizedBox(
              width: 80,
              child: Text(label,
                  style: const TextStyle(
                      color: AppTheme.textSecondary, fontSize: 13))),
          Expanded(
              child: Text(value,
                  style: const TextStyle(fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}

enum _RecordType { request, job }

class _RepairRow extends StatelessWidget {
  const _RepairRow({required this.record, required this.type});
  final Map<String, dynamic> record;
  final _RecordType type;

  @override
  Widget build(BuildContext context) {
    final id = record['id'] as String?;
    final refNumber = record['referenceNumber'] as String? ?? '—';
    final status = record['status'] as String? ?? '—';
    final category = record['deviceCategoryName'] as String? ?? '—';
    final updatedRaw = record['updatedAt'] as String?;
    final updatedAt = updatedRaw != null
        ? DateFormat('MMM d, yyyy')
            .format(DateTime.parse(updatedRaw).toLocal())
        : null;

    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(refNumber,
          style: const TextStyle(
              fontWeight: FontWeight.w600, color: AppTheme.primary)),
      subtitle: Text('$category • $status',
          style: const TextStyle(fontSize: 12)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (updatedAt != null)
            Text(updatedAt,
                style: const TextStyle(
                    fontSize: 11, color: AppTheme.textSecondary)),
          if (id != null) const Icon(Icons.chevron_right, size: 18),
        ],
      ),
      onTap: id == null
          ? null
          : () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => type == _RecordType.request
                    ? RequestDetailScreen(
                        requestId: id, onActionCompleted: () {})
                    : JobDetailScreen(jobId: id),
              )),
    );
  }
}
