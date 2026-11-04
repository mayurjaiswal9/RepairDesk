import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/trip_models.dart';
import '../../../core/location/trip_repository.dart';
import '../../../shared/theme/app_theme.dart';

final _myTripsProvider = FutureProvider.autoDispose<MileageDays>((ref) {
  final to = DateTime.now().toUtc();
  final from = to.subtract(const Duration(days: 6));
  return ref.watch(tripRepositoryProvider).days(
        from: indiaDate(from),
        to: indiaDate(to),
      );
});

class MyTripsScreen extends ConsumerWidget {
  const MyTripsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final days = ref.watch(_myTripsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My trips')),
      body: days.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(AppTheme.spaceL),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  error is AppException ? error.userMessage : error.toString(),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: AppTheme.spaceM),
                FilledButton(
                  onPressed: () => ref.invalidate(_myTripsProvider),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (data) {
          if (data.rows.isEmpty) {
            return const Center(
              child: Text(
                'No trips in the last 7 days.',
                style: TextStyle(color: AppTheme.textSecondary),
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(_myTripsProvider),
            child: ListView(
              padding: const EdgeInsets.all(AppTheme.spaceM),
              children: [
                Text(
                  '${kmText(data.summary.payableDistanceM)} · '
                  '${data.summary.trips} trip${data.summary.trips == 1 ? '' : 's'}'
                  '${data.summary.amountPaise > 0 ? ' · ${rupeesText(data.summary.amountPaise)}' : ''}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: AppTheme.spaceS),
                for (final row in data.rows)
                  Card(
                    child: ListTile(
                      title: Text(row.date ?? row.month ?? ''),
                      subtitle: Text(
                        '${row.trips} trip${row.trips == 1 ? '' : 's'} · '
                        '${kmText(row.payableDistanceM)}'
                        '${row.amountPaise > 0 ? ' · ${rupeesText(row.amountPaise)}' : ''}',
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: row.date == null
                          ? null
                          : () => Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => MyTripDayScreen(
                                    technicianId: row.technicianId,
                                    date: row.date!,
                                  ),
                                ),
                              ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class MyTripDayScreen extends ConsumerWidget {
  const MyTripDayScreen({
    super.key,
    required this.technicianId,
    required this.date,
  });

  final String technicianId;
  final String date;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return FutureBuilder<MileageDayDetail>(
      future: ref.read(tripRepositoryProvider).day(technicianId, date),
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return Scaffold(
            appBar: AppBar(title: Text(date)),
            body: const Center(child: CircularProgressIndicator()),
          );
        }
        if (snapshot.hasError) {
          final error = snapshot.error;
          return Scaffold(
            appBar: AppBar(title: Text(date)),
            body: Center(
              child: Text(
                error is AppException ? error.userMessage : error.toString(),
                textAlign: TextAlign.center,
              ),
            ),
          );
        }
        final day = snapshot.data!;
        return Scaffold(
          appBar: AppBar(title: Text(date)),
          body: ListView(
            padding: const EdgeInsets.all(AppTheme.spaceM),
            children: [
              Text(
                '${kmText(day.summary.payableDistanceM)} · '
                '${day.summary.trips} trip${day.summary.trips == 1 ? '' : 's'}',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: AppTheme.spaceM),
              for (final trip in day.trips)
                Card(
                  child: ListTile(
                    title: Text(trip.jobReference),
                    subtitle: Text(
                      '${indiaTime(trip.startedAt ?? DateTime.now())}'
                      '${trip.endedAt != null ? '–${indiaTime(trip.endedAt!)}' : ''} · '
                      '${kmText(trip.payableDistanceM)} · '
                      '${tripStatusLabel(trip)}'
                      '${trip.flags.isEmpty ? '' : ' · ${trip.flags.map(tripFlagLabel).join(', ')}'}',
                    ),
                  ),
                ),
              if (day.noTripJobs.isNotEmpty) ...[
                const SizedBox(height: AppTheme.spaceM),
                Text('Started without a trip',
                    style: Theme.of(context).textTheme.titleMedium),
                for (final job in day.noTripJobs)
                  ListTile(
                    title: Text(job.referenceNumber),
                    subtitle: Text(
                      job.reasonText?.trim().isNotEmpty == true
                          ? job.reasonText!
                          : NoTripReason.labelFor(job.reasonCode),
                    ),
                  ),
              ],
            ],
          ),
        );
      },
    );
  }
}
