import 'dart:async';

import 'package:dio/dio.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/session_controller.dart';
import '../location/notification_permission.dart';
import '../network/api_client.dart';
import 'firebase_options.dart';
import 'notification_target.dart';

final pushRegistrarProvider = Provider<PushRegistrar>((ref) {
  return PushRegistrar(ref.watch(apiClientProvider), () {
    return ref.read(sessionControllerProvider).role;
  });
});

class PushRegistrar {
  PushRegistrar(this._dio, this._role);
  final Dio _dio;
  final String? Function() _role;
  String? _token;
  bool _started = false;
  bool _registering = false;
  StreamSubscription<String>? _refresh;

  Future<void> register() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    if (_registering || _started) return;
    _registering = true;
    try {
      final allowed = await NotificationPermission.request();
      if (!FirebasePushOptions.configured || !allowed) return;
      _started = true;
      await Firebase.initializeApp(options: FirebasePushOptions.options);
      FirebaseMessaging.onMessageOpenedApp.listen(_opened);
      final initial = await FirebaseMessaging.instance.getInitialMessage();
      if (initial != null) _opened(initial);
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null && token.isNotEmpty) {
        _token = token;
        await _post(token);
      }
      _refresh = FirebaseMessaging.instance.onTokenRefresh.listen((next) async {
        _token = next;
        await _post(next);
      });
    } catch (_) {
      _started = false;
    } finally {
      _registering = false;
    }
  }

  Future<void> unregister() async {
    final token = _token;
    _token = null;
    await _refresh?.cancel();
    _refresh = null;
    _started = false;
    _registering = false;
    if (token == null) return;
    try {
      await _dio.delete<void>('/me/devices', data: {
        'token': token,
        'platform': 'ANDROID',
      });
    } catch (_) {
      // The session is ending either way.
    }
  }

  Future<void> _post(String token) async {
    await _dio.post<void>('/me/devices', data: {
      'token': token,
      'platform': 'ANDROID',
    });
  }

  void _opened(RemoteMessage message) {
    final data = message.data;
    openNotificationTarget(
      role: _role(),
      relatedType: data['relatedType']?.toString() ?? '',
      relatedId: data['relatedId']?.toString() ?? '',
      reference: data['reference']?.toString() ?? '',
    );
  }
}
