import 'package:intl/intl.dart';

/// A latitude / longitude pair as sent by the trip endpoints.
class LatLngPoint {
  const LatLngPoint(this.latitude, this.longitude);

  final double latitude;
  final double longitude;

  static LatLngPoint? fromJson(Object? json) {
    if (json is! Map) return null;
    final lat = json['latitude'];
    final lng = json['longitude'];
    if (lat is! num || lng is! num) return null;
    return LatLngPoint(lat.toDouble(), lng.toDouble());
  }

  Map<String, dynamic> toJson() => {'latitude': latitude, 'longitude': longitude};
}

/// `TripResponse` from the trip and mileage endpoints.
class Trip {
  const Trip({
    required this.id,
    required this.jobId,
    required this.jobReference,
    required this.technicianId,
    required this.status,
    this.technicianName,
    this.startedAt,
    this.endedAt,
    this.durationSeconds = 0,
    this.startKind,
    this.startJobReference,
    this.start,
    this.end,
    this.destination,
    this.trackedDistanceM = 0,
    this.estimatedGapM = 0,
    this.plannedDistanceM,
    this.plannedDurationSeconds,
    this.plannedEstimated = false,
    this.adjustedDistanceM,
    this.adjustReason,
    this.adjustedByName,
    this.adjustedAt,
    this.payableDistanceM = 0,
    this.vehicleType,
    this.ratePaise,
    this.amountPaise,
    this.flags = const [],
    this.pointCount = 0,
    this.endReason,
    this.route = const [],
  });

  final String id;
  final String jobId;
  final String jobReference;
  final String technicianId;
  final String? technicianName;

  /// `ACTIVE`, `ENDED`, or `CANCELLED`.
  final String status;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final int durationSeconds;
  final String? startKind;
  final String? startJobReference;
  final LatLngPoint? start;
  final LatLngPoint? end;
  final LatLngPoint? destination;
  final int trackedDistanceM;
  final int estimatedGapM;
  final int? plannedDistanceM;
  final int? plannedDurationSeconds;
  final bool plannedEstimated;
  final int? adjustedDistanceM;
  final String? adjustReason;
  final String? adjustedByName;
  final DateTime? adjustedAt;
  final int payableDistanceM;
  final String? vehicleType;
  final int? ratePaise;
  final int? amountPaise;
  final List<String> flags;
  final int pointCount;
  final String? endReason;
  final List<LatLngPoint> route;

  bool get isActive => status == 'ACTIVE';
  bool get isEnded => status == 'ENDED';
  bool get isCancelled => status == 'CANCELLED';
  bool get isAdjusted => adjustedDistanceM != null;

  factory Trip.fromJson(Map<String, dynamic> json) {
    return Trip(
      id: json['id'] as String,
      jobId: json['jobId'] as String? ?? '',
      jobReference: json['jobReference'] as String? ?? '',
      technicianId: json['technicianId']?.toString() ?? '',
      technicianName: json['technicianName'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
      startedAt: _instant(json['startedAt']),
      endedAt: _instant(json['endedAt']),
      durationSeconds: (json['durationSeconds'] as num?)?.toInt() ?? 0,
      startKind: json['startKind'] as String?,
      startJobReference: json['startJobReference'] as String?,
      start: LatLngPoint.fromJson(json['start']),
      end: LatLngPoint.fromJson(json['end']),
      destination: LatLngPoint.fromJson(json['destination']),
      trackedDistanceM: (json['trackedDistanceM'] as num?)?.toInt() ?? 0,
      estimatedGapM: (json['estimatedGapM'] as num?)?.toInt() ?? 0,
      plannedDistanceM: (json['plannedDistanceM'] as num?)?.toInt(),
      plannedDurationSeconds: (json['plannedDurationSeconds'] as num?)?.toInt(),
      plannedEstimated: json['plannedEstimated'] == true,
      adjustedDistanceM: (json['adjustedDistanceM'] as num?)?.toInt(),
      adjustReason: json['adjustReason'] as String?,
      adjustedByName: json['adjustedByName'] as String?,
      adjustedAt: _instant(json['adjustedAt']),
      payableDistanceM: (json['payableDistanceM'] as num?)?.toInt() ?? 0,
      vehicleType: json['vehicleType'] as String?,
      ratePaise: (json['ratePaise'] as num?)?.toInt(),
      amountPaise: (json['amountPaise'] as num?)?.toInt(),
      flags: (json['flags'] as List<dynamic>? ?? const [])
          .map((flag) => flag.toString())
          .toList(),
      pointCount: (json['pointCount'] as num?)?.toInt() ?? 0,
      endReason: json['endReason'] as String?,
      route: (json['route'] as List<dynamic>? ?? const [])
          .whereType<List<dynamic>>()
          .where((pair) => pair.length >= 2 && pair[0] is num && pair[1] is num)
          .map((pair) => LatLngPoint(
              (pair[0] as num).toDouble(), (pair[1] as num).toDouble()))
          .toList(),
    );
  }

  /// Enough to restore the banner and recording offline. The route is not kept.
  Map<String, dynamic> toJson() => {
        'id': id,
        'jobId': jobId,
        'jobReference': jobReference,
        'technicianId': technicianId,
        'technicianName': technicianName,
        'status': status,
        'startedAt': startedAt?.toUtc().toIso8601String(),
        'endedAt': endedAt?.toUtc().toIso8601String(),
        'durationSeconds': durationSeconds,
        'startKind': startKind,
        'start': start?.toJson(),
        'destination': destination?.toJson(),
        'trackedDistanceM': trackedDistanceM,
        'estimatedGapM': estimatedGapM,
        'payableDistanceM': payableDistanceM,
        'flags': flags,
        'pointCount': pointCount,
        'endReason': endReason,
      };
}

/// `GET /jobs/{jobId}/trips`.
class JobTrips {
  const JobTrips({required this.trips, this.noTrip});

