import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../shared/theme/app_theme.dart';
import '../error/app_exception.dart';
import 'location_service.dart';
import 'maps_repository.dart';
import 'suggestion_schedule.dart';

/// Result of the pin picker.
class PinPick {
  const PinPick({
    required this.latitude,
    required this.longitude,
    required this.moved,
    required this.pinSource,
    this.addressLine,
  });

  final double latitude;
  final double longitude;
  final bool moved;

  /// `GEOCODED`, `MANUAL_PIN`, or `CUSTOMER_LINK`.
  final String pinSource;
  final String? addressLine;

  bool get geocoded => pinSource == 'GEOCODED';
}

/// Full-screen map. The pin stays in the centre and the map moves under it.
class PinPickerScreen extends ConsumerStatefulWidget {
  const PinPickerScreen({
    super.key,
    required this.latitude,
    required this.longitude,
    this.initialIsGeocode = false,
  });

  final double latitude;
  final double longitude;

  /// True when [latitude] and [longitude] already came from a geocode.
  final bool initialIsGeocode;

  @override
  ConsumerState<PinPickerScreen> createState() => _PinPickerScreenState();
}

class _PinPickerScreenState extends ConsumerState<PinPickerScreen> {
  final _location = LocationService();
  final _searchCtrl = TextEditingController();
  final _pasteCtrl = TextEditingController();
  final _schedule = SuggestionSchedule();
  GoogleMapController? _map;
  late LatLng _centre = LatLng(widget.latitude, widget.longitude);
  late LatLng _anchor = _centre;
  late String _pinSource = widget.initialIsGeocode ? 'GEOCODED' : 'MANUAL_PIN';
  String? _addressLine;
  bool _moved = false;
  bool _locating = false;
  bool _searching = false;
  bool _readingLink = false;
  bool _suppressMove = false;
  bool _ignoreSearchChange = false;
  String? _status;
  List<PlaceSuggestion> _suggestions = const [];
  Timer? _suggestTimer;
  String _session = _newSession();

