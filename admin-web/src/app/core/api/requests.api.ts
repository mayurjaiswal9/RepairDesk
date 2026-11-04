import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { toFileFormData } from './multipart';
import { PageResponse } from '../models/dashboard.models';
import {
  ApproveRequestBody,
  AttachmentMeta,
  InfoRequestBody,
  RejectRequestBody,
  RepairRequestDetail,
  RepairRequestSummary,
} from '../models/request.models';

export interface CreateRequestBody {
  customerId: string;
  deviceCategoryId: string;
  contactName: string;
  contactMobile: string;
  contactEmail?: string;
  deviceBrand?: string;
  deviceModel?: string;
  issueDescription: string;
  preferredDate: string;
  preferredTime: string;
  approveOnCreate?: boolean;
  priority?: string;
  note?: string;
  intakeChannel?: string;
  serviceLocation?: {
    addressLine?: string;
    landmark?: string;
    city?: string;
    pincode?: string;
    latitude?: number;
    longitude?: number;
    pinSource?: 'GEOCODED' | 'MANUAL_PIN' | 'CUSTOMER_LINK';
  };
}

export interface RequestListQuery {
  status?: string;
  q?: string;
  deviceCategoryId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class RequestsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(query: RequestListQuery = {}): Observable<PageResponse<RepairRequestSummary>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 20));
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    if (query.deviceCategoryId) params = params.set('deviceCategoryId', query.deviceCategoryId);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<PageResponse<RepairRequestSummary>>(`${this.base}/requests`, { params });
  }

  get(id: string): Observable<RepairRequestDetail> {
    return this.http.get<RepairRequestDetail>(`${this.base}/requests/${id}`);
  }

  create(body: CreateRequestBody): Observable<RepairRequestDetail> {
    return this.http.post<RepairRequestDetail>(`${this.base}/requests`, body);
  }

  uploadAttachment(requestId: string, file: File): Observable<AttachmentMeta> {
    return this.http.post<AttachmentMeta>(
      `${this.base}/requests/${requestId}/attachments`,
      toFileFormData(file),
    );
  }

  listAttachments(requestId: string): Observable<AttachmentMeta[]> {
    return this.http.get<AttachmentMeta[]>(`${this.base}/requests/${requestId}/attachments`);
  }

  /** Authenticated binary download — never use as bare <img src>. */
  downloadAttachment(storageKey: string): Observable<Blob> {
    return this.http.get(`${this.base}/attachments/${encodeURIComponent(storageKey)}`, {
      responseType: 'blob',
    });
  }

  /** Preferred download by attachment id (correct Content-Type from API). */
  downloadAttachmentById(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/attachments/by-id/${id}`, {
      responseType: 'blob',
    });
  }

  approve(id: string, body: ApproveRequestBody = {}): Observable<unknown> {
    return this.http.post(`${this.base}/requests/${id}/approve`, body);
  }

  reject(id: string, body: RejectRequestBody): Observable<void> {
    return this.http.post<void>(`${this.base}/requests/${id}/reject`, body);
  }

  requestInfo(id: string, body: InfoRequestBody): Observable<void> {
    return this.http.post<void>(`${this.base}/requests/${id}/request-info`, body);
  }
}
