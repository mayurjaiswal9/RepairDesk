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
import { CustomersApi } from '../../core/api/customers.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import {
  DeleteCustomerDialogComponent,
  ResetPasswordDialogComponent,
} from './dialogs/customer-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

function accountStatusChip(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'chip-success';
    case 'DISABLED':
      return 'chip-warning';
    case 'DELETED':
      return 'chip-error';
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
    case 'DELETED':
      return 'Deleted';
    default:
      return status;
  }
}

@Component({
  selector: 'app-customers-list',
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
  templateUrl: './customers-list.component.html',
  styleUrl: './customers-list.component.scss',
})
export class CustomersListComponent implements OnInit {
  private readonly api = inject(CustomersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly accountStatusChip = accountStatusChip;
  readonly accountStatusLabel = accountStatusLabel;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly all = signal<UserProfile[]>([]);
  readonly error = signal<MappedApiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly q = signal('');
  readonly staleIds = signal<Set<string>>(new Set());

  readonly searchCtrl = new FormControl('', { nonNullable: true });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly isStaffOrAdmin = computed(
    () => this.session.role() === 'ADMIN' || this.session.role() === 'STAFF',
  );

  readonly total = computed(() => this.all().length);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.size()) || 1));
  readonly rows = computed(() => {
    const start = this.page() * this.size();
    return this.all().slice(start, start + this.size());
  });

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    const n = this.total();
    const q = this.q();
    if (q) return `${n} customer${n === 1 ? '' : 's'} match “${q}”`;
    return `${n} customer${n === 1 ? '' : 's'}`;
  });

  readonly empty = computed(() => this.state() === 'ready' && this.total() === 0 && !this.q());
  readonly filteredEmpty = computed(
    () => this.state() === 'ready' && this.total() === 0 && !!this.q(),
  );

  /** Preserve list search/paging when opening detail (breadcrumb return). */
  readonly detailNavState = computed(() => {
    const q: Record<string, string> = {};
    if (this.q()) q['q'] = this.q();
    if (this.page()) q['page'] = String(this.page());
    if (this.size() !== 20) q['size'] = String(this.size());
    return { returnQuery: q };
  });

  ngOnInit(): void {
    if (!this.isStaffOrAdmin()) {
      this.state.set('forbidden');
      return;
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q.set(params.get('q') ?? '');
      this.page.set(Number(params.get('page') ?? 0));
      this.size.set(Number(params.get('size') ?? 20));
      if (this.searchCtrl.value !== this.q()) {
        this.searchCtrl.setValue(this.q(), { emitEvent: false });
      }
      this.fetch(false);
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((raw) => {
        const q = raw.trim();
        this.patchQuery({ q: q || null, page: 0 });
      });
  }

  refresh(): void {
    this.fetch(true);
  }

  retry(): void {
    this.fetch(false);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null, page: 0 });
  }

  changePage(delta: number): void {
    this.patchQuery({ page: this.page() + delta });
  }

  changeSize(event: Event): void {
    this.patchQuery({ size: Number((event.target as HTMLSelectElement).value), page: 0 });
  }

  displayName(c: UserProfile): string {
    return c.name?.trim() || c.username;
  }

  isStale(id: string): boolean {
    return this.staleIds().has(id);
  }

  openReset(c: UserProfile): void {
    this.dialog
      .open(ResetPasswordDialogComponent, {
        width: '480px',
        data: { customer: c },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`Password reset for ${this.displayName(c)}`, undefined, {
            duration: 4000,
          });
        } else if (result === 'stale') {
          this.markStale(c.id);
          this.snack.open('This account is no longer available', undefined, { duration: 4000 });
          this.fetch(true);
        }
      });
  }

  openDelete(c: UserProfile): void {
    this.dialog
      .open(DeleteCustomerDialogComponent, {
        width: '520px',
        data: { customer: c },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`${this.displayName(c)}'s account deleted`, undefined, {
            duration: 4000,
          });
          this.fetch(true);
        } else if (result === 'stale') {
          this.markStale(c.id);
          this.snack.open('This account is no longer available', undefined, { duration: 4000 });
          this.fetch(true);
        }
      });
  }

  private markStale(id: string): void {
    const next = new Set(this.staleIds());
    next.add(id);
    this.staleIds.set(next);
    setTimeout(() => {
      const cleared = new Set(this.staleIds());
      cleared.delete(id);
      this.staleIds.set(cleared);
    }, 5000);
  }

  private fetch(isRefresh: boolean): void {
    if (isRefresh && this.state() === 'ready') this.refetching.set(true);
    else this.state.set('loading');

    this.api
      .list(this.q() || undefined)
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: (list) => {
          this.all.set(list ?? []);
          // Clamp page if search shrunk the set
          const maxPage = Math.max(0, Math.ceil((list?.length ?? 0) / this.size()) - 1);
          if (this.page() > maxPage) this.patchQuery({ page: maxPage });
          this.error.set(null);
          this.state.set('ready');
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
