import {
  Component,
  DestroyRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { RequestsApi } from '../../core/api/requests.api';
import { googleMapsDirectionsUrl } from '../../core/maps/directions';
import { MapPin, PinMapComponent } from '../../core/maps/pin-map.component';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { objectUrlForPhoto } from '../../core/api/photo-blob';
import { SessionService } from '../../core/auth/session.service';
import {
  AttachmentMeta,
  RepairRequestDetail,
  ServiceLocation,
  formatAbsolute,
  formatDateOnly,
  formatTimeOnly,
  maskSerial,
  statusChipClass,
  statusLabel,
  warrantyLabel,
} from '../../core/models/request.models';
import { ApproveDialogComponent } from './dialogs/approve-dialog.component';
import { RejectDialogComponent } from './dialogs/reject-dialog.component';
import { RequestInfoDialogComponent } from './dialogs/request-info-dialog.component';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden' | 'notfound';

interface PhotoTile {
  meta: AttachmentMeta;
  objectUrl: string | null;
  loading: boolean;
  error: boolean;
}

@Component({
  selector: 'app-request-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    PinMapComponent,
  ],
  templateUrl: './request-detail.component.html',
  styleUrl: './request-detail.component.scss',
})
export class RequestDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(RequestsApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;
  readonly warrantyLabel = warrantyLabel;
  readonly formatAbsolute = formatAbsolute;
  readonly formatDateOnly = formatDateOnly;
  readonly formatTimeOnly = formatTimeOnly;

  requestPins(location: ServiceLocation): MapPin[] {
    if (location.latitude == null || location.longitude == null) return [];
    return [{
      id: 'request',
      latitude: location.latitude,
      longitude: location.longitude,
      title: 'Service address',
      kind: 'job',
    }];
  }

  directionsUrl(location: ServiceLocation | null | undefined): string | null {
    if (!location) return null;
    return googleMapsDirectionsUrl({
      latitude: location.latitude,
      longitude: location.longitude,
      address: [location.addressLine, location.landmark, location.city, location.pincode]
        .filter((part): part is string => !!part && part.trim().length > 0)
        .join(', '),
    });
  }

  serviceAddress(location: ServiceLocation): string {
    const parts = [location.addressLine, location.landmark, location.city, location.pincode]
      .map((part) => part?.trim())
      .filter((part): part is string => !!part);
    const text = parts.join(', ');
    if (location.latitude == null || location.longitude == null) return text || 'Address saved';
    const pin = location.pinSource === 'MANUAL_PIN'
      ? 'Pin placed on the map'
      : location.pinSource === 'CUSTOMER_LINK'
        ? 'Pin from the customer’s location link'
        : 'Pin found from the address';
    return text ? `${text} · ${pin}` : pin;
  }

  readonly state = signal<PageState>('loading');
  readonly refetching = signal(false);
  readonly detail = signal<RepairRequestDetail | null>(null);
  readonly error = signal<MappedApiError | null>(null);
  readonly photos = signal<PhotoTile[]>([]);
  readonly serialRevealed = signal(false);
  readonly staleMark = signal(false);
  readonly lightboxIndex = signal<number | null>(null);

  /** Queue query params from list navigation, if present. */
  readonly returnQuery = signal<Record<string, string>>({});

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly canDecide = computed(() => {
    const s = this.detail()?.status;
    return s === 'PENDING_REVIEW' || s === 'INFORMATION_REQUIRED';
  });
  readonly canRequestInfo = computed(() => this.detail()?.status === 'PENDING_REVIEW');
  readonly showPriority = computed(() => {
    const p = this.detail()?.priority;
    return p === 'HIGH' || p === 'URGENT';
  });
  readonly deviceModel = computed(() => {
    const d = this.detail();
    if (!d) return '—';
    const parts = [d.deviceBrand, d.deviceModel].filter(Boolean);
    return parts.length ? parts.join(' ') : '—';
  });
  readonly serialDisplay = computed(() => {
    const serial = this.detail()?.deviceSerial;
    if (!serial) return null;
    return this.serialRevealed() ? serial : maskSerial(serial);
  });
  readonly resolvedLine = computed(() => {
    switch (this.detail()?.status) {
      case 'APPROVED':
        return 'This request was approved — no further decisions are available.';
      case 'REJECTED':
        return 'This request was rejected — no further decisions are available.';
      case 'CANCELLED':
        return 'This request was cancelled by the customer.';
      default:
        return null;
    }
  });
  readonly lightboxPhoto = computed(() => {
    const i = this.lightboxIndex();
    if (i === null) return null;
    return this.photos()[i] ?? null;
  });

  private requestId = '';

  ngOnInit(): void {
    const nav = this.router.getCurrentNavigation();
    const state =
      (nav?.extras.state as { returnQuery?: Record<string, string> } | undefined) ??
      (history.state as { returnQuery?: Record<string, string> } | undefined);
    if (state?.returnQuery) {
      this.returnQuery.set(state.returnQuery);
    }

    this.route.paramMap
      .pipe(
        tap((params) => {
          this.requestId = params.get('id') ?? '';
          this.serialRevealed.set(false);
          this.closeLightbox();
        }),
        switchMap(() => this.fetch$(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.revokeAllPhotos();
  }

  @HostListener('window:blur')
  onWindowBlur(): void {
    this.serialRevealed.set(false);
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange(): void {
    if (document.visibilityState === 'hidden') {
      this.serialRevealed.set(false);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this.lightboxIndex() === null) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeLightbox();
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.lightboxNext();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.lightboxPrev();
    }
  }

  refresh(): void {
    this.fetch$(true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  retry(): void {
    this.fetch$(false).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  toggleSerial(): void {
    this.serialRevealed.update((v) => !v);
  }

  openApprove(): void {
    const row = this.detail();
    if (!row) return;
    this.dialog
      .open(ApproveDialogComponent, { width: '480px', data: { row }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) =>
        this.afterDecision(result, `${row.referenceNumber} approved · job created`),
      );
  }

  openReject(): void {
    const row = this.detail();
    if (!row) return;
    this.dialog
      .open(RejectDialogComponent, { width: '560px', data: { row }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => this.afterDecision(result, `${row.referenceNumber} rejected`));
  }

  openInfo(): void {
    const row = this.detail();
    if (!row) return;
    this.dialog
      .open(RequestInfoDialogComponent, {
        width: '480px',
        data: { row },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) =>
        this.afterDecision(
          result,
          `Information requested from ${row.contactName || row.customerUsername}`,
        ),
      );
  }

  openLightbox(index: number): void {
    const tile = this.photos()[index];
    if (!tile?.objectUrl) return;
    this.lightboxIndex.set(index);
  }

  closeLightbox(): void {
    this.lightboxIndex.set(null);
  }

  lightboxNext(): void {
    const loaded = this.photos()
      .map((p, i) => ({ p, i }))
      .filter((x) => x.p.objectUrl);
    if (!loaded.length) return;
    const current = this.lightboxIndex() ?? 0;
    const pos = loaded.findIndex((x) => x.i === current);
    const next = loaded[(pos + 1) % loaded.length];
    this.lightboxIndex.set(next.i);
  }

  lightboxPrev(): void {
    const loaded = this.photos()
      .map((p, i) => ({ p, i }))
      .filter((x) => x.p.objectUrl);
    if (!loaded.length) return;
    const current = this.lightboxIndex() ?? 0;
    const pos = loaded.findIndex((x) => x.i === current);
    const prev = loaded[(pos - 1 + loaded.length) % loaded.length];
    this.lightboxIndex.set(prev.i);
  }

  retryPhoto(index: number): void {
    const tiles = [...this.photos()];
    const tile = tiles[index];
    if (!tile) return;
    if (tile.objectUrl) {
      URL.revokeObjectURL(tile.objectUrl);
    }
    tiles[index] = { ...tile, loading: true, error: false, objectUrl: null };
    this.photos.set(tiles);
    this.fetchPhotoBlob(index, tile.meta);
  }

  priorityLabel(priority: string): string {
    return priority.charAt(0) + priority.slice(1).toLowerCase();
  }

  private afterDecision(result: boolean | 'reload' | undefined, toast: string): void {
    if (result === 'reload') {
      this.staleMark.set(true);
      setTimeout(() => this.staleMark.set(false), 5000);
      this.refresh();
      return;
    }
    if (result === true) {
      this.snack.open(toast, undefined, { duration: 4000 });
      this.refresh();
    }
  }

  private fetch$(isRefresh: boolean): Observable<void> {
    if (!this.isAdmin()) {
      this.revokeAllPhotos();
      this.detail.set(null);
      this.photos.set([]);
      this.state.set('forbidden');
      this.title.setTitle('Request detail · RepairDesk');
      return of(void 0);
    }

    if (!this.requestId) {
      this.state.set('notfound');
      return of(void 0);
    }

    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
      this.revokeAllPhotos();
      this.photos.set([]);
    }

    return this.api.get(this.requestId).pipe(
      switchMap((detail) =>
        forkJoin({
          detail: of(detail),
          attachments: this.api.listAttachments(this.requestId).pipe(
            catchError(() => of([] as AttachmentMeta[])),
          ),
        }),
      ),
      tap({
        next: ({ detail, attachments }) => {
          this.detail.set(detail);
          this.error.set(null);
          this.state.set('ready');
          this.title.setTitle(`${detail.referenceNumber} · RepairDesk`);
          this.bootstrapPhotos(attachments ?? []);
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (isRefresh && this.detail()) {
            // Keep content; surface banner via error + refetching clear
            this.state.set('ready');
          } else {
            this.detail.set(null);
            this.revokeAllPhotos();
            this.photos.set([]);
            if (mapped.status === 403) this.state.set('forbidden');
            else if (mapped.status === 404) this.state.set('notfound');
            else this.state.set('error');
          }
          this.title.setTitle(
            this.detail()
              ? `${this.detail()!.referenceNumber} · RepairDesk`
              : 'Request detail · RepairDesk',
          );
        },
      }),
      map(() => void 0),
      catchError(() => of(void 0)),
      finalize(() => this.refetching.set(false)),
    );
  }

  private bootstrapPhotos(metas: AttachmentMeta[]): void {
    this.revokeAllPhotos();
    const images = metas.filter((m) => (m.contentType || '').startsWith('image/'));
    const tiles: PhotoTile[] = images.map((meta) => ({
      meta,
      objectUrl: null,
      loading: true,
      error: false,
    }));
    this.photos.set(tiles);
    tiles.forEach((_, index) => this.fetchPhotoBlob(index, images[index]));
  }

  private fetchPhotoBlob(index: number, meta: AttachmentMeta): void {
    this.api.downloadAttachmentById(meta.id).subscribe({
      next: (blob) => {
        const current = this.photos();
        if (!current[index] || current[index].meta.id !== meta.id) return;
        const objectUrl = objectUrlForPhoto(blob, meta.contentType);
        const next = [...current];
        next[index] = { meta, objectUrl, loading: false, error: false };
        this.photos.set(next);
      },
      error: () => {
        const current = this.photos();
        if (!current[index] || current[index].meta.id !== meta.id) return;
        const next = [...current];
        next[index] = { meta, objectUrl: null, loading: false, error: true };
        this.photos.set(next);
      },
    });
  }

  private revokeAllPhotos(): void {
    for (const tile of this.photos()) {
      if (tile.objectUrl) URL.revokeObjectURL(tile.objectUrl);
    }
  }
}
