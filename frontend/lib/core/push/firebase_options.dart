import 'package:firebase_core/firebase_core.dart';

/// Fill these from the Firebase console for the Android app
/// `com.acetelecom.repairdesk`, then set [configured] to true.
/// Until then the phone inbox still works and system alerts are skipped.
class FirebasePushOptions {
  static const configured = false;

  static const FirebaseOptions options = FirebaseOptions(
    apiKey: 'replace-me',
    appId: 'replace-me',
    messagingSenderId: 'replace-me',
    projectId: 'replace-me',
  );
}
