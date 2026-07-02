import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/customer_management_repository.dart';

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

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
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
                          _searchCtrl.clear();
                          setState(() => _query = '');
                        })
                    : null,
              ),
              onChanged: (v) => setState(() => _query = v.trim()),
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
                    Text(e.toString(),
                        style: const TextStyle(color: AppTheme.error),
                        textAlign: TextAlign.center),
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
                          ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomerCard extends StatelessWidget {
  const _CustomerCard({required this.customer});
  final CustomerSummary customer;

  @override
  Widget build(BuildContext context) {
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
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('@${customer.username}',
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
            const SizedBox(width: 4),
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
              Text(e.toString(),
                  style: const TextStyle(color: AppTheme.error),
                  textAlign: TextAlign.center),
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
              ...history.requests.map((r) => _RepairRow(record: r)),
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
              ...history.jobs.map((j) => _RepairRow(record: j)),
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

class _RepairRow extends StatelessWidget {
  const _RepairRow({required this.record});
  final Map<String, dynamic> record;

  @override
  Widget build(BuildContext context) {
    final ref = record['referenceNumber'] as String? ?? '—';
    final status = record['status'] as String? ?? '—';
    final category = record['deviceCategoryName'] as String? ?? '—';
    final updatedRaw = record['updatedAt'] as String?;
    final updatedAt = updatedRaw != null
        ? DateFormat('MMM d, yyyy')
            .format(DateTime.parse(updatedRaw).toLocal())
        : null;

    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(ref,
          style: const TextStyle(
              fontWeight: FontWeight.w600, color: AppTheme.primary)),
      subtitle: Text('$category • $status',
          style: const TextStyle(fontSize: 12)),
      trailing: updatedAt != null
          ? Text(updatedAt,
              style: const TextStyle(
                  fontSize: 11, color: AppTheme.textSecondary))
          : null,
    );
  }
}
