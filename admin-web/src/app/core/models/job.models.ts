import { ServiceLocation } from './request.models';

export type JobStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'AWAITING_PARTS'
  | 'COMPLETED'
  | 'CANCELLED';

export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export interface JobDetail {
  id: string;
  referenceNumber: string;
  requestId: string;
  customerId: string;
  customerUsername: string;
  customerName?: string | null;
  deviceCategoryId: string;
  deviceCategoryName: string;
  contactName?: string | null;
  contactMobile?: string | null;
  deviceBrand?: string | null;
  deviceModel?: string | null;
  deviceSerial?: string | null;
  purchaseDate?: string | null;
  warrantyConfirmed: boolean;
  issueDescription?: string | null;
  status: JobStatus;
  priority: Priority;
  assignedTechnicianId?: string | null;
  assignedTechnicianName?: string | null;
  currentAppointmentId?: string | null;
  resolutionSummary?: string | null;
  cancelReason?: string | null;
  internalNotes?: string | null;
  serviceLocation?: ServiceLocation | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AppointmentSummary {
  id: string;
  jobId: string;
  referenceNumber: string;
  technicianId: string;
  technicianName: string;
  customerName?: string | null;
  deviceCategoryName?: string | null;
  scheduledDate: string;
  startTime: string;
  endTime?: string | null;
  status: string;
  appointmentNotes?: string | null;
  createdAt: string;
  version: number;
}

export interface ParsedNote {
  text: string;
  at?: string | null;
}

/** Backend stores notes as a blob with `--- {Instant} ---` separators. */
export function parseInternalNotes(blob: string | null | undefined): ParsedNote[] {
  if (!blob?.trim()) return [];
  const parts = blob.split(/\n\n---\s*([^\n-]+?)\s*---\n/);
  // split yields: [firstText, ts1, text1, ts2, text2, ...]
  const notes: ParsedNote[] = [];
  if (parts[0]?.trim()) {
    notes.push({ text: parts[0].trim(), at: null });
  }
  for (let i = 1; i < parts.length; i += 2) {
    const at = parts[i]?.trim() || null;
    const text = parts[i + 1]?.trim() || '';
    if (text) notes.push({ text, at });
  }
  return notes;
}

export type StatusAction =
  | 'start'
  | 'awaiting-parts'
  | 'resume'
  | 'complete'
  | 'cancel';

export function statusActionsFor(
  status: JobStatus,
  canCancel: boolean,
): Array<{ key: StatusAction; label: string; primary?: boolean; danger?: boolean }> {
  const cancel = canCancel
    ? [{ key: 'cancel' as const, label: 'Cancel job', danger: true }]
    : [];
  switch (status) {
    case 'CREATED':
      return [...cancel];
    case 'ASSIGNED':
    case 'SCHEDULED':
      return [...cancel, { key: 'start', label: 'Start repair', primary: true }];
    case 'IN_PROGRESS':
      return [
        { key: 'awaiting-parts', label: 'Mark awaiting parts' },
        ...cancel,
        { key: 'complete', label: 'Mark complete', primary: true },
      ];
    case 'AWAITING_PARTS':
      return [
        { key: 'resume', label: 'Resume', primary: true },
        ...cancel,
        { key: 'complete', label: 'Mark complete' },
      ];
    default:
      return [];
  }
}

export function canAssignForStatus(status: JobStatus): boolean {
  return status === 'CREATED' || status === 'ASSIGNED';
}

export function canScheduleForStatus(status: JobStatus): boolean {
  return status === 'CREATED' || status === 'ASSIGNED' || status === 'SCHEDULED';
}
