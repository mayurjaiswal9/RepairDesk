import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/time/india_date.dart';
import '../../../shared/theme/app_theme.dart';
import '../data/jobs_repository.dart';

final openCarryoverProvider = FutureProvider.autoDispose<List<OpenJobDay>>(
  (ref) => ref.watch(jobsRepositoryProvider).openCarryover(),
);

class OpenJobsBanner extends ConsumerWidget {
  const OpenJobsBanner({super.key, required this.onShowDay});

  final ValueChanged<String> onShowDay;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final carry = ref.watch(openCarryoverProvider);
    return carry.maybeWhen(
      data: (days) {
        if (days.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.fromLTRB(
              AppTheme.spaceM, AppTheme.spaceS, AppTheme.spaceM, 0),
          child: Material(
            color: const Color(0xFFFFF4E5),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                  horizontal: AppTheme.spaceM, vertical: AppTheme.spaceS),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final day in days)
                    Row(
                      children: [
                        const Icon(Icons.warning_amber_rounded,
                            color: AppTheme.warning, size: 20),
                        const SizedBox(width: AppTheme.spaceS),
                        Expanded(
                          child: Text(
                            _line(day),
                            style: const TextStyle(
                              color: Color(0xFF8A4B08),
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        TextButton(
                          onPressed: () => onShowDay(day.date),
                          child: const Text('Show'),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        );
      },
      orElse: () => const SizedBox.shrink(),
    );
  }

  String _line(OpenJobDay day) {
    final jobs = day.count == 1 ? 'job' : 'jobs';
    return '${day.count} $jobs still open from ${formatIndiaDate(day.date)}';
  }
}
