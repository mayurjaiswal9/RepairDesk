import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppointmentSummary } from '../models/job.models';
import {
  CreateAppointmentBody,
  RescheduleAppointmentBody,
} from '../models/scheduling.models';

@Injectable({ providedIn: 'root' })
export class AppointmentsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  create(jobId: string, body: CreateAppointmentBody): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${this.base}/jobs/${jobId}/appointments`, body);
  }

  reschedule(
    jobId: string,
    appointmentId: string,
    body: RescheduleAppointmentBody,
  ): Observable<AppointmentSummary> {
    return this.http.put<AppointmentSummary>(
      `${this.base}/jobs/${jobId}/appointments/${appointmentId}/reschedule`,
      body,
    );
  }

  cancel(jobId: string, appointmentId: string, reason: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/jobs/${jobId}/appointments/${appointmentId}`, {
      body: { reason },
    });
  }

  calendar(from: string, to: string): Observable<AppointmentSummary[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AppointmentSummary[]>(`${this.base}/appointments`, { params });
  }

  mySchedule(from: string, to: string): Observable<AppointmentSummary[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AppointmentSummary[]>(`${this.base}/me/schedule`, { params });
  }
}