  final List<Trip> trips;
  final NoTripRecord? noTrip;

  /// True when this technician already has a running or finished trip for the job,
  /// so Start job needs no reason.
  bool hasTripBy(String? technicianId) => trips.any((trip) =>
      (trip.isActive || trip.isEnded) &&
      (technicianId == null || trip.technicianId == technicianId));

  Trip? get latestEnded {
    final ended = trips.where((trip) => trip.isEnded).toList()
      ..sort((a, b) => (b.endedAt ?? b.startedAt ?? DateTime(0))
          .compareTo(a.endedAt ?? a.startedAt ?? DateTime(0)));
    return ended.isEmpty ? null : ended.first;
  }

  factory JobTrips.fromJson(Map<String, dynamic> json) {
    return JobTrips(
      trips: (json['trips'] as List<dynamic>? ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(Trip.fromJson)
          .toList(),
      noTrip: json['noTrip'] is Map<String, dynamic>
          ? NoTripRecord.fromJson(json['noTrip'] as Map<String, dynamic>)
          : null,
    );
  }
}

class NoTripRecord {
  const NoTripRecord({
    required this.reasonCode,
    this.reasonText,
    this.technicianName,
    this.createdAt,
  });

  final String reasonCode;
  final String? reasonText;
  final String? technicianName;
  final DateTime? createdAt;

  factory NoTripRecord.fromJson(Map<String, dynamic> json) => NoTripRecord(
        reasonCode: json['reasonCode'] as String? ?? 'OTHER',
        reasonText: json['reasonText'] as String?,
        technicianName: json['technicianName'] as String?,
        createdAt: _instant(json['createdAt']),
      );
}

/// How the technician ended a trip from the phone.
enum TripEndReason {
  arrived('ARRIVED'),
  geofence('GEOFENCE'),
  cancelled('CANCELLED');

  const TripEndReason(this.code);

  final String code;
}

/// Reasons a technician can give for starting a job without a trip.
enum NoTripReason {
  atShop('AT_SHOP', 'Customer came to the shop'),
  alreadyOnSite('ALREADY_ON_SITE', 'Already at this site'),
  phoneProblem('PHONE_PROBLEM', 'Phone or GPS problem'),
  other('OTHER', 'Other');

  const NoTripReason(this.code, this.label);

  final String code;
  final String label;

  bool get needsNote => this == NoTripReason.other;

  static String labelFor(String? code) {
    for (final reason in values) {
      if (reason.code == code) return reason.label;
    }
    return code ?? '';
  }
}

/// The technician's answer on the no-trip sheet.
class NoTripChoice {
  const NoTripChoice(this.reason, [this.note]);

  final NoTripReason reason;
  final String? note;
}

class MileageSummary {
  const MileageSummary({
    this.payableDistanceM = 0,
    this.trips = 0,
    this.amountPaise = 0,
    this.flaggedTrips = 0,
    this.noTripJobs = 0,
    this.activeTrips = 0,
  });

  final int payableDistanceM;
  final int trips;
  final int amountPaise;
  final int flaggedTrips;
  final int noTripJobs;
  final int activeTrips;

