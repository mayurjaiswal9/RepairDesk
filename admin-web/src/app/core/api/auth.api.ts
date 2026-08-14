import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  ResetPasswordRequest,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/login`, body);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {});
  }

  resetPassword(body: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/reset-password`, body);
  }

  changePassword(body: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/me/change-password`, body);
  }
}
