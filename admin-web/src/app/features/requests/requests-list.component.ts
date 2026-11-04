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
import { CategoriesApi } from '../../core/api/categories.api';
import { DashboardApi } from '../../core/api/dashboard.api';
import { RequestsApi } from '../../core/api/requests.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import {
  DeviceCategory,
  RepairRequestSummary,
  STATUS_FILTERS,
  formatSubmittedAt,
  statusChipClass,
  statusLabel,
} from '../../core/models/request.models';
import { formatIndiaDate, indiaToday } from '../../core/india-date';
import { OpenJobsBannerComponent } from '../jobs/open-jobs-banner.component';
import { ApproveDialogComponent } from './dialogs/approve-dialog.component';
import { RejectDialogComponent } from './dialogs/reject-dialog.component';
import { RequestInfoDialogComponent } from './dialogs/request-info-dialog.component';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-requests-list',
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
    OpenJobsBannerComponent,
  ],
  templateUrl: './requests-list.component.html',
  styleUrl: './requests-list.component.scss',
})
export class RequestsListComponent implements OnInit {
  private readonly api = inject(RequestsApi);
  private readonly categoriesApi = inject(CategoriesApi);
  private readonly dashboardApi = inject(DashboardApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusFilters = STATUS_FILTERS;
  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly rows = signal<RepairRequestSummary[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly totalPages = signal(0);
  readonly error = signal<MappedApiError | null>(null);
  readonly awaitingDecision = signal<number | null>(null);
  readonly categories = signal<DeviceCategory[]>([]);
  readonly staleIds = signal<Set<string>>(new Set());

  readonly searchCtrl = new FormControl('', { nonNullable: true });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly activeStatus = signal<string | undefined>('PENDING_REVIEW');
  readonly activeQ = signal<string>('');
  readonly activeCategory = signal<string>('');
  readonly activeSort = signal('submittedAt,desc');
  readonly dateMode = signal<'range' | 'all'>('range');
  readonly activeFrom = signal(indiaToday());
  readonly activeTo = signal(indiaToday());
  readonly carryoverToken = signal(0);

  readonly isDefaultToday = computed(
    () =>
      this.dateMode() === 'range' &&
      this.activeFrom() === indiaToday() &&
      this.activeTo() === indiaToday(),
  );

  readonly dateSummary = computed(() => {
    if (this.dateMode() === 'all') return 'All dates';
    const from = formatIndiaDate(this.activeFrom());
    const to = formatIndiaDate(this.activeTo());
    return from === to ? from : `${from} – ${to}`;
  });

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    const total = this.total();
    const awaiting = this.awaitingDecision();
    const base = `${total} request${total === 1 ? '' : 's'}`;
    if (awaiting == null) return base;
    return `${base} · ${awaiting} awaiting your decision`;
  });

  readonly filteredEmpty = computed(
    () =>
      this.state() === 'ready' &&
      this.rows().length === 0 &&
      (!!this.activeStatus() || !!this.activeQ() || !!this.activeCategory() || !this.isDefaultToday()),
  );

  readonly empty = computed(
    () =>
      this.state() === 'ready' &&
      this.rows().length === 0 &&
      !this.activeStatus() &&
      !this.activeQ() &&
      !this.activeCategory(),
  );

