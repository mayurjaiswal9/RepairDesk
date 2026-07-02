import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../shared/theme/app_theme.dart';
import '../../customer_home/presentation/customer_home_screen.dart';
import '../../jobs/presentation/jobs_list_screen.dart';
import '../../account/presentation/account_screen.dart';
import '../../notifications/data/unread_count_provider.dart';
import '../../notifications/presentation/notifications_screen.dart';
import '../../requests/presentation/request_queue_screen.dart';
import '../../admin/presentation/categories_screen.dart';
import '../../admin/presentation/staff_screen.dart';
import '../../admin/presentation/settings_screen.dart';
import '../../customer_management/presentation/customers_screen.dart';
import '../../scheduling/presentation/calendar_screen.dart';
import '../data/dashboard_repository.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    final role = session.role ?? '';

    return Scaffold(
      appBar: AppBar(
        title: const Text('RepairDesk'),
        actions: [
          _BellButton(),
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            tooltip: 'Account',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const AccountScreen()),
            ),
          ),
        ],
      ),
      body: switch (role) {
        'ADMIN' || 'STAFF' => _StaffDashboard(role: role),
        'CUSTOMER' => const CustomerHomeScreen(),
        'TECHNICIAN' => const _TechnicianLanding(),
        _ => const Center(child: Text('Unknown role')),
      },
    );
  }
}

// ── Staff / Admin dashboard ───────────────────────────────────────────────

final _countersProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(dashboardRepositoryProvider).counters());

class _StaffDashboard extends ConsumerWidget {
  const _StaffDashboard({required this.role});
  final String role;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final countersAsync = ref.watch(_countersProvider);

    return RefreshIndicator(
      onRefresh: () async => ref.refresh(_countersProvider.future),
      child: ListView(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        children: [
          const SizedBox(height: AppTheme.spaceS),
          Text('Welcome back',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppTheme.spaceM),
          countersAsync.when(
            loading: () => const SizedBox(
                height: 80,
                child: Center(child: CircularProgressIndicator())),
            error: (_, err) => const SizedBox.shrink(),
            data: (c) => Column(
              children: [
                Row(
                  children: [
                    _CounterCard(
                        label: 'Pending Review',
                        count: c.pendingReview,
                        color: AppTheme.warning),
                    const SizedBox(width: AppTheme.spaceM),
                    _CounterCard(
                        label: 'Needs Info',
                        count: c.infoRequired,
                        color: AppTheme.secondary),
                    const SizedBox(width: AppTheme.spaceM),
                    _CounterCard(
                        label: 'Active Jobs',
                        count: c.activeJobs,
                        color: AppTheme.primary),
                  ],
                ),
                if (c.unscheduledJobs > 0) ...[
                  const SizedBox(height: AppTheme.spaceS),
                  _UnscheduledBanner(count: c.unscheduledJobs),
                ],
              ],
            ),
          ),
          const SizedBox(height: AppTheme.spaceL),
          Text('Quick Actions',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppTheme.spaceM),
          _NavTile(
            icon: Icons.inbox_outlined,
            label: 'Repair Requests',
            subtitle: 'Review and manage incoming requests',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(
                  builder: (_) => const RequestQueueScreen()),
            ),
          ),
          _NavTile(
            icon: Icons.build_outlined,
            label: 'Jobs',
            subtitle: 'Track ongoing repairs',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const JobsListScreen()),
            ),
          ),
          _NavTile(
            icon: Icons.calendar_month_outlined,
            label: 'Appointments',
            subtitle: 'Weekly calendar view',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const CalendarScreen()),
            ),
          ),
          _NavTile(
            icon: Icons.person_search_outlined,
            label: 'Customers',
            subtitle: 'Search customer profiles and history',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const CustomersScreen()),
            ),
          ),
          if (role == 'ADMIN') ...[
            _NavTile(
              icon: Icons.people_outline,
              label: 'Staff Management',
              subtitle: 'Manage team members',
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const StaffScreen()),
              ),
            ),
            _NavTile(
              icon: Icons.devices_outlined,
              label: 'Device Categories',
              subtitle: 'Manage product catalog',
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const CategoriesScreen()),
              ),
            ),
            _NavTile(
              icon: Icons.settings_outlined,
              label: 'Settings',
              subtitle: 'Business settings and working hours',
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _CounterCard extends StatelessWidget {
  const _CounterCard(
      {required this.label, required this.count, required this.color});
  final String label;
  final int count;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(
            vertical: AppTheme.spaceM, horizontal: AppTheme.spaceS),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(AppTheme.radiusM),
          border: Border.all(color: color.withValues(alpha: 0.30)),
        ),
        child: Column(
          children: [
            Text('$count',
                style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: color)),
            const SizedBox(height: 2),
            Text(label,
                style: const TextStyle(
                    fontSize: 11, color: AppTheme.textSecondary),
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

class _NavTile extends StatelessWidget {
  const _NavTile(
      {required this.icon,
      required this.label,
      required this.subtitle,
      required this.onTap});
  final IconData icon;
  final String label;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: AppTheme.primary),
        title: Text(label),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

// ── Unscheduled-jobs banner (SCH-023) ─────────────────────────────────────

class _UnscheduledBanner extends StatelessWidget {
  const _UnscheduledBanner({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
          horizontal: AppTheme.spaceM, vertical: AppTheme.spaceS),
      decoration: BoxDecoration(
        color: AppTheme.warning.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        border: Border.all(color: AppTheme.warning.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.calendar_today_outlined,
              size: 18, color: AppTheme.warning),
          const SizedBox(width: AppTheme.spaceS),
          Expanded(
            child: Text(
              '$count job${count == 1 ? '' : 's'} not yet scheduled',
              style: const TextStyle(
                  color: AppTheme.warning, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Bell button with unread badge ────────────────────────────────────────

class _BellButton extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final countAsync = ref.watch(unreadCountProvider);
    final count = countAsync.value ?? 0;

    return IconButton(
      tooltip: 'Notifications',
      onPressed: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const NotificationsScreen())),
      icon: Stack(
        clipBehavior: Clip.none,
        children: [
          const Icon(Icons.notifications_outlined),
          if (count > 0)
            Positioned(
              right: -4,
              top: -4,
              child: Container(
                padding: const EdgeInsets.all(3),
                decoration: const BoxDecoration(
                    color: AppTheme.error, shape: BoxShape.circle),
                constraints:
                    const BoxConstraints(minWidth: 16, minHeight: 16),
                child: Text(
                  count > 99 ? '99+' : '$count',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 9,
                      fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

// ── Technician landing ────────────────────────────────────────────────────

class _TechnicianLanding extends StatelessWidget {
  const _TechnicianLanding();

  @override
  Widget build(BuildContext context) {
    return const JobsListScreen();
  }
}
