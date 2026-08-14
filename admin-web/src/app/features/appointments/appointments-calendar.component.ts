import {
  Component,
  DestroyRef,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AppointmentsApi } from '../../core/api/appointments.api';
import { JobsApi } from '../../core/api/jobs.api';
import { SettingsApi } from '../../core/api/settings.api';
import { UsersApi } from '../../core/api/users.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { JobSummary, UserProfile } from '../../core/models/dashboard.models';
import { AppointmentSummary } from '../../core/models/job.models';
import {
  SchedulingConfig,
  addDays,
  parseTimeToMinutes,
  toDateInputValue,
} from '../../core/models/scheduling.models';
import { formatTimeOnly, statusChipClass, statusLabel } from '../../core/models/request.models';
import {
  CalView,
  DayColumn,
  LaidOutAppt,
  buildDayColumns,
  computeAxis,
  formatRangeLabel,
  layoutDayAppointments,
  nowLinePct,
  parseAnchor,
  rangeForView,
  timeLabels,
  visibleAndOverflow,
} from './calendar.utils';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

const PANEL_KEY = 'repairdesk.appointments.unscheduledOpen';

interface DayBoard {
  column: DayColumn;
  visible: LaidOutAppt[];
  overflow: AppointmentSummary[];
  shadeTopPct: number;
  shadeBottomPct: number;
  closed: boolean;
  index: number;
}

