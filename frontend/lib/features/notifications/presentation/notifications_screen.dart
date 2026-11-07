import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/session_controller.dart';
import '../../../core/push/notification_target.dart';
import '../../../shared/theme/app_theme.dart';
import '../data/notifications_repository.dart';
import '../data/unread_count_provider.dart';

final _inboxProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(notificationsRepositoryProvider).inbox());

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inboxAsync = ref.watch(_inboxProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          TextButton(
            onPressed: () async {
              await ref
                  .read(notificationsRepositoryProvider)
                  .markAllRead();
              ref.invalidate(_inboxProvider);
              await ref.read(unreadCountProvider.notifier).refresh();
            },
            child: const Text('Mark all read',
                style: TextStyle(color: AppTheme.onPrimary)),
          ),
        ],
      ),
      body: inboxAsync.when(
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
                  onPressed: () => ref.invalidate(_inboxProvider),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (items) => items.isEmpty
            ? const _EmptyInbox()
            : RefreshIndicator(
                onRefresh: () => ref.refresh(_inboxProvider.future),
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(
                      vertical: AppTheme.spaceS),
                  itemCount: items.length,
                  separatorBuilder: (_, sep) => const Divider(height: 0),
                  itemBuilder: (ctx, i) => _NotifTile(
                    notif: items[i],
                    onRead: () async {
                      if (!items[i].read) {
                        await ref
                            .read(notificationsRepositoryProvider)
                            .markRead(items[i].id);
                        ref.invalidate(_inboxProvider);
                        ref
                            .read(unreadCountProvider.notifier)
                            .decrement();
                      }
                      openNotificationTarget(
                        role: ref.read(sessionControllerProvider).role,
                        relatedType: items[i].relatedType ?? '',
                        relatedId: items[i].relatedEntityId ?? '',
                        reference: items[i].referenceNumber ?? '',
                      );
                    },
                  ),
                ),
              ),
      ),
    );
  }
}

class _NotifTile extends StatelessWidget {
  const _NotifTile({required this.notif, required this.onRead});
  final AppNotification notif;
  final VoidCallback onRead;

  @override
  Widget build(BuildContext context) {
    final dt = DateTime.tryParse(notif.createdAt)?.toLocal();
    final timeStr = dt != null
        ? DateFormat('MMM d, h:mm a').format(dt)
        : notif.createdAt;

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(
          horizontal: AppTheme.spaceM, vertical: AppTheme.spaceXS),
      leading: CircleAvatar(
        backgroundColor: notif.read
            ? AppTheme.divider
            : AppTheme.primary.withValues(alpha: 0.15),
        child: Icon(
          notif.read ? Icons.notifications_none : Icons.notifications,
          color: notif.read ? AppTheme.textSecondary : AppTheme.primary,
          size: 20,
        ),
      ),
      title: Text(
        notif.message,
        style: TextStyle(
          fontWeight: notif.read ? FontWeight.normal : FontWeight.w600,
          fontSize: 14,
        ),
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (notif.referenceNumber != null)
            Text(notif.referenceNumber!,
                style: const TextStyle(
                    color: AppTheme.primary, fontSize: 12)),
          Text(timeStr,
              style: const TextStyle(
                  color: AppTheme.textSecondary, fontSize: 11)),
        ],
      ),
      tileColor: notif.read
          ? null
          : AppTheme.primary.withValues(alpha: 0.04),
      onTap: onRead,
    );
  }
}

class _EmptyInbox extends StatelessWidget {
  const _EmptyInbox();
  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.notifications_none,
              size: 64, color: AppTheme.textSecondary),
          SizedBox(height: AppTheme.spaceM),
          Text('No notifications',
              style: TextStyle(
                  fontSize: 18, color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}
