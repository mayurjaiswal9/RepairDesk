import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'notifications_repository.dart';

part 'unread_count_provider.g.dart';

/// Polls unread notification count every 30 seconds while the app is in the
/// foreground. Stops polling when the provider is disposed (NOTIF-021).
@riverpod
class UnreadCountNotifier extends _$UnreadCountNotifier
    with WidgetsBindingObserver {
  Timer? _timer;

  @override
  Future<int> build() async {
    // Register lifecycle observer so polling pauses in background
    WidgetsBinding.instance.addObserver(this);
    ref.onDispose(() {
      WidgetsBinding.instance.removeObserver(this);
      _timer?.cancel();
    });

    _startPolling();
    return _fetch();
  }

  Future<int> _fetch() async {
    try {
      return await ref.read(notificationsRepositoryProvider).unreadCount();
    } catch (_) {
      // Polling failures are silent — don't spam the UI
      return state.value ?? 0;
    }
  }

  void _startPolling() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 30), (_) async {
      final count = await _fetch();
      state = AsyncValue.data(count);
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Immediately refresh + restart timer when coming back to foreground
      _fetch().then((count) => this.state = AsyncValue.data(count));
      _startPolling();
    } else if (state == AppLifecycleState.paused) {
      _timer?.cancel();
    }
  }

  /// Called after marking a notification as read to update the badge instantly.
  void decrement() {
    final current = state.value ?? 0;
    state = AsyncValue.data(current > 0 ? current - 1 : 0);
  }

  /// Force a full refresh (e.g. after mark-all-read).
  Future<void> refresh() async {
    state = AsyncValue.data(await _fetch());
  }
}
