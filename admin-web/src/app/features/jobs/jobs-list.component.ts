import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { JobsApi } from '../../core/api/jobs.api';
import { UsersApi } from '../../core/api/users.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import {
  JOB_STATUS_FILTERS,
  JobSummary,
  UserProfile,
} from '../../core/models/dashboard.models';
import {
  formatSubmittedAt,
  statusChipClass,
  statusLabel,
} from '../../core/models/request.models';
import { formatIndiaDate, indiaToday } from '../../core/india-date';
import { OpenJobsBannerComponent } from './open-jobs-banner.component';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-jobs-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    OpenJobsBannerComponent,
  ],
  templateUrl: './jobs-list.component.html',
  styleUrl: './jobs-list.component.scss',
})
export class JobsListComponent implements OnInit {
  private readonly api = inject(JobsApi);
  private readonly usersApi = inject(UsersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusFilters = JOB_STATUS_FILTERS;
  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly rows = signal<JobSummary[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly totalPages = signal(0);
  readonly error = signal<MappedApiError | null>(null);
  readonly technicians = signal<UserProfile[]>([]);
  readonly unassignedCount = signal<number | null>(null);

  readonly searchCtrl = new FormControl('', { nonNullable: true });

  readonly role = computed(() => this.session.role());
  readonly isTechnician = computed(() => this.role() === 'TECHNICIAN');
  readonly showTechFilter = computed(() => !this.isTechnician());

  readonly activeStatus = signal<string | undefined>(undefined);
  readonly activeQ = signal('');
  readonly activeTechnician = signal(''); // '' | 'unassigned' | uuid
  readonly activeSort = signal('updatedAt,desc');
  readonly activeActive = signal(false);
  readonly activeUnscheduled = signal(false);
  readonly dateMode = signal<'range' | 'all'>('range');
  readonly activeFrom = signal(indiaToday());
  readonly activeTo = signal(indiaToday());
  readonly carryoverToken = signal(0);

  readonly pageTitle = computed(() => (this.isTechnician() ? 'My jobs' : 'Jobs'));

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    const total = this.total();
    const base = `${total} job${total === 1 ? '' : 's'}`;
    const unassigned = this.unassignedCount();
    if (!this.isTechnician() && unassigned != null) {
      return `${base} · ${unassigned} unassigned`;
    }
    return base;
  });

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

  readonly hasFilters = computed(
    () =>
      !!this.activeStatus() ||
      !!this.activeQ() ||
      !!this.activeTechnician() ||
      this.activeActive() ||
      this.activeUnscheduled() ||
      !this.isDefaultToday(),
  );

  readonly filteredEmpty = computed(
    () => this.state() === 'ready' && this.rows().length === 0 && this.hasFilters(),
  );

  readonly empty = computed(
    () => this.state() === 'ready' && this.rows().length === 0 && !this.hasFilters(),
  );

  ngOnInit(): void {
    if (this.showTechFilter()) {
      this.usersApi.listByRole('TECHNICIAN').subscribe({
        next: (list) =>
          this.technicians.set(
            [...(list ?? [])].sort((a, b) =>
              (a.name || a.username).localeCompare(b.name || b.username),
            ),
          ),
        error: () => undefined,
      });
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (!params.keys.length) {
        const today = indiaToday();
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { from: today, to: today, sort: 'updatedAt,desc', page: 0, size: 20 },
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
      this.activeTechnician.set(params.get('technicianId') ?? (params.get('unassigned') === 'true' ? 'unassigned' : ''));
      this.activeSort.set(params.get('sort') ?? 'updatedAt,desc');
      this.page.set(Number(params.get('page') ?? 0));
      this.size.set(Number(params.get('size') ?? 20));
      this.activeActive.set(params.get('active') === 'true');
      this.activeUnscheduled.set(params.get('scheduled') === 'false');

      if (this.searchCtrl.value !== (params.get('q') ?? '')) {
        this.searchCtrl.setValue(params.get('q') ?? '', { emitEvent: false });
      }

      this.fetch(false);
      this.refreshUnassigned();
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => {
        this.patchQuery({ q: q.trim() || null, page: 0 });
      });
  }

  setStatusFilter(status?: string): void {
    this.patchQuery({
      status: status ?? null,
      active: null,
      scheduled: null,
      page: 0,
    });
  }

  isStatusActive(status?: string): boolean {
    if (this.activeActive() || this.activeUnscheduled()) return false;
    const current = this.activeStatus();
    if (!status) return !current;
    return current === status;
  }

  onTechnicianChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (value === 'unassigned') {
      this.patchQuery({ unassigned: 'true', technicianId: null, page: 0 });
    } else if (value) {
      this.patchQuery({ technicianId: value, unassigned: null, page: 0 });
    } else {
      this.patchQuery({ technicianId: null, unassigned: null, page: 0 });
    }
  }

  onSortChange(event: Event): void {
    this.patchQuery({ sort: (event.target as HTMLSelectElement).value, page: 0 });
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

  statusText(status: string): string {
    return status.includes(',') ? 'Open' : statusLabel(status);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null, page: 0 });
  }

  clearTechnician(): void {
    this.patchQuery({ technicianId: null, unassigned: null, page: 0 });
  }

  clearDashboardChip(): void {
    this.patchQuery({ active: null, scheduled: null, page: 0 });
  }

  clearFilters(): void {
    this.searchCtrl.setValue('', { emitEvent: false });
    const today = indiaToday();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: today, to: today, sort: 'updatedAt,desc', page: 0, size: this.size() },
    });
  }

  refresh(): void {
    this.carryoverToken.update((n) => n + 1);
    this.fetch(true);
    this.refreshUnassigned();
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

  updated(job: JobSummary): { relative: string; absolute: string } {
    return formatSubmittedAt(job.updatedAt);
  }

  priorityMarker(priority?: string): { icon: string; label: string } | null {
    if (priority === 'HIGH') return { icon: 'priority_high', label: 'Priority: High' };
    if (priority === 'URGENT') return { icon: 'priority_high', label: 'Priority: Urgent' };
    return null;
  }

  technicianLabel(id: string): string {
    if (id === 'unassigned') return 'Unassigned';
    const tech = this.technicians().find((t) => t.id === id);
    return tech ? tech.name || tech.username : 'Technician';
  }

  /** Snapshot for detail breadcrumb to restore board filters. */
  returnQuery(): Record<string, string> {
    const q: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.route.snapshot.queryParams)) {
      if (v != null && v !== '') q[k] = String(v);
    }
    return q;
  }

  private fetch(isRefresh: boolean): void {
    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
    }

    const tech = this.activeTechnician();
    this.api
      .list({
        status: this.activeStatus(),
        active: this.activeActive() || undefined,
        scheduled: this.activeUnscheduled() ? false : undefined,
        technicianId: tech && tech !== 'unassigned' ? tech : undefined,
        unassigned: tech === 'unassigned' ? true : undefined,
        q: this.activeQ() || undefined,
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
          this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
        },
      });
  }

  private refreshUnassigned(): void {
    if (this.isTechnician()) {
      this.unassignedCount.set(null);
      return;
    }
    this.api.list({ unassigned: true, page: 0, size: 1, sort: 'updatedAt,desc' }).subscribe({
      next: (page) => this.unassignedCount.set(page.totalElements ?? 0),
      error: () => this.unassignedCount.set(null),
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
