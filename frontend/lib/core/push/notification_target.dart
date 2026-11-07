import 'package:flutter/material.dart';

import '../../features/customer_home/presentation/repair_detail_screen.dart';
import '../../features/jobs/presentation/job_detail_screen.dart';
import '../../features/requests/presentation/request_detail_screen.dart';
import '../../router/app_router.dart';
import '../auth/roles.dart';

void openNotificationTarget({
  required String? role,
  required String relatedType,
  required String relatedId,
  required String reference,
}) {
  final nav = rootNavigatorKey.currentState;
  if (nav == null) return;
  if (Roles.normalize(role) == Roles.customer && reference.isNotEmpty) {
    nav.push(MaterialPageRoute(
      builder: (_) => RepairDetailScreen(referenceNumber: reference),
    ));
    return;
  }
  if (relatedType == 'REPAIR_REQUEST' && relatedId.isNotEmpty) {
    nav.push(MaterialPageRoute(
      builder: (_) => RequestDetailScreen(
        requestId: relatedId,
        onActionCompleted: () {},
      ),
    ));
    return;
  }
  if ((relatedType == 'JOB' || relatedType == 'APPOINTMENT') &&
      relatedId.isNotEmpty) {
    nav.push(MaterialPageRoute(
      builder: (_) => JobDetailScreen(jobId: relatedId),
    ));
  }
}