@Component({
  selector: 'app-appointments-calendar',
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
  ],
  templateUrl: './appointments-calendar.component.html',
  styleUrl: './appointments-calendar.component.scss',
})
export class AppointmentsCalendarComponent implements OnInit {
  private readonly apptsApi = inject(AppointmentsApi);
  private readonly jobsApi = inject(JobsApi);
  private readonly settingsApi = inject(SettingsApi);
  private readonly usersApi = inject(UsersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;
  readonly formatTimeOnly = formatTimeOnly;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly error = signal<MappedApiError | null>(null);
  readonly config = signal<SchedulingConfig | null>(null);
  readonly configNotice = signal(false);
  readonly allAppts = signal<AppointmentSummary[]>([]);
  readonly technicians = signal<UserProfile[]>([]);
  readonly unscheduled = signal<JobSummary[]>([]);
  readonly panelOpen = signal(localStorage.getItem(PANEL_KEY) !== '0');
  readonly nowPct = signal<number | null>(null);

  readonly view = signal<CalView>('week');
  readonly anchor = signal(toDateInputValue(new Date()));
  readonly techFilter = signal('');
  readonly statusFilter = signal('');
  readonly q = signal('');
  readonly searchCtrl = new FormControl('', { nonNullable: true });

  readonly role = computed(() => this.session.role());
  readonly isTechnician = computed(() => this.role() === 'TECHNICIAN');
  readonly isStaffOrAdmin = computed(
    () => this.role() === 'ADMIN' || this.role() === 'STAFF',
  );

  readonly days = computed(() => {
    const { days } = rangeForView(this.view(), parseAnchor(this.anchor()));
    return buildDayColumns(days, this.config());
  });

  readonly rangeLabel = computed(() =>
    formatRangeLabel(
      this.view(),
      this.days().map((d) => d.date),
    ),
  );

  readonly filtered = computed(() => {
    let list = this.allAppts();
    const tech = this.techFilter();
    const status = this.statusFilter();
    const q = this.q().trim().toLowerCase();
    if (tech) list = list.filter((a) => a.technicianId === tech);
    if (status) list = list.filter((a) => a.status === status);
    if (q) {
      list = list.filter(
        (a) =>
          a.referenceNumber.toLowerCase().includes(q) ||
          (a.customerName || '').toLowerCase().includes(q) ||
          (a.deviceCategoryName || '').toLowerCase().includes(q),
      );
    }
    return list;
  });

  readonly axis = computed(() =>
    computeAxis(
      this.config(),
      this.days().map((d) => d.date),
      this.filtered(),
    ),
  );

  readonly labels = computed(() => {
    const a = this.axis();
    return timeLabels(a.start, a.end, a.step);
  });

  readonly boards = computed((): DayBoard[] => {
    const a = this.axis();
    const span = Math.max(1, a.end - a.start);
    return this.days().map((column, index) => {
      const dayAppts = this.filtered().filter((x) => x.scheduledDate === column.dateStr);
      const laid = layoutDayAppointments(dayAppts, a.start, a.end);
      const { visible, overflow } = visibleAndOverflow(laid);
      let shadeTopPct = 0;
      let shadeBottomPct = 0;
      if (column.closed) {
        shadeTopPct = 100;
        shadeBottomPct = 0;
      } else if (column.hours) {
        const open = parseTimeToMinutes(column.hours.openTime ?? undefined) ?? a.start;
        const close = parseTimeToMinutes(column.hours.closeTime ?? undefined) ?? a.end;
        shadeTopPct = Math.max(0, ((open - a.start) / span) * 100);
        shadeBottomPct = Math.max(0, ((a.end - close) / span) * 100);
      }
      return { column, visible, overflow, shadeTopPct, shadeBottomPct, closed: column.closed, index };
    });
  });

  readonly summary = computed(() => {
    if (this.state() === 'loading') return '';
    const n = this.filtered().length;
    return `${n} appointment${n === 1 ? '' : 's'} · ${this.rangeLabel()}`;
  });

  readonly hasFilters = computed(
    () => !!this.techFilter() || !!this.statusFilter() || !!this.q(),
  );

  readonly filteredEmpty = computed(
    () => this.state() === 'ready' && this.filtered().length === 0 && this.hasFilters(),
  );

  readonly empty = computed(
    () => this.state() === 'ready' && this.filtered().length === 0 && !this.hasFilters(),
  );

  readonly containsToday = computed(() => this.days().some((d) => d.isToday));

  readonly techName = computed(() => {
    const id = this.techFilter();
    if (!id) return '';
    const t = this.technicians().find((x) => x.id === id);
    return t ? t.name || t.username : 'Technician';
  });

  private nowTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (!params.keys.length) {
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { view: 'week', date: toDateInputValue(new Date()) },
          replaceUrl: true,
        });
        return;
      }
      this.view.set((params.get('view') as CalView) === 'day' ? 'day' : 'week');
      this.anchor.set(params.get('date') || toDateInputValue(new Date()));
      this.techFilter.set(params.get('technicianId') ?? '');
      this.statusFilter.set(params.get('status') ?? '');
      this.q.set(params.get('q') ?? '');
      if (this.searchCtrl.value !== this.q()) {
        this.searchCtrl.setValue(this.q(), { emitEvent: false });
      }
      this.fetch(false);
    });

    this.searchCtrl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.patchQuery({ q: q.trim() || null }));

    if (this.isStaffOrAdmin()) {
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

    this.tickNow();
    this.nowTimer = setInterval(() => this.tickNow(), 60_000);
    this.destroyRef.onDestroy(() => {
      if (this.nowTimer) clearInterval(this.nowTimer);
    });
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
    if (event.key === 'PageDown') {
      event.preventDefault();
      this.shiftRange(1);
    } else if (event.key === 'PageUp') {
      event.preventDefault();
      this.shiftRange(-1);
    }
  }

  setView(view: CalView): void {
    this.patchQuery({ view, date: this.anchor() });
  }

  shiftRange(delta: number): void {
    const anchor = parseAnchor(this.anchor());
    const next = this.view() === 'day' ? addDays(anchor, delta) : addDays(anchor, delta * 7);
    this.patchQuery({ date: toDateInputValue(next) });
  }

  goToday(): void {
    this.patchQuery({ date: toDateInputValue(new Date()) });
  }

  onTechChange(event: Event): void {
    const v = (event.target as HTMLSelectElement).value;
    this.patchQuery({ technicianId: v || null });
  }

  onStatusChange(event: Event): void {
    const v = (event.target as HTMLSelectElement).value;
    this.patchQuery({ status: v || null });
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.patchQuery({ q: null });
  }

  clearFilters(): void {
    this.searchCtrl.setValue('', { emitEvent: false });
    this.patchQuery({ technicianId: null, status: null, q: null });
  }

  clearTech(): void {
    this.patchQuery({ technicianId: null });
  }

  clearStatus(): void {
    this.patchQuery({ status: null });
  }

  refresh(): void {
    this.fetch(true);
  }

  retry(): void {
    this.fetch(false);
  }

  togglePanel(): void {
    this.panelOpen.update((v) => !v);
    localStorage.setItem(PANEL_KEY, this.panelOpen() ? '1' : '0');
  }

  apptTooltip(a: AppointmentSummary): string {
    return [
      `${formatTimeOnly(a.startTime)}${a.endTime ? ' – ' + formatTimeOnly(a.endTime) : ''}`,
      a.referenceNumber,
      a.customerName || '',
      a.technicianName || '',
      a.deviceCategoryName || '',
      statusLabel(a.status),
    ]
      .filter(Boolean)
      .join(' · ');
  }

  apptAria(a: AppointmentSummary): string {
    return this.apptTooltip(a);
  }

  blockStyle(item: LaidOutAppt): Record<string, string> {
    const width = 100 / item.colCount;
    return {
      top: `${item.topPct}%`,
      height: `${item.heightPct}%`,
      left: `calc(${item.colIndex * width}% + 2px)`,
      width: `calc(${width}% - 4px)`,
    };
  }

  private tickNow(): void {
    const a = this.axis();
    this.nowPct.set(nowLinePct(a.start, a.end));
  }

  private fetch(isRefresh: boolean): void {
    if (isRefresh && this.state() === 'ready') this.refetching.set(true);
    else this.state.set('loading');

    const { from, to } = rangeForView(this.view(), parseAnchor(this.anchor()));
    const appts$ = this.isTechnician()
      ? this.apptsApi.mySchedule(from, to)
      : this.apptsApi.calendar(from, to);

    const config$ = this.settingsApi.getSchedulingConfig().pipe(
      catchError(() => {
        this.configNotice.set(true);
        return of(null);
      }),
    );

    const unscheduled$ = this.isStaffOrAdmin()
      ? forkJoin({
          created: this.jobsApi.list({ status: 'CREATED', size: 50, sort: 'updatedAt,asc' }),
          assigned: this.jobsApi.list({ status: 'ASSIGNED', size: 50, sort: 'updatedAt,asc' }),
        }).pipe(
          catchError(() => of({ created: { content: [] }, assigned: { content: [] } })),
        )
      : of({ created: { content: [] as JobSummary[] }, assigned: { content: [] as JobSummary[] } });

    forkJoin({ appts: appts$, config: config$, unsched: unscheduled$ })
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: ({ appts, config, unsched }) => {
          this.allAppts.set(appts ?? []);
          if (config) {
            this.config.set(config);
            this.configNotice.set(false);
          }
          const merged = [...(unsched.created.content ?? []), ...(unsched.assigned.content ?? [])];
          merged.sort((a, b) => (a.updatedAt || '').localeCompare(b.updatedAt || ''));
          this.unscheduled.set(merged);
          this.error.set(null);
          this.state.set('ready');
          this.tickNow();
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
        },
      });
  }

  private patchQuery(patch: Record<string, string | null>): void {
    const current = { ...this.route.snapshot.queryParams };
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') delete current[k];
      else current[k] = v;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: current });
  }
}
