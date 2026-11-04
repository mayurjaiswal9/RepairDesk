/// Decides when a place-suggestion request may go out.
///
/// Nothing is sent before 5 letters. After that, the next request waits until
/// 5 seconds have passed since the previous one.
class SuggestionSchedule {
  DateTime? lastSentAt;
  String lastSentText = '';

  static const gap = Duration(seconds: 5);
  static const minimumLetters = 5;

  /// How long to wait before sending [text]. Null means do not send.
  Duration? waitFor(String text, DateTime now) {
    final trimmed = text.trim();
    if (trimmed.length < minimumLetters || trimmed == lastSentText) return null;
    final sent = lastSentAt;
    if (sent == null) return Duration.zero;
    final elapsed = now.difference(sent);
    if (elapsed >= gap) return Duration.zero;
    return gap - elapsed;
  }

  void markSent(String text, DateTime now) {
    lastSentText = text.trim();
    lastSentAt = now;
  }

  void reset() {
    lastSentAt = null;
    lastSentText = '';
  }
}
