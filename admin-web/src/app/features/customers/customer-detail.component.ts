import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CustomersApi } from '../../core/api/customers.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { JobSummary, UserProfile } from '../../core/models/dashboard.models';
import {
  RepairRequestSummary,
  formatDateOnly,
  statusChipClass,
  statusLabel,
} from '../../core/models/request.models';
import {
  DeleteCustomerDialogComponent,
  ResetPasswordDialogComponent,
} from './dialogs/customer-dialogs';

type RegionState = 'loading' | 'ready' | 'error' | 'notfound' | 'forbidden';

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
  selector: 'app-customer-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatMenuModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './customer-detail.component.html',
  styleUrl: './customer-detail.component.scss',
})
export class CustomerDetailComponent implements OnInit {
  private readonly api = inject(CustomersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly accountStatusChip = accountStatusChip;
  readonly accountStatusLabel = accountStatusLabel;
  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;
  readonly formatDateOnly = formatDateOnly;

  readonly returnQuery = signal<Record<string, string>>({});
  readonly customerId = signal('');

  readonly profileState = signal<RegionState>('loading');
  readonly historyState = signal<RegionState>('loading');
  readonly profile = signal<UserProfile | null>(null);
  readonly requests = signal<RepairRequestSummary[]>([]);
  readonly jobs = signal<JobSummary[]>([]);
  readonly profileError = signal<MappedApiError | null>(null);
  readonly historyError = signal<MappedApiError | null>(null);

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly isStaffOrAdmin = computed(
    () => this.session.role() === 'ADMIN' || this.session.role() === 'STAFF',
  );

  readonly displayName = computed(() => {
    const p = this.profile();
    if (!p) return '…';
    return p.name?.trim() || p.username;
  });

  readonly isDeleted = computed(() => this.profile()?.status === 'DELETED');
  readonly showAccountActions = computed(
    () => this.isAdmin() && !!this.profile() && !this.isDeleted() && this.profileState() === 'ready',
  );

  readonly historyEmpty = computed(
    () =>
      this.historyState() === 'ready' &&
      this.requests().length === 0 &&
      this.jobs().length === 0,
  );

  ngOnInit(): void {
    if (!this.isStaffOrAdmin()) {
      this.profileState.set('forbidden');
      this.historyState.set('forbidden');
      return;
    }

    const nav = this.router.getCurrentNavigation();
    const state =
      (nav?.extras.state as { returnQuery?: Record<string, string> } | undefined) ??
      (history.state as { returnQuery?: Record<string, string> } | undefined);
    if (state?.returnQuery) this.returnQuery.set(state.returnQuery);

    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id') ?? '';
      this.customerId.set(id);
      if (!id) {
        this.profileState.set('notfound');
        this.historyState.set('notfound');
        return;
      }
      this.loadProfile();
      this.loadHistory();
    });
  }

  retryProfile(): void {
    this.loadProfile();
  }

  retryHistory(): void {
    this.loadHistory();
  }

  openReset(): void {
    const c = this.profile();
    if (!c) return;
    this.dialog
      .open(ResetPasswordDialogComponent, {
        width: '480px',
        data: { customer: c },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`Password reset for ${this.displayName()}`, undefined, {
            duration: 4000,
          });
        } else if (result === 'stale') {
          this.snack.open('This account is no longer available', undefined, { duration: 4000 });
          void this.router.navigate(['/customers'], { queryParams: this.returnQuery() });
        }
      });
  }

  openDelete(): void {
    const c = this.profile();
    if (!c) return;
    this.dialog
      .open(DeleteCustomerDialogComponent, {
        width: '520px',
        data: {
          customer: c,
          requestCount: this.requests().length,
          jobCount: this.jobs().length,
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === true) {
          this.snack.open(`${this.displayName()}'s account deleted`, undefined, {
            duration: 4000,
          });
          void this.router.navigate(['/customers'], { queryParams: this.returnQuery() });
        } else if (result === 'stale') {
          this.snack.open('This account is no longer available', undefined, { duration: 4000 });
          void this.router.navigate(['/customers'], { queryParams: this.returnQuery() });
        }
      });
  }

  private loadProfile(): void {
    this.profileState.set('loading');
    this.profileError.set(null);
    this.api.get(this.customerId()).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.profileState.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.profileError.set(mapped);
        if (mapped.status === 404) this.profileState.set('notfound');
        else if (mapped.status === 403) this.profileState.set('forbidden');
        else this.profileState.set('error');
      },
    });
  }

  private loadHistory(): void {
    this.historyState.set('loading');
    this.historyError.set(null);
    this.api.history(this.customerId()).subscribe({
      next: (h) => {
        this.requests.set(h.requests ?? []);
        this.jobs.set(h.jobs ?? []);
        this.historyState.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.historyError.set(mapped);
        if (mapped.status === 404) this.historyState.set('notfound');
        else if (mapped.status === 403) this.historyState.set('forbidden');
        else this.historyState.set('error');
      },
    });
  }
}
