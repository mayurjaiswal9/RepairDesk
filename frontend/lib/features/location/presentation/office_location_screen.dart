import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/location_models.dart';
import '../../../core/location/maps_repository.dart';
import '../../../core/location/pin_picker_screen.dart';
import '../../../shared/theme/app_theme.dart';

/// Admin shop pin, geofence, and the attendance switches.
///
/// Every field is sent on save, so an untouched switch stays as it was loaded.
class OfficeLocationScreen extends ConsumerStatefulWidget {
  const OfficeLocationScreen({super.key});

  @override
  ConsumerState<OfficeLocationScreen> createState() => _OfficeLocationScreenState();
}

class _OfficeLocationScreenState extends ConsumerState<OfficeLocationScreen> {
  final _lat = TextEditingController();
  final _lng = TextEditingController();
  final _radius = TextEditingController();
  final _pingMinutes = TextEditingController();
  final _pingDistance = TextEditingController();
  final _autoEnd = TextEditingController();
  final _far = TextEditingController();
  final _rateTwo = TextEditingController();
  final _rateFour = TextEditingController();
  final _retention = TextEditingController();
  final _privacy = TextEditingController();
  final _travelPing = TextEditingController();
  final _onSitePing = TextEditingController();

  bool _loading = true;
  bool _saving = false;
  String? _error;
  bool _checkIn = false;
  bool _attendance = false;
  bool _tracking = false;
  bool _travel = false;
  bool _eta = false;
  bool _allowOutside = false;
  bool _strictTrip = true;
  bool _openMaps = true;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void dispose() {
    for (final controller in [
      _lat, _lng, _radius, _pingMinutes, _pingDistance, _autoEnd,
      _far, _rateTwo, _rateFour, _retention, _privacy, _travelPing, _onSitePing,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final settings = await ref.read(mapsRepositoryProvider).locationSettings();
      if (!mounted) return;
      _apply(settings);
      setState(() => _loading = false);
    } on AppException catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.userMessage;
      });
    }
  }

  void _apply(LocationSettings settings) {
    _lat.text = settings.shopLatitude?.toString() ?? '';
    _lng.text = settings.shopLongitude?.toString() ?? '';
    _radius.text = '${settings.geofenceRadiusM}';
    _pingMinutes.text = '${settings.pingIntervalMinutes}';
    _pingDistance.text = '${settings.pingDistanceM}';
    _autoEnd.text = settings.dayAutoEndTime;
    _far.text = '${settings.farFromAddressM}';
    _rateTwo.text = _rupees(settings.rateTwoWheelerPaise);
    _rateFour.text = _rupees(settings.rateFourWheelerPaise);
    _retention.text = '${settings.retentionDays}';
    _privacy.text = settings.privacyNoticeVersion;
    _checkIn = settings.checkInEnabled;
    _attendance = settings.attendanceEnabled;
    _tracking = settings.trackingEnabled;
    _travel = settings.travelEnabled;
    _eta = settings.etaEnabled;
    _allowOutside = settings.allowStartOutsideGeofence;
    _strictTrip = settings.strictTripMode;
    _openMaps = settings.tripOpenMaps;
    _travelPing.text = '${settings.travelPingSeconds}';
    _onSitePing.text = '${settings.onSitePingMinutes}';
  }

  String _rupees(int paise) {
    final rupees = paise / 100;
    return rupees == rupees.roundToDouble()
        ? rupees.toStringAsFixed(0)
        : rupees.toStringAsFixed(2);
  }

  int? _int(TextEditingController controller) => int.tryParse(controller.text.trim());

  int? _paise(TextEditingController controller) {
    final rupees = double.tryParse(controller.text.trim());
    if (rupees == null) return null;
    return (rupees * 100).round();
  }

  Future<void> _movePin() async {
    final picked = await Navigator.of(context).push<PinPick>(
      MaterialPageRoute(
        builder: (_) => PinPickerScreen(
          latitude: double.tryParse(_lat.text) ?? 19.0760,
          longitude: double.tryParse(_lng.text) ?? 72.8777,
        ),
      ),
    );
    if (picked == null || !mounted) return;
    setState(() {
      _lat.text = picked.latitude.toString();
      _lng.text = picked.longitude.toString();
    });
  }

  Future<void> _save() async {
    final latitude = double.tryParse(_lat.text.trim());
    final longitude = double.tryParse(_lng.text.trim());
    final radius = _int(_radius);
    final pingMinutes = _int(_pingMinutes);
    final pingDistance = _int(_pingDistance);
    final far = _int(_far);
    final two = _paise(_rateTwo);
    final four = _paise(_rateFour);
    final retention = _int(_retention);
    final clock = _autoEnd.text.trim();
    if (latitude == null ||
        longitude == null ||
        radius == null ||
        pingMinutes == null ||
        pingDistance == null ||
        far == null ||
        two == null ||
        four == null ||
        retention == null ||
        !RegExp(r'^\d{2}:\d{2}$').hasMatch(clock)) {
      _snack('Check the numbers. Auto end of day uses 20:00.');
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(mapsRepositoryProvider).saveLocationSettings(LocationSettings(
            shopLatitude: latitude,
            shopLongitude: longitude,
            geofenceRadiusM: radius,
            checkInEnabled: _checkIn,
            attendanceEnabled: _attendance,
            trackingEnabled: _tracking,
            travelEnabled: _travel,
            etaEnabled: _eta,
            pingIntervalMinutes: pingMinutes,
            pingDistanceM: pingDistance,
            dayAutoEndTime: '$clock:00',
            allowStartOutsideGeofence: _allowOutside,
            farFromAddressM: far,
            rateTwoWheelerPaise: two,
            rateFourWheelerPaise: four,
            retentionDays: retention,
            privacyNoticeVersion: _privacy.text.trim().isEmpty ? '1' : _privacy.text.trim(),
            strictTripMode: _strictTrip,
            tripOpenMaps: _openMaps,
            travelPingSeconds: _int(_travelPing) ?? 120,
            onSitePingMinutes: _int(_onSitePing) ?? 30,
          ));
      if (!mounted) return;
      _snack('Office location saved');
    } on AppException catch (error) {
      if (!mounted) return;
      _snack(error.userMessage);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _snack(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Office location')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(AppTheme.spaceL),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: AppTheme.spaceM),
                        FilledButton(onPressed: _load, child: const Text('Retry')),
                      ],
                    ),
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(AppTheme.spaceM),
                  children: [
                    const Text(
                      'The shop pin is where travel time starts until a technician shares a position. '
                      'Leave the switches off unless you intend to turn that behaviour on. '
                      'Technicians start the day in the app. The day ends automatically at 8:00 PM.',
                      style: TextStyle(color: AppTheme.textSecondary),
                    ),
                    const SizedBox(height: AppTheme.spaceM),
                    _field('Latitude', _lat, decimal: true),
                    _field('Longitude', _lng, decimal: true),
                    _field('Geofence radius (m)', _radius),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: OutlinedButton.icon(
                        onPressed: _movePin,
                        icon: const Icon(Icons.place_outlined),
                        label: const Text('Move the pin'),
                      ),
                    ),
                    const SizedBox(height: AppTheme.spaceL),
                    Text('Features', style: Theme.of(context).textTheme.titleMedium),
                    _switch('Check-in', _checkIn, (value) => setState(() => _checkIn = value)),
                    _switch('Attendance', _attendance,
                        (value) => setState(() => _attendance = value)),
                    _switch('Live location on a job', _tracking,
                        (value) => setState(() => _tracking = value)),
                    _switch('Record job trips (km)', _travel, (value) {
                      setState(() {
                        _travel = value;
                        if (!value) _strictTrip = false;
                      });
                    }),
                    if (_travel)
                      const Text(
                        'Turn on after technicians install the latest app.',
                        style: TextStyle(color: AppTheme.textSecondary, fontSize: 12),
                      ),
                    _switch('Strict mode — no trip, no km', _strictTrip,
                        _travel ? (value) => setState(() => _strictTrip = value) : null),
                    _switch('Open Google Maps on Start travel', _openMaps,
                        (value) => setState(() => _openMaps = value)),
                    _switch('Customer ETA', _eta, (value) => setState(() => _eta = value)),
                    _switch(
                      'Allow start outside the office geofence',
                      _allowOutside,
                      (value) => setState(() => _allowOutside = value),
                    ),
                    const SizedBox(height: AppTheme.spaceM),
                    _field('Upload while travelling (seconds)', _travelPing),
                    _field('Update while on site (minutes)', _onSitePing),
                    _field('Ping every (minutes)', _pingMinutes),
                    _field('Or after moving (m)', _pingDistance),
                    _field('Auto end of day', _autoEnd, hint: '20:00'),
                    _field('Far from address (m)', _far),
                    _field('Two-wheeler rate (₹ / km)', _rateTwo, decimal: true),
                    _field('Four-wheeler rate (₹ / km)', _rateFour, decimal: true),
                    _field('Keep location history (days)', _retention),
                    _field('Privacy notice version', _privacy, text: true),
                    const SizedBox(height: AppTheme.spaceL),
                    FilledButton(
                      onPressed: _saving ? null : _save,
                      child: Text(_saving ? 'Saving…' : 'Save office location'),
                    ),
                  ],
                ),
    );
  }

  Widget _field(
    String label,
    TextEditingController controller, {
    bool decimal = false,
    bool text = false,
    String? hint,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: TextField(
        controller: controller,
        keyboardType: text
            ? TextInputType.text
            : TextInputType.numberWithOptions(decimal: decimal, signed: decimal),
        inputFormatters: text || decimal
            ? null
            : [FilteringTextInputFormatter.digitsOnly],
        decoration: InputDecoration(
          labelText: label,
          hintText: hint,
          border: const OutlineInputBorder(),
        ),
      ),
    );
  }

  Widget _switch(String label, bool value, ValueChanged<bool>? onChanged) {
    return SwitchListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      value: value,
      onChanged: onChanged,
    );
  }
}
