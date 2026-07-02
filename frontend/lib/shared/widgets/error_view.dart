import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// A reusable full-screen error + retry widget (ERR-001).
class ErrorView extends StatelessWidget {
  const ErrorView({
    super.key,
    required this.error,
    this.onRetry,
    this.message,
  });

  final Object error;
  final VoidCallback? onRetry;
  final String? message;

  @override
  Widget build(BuildContext context) {
    final displayMessage = message ?? _friendlyMessage(error);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceL),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48, color: AppTheme.error),
            const SizedBox(height: AppTheme.spaceM),
            Text(
              displayMessage,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AppTheme.textSecondary,
                  ),
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: AppTheme.spaceM),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh, size: 18),
                label: const Text('Retry'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _friendlyMessage(Object error) {
    final msg = error.toString();
    if (msg.contains('SocketException') || msg.contains('Connection refused') ||
        msg.contains('Network')) {
      return 'No connection. Check your network and try again.';
    }
    if (msg.contains('401') || msg.contains('Unauthorized') ||
        msg.contains('Authentication')) {
      return 'Your session has expired. Please sign in again.';
    }
    if (msg.contains('403') || msg.contains('Forbidden')) {
      return 'You don\'t have permission to view this.';
    }
    if (msg.contains('404') || msg.contains('Not found')) {
      return 'This item was not found.';
    }
    if (msg.contains('500') || msg.contains('Server error')) {
      return 'Something went wrong on the server. Please try again later.';
    }
    // Strip leading "Exception: " prefix common in Dart
    if (msg.startsWith('Exception: ')) return msg.substring(11);
    return msg;
  }
}

/// Thin loading placeholder that matches the app's style.
class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.message});
  final String? message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          if (message != null) ...[
            const SizedBox(height: AppTheme.spaceM),
            Text(message!,
                style: const TextStyle(color: AppTheme.textSecondary)),
          ],
        ],
      ),
    );
  }
}
