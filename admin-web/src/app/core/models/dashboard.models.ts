/** Shipped dashboard counters (DashboardController — not API.md §17). */
export interface DashboardCounters {
  pendingReview: number;
  infoRequired: number;
  activeJobs: number;
  unscheduledJobs: number;
}

export interface UserProfile {
  id: string;
  username: string;
  mobile: string;
  name: string | null;
  role: string;
  status: string;
  mustChangePassword: boolean;
  /** Present when the API includes it (customer detail). */
  createdAt?: string | null;
  /** Present on GET /customers/{id}; null on list/staff payloads. */
  repairCount?: number | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface JobSummary {
  id: string;
  referenceNumber: string;
  status: string;
  priority?: string;
  contactName?: string;
  customerUsername?: string;
  customerName?: string | null;
  deviceCategoryName?: string;
  assignedTechnicianId?: string | null;
  assignedTechnicianName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CustomerHistory {
  customer: UserProfile;
  requests: import('./request.models').RepairRequestSummary[];
  jobs: JobSummary[];
}

export const JOB_STATUS_FILTERS: Array<{ key: string; label: string; status?: string }> = [
  { key: 'all', label: 'All' },
  { key: 'new', label: 'New', status: 'CREATED' },
  { key: 'assigned', label: 'Assigned', status: 'ASSIGNED' },
  { key: 'scheduled', label: 'Scheduled', status: 'SCHEDULED' },
  { key: 'in_progress', label: 'In progress', status: 'IN_PROGRESS' },
  { key: 'awaiting', label: 'Awaiting parts', status: 'AWAITING_PARTS' },
  { key: 'completed', label: 'Completed', status: 'COMPLETED' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED' },
];
