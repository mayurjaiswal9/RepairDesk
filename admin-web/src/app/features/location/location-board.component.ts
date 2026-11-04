import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LocationApi, LocationBoard } from '../../core/api/location.api';
import { SessionService } from '../../core/auth/session.service';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { googleMapsDirectionsUrl } from '../../core/maps/directions';
import { MapPin, PinMapComponent } from '../../core/maps/pin-map.component';

type PageState = 'loading' | 'ready' | 'error';
type Technician = LocationBoard['technicians'][number];

/** A position counts as stale after the matching update interval plus this grace. */
const STALE_GRACE_MS = 15 * 60_000;

export interface PresenceLabel {
  text: string;
  tone: 'travelling' | 'onsite' | 'stale';
}

@Component({
  selector: 'app-location-board',
  standalone: true,
  imports: [RouterLink, PinMapComponent, DatePipe],
  templateUrl: './location-board.component.html',
  styleUrl: './location-board.component.scss',
})
export class LocationBoardComponent implements OnInit {
  private readonly api = inject(LocationApi);
  private readonly session = inject(SessionService);

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly board = signal<LocationBoard | null>(null);
  readonly travelPingSeconds = signal(120);
  readonly onSitePingMinutes = signal(60);
  readonly technicianId = signal('');
  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');

  readonly technicians = computed(() => this.board()?.technicians ?? []);
  readonly visibleTechnicians = computed(() => {
    const id = this.technicianId();
    const list = this.technicians();
    return id ? list.filter((tech) => tech.id === id) : list;
  });
  readonly visibleJobs = computed(() => {
    const board = this.board();
    if (!board) return [];
    const id = this.technicianId();
    return id ? board.jobs.filter((job) => job.technicianId === id) : board.jobs;
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
    for (const tech of this.visibleTechnicians()) {
      if (tech.latitude == null || tech.longitude == null) continue;
      pins.push({
        id: tech.id,
        latitude: tech.latitude,
        longitude: tech.longitude,
        title: tech.name,
        kind: 'technician',
      });
    }
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
    return pins;
  });

  readonly locatedTechnicians = computed(
    () => this.visibleTechnicians().filter((tech) => tech.latitude != null),
  );

  onTechnicianInput(event: Event): void {
    this.technicianId.set((event.target as HTMLSelectElement).value);
  }

  canLiveTrack(tech: Technician): boolean {
    return this.isAdmin() && tech.state === 'TRAVELLING' && !!tech.jobId;
  }

  jobReference(jobId?: string | null): string | null {
    if (!jobId) return null;
    return this.board()?.jobs.find((job) => job.id === jobId)?.referenceNumber ?? null;
  }

  ngOnInit(): void {
    this.api.config().subscribe({
      next: (config) => {
        if (config.travelPingSeconds) this.travelPingSeconds.set(config.travelPingSeconds);
        if (config.onSitePingMinutes) this.onSitePingMinutes.set(config.onSitePingMinutes);
      },
      error: () => undefined,
    });
    this.reload();
  }

  reload(): void {
    this.state.set('loading');
    this.api.board().subscribe({
      next: (board) => {
        this.board.set(board);
        this.state.set('ready');
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

  /** Null when the technician has no state; the board then shows the plain time. */
  presenceLabel(tech: Technician): PresenceLabel | null {
    if (!tech.state || !tech.recordedAt) return null;
    const recorded = new Date(tech.recordedAt).getTime();
    if (Number.isNaN(recorded)) return null;
    const age = Math.max(0, Date.now() - recorded);
    const job = this.jobReference(tech.jobId);
    if (tech.state === 'TRAVELLING' && age <= this.travelPingSeconds() * 1000 + STALE_GRACE_MS) {
      return { text: `Travelling${job ? ` to ${job}` : ''} · ${agoLabel(age)}`, tone: 'travelling' };
    }
    if (tech.state === 'ON_SITE' && age <= this.onSitePingMinutes() * 60_000 + STALE_GRACE_MS) {
      return { text: `On site${job ? ` at ${job}` : ''} · ${agoLabel(age)}`, tone: 'onsite' };
    }
    return { text: `Stale · last seen ${lastSeenLabel(tech.recordedAt)}`, tone: 'stale' };
  }

  etaLabel(job: LocationBoard['jobs'][number]): string {
    if (job.etaMinutes == null) return 'No ETA yet';
    const from = job.etaFrom === 'TECHNICIAN' ? 'from the technician' : 'from the office';
    const estimate = job.etaEstimated ? 'estimated ' : '';
    const km = job.distanceMetres == null ? '' : ` · ${(job.distanceMetres / 1000).toFixed(1)} km`;
    return `${estimate}${job.etaMinutes} min ${from}${km}`;
  }
}

function agoLabel(ms: number): string {
  const minutes = Math.floor(ms / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min ago`;
}

/** `HH:mm` India time, with the date when it was not today. */
function lastSeenLabel(iso: string): string {
  const date = new Date(iso);
  const day = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(d);
  const time = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date);
  if (day(date) === day(new Date())) return time;
  const shortDate = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short' }).format(date);
  return `${shortDate} ${time}`;
}
