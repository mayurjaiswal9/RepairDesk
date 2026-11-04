import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import {
  MileageApi,
  MileageDaysResponse,
  MileageExportLevel,
  MileageQuery,
  MileageRow,
  MileageShow,
  MileageView,
  VehicleType,
} from '../../core/api/mileage.api';
import { LocationApi } from '../../core/api/location.api';
import { UsersApi } from '../../core/api/users.api';
import { SessionService } from '../../core/auth/session.service';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { UserProfile } from '../../core/models/dashboard.models';
import { MileageSummaryComponent } from './mileage-summary.component';
import {
  DATE_PRESETS,
  DatePreset,
  dateLabel,
  downloadBlob,
  formatKm,
  formatRate,
  formatRupees,
  formatSignedKm,
  monthLabel,
  monthRange,
  presetRange,
  rangeProblem,
  vehicleLabel,
} from './mileage-format';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';
type Preset = DatePreset | 'custom';

const PRESET_KEYS = DATE_PRESETS.map((p) => p.key) as string[];

@Component({
  selector: 'app-mileage-list',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MileageSummaryComponent,
  ],
  templateUrl: './mileage-list.component.html',
  styleUrl: './mileage-list.component.scss',
})
export class MileageListComponent implements OnInit {
  private readonly api = inject(MileageApi);
  private readonly locationApi = inject(LocationApi);
  private readonly usersApi = inject(UsersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly presets = DATE_PRESETS;
  readonly km = formatKm;
  readonly signedKm = formatSignedKm;
  readonly money = formatRupees;
  readonly rate = formatRate;
  readonly vehicle = vehicleLabel;
  readonly dateLabel = dateLabel;
  readonly monthLabel = monthLabel;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly exporting = signal(false);
  readonly error = signal<MappedApiError | null>(null);
  readonly data = signal<MileageDaysResponse | null>(null);
  readonly technicians = signal<UserProfile[]>([]);
  readonly travelEnabled = signal(true);
  readonly rangeError = signal<string | null>(null);

  readonly preset = signal<Preset>('today');
  readonly from = signal('');
  readonly to = signal('');
  readonly technicianId = signal('');
  readonly vehicleType = signal<VehicleType | ''>('');
  readonly show = signal<MileageShow>('ALL');
  readonly view = signal<MileageView>('DAY');

  readonly rows = computed(() => this.data()?.rows ?? []);
  readonly isMonth = computed(() => (this.data()?.view ?? this.view()) === 'MONTH');
  readonly totals = computed(() =>
    this.rows().reduce(
      (sum, row) => ({
        trips: sum.trips + row.trips,
        activeTrips: sum.activeTrips + row.activeTrips,
        trackedDistanceM: sum.trackedDistanceM + row.trackedDistanceM,
        adjustmentM: sum.adjustmentM + row.adjustmentM,
        payableDistanceM: sum.payableDistanceM + row.payableDistanceM,
        amountPaise: sum.amountPaise + row.amountPaise,
        flaggedTrips: sum.flaggedTrips + row.flaggedTrips,
        noTripJobs: sum.noTripJobs + row.noTripJobs,
      }),
      {
        trips: 0,
        activeTrips: 0,
        trackedDistanceM: 0,
        adjustmentM: 0,
        payableDistanceM: 0,
        amountPaise: 0,
        flaggedTrips: 0,
        noTripJobs: 0,
      },
    ),
  );

  ngOnInit(): void {
    if (this.session.role() !== 'ADMIN') {
      this.state.set('forbidden');
      return;
    }
    this.usersApi.listByRole('TECHNICIAN').subscribe({
      next: (list) =>
        this.technicians.set(
          [...(list ?? [])].sort((a, b) => (a.name || a.username).localeCompare(b.name || b.username)),
        ),
      error: () => undefined,
    });
    this.locationApi.config().subscribe({
      next: (config) => this.travelEnabled.set(config.travelEnabled),
      error: () => this.travelEnabled.set(true),
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.readParams(params);
      this.fetch(false);
    });
  }

  refresh(): void {
    this.fetch(true);
  }

  setPreset(preset: DatePreset): void {
    this.patchQuery({ preset: preset === 'today' ? null : preset, from: null, to: null });
  }

  onFromChange(event: Event): void {
    this.patchQuery({ preset: 'custom', from: (event.target as HTMLInputElement).value, to: this.to() });
  }

  onToChange(event: Event): void {
    this.patchQuery({ preset: 'custom', from: this.from(), to: (event.target as HTMLInputElement).value });
  }

  onTechnicianChange(event: Event): void {
    this.patchQuery({ technicianId: (event.target as HTMLSelectElement).value || null });
  }

  onVehicleChange(event: Event): void {
    this.patchQuery({ vehicle: (event.target as HTMLSelectElement).value || null });
  }

  onShowChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.patchQuery({ show: value === 'ALL' ? null : value });
  }

