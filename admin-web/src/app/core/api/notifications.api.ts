import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type NotificationRelatedType = 'REPAIR_REQUEST' | 'JOB' | 'APPOINTMENT' | string;

export interface AppNotification {
  id: string;
  type: string;
  message: string;
  referenceNumber?: string | null;
  relatedType?: NotificationRelatedType | null;
  relatedEntityId?: string | null;
  read: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(`${this.base}/me/notifications`);
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/me/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/me/notifications/read-all`, {});
  }
}