  /** Snapshot for detail breadcrumb to restore queue filters. */
  returnQuery(): Record<string, string> {
    const q: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.route.snapshot.queryParams)) {
      if (v != null && v !== '') q[k] = String(v);
    }
    return q;
  }

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }

    this.categoriesApi.listActive().subscribe({
      next: (list) => this.categories.set(list ?? []),
      error: () => undefined,
    });

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (!params.keys.length) {
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {
            status: 'PENDING_REVIEW',
            from: indiaToday(),
            to: indiaToday(),
            sort: 'submittedAt,desc',
            page: 0,
            size: 20,
          },
          replaceUrl: true,
        });
        return;
      }

      const mode = params.get('dates') === 'all' ? 'all' : 'range';
      if (mode === 'range' && !params.get('from')) {
        const today = indiaToday();
        this.patchQuery({ from: today, to: params.get('to') || today });
        return;
      }
      this.dateMode.set(mode);
      this.activeFrom.set(params.get('from') ?? indiaToday());
      this.activeTo.set(params.get('to') ?? params.get('from') ?? indiaToday());
      this.activeStatus.set(params.get('status') ?? undefined);
      this.activeQ.set(params.get('q') ?? '');
      this.activeCategory.set(params.get('deviceCategoryId') ?? '');
      this.activeSort.set(params.get('sort') ?? 'submittedAt,desc');
      this.page.set(Number(params.get('page') ?? 0));
      this.size.set(Number(params.get('size') ?? 20));

      if (this.searchCtrl.value !== (params.get('q') ?? '')) {
        this.searchCtrl.setValue(params.get('q') ?? '', { emitEvent: false });
      }

      this.fetch(false);
      this.refreshAwaiting();
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => {
        this.patchQuery({ q: q.trim() || null, page: 0 });
      });
  }

  setStatusFilter(status?: string): void {
    this.patchQuery({ status: status ?? null, page: 0 });
  }

  isStatusActive(status?: string): boolean {
    const current = this.activeStatus();
    if (!status) return !current;
    return current === status;
  }

  onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.patchQuery({ deviceCategoryId: value || null, page: 0 });
  }

  onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.patchQuery({ sort: value, page: 0 });
  }

  onFromInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (!value) return;
    const to = this.activeTo() && this.activeTo() >= value ? this.activeTo() : value;
    this.patchQuery({ from: value, to, dates: null, page: 0 });
  }

  onToInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (!value) return;
    const from = this.activeFrom() && this.activeFrom() <= value ? this.activeFrom() : value;
    this.patchQuery({ from, to: value, dates: null, page: 0 });
  }

  setToday(): void {
    const today = indiaToday();
    this.patchQuery({ from: today, to: today, dates: null, page: 0 });
  }

  setAllDates(): void {
    this.patchQuery({ dates: 'all', from: null, to: null, page: 0 });
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
  }

  clearFilters(): void {
    this.searchCtrl.setValue('', { emitEvent: false });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        status: 'PENDING_REVIEW',
        from: indiaToday(),
        to: indiaToday(),
        sort: 'submittedAt,desc',
        page: 0,
        size: this.size(),
      },
    });
  }

  clearCategory(): void {
    this.patchQuery({ deviceCategoryId: null, page: 0 });
  }

  refresh(): void {
    this.carryoverToken.update((n) => n + 1);
    this.fetch(true);
    this.refreshAwaiting();
  }

  retry(): void {
    this.fetch(false);
  }

  changePage(delta: number): void {
    this.patchQuery({ page: this.page() + delta });
  }

  changeSize(event: Event): void {
    const size = Number((event.target as HTMLSelectElement).value);
    this.patchQuery({ size, page: 0 });
  }

  submitted(row: RepairRequestSummary): { relative: string; absolute: string } {
    return formatSubmittedAt(row.submittedAt);
  }

  deviceLine(row: RepairRequestSummary): string {
    const parts = [row.deviceBrand, row.deviceModel].filter(Boolean);
    return parts.length ? parts.join(' ') : '—';
  }

  canDecide(row: RepairRequestSummary): boolean {
    return row.status === 'PENDING_REVIEW' || row.status === 'INFORMATION_REQUIRED';
  }

  /** Server allows request-info only from PENDING_REVIEW. */
  canRequestInfo(row: RepairRequestSummary): boolean {
    return row.status === 'PENDING_REVIEW';
  }

  isStale(id: string): boolean {
    return this.staleIds().has(id);
  }

  openApprove(row: RepairRequestSummary): void {
    this.dialog
      .open(ApproveDialogComponent, { width: '480px', data: { row } })
      .afterClosed()
      .subscribe((result) => this.afterDecision(result, row, `${row.referenceNumber} approved · job created`));
  }

  openReject(row: RepairRequestSummary): void {
    this.dialog
      .open(RejectDialogComponent, { width: '560px', data: { row } })
      .afterClosed()
      .subscribe((result) => this.afterDecision(result, row, `${row.referenceNumber} rejected`));
  }

  openInfo(row: RepairRequestSummary): void {
    this.dialog
      .open(RequestInfoDialogComponent, { width: '480px', data: { row } })
      .afterClosed()
      .subscribe((result) =>
        this.afterDecision(
          result,
          row,
          `Information requested from ${row.contactName || row.customerUsername}`,
        ),
      );
  }

  private afterDecision(
    result: boolean | 'reload' | undefined,
    row: RepairRequestSummary,
    toast: string,
  ): void {
    if (result === 'reload') {
      this.markStale(row.id);
      this.fetch(true);
      return;
    }
    if (result === true) {
      this.snack.open(toast, undefined, { duration: 4000 });
      this.fetch(true);
      this.refreshAwaiting();
    }
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
    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
    }

    this.api
      .list({
        status: this.activeStatus(),
        q: this.activeQ() || undefined,
        deviceCategoryId: this.activeCategory() || undefined,
        from: this.dateMode() === 'all' ? undefined : this.activeFrom(),
        to: this.dateMode() === 'all' ? undefined : this.activeTo(),
        page: this.page(),
        size: this.size(),
        sort: this.activeSort(),
      })
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: (page) => {
          this.rows.set(page.content ?? []);
          this.total.set(page.totalElements ?? 0);
          this.totalPages.set(page.totalPages ?? 0);
          this.page.set(page.page ?? 0);
          this.size.set(page.size ?? 20);
          this.error.set(null);
          this.state.set('ready');
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (mapped.status === 403) {
            this.state.set('forbidden');
          } else {
            this.state.set('error');
          }
        },
      });
  }

  private refreshAwaiting(): void {
    this.dashboardApi.getCounters().subscribe({
      next: (c) => this.awaitingDecision.set((c.pendingReview ?? 0) + (c.infoRequired ?? 0)),
      error: () => this.awaitingDecision.set(null),
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
