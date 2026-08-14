/** Shipped login contract (verified against AuthResponse / LoginRequest). */
export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  role: UserRole;
  mustChangePassword: boolean;
}

export type UserRole = 'ADMIN' | 'STAFF' | 'TECHNICIAN' | 'CUSTOMER';

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ResetPasswordRequest {
  username: string;
  newPassword: string;
}

export interface SessionUser {
  userId: string;
  username: string;
  role: UserRole;
  mustChangePassword: boolean;
  /** Display name from GET /me — optional until profile loads. */
  name?: string | null;
}

/** Standard API error envelope from API.md §3. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  errorCode?: string;
  message?: string;
  path?: string;
  correlationId?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}
