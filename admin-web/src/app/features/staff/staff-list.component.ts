import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { UsersApi } from '../../core/api/users.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import { ResetPasswordDialogComponent } from '../customers/dialogs/customer-dialogs';
import {
  CreateUserDialogComponent,
  DeleteStaffDialogComponent,
  DisableUserDialogComponent,
  EditUserDialogComponent,
  EnableUserDialogComponent,
} from './dialogs/staff-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';
type RoleFilter = '' | 'ADMIN' | 'STAFF' | 'TECHNICIAN';
type StatusFilter = '' | 'ACTIVE' | 'DISABLED';

const ROLE_TABS: Array<{ key: RoleFilter; label: string }> = [
  { key: '', label: 'All internal' },
  { key: 'ADMIN', label: 'Admin' },
  { key: 'STAFF', label: 'Staff' },
  { key: 'TECHNICIAN', label: 'Technician' },
];

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

function accountStatusChip(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'chip-success';
    case 'DISABLED':
      return 'chip-warning';
    default:
      return 'chip-neutral';
  }
}

function accountStatusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'DISABLED':
      return 'Disabled';
    default:
      return status;
  }
}

function displayName(u: UserProfile): string {
  return u.name?.trim() || u.username;
}

@Component({
  selector: 'app-staff-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './staff-list.component.html',
  styleUrl: './staff-list.component.scss',
})
export class StaffListComponent implements OnInit {
  private readonly api = inject(UsersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly roleTabs = ROLE_TABS;
  readonly roleLabel = roleLabel;
  readonly roleBadgeClass = roleBadgeClass;
  readonly accountStatusChip = accountStatusChip;
  readonly accountStatusLabel = accountStatusLabel;
  readonly displayName = displayName;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly all = signal<UserProfile[]>([]);
  readonly error = signal<MappedApiError | null>(null);
  readonly role = signal<RoleFilter>('');
  readonly status = signal<StatusFilter>('');
  readonly q = signal('');
  readonly page = signal(0);
  readonly size = signal(20);

  readonly searchCtrl = new FormControl('', { nonNullable: true });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly meId = computed(() => this.session.user()?.userId ?? '');

  /** Full roster only when All internal — used for sole-admin pre-emption. */
  readonly soleAdminId = computed(() => {
    if (this.role()) return null;
    const admins = this.all().filter((u) => u.role === 'ADMIN' && u.status === 'ACTIVE');
    return admins.length === 1 ? admins[0].id : null;
  });

  readonly filtered = computed(() => {
    const status = this.status();
    const q = this.q().toLowerCase();
    return this.all().filter((u) => {
      if (status && u.status !== status) return false;
      if (!q) return true;
      const name = (u.name ?? '').toLowerCase();
      return (
        name.includes(q) ||
        u.username.toLowerCase().includes(q) ||
        (u.mobile ?? '').toLowerCase().includes(q)
      );
    });
  });

  readonly total = computed(() => this.filtered().length);
  readonly activeCount = computed(
    () => this.filtered().filter((u) => u.status === 'ACTIVE').length,
  );
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.size()) || 1));
  readonly rows = computed(() => {
    const start = this.page() * this.size();
    return this.filtered().slice(start, start + this.size());
  });

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    const n = this.total();
    const active = this.activeCount();
    const noun = n === 1 ? 'user' : 'users';
    if (this.hasFilters()) {
      return `${n} matching ${noun} · ${active} active`;
    }
    return `${n} internal ${noun} · ${active} active`;
  });

  readonly empty = computed(
    () => this.state() === 'ready' && this.total() === 0 && !this.hasFilters(),
  );
  readonly filteredEmpty = computed(
    () => this.state() === 'ready' && this.total() === 0 && this.hasFilters(),
  );

  readonly hasFilters = computed(
    () => !!this.role() || !!this.status() || !!this.q(),
  );

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const nextRole = (params.get('role') ?? '') as RoleFilter;
      const prevRole = this.role();
      this.role.set(
        nextRole === 'ADMIN' || nextRole === 'STAFF' || nextRole === 'TECHNICIAN' ? nextRole : '',
      );
      const st = params.get('status') ?? '';
      this.status.set(st === 'ACTIVE' || st === 'DISABLED' ? st : '');
      this.q.set(params.get('q') ?? '');
      this.page.set(Number(params.get('page') ?? 0));
      this.size.set(Number(params.get('size') ?? 20));
      if (this.searchCtrl.value !== this.q()) {
        this.searchCtrl.setValue(this.q(), { emitEvent: false });
      }
      if (this.state() === 'loading' || prevRole !== this.role() || this.all().length === 0) {
        this.fetch(false);
      } else {
        this.clampPage();
      }
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((raw) => {
        this.patchQuery({ q: raw.trim() || null, page: 0 });
      });
  }

  isSelf(u: UserProfile): boolean {
    return !!this.meId() && u.id === this.meId();
  }

  isSoleAdmin(u: UserProfile): boolean {
    return this.soleAdminId() === u.id;
  }

  deleteDisabledReason(u: UserProfile): string | null {
    if (this.isSelf(u)) {
      return "You can't delete your own account. Ask another administrator to do it.";
    }
    if (this.isSoleAdmin(u)) {
      return 'This is the only active administrator.';
    }
    return null;
  }

  disableDisabledReason(u: UserProfile): string | null {
    if (this.isSoleAdmin(u)) {
      return 'This is the only active administrator.';
    }
    return null;
  }

  setRole(role: RoleFilter): void {
    this.patchQuery({ role: role || null, page: 0 });
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.patchQuery({
      status: value === 'ACTIVE' || value === 'DISABLED' ? value : null,
      page: 0,
    });
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null, page: 0 });
  }

  clearFilters(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ role: null, status: null, q: null, page: 0 });
  }

  clearRole(): void {
    this.patchQuery({ role: null, page: 0 });
  }

  clearStatus(): void {
    this.patchQuery({ status: null, page: 0 });
  }

  refresh(): void {
    this.fetch(true);
  }

  retry(): void {
    this.fetch(false);
  }

  changePage(delta: number): void {
    this.patchQuery({ page: this.page() + delta });
  }

  changeSize(event: Event): void {
    this.patchQuery({ size: Number((event.target as HTMLSelectElement).value), page: 0 });
  }

  openCreate(): void {
    this.dialog
      .open(CreateUserDialogComponent, { width: '520px', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          const created = result as UserProfile;
          this.snack.open(
            `${displayName(created)} created as ${roleLabel(created.role)}`,
            undefined,
            { duration: 4000 },
          );
          this.fetch(true);
        }
      });
  }

  openEdit(u: UserProfile): void {
    this.dialog
      .open(EditUserDialogComponent, {
        width: '520px',
        data: {
          user: u,
          isSelf: this.isSelf(u),
          isSoleAdmin: this.isSoleAdmin(u),
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'stale') {
          this.fetch(true);
          return;
        }
        if (result && typeof result === 'object' && 'id' in result) {
          const updated = result as UserProfile;
          this.snack.open(`${displayName(updated)} updated`, undefined, { duration: 4000 });
          this.patchRow(updated);
        }
      });
  }

  openDisable(u: UserProfile): void {
    if (this.disableDisabledReason(u)) return;
    this.dialog
      .open(DisableUserDialogComponent, {
        width: '520px',
        data: { user: u, isSelf: this.isSelf(u) },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          this.snack.open(`${displayName(u)} disabled`, undefined, { duration: 4000 });
          this.patchRow(result as UserProfile);
        }
      });
  }

  openEnable(u: UserProfile): void {
    this.dialog
      .open(EnableUserDialogComponent, {
        width: '480px',
        data: { user: u },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result && typeof result === 'object' && 'id' in result) {
          this.snack.open(`${displayName(u)} enabled`, undefined, { duration: 4000 });
          this.patchRow(result as UserProfile);
        }
      });
  }

  openReset(u: UserProfile): void {
    this.dialog
      .open(ResetPasswordDialogComponent, {
        width: '480px',
        data: { customer: u },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`Password reset for ${displayName(u)}`, undefined, { duration: 4000 });
        } else if (result === 'stale') {
          this.snack.open('This account is no longer available', undefined, { duration: 4000 });
          this.fetch(true);
        }
      });
  }

  openDelete(u: UserProfile): void {
    if (this.deleteDisabledReason(u)) return;
    this.dialog
      .open(DeleteStaffDialogComponent, {
        width: '520px',
        data: { user: u },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`${displayName(u)}'s account deleted`, undefined, { duration: 4000 });
          this.fetch(true);
        }
      });
  }

  private patchRow(updated: UserProfile): void {
    this.all.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
  }

  private clampPage(): void {
    const maxPage = Math.max(0, Math.ceil(this.total() / this.size()) - 1);
    if (this.page() > maxPage) this.patchQuery({ page: maxPage });
  }

  private fetch(isRefresh: boolean): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }
    if (isRefresh && this.state() === 'ready') this.refetching.set(true);
    else this.state.set('loading');

    const req = this.role() ? this.api.listByRole(this.role()) : this.api.listInternal();
    req.pipe(finalize(() => this.refetching.set(false))).subscribe({
      next: (list) => {
        this.all.set(list ?? []);
        this.error.set(null);
        this.state.set('ready');
        this.clampPage();
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.error.set(mapped);
        this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
      },
    });
  }

  private patchQuery(patch: Record<string, string | number | null>): void {
    const current = { ...this.route.snapshot.queryParams };
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') delete current[k];
      else current[k] = String(v);
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: current });
  }
}
