export type RequestStatus =
  | 'PENDING_REVIEW'
  | 'INFORMATION_REQUIRED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export type WarrantyStatus = 'IN_WARRANTY' | 'OUT_OF_WARRANTY' | 'UNKNOWN';

export interface ServiceLocation {
  addressLine?: string | null;
  landmark?: string | null;
  city?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  pinSource?: 'GEOCODED' | 'MANUAL_PIN' | 'CHECK_IN' | 'CUSTOMER_LINK' | null;
}

/** Minimal shape for shared approve/reject/info dialogs. */
export interface DecisionTarget {
  id: string;
  referenceNumber: string;
  contactName?: string | null;
  customerUsername?: string | null;
}

export interface RepairRequestDetail extends DecisionTarget {
  customerId: string;
  customerUsername: string;
  deviceCategoryId: string;
  deviceCategoryName: string;
  contactName: string;
  contactMobile?: string | null;
  contactEmail?: string | null;
  deviceBrand?: string | null;
  deviceModel?: string | null;
  deviceSerial?: string | null;
  purchaseDate?: string | null;
  warrantyStatus?: WarrantyStatus | null;
  issueDescription: string;
  preferredDate?: string | null;
  preferredTime?: string | null;
  status: RequestStatus;
  priority: Priority;
  rejectionReasonCode?: string | null;
  customerFacingMessage?: string | null;
  infoRequestedMessage?: string | null;
  customerInfoResponse?: string | null;
  submittedAt: string;
  updatedAt: string;
  version: number;
  jobId?: string | null;
  jobStatus?: string | null;
  assignedTechnicianName?: string | null;
  serviceLocation?: ServiceLocation | null;
}

export interface AttachmentMeta {
  id: string;
  ownerType: string;
  ownerId: string;
  contentType: string;
  fileSizeBytes: number;
  originalFilename: string;
  storageKey: string;
  createdAt: string;
}

export interface RepairRequestSummary extends DecisionTarget {
  id: string;
  referenceNumber: string;
  customerUsername: string;
  deviceCategoryName: string;
  contactName: string;
  contactMobile?: string | null;
  deviceBrand?: string | null;
  deviceModel?: string | null;
  status: RequestStatus;
  priority: Priority;
  submittedAt: string;
  updatedAt: string;
  jobId?: string | null;
  jobStatus?: string | null;
  assignedTechnicianName?: string | null;
}

export interface ApproveRequestBody {
  priority?: Priority;
  note?: string;
}

export interface RejectRequestBody {
  reasonCode: string;
  customerMessage: string;
}

export interface InfoRequestBody {
  message: string;
}

export interface DeviceCategory {
  id: string;
  name: string;
  description?: string | null;
  archived?: boolean;
  brandMode?: 'FREE_TEXT' | 'CONFIGURED' | string;
  activeBrandCount?: number;
  /** Legacy alias — prefer `!archived`. */
  active?: boolean;
}

export interface DeviceBrand {
  id: string;
  deviceCategoryId: string;
  name: string;
  archived: boolean;
}

export interface DeviceCategoryRequest {
  name: string;
  description?: string | null;
  brandMode?: string;
}

export interface DeviceBrandRequest {
  name: string;
}

/** Interim reason codes (APB-006 / Tasks decision 11 + mobile value). */
export const REJECT_REASON_CODES: Array<{ value: string; label: string }> = [
  { value: 'UNABLE_TO_SERVICE', label: 'Unable to service' },
  { value: 'UNSUPPORTED_DEVICE', label: 'Unsupported device' },
  { value: 'DUPLICATE', label: 'Duplicate request' },
  { value: 'INSUFFICIENT_INFORMATION', label: 'Insufficient information' },
  { value: 'NON_REPAIRABLE', label: 'Non-repairable' },
  { value: 'SPAM_INVALID', label: 'Spam / invalid' },
];

export const STATUS_FILTERS: Array<{ key: string; label: string; status?: RequestStatus }> = [
  { key: 'all', label: 'All' },
  { key: 'pending', label: 'Pending', status: 'PENDING_REVIEW' },
  { key: 'info', label: 'Needs info', status: 'INFORMATION_REQUIRED' },
  { key: 'approved', label: 'Approved', status: 'APPROVED' },
  { key: 'rejected', label: 'Rejected', status: 'REJECTED' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED' },
];

export function statusChipClass(status: string): string {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'chip-warning';
    case 'INFORMATION_REQUIRED':
      return 'chip-secondary';
    case 'APPROVED':
      return 'chip-success';
    case 'REJECTED':
    case 'CANCELLED':
      return 'chip-error';
    case 'CREATED':
      return 'chip-primary-light';
    case 'ASSIGNED':
    case 'SCHEDULED':
      return 'chip-primary';
    case 'IN_PROGRESS':
      return 'chip-secondary';
    case 'AWAITING_PARTS':
      return 'chip-warning';
    case 'COMPLETED':
      return 'chip-success';
    default:
      return 'chip-neutral';
  }
}

export function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING_REVIEW: 'Pending review',
    INFORMATION_REQUIRED: 'Needs info',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    CANCELLED: 'Cancelled',
    CREATED: 'New',
    ASSIGNED: 'Assigned',
    SCHEDULED: 'Scheduled',
    IN_PROGRESS: 'In progress',
    AWAITING_PARTS: 'Awaiting parts',
    COMPLETED: 'Completed',
  };
  return map[status] ?? status.replaceAll('_', ' ');
}

export function warrantyLabel(status: string | null | undefined): string {
  switch (status) {
    case 'IN_WARRANTY':
      return 'In warranty';
    case 'OUT_OF_WARRANTY':
      return 'Out of warranty';
    case 'UNKNOWN':
      return 'Unknown';
    default:
      return '—';
  }
}

export function formatAbsolute(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatDateOnly(iso: string | null | undefined): string {
  if (!iso) return '—';
  // LocalDate arrives as YYYY-MM-DD
  const date = new Date(iso.length === 10 ? `${iso}T12:00:00` : iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export function formatTimeOnly(time: string | null | undefined): string {
  if (!time) return '—';
  const [h, m] = time.split(':').map(Number);
  if (Number.isNaN(h)) return time;
  const d = new Date();
  d.setHours(h, m || 0, 0, 0);
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export function maskSerial(serial: string): string {
  const cleaned = serial.trim();
  if (cleaned.length <= 4) return cleaned;
  return `${'•'.repeat(Math.min(cleaned.length - 4, 12))} ${cleaned.slice(-4)}`;
}

export function formatSubmittedAt(iso: string | null | undefined): { relative: string; absolute: string } {
  if (!iso) return { relative: '—', absolute: '—' };
  const date = new Date(iso);
  if (Number.isNaN(date.getTime()) || date.getTime() === 0) {
    return { relative: '—', absolute: '—' };
  }
  const absolute = date.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const diffMs = Date.now() - date.getTime();
  const dayMs = 86_400_000;
  if (diffMs < dayMs) {
    const h = Math.max(1, Math.round(diffMs / 3_600_000));
    return { relative: h === 1 ? '1h ago' : `${h}h ago`, absolute };
  }
  if (diffMs < 7 * dayMs) {
    const d = Math.round(diffMs / dayMs);
    return { relative: d === 1 ? '1d ago' : `${d}d ago`, absolute };
  }
  return { relative: absolute, absolute };
}
