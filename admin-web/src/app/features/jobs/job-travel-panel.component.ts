import { Component, Input, OnChanges, SimpleChanges, computed, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MileageApi, JobTravelResponse, TripResponse } from '../../core/api/mileage.api';
import { mapHttpError } from '../../core/api/api-error';
import { RouteMapComponent } from '../../core/maps/route-map.component';
import { AdjustDistanceDialogComponent } from '../mileage/adjust-distance-dialog.component';
import { TripCardComponent } from '../mileage/trip-card.component';
import { tripPins, tripRoutes } from '../mileage/trip-map';
import { formatKm, indiaDateTime, noTripReasonLabel } from '../mileage/mileage-format';

type PanelState = 'loading' | 'ready' | 'hidden' | 'error';

@Component({
  selector: 'app-job-travel-panel',
  standalone: true,
  imports: [MatDialogModule, MatSnackBarModule, RouteMapComponent, TripCardComponent],
  template: `
    @if (state() === 'error') {
      <section class="card" aria-labelledby="travel-heading">
        <h2 id="travel-heading" class="section-title">Travel</h2>
        <p class="muted">Travel details unavailable.</p>
        <button type="button" class="link-btn" (click)="load()">Retry</button>
      </section>
    } @else if (state() === 'ready' && travel()) {
      @let t = travel()!;
      <section class="card" aria-labelledby="travel-heading">
        <h2 id="travel-heading" class="section-title">
          Travel
          @if (t.trips.length) {
            <span class="count">· {{ km(payableTotal()) }} km</span>
          }
        </h2>
        @if (t.trips.length) {
          <app-route-map [routes]="routes()" [pins]="pins()" [height]="240" />
          <p class="legend">Numbers mark where each trip started · the red pin is the job</p>
          <div class="trips">
            @for (trip of t.trips; track trip.id; let i = $index) {
              <app-trip-card
                [trip]="trip"
                [index]="i"
                [showJob]="false"
                [showTechnician]="true"
                [canAdjust]="true"
                (adjust)="openAdjust($event)"
              />
            }
          </div>
        }
        @if (t.noTrip; as n) {
          <p class="no-trip">
            <strong>Started without a trip:</strong> {{ reasonLabel(n.reasonCode, n.reasonText) }}
            <span class="muted">· {{ n.technicianName || 'Technician' }}, {{ when(n.createdAt) }} · no km paid</span>
          </p>
        }
      </section>
    }
  `,
  styles: `
    :host { display: contents; }
    .card {
      background: var(--color-surface);
      border: 1px solid var(--color-divider);
      border-radius: var(--radius-l);
      padding: var(--space-m) var(--space-l);
    }
    .section-title { margin: 0 0 12px; font-size: 15px; font-weight: 700; }
    .count { font-weight: 600; color: var(--color-text-secondary); }
    .muted, .legend { color: var(--color-text-secondary); font-size: 12px; }
    .muted { margin: 0; }
    .legend { margin: 6px 0 12px; }
    .no-trip {
      margin: 12px 0 0; padding: 10px 12px; border-radius: var(--radius-m);
      background: #fff3e0; font-size: 14px;
    }
    .link-btn {
      padding: 0; border: none; background: none; color: var(--color-primary);
      font: inherit; font-weight: 600; font-size: 13px; cursor: pointer;
    }
  `,
})
export class JobTravelPanelComponent implements OnChanges {
  private readonly api = inject(MileageApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  @Input({ required: true }) jobId!: string;
  /** Any change (e.g. the job's updatedAt) reloads the trips. */
  @Input() refreshKey: string | null | undefined;

  readonly km = formatKm;
  readonly when = indiaDateTime;
  readonly reasonLabel = noTripReasonLabel;

  readonly state = signal<PanelState>('loading');
  readonly travel = signal<JobTravelResponse | null>(null);
  readonly routes = computed(() => tripRoutes(this.travel()?.trips ?? []));
  readonly pins = computed(() => tripPins(this.travel()?.trips ?? []));
  readonly payableTotal = computed(() =>
    (this.travel()?.trips ?? [])
      .filter((t) => t.status !== 'CANCELLED')
      .reduce((sum, t) => sum + t.payableDistanceM, 0),
  );

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['jobId'] || changes['refreshKey']) this.load();
  }

  load(): void {
    if (!this.jobId) return;
    const jobId = this.jobId;
    if (!this.travel()) this.state.set('loading');
    this.api.jobTrips(jobId).subscribe({
      next: (travel) => {
        if (jobId !== this.jobId) return;
        this.travel.set(travel);
        const empty = !(travel?.trips?.length) && !travel?.noTrip;
        this.state.set(empty ? 'hidden' : 'ready');
      },
      error: (err) => {
        if (jobId !== this.jobId) return;
        const status = mapHttpError(err).status;
        this.travel.set(null);
        this.state.set(status === 403 || status === 404 ? 'hidden' : 'error');
      },
    });
  }

  openAdjust(trip: TripResponse): void {
    this.dialog
      .open(AdjustDistanceDialogComponent, { width: '520px', data: { trip }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((updated: TripResponse | undefined) => {
        if (!updated) return;
        this.snack.open(`Km updated for ${updated.jobReference}`, undefined, { duration: 4000 });
        this.load();
      });
  }
}
