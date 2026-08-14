import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter } from 'rxjs';
import { AuthApi } from '../core/api/auth.api';
import { MeApi } from '../core/api/me.api';
import { SessionService } from '../core/auth/session.service';
import { UnreadBadgeService } from '../core/notifications/unread-badge.service';
import { NavGroupDef, isNavActive, navGroupsForRole } from '../core/nav/nav.config';
import { UserRole } from '../core/models/auth.models';

const RAIL_KEY = 'repairdesk.admin.sidebarCollapsed';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly session = inject(SessionService);
  private readonly meApi = inject(MeApi);
  private readonly authApi = inject(AuthApi);
  private readonly badge = inject(UnreadBadgeService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly user = this.session.user;
  readonly role = this.session.role;
  readonly collapsed = signal(this.readCollapsed());
  readonly unreadCount = this.badge.count;
  readonly currentUrl = signal(this.router.url);

  readonly groups = computed(() => navGroupsForRole(this.role()));

  readonly pageTitle = computed(() => {
    const url = this.currentUrl().split('?')[0];
    for (const g of this.groups()) {
      for (const item of g.items) {
        if (isNavActive(url, item.path)) return item.label;
      }
    }
    if (url.startsWith('/account')) return 'My account';
    if (url.startsWith('/notifications')) return 'Notifications';
    if (url === '/403') return 'Forbidden';
    return 'RepairDesk';
  });

  readonly initials = computed(() => {
    const u = this.user();
    if (!u) return '?';
    const source = (u.name || u.username || '?').trim();
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return source.slice(0, 2).toUpperCase();
  });

  readonly unreadLabel = computed(() => {
    const n = this.unreadCount();
    if (n <= 0) return 'Notifications';
    if (n > 99) return 'Notifications, 99+ unread';
    return `Notifications, ${n} unread`;
  });

  readonly badgeText = computed(() => {
    const n = this.unreadCount();
    if (n <= 0) return '';
    return n > 99 ? '99+' : String(n);
  });

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.meApi.getProfile().subscribe({
      next: (p) => this.session.patchProfile({ name: p.name, username: p.username }),
      error: () => undefined,
    });

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e) => this.currentUrl.set(e.urlAfterRedirects));

    this.startUnreadPolling();
    document.addEventListener('visibilitychange', this.onVisibility);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', this.onVisibility);
      this.stopUnreadPolling();
    });
  }

  toggleSidebar(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    localStorage.setItem(RAIL_KEY, next ? '1' : '0');
  }

  isActive(path: string): boolean {
    return isNavActive(this.currentUrl(), path);
  }

  landingLink(): string {
    return this.session.defaultLanding();
  }

  signOut(): void {
    this.authApi.logout().subscribe({ error: () => undefined });
    this.session.clear();
    void this.router.navigate(['/login'], { queryParams: { reason: 'signed-out' } });
  }

  groupVisible(_group: NavGroupDef): boolean {
    return true;
  }

  roleLabel(role: UserRole | null): string {
    if (!role) return '';
    return role.charAt(0) + role.slice(1).toLowerCase();
  }

  private readCollapsed(): boolean {
    if (typeof window !== 'undefined' && window.innerWidth < 1024) return true;
    return localStorage.getItem(RAIL_KEY) === '1';
  }

  private onVisibility = (): void => {
    if (document.hidden) {
      this.stopUnreadPolling();
    } else {
      this.fetchUnread();
      this.startUnreadPolling();
    }
  };

  private startUnreadPolling(): void {
    this.stopUnreadPolling();
    this.fetchUnread();
    this.pollTimer = setInterval(() => this.fetchUnread(), 30_000);
  }

  private stopUnreadPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private fetchUnread(): void {
    this.meApi.unreadNotificationCount().subscribe({
      next: (r) => this.badge.set(r.count ?? 0),
      error: () => undefined,
    });
  }
}