  @override
  void dispose() {
    _suggestTimer?.cancel();
    _searchCtrl.dispose();
    _pasteCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Place the pin')),
      body: Stack(
        children: [
          GoogleMap(
            initialCameraPosition: CameraPosition(target: _centre, zoom: 16),
            myLocationButtonEnabled: false,
            myLocationEnabled: true,
            zoomControlsEnabled: false,
            onMapCreated: (controller) => _map = controller,
            onCameraMove: (position) {
              _centre = position.target;
              if (_suppressMove) return;
              final movedLat = (position.target.latitude - _anchor.latitude).abs();
              final movedLng = (position.target.longitude - _anchor.longitude).abs();
              if (movedLat > 0.0002 || movedLng > 0.0002) {
                _moved = true;
                _pinSource = 'MANUAL_PIN';
              }
            },
            onCameraIdle: () {
              if (_suppressMove) _suppressMove = false;
            },
          ),
          const IgnorePointer(
            child: Center(
              child: Padding(
                padding: EdgeInsets.only(bottom: 36),
                child: Icon(Icons.location_on, size: 42, color: AppTheme.brandAccent),
              ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            top: 12,
            child: Material(
              elevation: 3,
              borderRadius: BorderRadius.circular(12),
              color: AppTheme.surface,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: _searchCtrl,
                    textInputAction: TextInputAction.search,
                    onChanged: _onSearchChanged,
                    onSubmitted: (_) => _search(),
                    decoration: InputDecoration(
                      hintText: 'Search a place, area, or PIN code',
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      suffixIcon: IconButton(
                        tooltip: 'Search',
                        onPressed: _searching ? null : _search,
                        icon: _searching
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.search),
                      ),
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Suggestions start after 5 letters, then every 5 seconds.',
                        style: TextStyle(color: AppTheme.textSecondary, fontSize: 12),
                      ),
                    ),
                  ),
                  if (_suggestions.isNotEmpty)
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 180),
                      child: ListView.separated(
                        shrinkWrap: true,
                        itemCount: _suggestions.length,
                        separatorBuilder: (context, index) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          final item = _suggestions[index];
                          return ListTile(
                            dense: true,
                            title: Text(item.primaryText),
                            subtitle: item.secondaryText == null || item.secondaryText!.isEmpty
                                ? null
                                : Text(item.secondaryText!),
                            onTap: () => _choose(item),
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 24,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_status != null)
                  Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _status!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
                    ),
                  ),
                Material(
                  elevation: 2,
                  borderRadius: BorderRadius.circular(12),
                  color: AppTheme.surface,
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        TextField(
                          controller: _pasteCtrl,
                          minLines: 1,
                          maxLines: 2,
                          decoration: const InputDecoration(
                            labelText: "Customer's location",
                            hintText: 'Paste the Maps link the customer sent',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton(
                          onPressed: _readingLink ? null : _useCustomerLink,
                          child: Text(_readingLink ? 'Reading…' : "Use customer's pin"),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _locating ? null : _useMyLocation,
                  icon: _locating
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.my_location),
                  label: const Text('Use my location'),
                ),
                const SizedBox(height: 8),
                FilledButton(
                  onPressed: () => Navigator.pop(
                    context,
                    PinPick(
                      latitude: _centre.latitude,
                      longitude: _centre.longitude,
                      moved: _moved,
                      pinSource: _moved ? 'MANUAL_PIN' : _pinSource,
                      addressLine: _addressLine,
                    ),
                  ),
                  child: const Text('Use this pin'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _onSearchChanged(String value) {
    if (_ignoreSearchChange) return;
    final text = value.trim();
    if (text.length < SuggestionSchedule.minimumLetters) {
      _suggestTimer?.cancel();
      if (_suggestions.isNotEmpty) setState(() => _suggestions = const []);
      return;
    }
    _armSuggest();
  }

  void _armSuggest() {
    _suggestTimer?.cancel();
    final wait = _schedule.waitFor(_searchCtrl.text, DateTime.now());
    if (wait == null) return;
    _suggestTimer = Timer(wait, _sendSuggest);
  }

  Future<void> _sendSuggest() async {
    final text = _searchCtrl.text.trim();
    final wait = _schedule.waitFor(text, DateTime.now());
    if (wait == null) return;
    if (wait > Duration.zero) {
      _armSuggest();
      return;
    }
    _schedule.markSent(text, DateTime.now());
    setState(() => _searching = true);
    try {
      final list = await ref.read(mapsRepositoryProvider).suggest(
            input: text,
            sessionToken: _session,
            latitude: _centre.latitude,
            longitude: _centre.longitude,
          );
      if (!mounted) return;
      if (_searchCtrl.text.trim() != text) {
        setState(() => _searching = false);
        _armSuggest();
        return;
      }
      setState(() {
        _searching = false;
        _suggestions = list;
        if (list.isEmpty) _status = 'No places found. Drop the pin, or paste a Maps link.';
      });
    } on AppException catch (e) {
      if (!mounted) return;
      _suggestTimer?.cancel();
      setState(() {
        _searching = false;
        _suggestions = const [];
        _status = e.userMessage;
      });
    }
  }

  Future<void> _choose(PlaceSuggestion item) async {
    FocusScope.of(context).unfocus();
    setState(() {
      _searching = true;
      _suggestions = const [];
      _status = 'Opening that place…';
    });
    try {
      final hit = await ref.read(mapsRepositoryProvider).selectPlace(
            placeId: item.placeId,
            sessionToken: _session,
          );
      if (!mounted) return;
      await _moveTo(
        LatLng(hit.latitude, hit.longitude),
        pinSource: 'GEOCODED',
        address: hit.formattedAddress ?? item.primaryText,
        status: hit.formattedAddress ?? item.primaryText,
      );
      _session = _newSession();
      _schedule.reset();
      _ignoreSearchChange = true;
      _searchCtrl.text = item.primaryText;
      _ignoreSearchChange = false;
    } on AppException catch (e) {
      if (!mounted) return;
      setState(() {
        _searching = false;
        _status = e.userMessage;
      });
    }
  }

  Future<void> _search() async {
    final query = _searchCtrl.text.trim();
    if (query.isEmpty) {
      setState(() => _status = 'Type a place name, area, or PIN code.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _searching = true;
      _suggestions = const [];
      _status = 'Searching…';
    });
    try {
      final hit = await ref.read(mapsRepositoryProvider).geocode(query);
      if (!mounted) return;
      await _moveTo(
        LatLng(hit.latitude, hit.longitude),
        pinSource: 'GEOCODED',
        address: hit.formattedAddress ?? query,
        status: hit.formattedAddress ?? 'Pin moved to the search result. Drag to adjust.',
      );
    } on AppException catch (e) {
      if (!mounted) return;
      setState(() {
        _searching = false;
        _status = e.userMessage;
      });
    }
  }

  Future<void> _useCustomerLink() async {
    final text = _pasteCtrl.text.trim();
    if (text.isEmpty) {
      setState(() => _status = 'Paste the Maps link the customer sent.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _readingLink = true;
      _status = 'Reading the customer’s pin…';
    });
    try {
      final hit = await ref.read(mapsRepositoryProvider).customerPin(text);
      if (!mounted) return;
      await _moveTo(
        LatLng(hit.latitude, hit.longitude),
        pinSource: 'CUSTOMER_LINK',
        address: hit.formattedAddress,
        status: 'Pin placed from the customer’s link. Press Use this pin to keep it.',
      );
      _pasteCtrl.clear();
    } on AppException catch (e) {
      if (!mounted) return;
      setState(() {
        _readingLink = false;
        _status = e.userMessage;
      });
    }
  }

  Future<void> _moveTo(
    LatLng target, {
    required String pinSource,
    required String? address,
    required String status,
  }) async {
    _suppressMove = true;
    await _map?.animateCamera(CameraUpdate.newLatLngZoom(target, 16));
    if (!mounted) return;
    setState(() {
      _centre = target;
      _anchor = target;
      _moved = false;
      _pinSource = pinSource;
      _addressLine = address;
      _searching = false;
      _readingLink = false;
      _suggestions = const [];
      _status = status;
    });
  }

  Future<void> _useMyLocation() async {
    setState(() {
      _locating = true;
      _status = 'Getting your location…';
    });
    final fix = await _location.bestFix(timeout: const Duration(seconds: 12));
    if (!mounted) return;
    if (fix == null) {
      setState(() {
        _locating = false;
        _status = 'Location is off or not allowed. You can still drag the map.';
      });
      return;
    }
    final target = LatLng(fix.latitude, fix.longitude);
    _suppressMove = true;
    await _map?.animateCamera(CameraUpdate.newLatLngZoom(target, 17));
    if (!mounted) return;
    setState(() {
      _centre = target;
      _anchor = target;
      _moved = true;
      _pinSource = 'MANUAL_PIN';
      _locating = false;
      _status = 'Accurate to about ${fix.accuracyM.round()} m';
    });
  }
}

String _newSession() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-${hex.substring(20)}';
}
