import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardCounters } from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getCounters(): Observable<DashboardCounters> {
    return this.http.get<DashboardCounters>(`${this.base}/dashboard/counters`);
  }
}
