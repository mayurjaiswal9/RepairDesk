import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SchedulingConfig, WorkingHoursDay } from '../models/scheduling.models';

export interface BusinessSettings {
  id?: number;
  businessName?: string | null;
  businessAddress?: string | null;
  businessPhone?: string | null;
  businessEmail?: string | null;
  slotDurationMinutes: number;
  bookingHorizonDays: number;
  updatedAt?: string | null;
  version?: number;
}

export interface UpdateSettingsBody {
  businessName?: string | null;
  businessAddress?: string | null;
  businessPhone?: string | null;
  businessEmail?: string | null;
  slotDurationMinutes?: number;
  bookingHorizonDays?: number;
}

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getSchedulingConfig(): Observable<SchedulingConfig> {
    return this.http.get<SchedulingConfig>(`${this.base}/settings/scheduling-config`);
  }

  getSettings(): Observable<BusinessSettings> {
    return this.http.get<BusinessSettings>(`${this.base}/settings`);
  }

  updateSettings(body: UpdateSettingsBody): Observable<BusinessSettings> {
    return this.http.put<BusinessSettings>(`${this.base}/settings`, body);
  }

  updateWorkingHours(entries: WorkingHoursDay[]): Observable<SchedulingConfig> {
    return this.http.put<SchedulingConfig>(`${this.base}/settings/working-hours`, entries);
  }
}
