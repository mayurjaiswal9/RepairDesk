import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { toFileFormData } from './multipart';
import { AttachmentMeta } from '../models/request.models';
import { AppointmentSummary, JobDetail, Priority } from '../models/job.models';
import { JobSummary, PageResponse } from '../models/dashboard.models';

export interface JobListQuery {
  status?: string;
  active?: boolean;
  scheduled?: boolean;
  technicianId?: string;
  unassigned?: boolean;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class JobsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(query: JobListQuery = {}): Observable<PageResponse<JobSummary>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 20));
    if (query.status) params = params.set('status', query.status);
    if (query.active === true) params = params.set('active', 'true');
    if (query.scheduled === false) params = params.set('scheduled', 'false');
    if (query.scheduled === true) params = params.set('scheduled', 'true');
    if (query.technicianId) params = params.set('technicianId', query.technicianId);
    if (query.unassigned === true) params = params.set('unassigned', 'true');
    if (query.q) params = params.set('q', query.q);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<PageResponse<JobSummary>>(`${this.base}/jobs`, { params });
  }

  get(id: string): Observable<JobDetail> {
    return this.http.get<JobDetail>(`${this.base}/jobs/${id}`);
  }

  assign(id: string, technicianId: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/assign`, { technicianId });
  }

  unassign(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/unassign`, {});
  }

  start(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/start`, {});
  }

  awaitingParts(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/awaiting-parts`, {});
  }

  resume(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/resume`, {});
  }

  complete(id: string, resolutionSummary: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/complete`, { resolutionSummary });
  }

  cancel(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/jobs/${id}/cancel`, { reason });
  }

  setPriority(id: string, priority: Priority): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/priority`, { priority });
  }

  setWarranty(id: string, confirmed: boolean): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/warranty`, { confirmed });
  }

  appendNote(id: string, note: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${this.base}/jobs/${id}/notes`, { note });
  }

  listAppointments(jobId: string): Observable<AppointmentSummary[]> {
    return this.http.get<AppointmentSummary[]>(`${this.base}/jobs/${jobId}/appointments`);
  }

  listAttachments(jobId: string): Observable<AttachmentMeta[]> {
    return this.http.get<AttachmentMeta[]>(`${this.base}/jobs/${jobId}/attachments`);
  }

  uploadAttachment(jobId: string, file: File): Observable<AttachmentMeta> {
    return this.http.post<AttachmentMeta>(`${this.base}/jobs/${jobId}/attachments`, toFileFormData(file));
  }

  downloadAttachment(storageKey: string): Observable<Blob> {
    return this.http.get(`${this.base}/attachments/${encodeURIComponent(storageKey)}`, {
      responseType: 'blob',
    });
  }

  /** Preferred download by attachment id (avoids storage-key path issues). */
  downloadAttachmentById(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/attachments/by-id/${id}`, {
      responseType: 'blob',
    });
  }
}