  factory MileageSummary.fromJson(Object? json) {
    if (json is! Map) return const MileageSummary();
    int value(String key) => (json[key] as num?)?.toInt() ?? 0;
    return MileageSummary(
      payableDistanceM: value('payableDistanceM'),
      trips: value('trips'),
      amountPaise: value('amountPaise'),
      flaggedTrips: value('flaggedTrips'),
      noTripJobs: value('noTripJobs'),
      activeTrips: value('activeTrips'),
    );
  }
}

/// One technician-day (or month) from `GET /mileage/days`.
class MileageRow {
  const MileageRow({
    this.date,
    this.month,
    required this.technicianId,
    required this.technicianName,
    this.vehicleType,
    this.trips = 0,
    this.trackedDistanceM = 0,
    this.adjustmentM = 0,
    this.payableDistanceM = 0,
    this.ratePaise,
    this.amountPaise = 0,
    this.flaggedTrips = 0,
    this.noTripJobs = 0,
    this.activeTrips = 0,
  });

  final String? date;
  final String? month;
  final String technicianId;
  final String technicianName;
  final String? vehicleType;
  final int trips;
  final int trackedDistanceM;
  final int adjustmentM;
  final int payableDistanceM;
  final int? ratePaise;
  final int amountPaise;
  final int flaggedTrips;
  final int noTripJobs;
  final int activeTrips;

  factory MileageRow.fromJson(Map<String, dynamic> json) {
    int value(String key) => (json[key] as num?)?.toInt() ?? 0;
    return MileageRow(
      date: json['date'] as String?,
      month: json['month'] as String?,
      technicianId: json['technicianId']?.toString() ?? '',
      technicianName: json['technicianName'] as String? ?? '',
      vehicleType: json['vehicleType'] as String?,
      trips: value('trips'),
      trackedDistanceM: value('trackedDistanceM'),
      adjustmentM: value('adjustmentM'),
      payableDistanceM: value('payableDistanceM'),
      ratePaise: (json['ratePaise'] as num?)?.toInt(),
      amountPaise: value('amountPaise'),
      flaggedTrips: value('flaggedTrips'),
      noTripJobs: value('noTripJobs'),
      activeTrips: value('activeTrips'),
    );
  }
}

/// `GET /mileage/days`.
class MileageDays {
  const MileageDays({
    required this.from,
    required this.to,
    required this.summary,
    required this.rows,
  });

  final String from;
  final String to;
  final MileageSummary summary;
  final List<MileageRow> rows;

