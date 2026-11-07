import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/push/firebase_options.dart';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  if (!FirebasePushOptions.configured) return;
  await Firebase.initializeApp(options: FirebasePushOptions.options);
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  if (FirebasePushOptions.configured) {
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
  }
  runApp(
    const ProviderScope(
      child: RepairDeskApp(),
    ),
  );
}
