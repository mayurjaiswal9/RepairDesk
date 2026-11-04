import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, catchError, combineLatest, finalize, map, of, switchMap, tap } from 'rxjs';
import { MileageApi, MileageDayDetail, TripResponse } from '../../core/api/mileage.api';
import { SessionService } from '../../core/auth/session.service';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { RouteMapComponent } from '../../core/maps/route-map.component';
import { AdjustDistanceDialogComponent } from './adjust-distance-dialog.component';
import { MileageSummaryComponent } from './mileage-summary.component';
import { TripCardComponent } from './trip-card.component';
import { tripPins, tripRoutes } from './trip-map';
import { dateLabel, indiaClock, noTripReasonLabel, vehicleLabel } from './mileage-format';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden' | 'notfound';

@Component({
  selector: 'app-mileage-day',
  standalone: true,
  imports: [
    RouterLink,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MileageSummaryComponent,
    RouteMapComponent,
    TripCardComponent,
  ],
  templateUrl: './mileage-day.component.html',
  styleUrl: './mileage-day.component.scss',
})
export class MileageDayComponent implements OnInit {
  private readonly api = inject(MileageApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly vehicle = vehicleLabel;
  readonly clock = indiaClock;
  readonly reasonLabel = noTripReasonLabel;

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly error = signal<MappedApiError | null>(null);
  readonly detail = signal<MileageDayDetail | null>(null);
  readonly technicianId = signal('');
  readonly date = signal('');
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly selectedTripId = signal<string | null>(null);
  readonly returnQuery = signal<Record<string, string>>({});

  readonly dateText = computed(() => {
    const from = this.fromDate() || this.date();
    const to = this.toDate() || from;
    if (!from) return '';
    if (from === to) return dateLabel(from, true);
    return `${dateLabel(from, true)} – ${dateLabel(to, true)}`;
  });
  readonly selectedTrip = computed(() => {
    const id = this.selectedTripId();
    if (!id) return null;
    return this.detail()?.trips.find((trip) => trip.id === id) ?? null;
  });
  readonly routes = computed(() => {
    const trip = this.selectedTrip();
    return trip ? tripRoutes([trip]) : [];
  });
  readonly pins = computed(() => {
    const trip = this.selectedTrip();
    return trip ? tripPins([trip], this.detail()?.shop) : [];
  });

  ngOnInit(): void {
    if (this.session.role() !== 'ADMIN') {
      this.state.set('forbidden');
      return;
    }
    const state = history.state as { returnQuery?: Record<string, string> } | undefined;
    if (state?.returnQuery) this.returnQuery.set(state.returnQuery);

    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(
        tap(([params, query]) => {
          const date = params.get('date') ?? '';
          this.technicianId.set(params.get('technicianId') ?? '');
          this.date.set(date);
          this.fromDate.set(date);
          this.toDate.set(query.get('to') || date);
        }),
        switchMap(() => this.fetch$(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  refresh(): void {
    this.fetch$(true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  onFromInput(event: Event): void {
    this.fromDate.set((event.target as HTMLInputElement).value);
  }

  onToInput(event: Event): void {
    this.toDate.set((event.target as HTMLInputElement).value);
  }

  applyRange(): void {
    const from = this.fromDate();
    const to = this.toDate() || from;
    if (!from) return;
    void this.router.navigate(['/mileage', this.technicianId(), from], {
      queryParams: to && to !== from ? { to } : {},
      state: { returnQuery: this.returnQuery() },
    });
  }

  toggleTrip(trip: TripResponse): void {
    this.selectedTripId.set(this.selectedTripId() === trip.id ? null : trip.id);
  }

  openAdjust(trip: TripResponse): void {
    this.dialog
      .open(AdjustDistanceDialogComponent, { width: '520px', data: { trip }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((updated: TripResponse | undefined) => {
        if (!updated) return;
        this.snack.open(`Km updated for ${updated.jobReference}`, undefined, { duration: 4000 });
        this.refresh();
      });
  }

  private fetch$(isRefresh: boolean): Observable<void> {
    if (!this.technicianId() || !/^\d{4}-\d{2}-\d{2}$/.test(this.date())) {
      this.state.set('notfound');
      return of(void 0);
    }
    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
    }
    return this.api.day(this.technicianId(), this.date(), this.toDate() || this.date()).pipe(
      tap({
        next: (detail) => {
          this.detail.set(detail);
          const selected = this.selectedTripId();
          if (selected && !detail.trips.some((trip) => trip.id === selected)) {
            this.selectedTripId.set(null);
          }
          this.error.set(null);
          this.state.set('ready');
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (isRefresh && this.detail()) {
            this.snack.open(mapped.message, 'Dismiss', { duration: 5000 });
            this.state.set('ready');
            return;
          }
          this.detail.set(null);
          if (mapped.status === 403) this.state.set('forbidden');
          else if (mapped.status === 404) this.state.set('notfound');
          else this.state.set('error');
        },
      }),
      map(() => void 0),
      catchError(() => of(void 0)),
      finalize(() => this.refetching.set(false)),
    );
  }
}
