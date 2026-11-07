import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LocationApi, LocationBoard, LocationHistory } from '../../core/api/location.api';
import { SessionService } from '../../core/auth/session.service';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { googleMapsDirectionsUrl } from '../../core/maps/directions';
import { MapPin, PinMapComponent } from '../../core/maps/pin-map.component';

type PageState = 'loading' | 'ready' | 'error';
type LocationEntry = LocationHistory['locations'][number];

@Component({
  selector: 'app-location-board',
  standalone: true,
  imports: [RouterLink, PinMapComponent],
  templateUrl: './location-board.component.html',
  styleUrl: './location-board.component.scss',
})
export class LocationBoardComponent implements OnInit {
  private readonly api = inject(LocationApi);
  private readonly session = inject(SessionService);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly historyError = signal<string | null>(null);
  readonly board = signal<LocationBoard | null>(null);
  readonly historyDays = signal<string[]>([]);
  readonly historyDate = signal('');
  readonly locations = signal<LocationEntry[]>([]);
  readonly historyLoading = signal(false);
  readonly selectedLocationId = signal('');
  readonly technicianId = signal('');
  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');

  readonly technicians = computed(() => this.board()?.technicians ?? []);
  readonly visibleJobs = computed(() => {
    const board = this.board();
    if (!board) return [];
    const id = this.technicianId();
    return id ? board.jobs.filter((job) => job.technicianId === id) : board.jobs;
  });
  readonly visibleLocations = computed(() => {
    const id = this.technicianId();
    const list = this.locations();
    return id ? list.filter((row) => row.technicianId === id) : list;
  });
  readonly viewingToday = computed(() => {
    const days = this.historyDays();
    return !!days.length && this.historyDate() === days[0];
  });

  readonly pins = computed<MapPin[]>(() => {
    const board = this.board();
    if (!board) return [];
    const pins: MapPin[] = [];
    if (board.shop.latitude != null && board.shop.longitude != null) {
      pins.push({
        id: 'office',
        latitude: board.shop.latitude,
        longitude: board.shop.longitude,
        title: 'Office',
        kind: 'office',
      });
    }
    const selected = this.visibleLocations().find((row) => row.id === this.selectedLocationId());
    const shown = selected ? [selected] : latestPerTechnician(this.visibleLocations());
    for (const row of shown) {
      pins.push({
        id: row.id,
        latitude: row.latitude,
        longitude: row.longitude,
        title: row.technicianName,
        kind: 'technician',
      });
    }
    if (this.viewingToday()) {
      for (const job of this.visibleJobs()) {
        if (job.latitude == null || job.longitude == null) continue;
        pins.push({
          id: job.id,
          latitude: job.latitude,
          longitude: job.longitude,
          title: `${job.referenceNumber} ${job.address ?? ''}`.trim(),
          kind: 'job',
        });
      }
    }
    return pins;
  });

  onTechnicianInput(event: Event): void {
    this.technicianId.set((event.target as HTMLSelectElement).value);
    this.selectedLocationId.set('');
  }

  onDayInput(event: Event): void {
    const date = (event.target as HTMLSelectElement).value;
    this.historyDate.set(date);
    this.selectedLocationId.set('');
    this.loadHistory(date);
  }

  selectLocation(id: string): void {
    this.selectedLocationId.update((current) => (current === id ? '' : id));
  }

  canLiveTrack(row: LocationEntry): boolean {
    if (!this.viewingToday() || !this.isAdmin() || row.state !== 'TRAVELLING' || !row.jobId) return false;
    const newest = this.visibleLocations().find((item) => item.technicianId === row.technicianId);
    return newest?.id === row.id;
  }

  dayLabel(iso: string): string {
    const days = this.historyDays();
    const shown = indiaDayLabel(iso);
    if (iso === days[0]) return `Today, ${shown}`;
    if (iso === days[1]) return `Yesterday, ${shown}`;
    return shown;
  }

  clock(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kolkata',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(iso));
  }

  stateLabel(state?: string | null): string {
    if (state === 'TRAVELLING') return 'Travelling';
    if (state === 'ON_SITE') return 'On site';
    return 'Shared';
  }

  stateTone(state?: string | null): string {
    if (state === 'TRAVELLING') return 'travelling';
    if (state === 'ON_SITE') return 'onsite';
    return 'stale';
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.state.set('loading');
    this.api.board().subscribe({
      next: (board) => {
        this.board.set(board);
        this.state.set('ready');
        this.loadHistory(this.historyDate());
      },
      error: (err) => {
        this.error.set(mapHttpError(err));
        this.state.set('error');
      },
    });
  }

  directionsUrl(job: LocationBoard['jobs'][number]): string | null {
    return googleMapsDirectionsUrl({
      latitude: job.latitude,
      longitude: job.longitude,
      address: job.address,
    });
  }

  etaLabel(job: LocationBoard['jobs'][number]): string {
    if (job.etaMinutes == null) return 'No ETA yet';
    const from = job.etaFrom === 'TECHNICIAN' ? 'from the technician' : 'from the office';
    const estimate = job.etaEstimated ? 'estimated ' : '';
    const km = job.distanceMetres == null ? '' : ` · ${(job.distanceMetres / 1000).toFixed(1)} km`;
    return `${estimate}${job.etaMinutes} min ${from}${km}`;
  }

  private loadHistory(date: string): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.api.history(date || null).subscribe({
      next: (history) => {
        this.historyDays.set(history.days ?? []);
        this.historyDate.set(history.date);
        this.locations.set(history.locations ?? []);
        this.historyLoading.set(false);
      },
      error: (err) => {
        this.locations.set([]);
        this.historyError.set(mapHttpError(err).message);
        this.historyLoading.set(false);
      },
    });
  }
}

/** Newest share for each technician. The list itself stays newest-first. */
function latestPerTechnician(rows: LocationEntry[]): LocationEntry[] {
  const seen = new Set<string>();
  const latest: LocationEntry[] = [];
  for (const row of rows) {
    if (seen.has(row.technicianId)) continue;
    seen.add(row.technicianId);
    latest.push(row);
  }
  return latest;
}

function indiaDayLabel(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  if (!year || !month || !day) return iso;
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    day: 'numeric',
    month: 'short',
  }).format(new Date(Date.UTC(year, month - 1, day, 6, 30)));
}
