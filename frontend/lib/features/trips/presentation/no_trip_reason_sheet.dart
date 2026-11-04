import 'package:flutter/material.dart';

import '../../../core/location/trip_models.dart';
import '../../../shared/theme/app_theme.dart';

/// Strict-mode sheet: why Start Repair is going ahead with no recorded trip.
Future<NoTripChoice?> showNoTripReasonSheet(BuildContext context) {
  return showModalBottomSheet<NoTripChoice>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => const _NoTripReasonSheet(),
  );
}

class _NoTripReasonSheet extends StatefulWidget {
  const _NoTripReasonSheet();

  @override
  State<_NoTripReasonSheet> createState() => _NoTripReasonSheetState();
}

class _NoTripReasonSheetState extends State<_NoTripReasonSheet> {
  NoTripReason? _reason;
  final _note = TextEditingController();

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  bool get _canStart {
    if (_reason == null) return false;
    if (_reason!.needsNote) return _note.text.trim().length >= 3;
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppTheme.spaceM,
        0,
        AppTheme.spaceM,
        AppTheme.spaceM + bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('No trip was recorded for this job. Why?',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppTheme.spaceS),
          const Text(
            'No km will be paid for this job.',
            style: TextStyle(color: AppTheme.textSecondary),
          ),
          const SizedBox(height: AppTheme.spaceS),
          for (final reason in NoTripReason.values)
            RadioListTile<NoTripReason>(
              contentPadding: EdgeInsets.zero,
              title: Text(reason.label),
              value: reason,
              groupValue: _reason,
              onChanged: (value) => setState(() => _reason = value),
            ),
          if (_reason?.needsNote == true) ...[
            TextField(
              controller: _note,
              maxLength: 500,
              minLines: 2,
              maxLines: 4,
              onChanged: (_) => setState(() {}),
              decoration: const InputDecoration(
                labelText: 'Tell the shop what happened',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: AppTheme.spaceS),
          ],
          Row(
            children: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancel'),
              ),
              const Spacer(),
              FilledButton(
                onPressed: _canStart
                    ? () => Navigator.pop(
                          context,
                          NoTripChoice(
                            _reason!,
                            _reason!.needsNote ? _note.text.trim() : null,
                          ),
                        )
                    : null,
                child: const Text('Start job'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
