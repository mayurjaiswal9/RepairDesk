import 'package:flutter/material.dart';

import '../../core/time/india_date.dart';
import '../theme/app_theme.dart';

class DateRangeBar extends StatelessWidget {
  const DateRangeBar({
    super.key,
    required this.from,
    required this.to,
    required this.allDates,
    required this.onFrom,
    required this.onTo,
    required this.onToday,
    required this.onAll,
  });

  final String? from;
  final String? to;
  final bool allDates;
  final ValueChanged<String> onFrom;
  final ValueChanged<String> onTo;
  final VoidCallback onToday;
  final VoidCallback onAll;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppTheme.spaceM, AppTheme.spaceS, AppTheme.spaceM, 0),
      child: Wrap(
        spacing: AppTheme.spaceS,
        runSpacing: AppTheme.spaceS,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          _DateButton(
            label: from == null ? 'From' : 'From  ${formatIndiaDate(from!)}',
            onPressed: () => _pick(context, from, onFrom),
          ),
          _DateButton(
            label: to == null ? 'To' : 'To  ${formatIndiaDate(to!)}',
            onPressed: () => _pick(context, to ?? from, onTo),
          ),
          ActionChip(label: const Text('Today'), onPressed: onToday),
          ActionChip(
            label: const Text('All dates'),
            onPressed: onAll,
            backgroundColor: allDates ? AppTheme.primarySoft : null,
          ),
        ],
      ),
    );
  }

  Future<void> _pick(
    BuildContext context,
    String? current,
    ValueChanged<String> onPicked,
  ) async {
    final initial = current == null ? DateTime.now() : indiaDateValue(current);
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2024),
      lastDate: DateTime(2030, 12, 31),
    );
    if (picked != null) onPicked(isoFromDate(picked));
  }
}

class _DateButton extends StatelessWidget {
  const _DateButton({required this.label, required this.onPressed});
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: onPressed,
      child: Text(label),
    );
  }
}
