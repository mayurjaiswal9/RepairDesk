import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MileageApi, TripResponse } from '../../core/api/mileage.api';
import { SessionService } from '../../core/auth/session.service';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { RouteMapComponent } from '../../core/maps/route-map.component';
import { tripPins, tripRoutes } from '../mileage/trip-map';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-live-track',
  standalone: true,
  imports: [RouterLink, RouteMapComponent],
  templateUrl: './live-track.component.html',
  styleUrl: './live-track.component.scss',
})
export class LiveTrackComponent implements OnInit {
  private readonly api = inject(MileageApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly technicianId = signal('');
  readonly jobId = signal('');
  readonly trip = signal<TripResponse | null>(null);
  readonly technicianName = signal('');
  readonly jobReference = signal('');

  readonly routes = computed(() => {
    const trip = this.trip();
    return trip ? tripRoutes([trip]) : [];
  });
  readonly pins = computed(() => {
    const trip = this.trip();
    return trip ? tripPins([trip]) : [];
  });
  readonly hasTrack = computed(() => {
    const trip = this.trip();
    return !!trip && ((trip.route?.length ?? 0) > 0 || !!trip.start || !!trip.destination);
  });

  ngOnInit(): void {
    if (this.session.role() !== 'ADMIN') {
      this.state.set('forbidden');
      return;
    }
    this.technicianId.set(this.route.snapshot.paramMap.get('technicianId') ?? '');
    this.jobId.set(this.route.snapshot.paramMap.get('jobId') ?? '');
    this.reload();
  }

  reload(): void {
    if (this.session.role() !== 'ADMIN') {
      this.state.set('forbidden');
      return;
    }
    const jobId = this.jobId();
    const technicianId = this.technicianId();
    if (!jobId || !technicianId) {
      this.trip.set(null);
      this.state.set('ready');
      return;
    }
    this.state.set('loading');
    this.api.jobTrips(jobId).subscribe({
      next: (travel) => {
        const mine = travel.trips.filter((trip) => trip.technicianId === technicianId);
        const live =
          mine.find((trip) => trip.status === 'ACTIVE') ??
          [...mine].sort((a, b) => b.startedAt.localeCompare(a.startedAt))[0] ??
          null;
        this.trip.set(live?.status === 'ACTIVE' ? live : null);
        this.technicianName.set(live?.technicianName || '');
        this.jobReference.set(live?.jobReference || mine[0]?.jobReference || '');
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
}
