import { Injectable, computed, signal } from '@angular/core';
import { AuthResponse, SessionUser, UserRole } from '../models/auth.models';

const STORAGE_KEY = 'repairdesk.admin.session';

interface StoredSession {
  token: string;
  user: SessionUser;
}

/**
 * Session holder. Token lives in memory with sessionStorage as reload fallback
 * (README §6.1 / AP-01 Q1 recommendation ratified for V1).
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<SessionUser | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());
  readonly role = computed(() => this.userSignal()?.role ?? null);
  readonly mustChangePassword = computed(
    () => this.userSignal()?.mustChangePassword ?? false,
  );

  constructor() {
    this.restore();
  }

  establish(response: AuthResponse): void {
    const user: SessionUser = {
      userId: response.userId,
      username: response.username,
      role: response.role,
      mustChangePassword: response.mustChangePassword,
      name: null,
    };
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);
    this.persist({ token: response.token, user });
  }

  /** Merge profile fields from GET /me into the session (greeting, avatar). */
  patchProfile(profile: { name?: string | null; username?: string }): void {
    const current = this.userSignal();
    if (!current) return;
    const updated: SessionUser = {
      ...current,
      name: profile.name ?? current.name,
      username: profile.username ?? current.username,
    };
    this.userSignal.set(updated);
    const token = this.tokenSignal();
    if (token) this.persist({ token, user: updated });
  }

  /** Keep token for provisional session during forced password change. */
  clearMustChangePassword(): void {
    const current = this.userSignal();
    if (!current) return;
    const updated = { ...current, mustChangePassword: false };
    this.userSignal.set(updated);
    const token = this.tokenSignal();
    if (token) this.persist({ token, user: updated });
  }

  clear(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
  }

  /** Discard a refused customer token without establishing a panel session. */
  discardToken(): void {
    this.clear();
  }

  isInternalRole(role: UserRole | null | undefined): boolean {
    return role === 'ADMIN' || role === 'STAFF' || role === 'TECHNICIAN';
  }

  defaultLanding(role: UserRole | null | undefined = this.role()): string {
    if (role === 'TECHNICIAN') return '/jobs';
    return '/dashboard';
  }

  canAccessRoute(path: string, role: UserRole | null | undefined = this.role()): boolean {
    if (!role || role === 'CUSTOMER') return false;
    // /requests is reachable so the page can render its own forbidden state (AC-AP04-11).
    if (path.startsWith('/staff') || path.startsWith('/catalog') || path.startsWith('/settings')) {
      return role === 'ADMIN';
    }
    if (path.startsWith('/customers')) {
      return role === 'ADMIN' || role === 'STAFF';
    }
    return true;
  }

  private persist(session: StoredSession): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  private restore(): void {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw) as StoredSession;
      if (parsed?.token && parsed?.user?.role) {
        this.tokenSignal.set(parsed.token);
        this.userSignal.set(parsed.user);
      }
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }
}
