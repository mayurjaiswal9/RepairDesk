import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserProfile } from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class MeApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}/me`);
  }

  updateMobile(mobile: string): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/me`, { mobile });
  }

  unreadNotificationCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/me/notifications/unread-count`);
  }
}
