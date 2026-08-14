import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthApi } from '../../core/api/auth.api';
import { MeApi } from '../../core/api/me.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import {
  AccountChangePasswordDialogComponent,
  ChangeMobileDialogComponent,
} from './dialogs/account-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

function roleLabel(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'Admin';
    case 'STAFF':
      return 'Staff';
    case 'TECHNICIAN':
      return 'Technician';
    default:
      return role;
  }
}

function roleBadgeClass(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'role-admin';
    case 'STAFF':
      return 'role-staff';
    case 'TECHNICIAN':
      return 'role-tech';
    default:
      return 'role-neutral';
  }
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatDialogModule, MatSnackBarModule],
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss',
})
export class AccountComponent implements OnInit {
  private readonly api = inject(MeApi);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly roleLabel = roleLabel;
  readonly roleBadgeClass = roleBadgeClass;

  readonly state = signal<PageState>('loading');
  readonly profile = signal<UserProfile | null>(null);
  readonly error = signal<MappedApiError | null>(null);

  readonly displayName = computed(() => {
    const p = this.profile();
    if (!p) return '…';
    return p.name?.trim() || p.username;
  });

  readonly initials = computed(() => {
    const source = this.displayName().trim();
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return source.slice(0, 2).toUpperCase();
  });

  readonly mustChange = computed(() => !!this.profile()?.mustChangePassword);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.api.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.session.patchProfile({ name: p.name, username: p.username });
        this.state.set('ready');
        const openPwd =
          p.mustChangePassword ||
          this.route.snapshot.queryParamMap.get('changePassword') === '1';
        if (openPwd) {
          // Defer so the page paints before the dialog.
          setTimeout(() => this.openChangePassword(), 0);
        }
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  retry(): void {
    this.load();
  }

  openChangeMobile(): void {
    const p = this.profile();
    if (!p) return;
    this.dialog
      .open(ChangeMobileDialogComponent, {
        width: '480px',
        data: { mobile: p.mobile ?? '' },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          this.profile.set(result as UserProfile);
          this.snack.open('Mobile number updated', undefined, { duration: 3000 });
        }
      });
  }

  openChangePassword(): void {
    this.dialog
      .open(AccountChangePasswordDialogComponent, {
        width: '480px',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (ok === true) {
          this.session.clearMustChangePassword();
          this.profile.update((p) => (p ? { ...p, mustChangePassword: false } : p));
          this.snack.open(
            "Password changed. You're still signed in on this browser.",
            undefined,
            { duration: 5000 },
          );
          if (this.route.snapshot.queryParamMap.has('changePassword')) {
            void this.router.navigate([], {
              relativeTo: this.route,
              queryParams: {},
              replaceUrl: true,
            });
          }
        }
      });
  }

  signOut(): void {
    if (
      !confirm(
        "Sign out of RepairDesk? You'll need your username or mobile and password to get back in.",
      )
    ) {
      return;
    }
    this.authApi.logout().subscribe({ error: () => undefined });
    this.session.clear();
    void this.router.navigate(['/login'], { queryParams: { reason: 'signed-out' } });
  }
}
