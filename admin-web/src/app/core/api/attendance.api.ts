import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AttendanceRow {
  technicianId: string;
  technicianName: string;
  workDate: string;
  status: 'NOT_STARTED' | 'ON_DUTY' | 'ENDED' | 'AUTO_ENDED' | string;
  startAt?: string | null;
  endAt?: string | null;
  startFlag?: string | null;
  endReason?: string | null;
  durationMinutes?: number | null;
}

export interface AttendanceConfig {
  attendanceEnabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class AttendanceApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  config(): Observable<AttendanceConfig> {
    return this.http.get<AttendanceConfig>(`${this.base}/location/config`);
  }

  list(from: string, to: string, technicianId?: string | null): Observable<AttendanceRow[]> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (technicianId) params = params.set('technicianId', technicianId);
    return this.http.get<AttendanceRow[]>(`${this.base}/attendance`, { params });
  }

  export(from: string, to: string, technicianId?: string | null): Observable<Blob> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (technicianId) params = params.set('technicianId', technicianId);
    return this.http.get(`${this.base}/attendance/export`, { params, responseType: 'blob' });
  }
}