  factory MileageDays.fromJson(Map<String, dynamic> json) => MileageDays(
        from: json['from'] as String? ?? '',
        to: json['to'] as String? ?? '',
        summary: MileageSummary.fromJson(json['summary']),
        rows: (json['rows'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(MileageRow.fromJson)
            .toList(),
      );
}

class NoTripJob {
  const NoTripJob({
    required this.jobId,
    required this.referenceNumber,
    required this.reasonCode,
    this.reasonText,
    this.createdAt,
  });

  final String jobId;
  final String referenceNumber;
  final String reasonCode;
  final String? reasonText;
  final DateTime? createdAt;

  factory NoTripJob.fromJson(Map<String, dynamic> json) => NoTripJob(
        jobId: json['jobId']?.toString() ?? '',
        referenceNumber: json['referenceNumber'] as String? ?? '',
        reasonCode: json['reasonCode'] as String? ?? 'OTHER',
        reasonText: json['reasonText'] as String?,
        createdAt: _instant(json['createdAt']),
      );
}

/// `GET /mileage/days/{technicianId}/{date}`.
class MileageDayDetail {
  const MileageDayDetail({
    required this.date,
    required this.technicianId,
    required this.technicianName,
    this.vehicleType,
    required this.summary,
    required this.trips,
    required this.noTripJobs,
  });

  final String date;
  final String technicianId;
  final String technicianName;
  final String? vehicleType;
  final MileageSummary summary;
  final List<Trip> trips;
  final List<NoTripJob> noTripJobs;

  factory MileageDayDetail.fromJson(Map<String, dynamic> json) => MileageDayDetail(
        date: json['date'] as String? ?? '',
        technicianId: json['technicianId']?.toString() ?? '',
        technicianName: json['technicianName'] as String? ?? '',
        vehicleType: json['vehicleType'] as String?,
        summary: MileageSummary.fromJson(json['summary']),
        trips: (json['trips'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(Trip.fromJson)
            .toList(),
        noTripJobs: (json['noTripJobs'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(NoTripJob.fromJson)
            .toList(),
      );
}

/// One GPS reading waiting on the phone for `POST /trips/{id}/points`.
class TripPoint {
  const TripPoint({
    required this.clientPointId,
    required this.recordedAt,
    required this.latitude,
    required this.longitude,
    this.accuracyM,
    this.speedMps,
    this.mocked = false,
  });

  final String clientPointId;
  final DateTime recordedAt;
  final double latitude;
  final double longitude;
  final double? accuracyM;
  final double? speedMps;
  final bool mocked;

  Map<String, dynamic> toJson() => {
        'clientPointId': clientPointId,
        'recordedAt': recordedAt.toUtc().toIso8601String(),
        'latitude': latitude,
        'longitude': longitude,
        if (accuracyM != null) 'accuracyM': accuracyM,
        if (speedMps != null) 'speedMps': speedMps,
        'mocked': mocked,
      };

  factory TripPoint.fromJson(Map<String, dynamic> json) => TripPoint(
        clientPointId: json['clientPointId'] as String,
        recordedAt: DateTime.parse(json['recordedAt'] as String),
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        accuracyM: (json['accuracyM'] as num?)?.toDouble(),
        speedMps: (json['speedMps'] as num?)?.toDouble(),
        mocked: json['mocked'] == true,
      );
}

/// `POST /trips/{id}/points` result.
class PointUploadResult {
  const PointUploadResult({
    required this.accepted,
    required this.duplicates,
    this.trackedDistanceM,
  });

  final int accepted;
  final int duplicates;
  final int? trackedDistanceM;

  factory PointUploadResult.fromJson(Map<String, dynamic> json) => PointUploadResult(
        accepted: (json['accepted'] as num?)?.toInt() ?? 0,
        duplicates: (json['duplicates'] as num?)?.toInt() ?? 0,
        trackedDistanceM: (json['trackedDistanceM'] as num?)?.toInt(),
      );
}

DateTime? _instant(Object? value) =>
    value is String ? DateTime.tryParse(value) : null;

// ── Labels shared by the job card, My trips, and the day detail ─────────────

String kmText(int metres) => '${(metres / 1000).toStringAsFixed(1)} km';

String tripDurationText(Duration duration) {
  final minutes = duration.inMinutes;
  if (minutes < 60) return '$minutes min';
  final rest = (minutes % 60).toString().padLeft(2, '0');
  return '${minutes ~/ 60} h $rest min';
}

final _rupees = NumberFormat.decimalPattern('en_IN');
final _rupeesWithPaise =
    NumberFormat.decimalPatternDigits(locale: 'en_IN', decimalDigits: 2);

String rupeesText(int paise) {
  final whole = paise % 100 == 0;
  return '₹${whole ? _rupees.format(paise ~/ 100) : _rupeesWithPaise.format(paise / 100)}';
}

String tripFlagLabel(String flag) => switch (flag) {
      'NO_POINTS' => 'No route recorded',
      'LONGER_THAN_ROUTE' => 'Longer than the route',
      'SHORTER_THAN_ROUTE' => 'Shorter than the route',
      'MOCKED' => 'Fake GPS detected',
      'GAPS' => 'Partly estimated',
      'FAR_FROM_JOB' => 'Ended far from the job',
      _ => flag,
    };

String tripStartLabel(Trip trip) => switch (trip.startKind) {
      'SHOP' => 'Shop',
      'PREVIOUS_JOB' => trip.startJobReference ?? 'Previous job',
      'OTHER' => 'Other location',
      _ => 'Unknown start',
    };

String tripStatusLabel(Trip trip) => switch (trip.status) {
      'ACTIVE' => 'Recording',
      'CANCELLED' => 'Cancelled',
      _ => switch (trip.endReason) {
          'ARRIVED' => 'Arrived',
          'GEOFENCE' => 'Arrived (at the customer)',
          'JOB_STARTED' => 'Ended at Start job',
          'REPLACED' => 'Ended by the next trip',
          'TIMEOUT' => 'Ended after 6 hours',
          _ => 'Ended',
        },
    };

/// "09:12" in India time. India has no daylight saving.
String indiaTime(DateTime instant) {
  final ist = instant.toUtc().add(const Duration(hours: 5, minutes: 30));
  return '${ist.hour.toString().padLeft(2, '0')}:${ist.minute.toString().padLeft(2, '0')}';
}

/// `YYYY-MM-DD` of [instant] in India time.
String indiaDate(DateTime instant) {
  final ist = instant.toUtc().add(const Duration(hours: 5, minutes: 30));
  final month = ist.month.toString().padLeft(2, '0');
  final day = ist.day.toString().padLeft(2, '0');
  return '${ist.year}-$month-$day';
}
