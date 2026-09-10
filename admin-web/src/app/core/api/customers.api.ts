import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CustomerHistory, UserProfile } from '../models/dashboard.models';

export interface CreateCustomerBody {
  name: string;
  mobile: string;
  username?: string;
  password?: string;
}

export interface CreateCustomerResponse {
  id: string;
  username: string;
  name: string;
  mobile: string;
  role: string;
  status: string;
  mustChangePassword: boolean;
  generatedPassword?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CustomersApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Bare array — omit `q` entirely when empty (Bugs.md #9). */
  list(q?: string): Observable<UserProfile[]> {
    let params = new HttpParams();
    const trimmed = q?.trim();
    if (trimmed) params = params.set('q', trimmed);
    return this.http.get<UserProfile[]>(`${this.base}/customers`, { params });
  }

  get(id: string): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}/customers/${id}`);
  }

  history(id: string): Observable<CustomerHistory> {
    return this.http.get<CustomerHistory>(`${this.base}/customers/${id}/history`);
  }

  create(body: CreateCustomerBody): Observable<CreateCustomerResponse> {
    return this.http.post<CreateCustomerResponse>(`${this.base}/customers`, body);
  }
}
