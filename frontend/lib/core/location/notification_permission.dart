import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android 13+ hides the trip notification until notifications are allowed.
/// Recording works either way, so a refusal is not an error.
class NotificationPermission {
  static const _channel = MethodChannel('com.acetelecom.repairdesk/notifications');

  static Future<bool> request() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return true;
    try {
      return await _channel.invokeMethod<bool>('requestPermission') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
