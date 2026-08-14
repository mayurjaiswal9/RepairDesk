import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserProfile } from '../models/dashboard.models';

export interface CreateUserBody {
  username: string;
  name: string;
  mobile: string;
  password: string;
  role: string;
}

export interface UpdateUserBody {
  name: string;
  mobile: string;
  role: string;
}

const INTERNAL_ROLES = ['ADMIN', 'STAFF', 'TECHNICIAN'] as const;

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listByRole(role: string): Observable<UserProfile[]> {
    const params = new HttpParams().set('role', role);
    return this.http.get<UserProfile[]>(`${this.base}/users`, { params });
  }

  /** Three parallel role-scoped calls — never the unfiltered customer-heavy list. */
  listInternal(): Observable<UserProfile[]> {
    return forkJoin(INTERNAL_ROLES.map((role) => this.listByRole(role))).pipe(
      map((chunks) => chunks.flat()),
    );
  }

  create(body: CreateUserBody): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.base}/users`, body);
  }

  update(id: string, body: UpdateUserBody): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/users/${id}`, body);
  }

  activate(id: string): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.base}/users/${id}/activate`, {});
  }

  deactivate(id: string): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.base}/users/${id}/deactivate`, {});
  }

  resetPassword(id: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/users/${id}/reset-password`, { newPassword });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }
}