  setView(view: MileageView): void {
    this.patchQuery({ view: view === 'DAY' ? null : view });
  }

  openRow(row: MileageRow): void {
    if (row.date) {
      void this.router.navigate(['/mileage', row.technicianId, row.date], {
        state: { returnQuery: this.returnQuery() },
      });
    } else if (row.month) {
      const range = monthRange(row.month);
      this.patchQuery({
        view: null,
        preset: 'custom',
        from: range.from,
        to: range.to,
        technicianId: row.technicianId,
      });
    }
  }

  exportCsv(level: MileageExportLevel): void {
    const problem = rangeProblem(this.from(), this.to());
    this.rangeError.set(problem);
    if (problem) return;
    this.exporting.set(true);
    this.api
      .export(this.query(), level)
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: (blob) => {
          const kind = level === 'TRIP' ? 'trips' : 'days';
          downloadBlob(blob, `mileage-${kind}-${this.from()}-to-${this.to()}.csv`);
        },
        error: (err) => {
          const mapped = mapHttpError(err, 'Could not download the mileage file.');
          this.snack.open(mapped.message, 'Dismiss', { duration: 5000 });
        },
      });
  }

  /** Snapshot for the day detail breadcrumb to restore these filters. */
  returnQuery(): Record<string, string> {
    const q: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.route.snapshot.queryParams)) {
      if (v != null && v !== '') q[k] = String(v);
    }
    return q;
  }

  rowKey(row: MileageRow): string {
    return `${row.technicianId}:${row.date ?? row.month}`;
  }

  private readParams(params: ParamMap): void {
    const presetParam = params.get('preset') ?? 'today';
    const from = params.get('from') ?? '';
    const to = params.get('to') ?? '';
    if (presetParam === 'custom') {
      this.preset.set('custom');
      this.from.set(from);
      this.to.set(to);
    } else {
      const preset = (PRESET_KEYS.includes(presetParam) ? presetParam : 'today') as DatePreset;
      const range = presetRange(preset);
      this.preset.set(preset);
      this.from.set(range.from);
      this.to.set(range.to);
    }
    this.technicianId.set(params.get('technicianId') ?? '');
    const vehicle = params.get('vehicle');
    this.vehicleType.set(vehicle === 'TWO_WHEELER' || vehicle === 'FOUR_WHEELER' ? vehicle : '');
    const show = params.get('show');
    this.show.set(show === 'FLAGGED' || show === 'NO_TRIP' ? show : 'ALL');
    this.view.set(params.get('view') === 'MONTH' ? 'MONTH' : 'DAY');
  }

  private query(): MileageQuery {
    return {
      from: this.from(),
      to: this.to(),
      technicianId: this.technicianId() || null,
      vehicle: this.vehicleType() || null,
      show: this.show(),
      view: this.view(),
    };
  }

  private fetch(isRefresh: boolean): void {
    const problem = rangeProblem(this.from(), this.to());
    this.rangeError.set(problem);
    if (problem) {
      this.data.set(null);
      this.state.set('ready');
      return;
    }
    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
    }
    this.api
      .days(this.query())
      .pipe(finalize(() => this.refetching.set(false)))
      .subscribe({
        next: (data) => {
          this.data.set(data);
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

  private patchQuery(patch: Record<string, string | null>): void {
    const current = { ...this.route.snapshot.queryParams };
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') delete current[k];
      else current[k] = v;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: current });
  }
}
