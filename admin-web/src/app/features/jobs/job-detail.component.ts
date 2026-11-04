import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
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
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { JobsApi } from '../../core/api/jobs.api';
import { LocationApi, PickedPlace } from '../../core/api/location.api';
import { PlaceAssistComponent } from '../location/place-assist.component';
import { JobTravelPanelComponent } from './job-travel-panel.component';
import { googleMapsDirectionsUrl } from '../../core/maps/directions';
import { MapPin, PinMapComponent } from '../../core/maps/pin-map.component';
import { RequestsApi } from '../../core/api/requests.api';
import { UsersApi } from '../../core/api/users.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { objectUrlForPhoto } from '../../core/api/photo-blob';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import {
  AppointmentSummary,
  JobDetail,
  StatusAction,
  canAssignForStatus,
  canScheduleForStatus,
  parseInternalNotes,
  statusActionsFor,
} from '../../core/models/job.models';
import {
  AttachmentMeta,
  ServiceLocation,
  formatAbsolute,
  formatDateOnly,
  formatTimeOnly,
  formatSubmittedAt,
  maskSerial,
  statusChipClass,
  statusLabel,
} from '../../core/models/request.models';
import {
  AddNoteDialogComponent,
  AssignTechDialogComponent,
  AwaitingPartsDialogComponent,
  CancelJobDialogComponent,
  CompleteJobDialogComponent,
  JobConfirmDialogComponent,
  JobDialogResult,
  PriorityDialogComponent,
  WarrantyDialogComponent,
} from './dialogs/job-dialogs';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden' | 'notfound';

interface PhotoTile {
  meta: AttachmentMeta;
  objectUrl: string | null;
  loading: boolean;
  error: boolean;
  uploading?: boolean;
  uploadError?: string | null;
}

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    PinMapComponent,
    PlaceAssistComponent,
    JobTravelPanelComponent,
  ],
  templateUrl: './job-detail.component.html',
  styleUrl: './job-detail.component.scss',
})
export class JobDetailComponent implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild(PlaceAssistComponent) private placeAssist?: PlaceAssistComponent;

  private readonly api = inject(JobsApi);
  private readonly locationApi = inject(LocationApi);
  private readonly requestsApi = inject(RequestsApi);
  private readonly usersApi = inject(UsersApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;
  readonly formatAbsolute = formatAbsolute;
  readonly formatDateOnly = formatDateOnly;
  readonly formatTimeOnly = formatTimeOnly;

  readonly state = signal<PageState>('loading');
  readonly locationQuery = signal('');
  readonly editingLocation = signal(false);
  readonly draftLatitude = signal<number | null>(null);
  readonly draftLongitude = signal<number | null>(null);
  readonly draftAddress = signal('');
  readonly draftPinSource = signal<'GEOCODED' | 'MANUAL_PIN' | 'CUSTOMER_LINK'>('MANUAL_PIN');
  readonly locating = signal(false);
  readonly savingLocation = signal(false);
  readonly refetching = signal(false);
  readonly busy = signal(false);
  readonly detail = signal<JobDetail | null>(null);
  readonly error = signal<MappedApiError | null>(null);
  readonly appointment = signal<AppointmentSummary | null>(null);
  readonly appointmentError = signal(false);
  readonly jobPhotos = signal<PhotoTile[]>([]);
  readonly customerPhotos = signal<PhotoTile[]>([]);
  readonly customerPhotosOpen = signal(false);
  readonly serialRevealed = signal(false);
  readonly staleMark = signal(false);
  readonly lightbox = signal<{ source: 'job' | 'customer'; index: number } | null>(null);
  readonly technicians = signal<UserProfile[]>([]);
  readonly returnQuery = signal<Record<string, string>>({});
  readonly uploadErrors = signal<string[]>([]);

  readonly role = computed(() => this.session.role());
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isStaffOrAdmin = computed(() => this.role() === 'ADMIN' || this.role() === 'STAFF');
  readonly canCancel = computed(() => this.isStaffOrAdmin());
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
  readonly notes = computed(() => parseInternalNotes(this.detail()?.internalNotes));
  readonly statusActions = computed(() => {
    const d = this.detail();
    if (!d) return [];
    return statusActionsFor(d.status, this.canCancel());
  });
  readonly terminalLine = computed(() => {
    const d = this.detail();
    if (!d) return null;
    if (d.status === 'COMPLETED') {
      return 'This job is completed — no further status changes are possible.';
    }
    if (d.status === 'CANCELLED') {
      return d.cancelReason
        ? `This job was cancelled — ${d.cancelReason}`
        : 'This job was cancelled.';
    }
    return null;
  });
  readonly canMutateAssign = computed(() => {
    const d = this.detail();
    return !!d && this.isStaffOrAdmin() && canAssignForStatus(d.status);
  });
  readonly canSchedule = computed(() => {
    const d = this.detail();
    return !!d && this.isStaffOrAdmin() && canScheduleForStatus(d.status);
  });
  readonly canAddNote = computed(() => {
    const d = this.detail();
    if (!d) return false;
    if (this.isStaffOrAdmin()) return true;
    return this.role() === 'TECHNICIAN' && !!d.assignedTechnicianId;
  });
  readonly canUpload = computed(() => this.canAddNote());
  readonly showOverflow = computed(() => this.isStaffOrAdmin());
  readonly updatedTooltip = computed(() => {
    const d = this.detail();
    if (!d) return '';
    return formatSubmittedAt(d.updatedAt).relative;
  });
  readonly lightboxPhoto = computed(() => {
    const lb = this.lightbox();
    if (!lb) return null;
    const list = lb.source === 'job' ? this.jobPhotos() : this.customerPhotos();
    return list[lb.index] ?? null;
  });

  private jobId = '';

  ngOnInit(): void {
    const nav = this.router.getCurrentNavigation();
    const state =
      (nav?.extras.state as { returnQuery?: Record<string, string> } | undefined) ??
      (history.state as { returnQuery?: Record<string, string> } | undefined);
    if (state?.returnQuery) this.returnQuery.set(state.returnQuery);

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

    this.route.paramMap
      .pipe(
        tap((params) => {
          this.jobId = params.get('id') ?? '';
          this.serialRevealed.set(false);
          this.closeLightbox();
        }),
        switchMap(() => this.fetch$(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.revokeAll(this.jobPhotos());
    this.revokeAll(this.customerPhotos());
  }

  @HostListener('window:blur')
  onBlur(): void {
    this.serialRevealed.set(false);
  }

  @HostListener('document:visibilitychange')
  onVisibility(): void {
    if (document.visibilityState === 'hidden') this.serialRevealed.set(false);
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (!this.lightbox()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeLightbox();
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.lightboxStep(1);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.lightboxStep(-1);
    }
  }

  refresh(): void {
    this.fetch$(true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  retry(): void {
    this.fetch$(false).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  retryAppointment(): void {
    if (!this.jobId) return;
    this.appointmentError.set(false);
    this.api.listAppointments(this.jobId).subscribe({
      next: (list) => this.appointment.set(this.pickActiveAppointment(list)),
      error: () => this.appointmentError.set(true),
    });
  }

  toggleSerial(): void {
    this.serialRevealed.update((v) => !v);
  }

  toggleCustomerPhotos(): void {
    this.customerPhotosOpen.update((v) => !v);
  }

  priorityLabel(p: string): string {
    return p.charAt(0) + p.slice(1).toLowerCase();
  }

  runAction(action: StatusAction): void {
    const job = this.detail();
    if (!job || this.busy()) return;
    switch (action) {
      case 'start':
        this.openConfirm(
          'start',
          `Start work on ${job.referenceNumber}?`,
          'Work will move to In progress and the customer may be notified.',
          'Start repair',
          `${job.referenceNumber} started`,
        );
        break;
      case 'resume':
        this.openConfirm(
          'resume',
          `Resume work on ${job.referenceNumber}?`,
          'The job returns to In progress.',
          'Resume',
          `${job.referenceNumber} resumed`,
        );
        break;
      case 'awaiting-parts':
        this.openDialog(AwaitingPartsDialogComponent, 480, `${job.referenceNumber} marked awaiting parts`);
        break;
      case 'complete':
        this.openDialog(CompleteJobDialogComponent, 640, `${job.referenceNumber} completed`);
        break;
      case 'cancel':
        this.openDialog(CancelJobDialogComponent, 560, `${job.referenceNumber} cancelled`);
        break;
    }
  }

  openAssign(): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(AssignTechDialogComponent, {
        width: '480px',
        data: { job, technicians: this.technicians() },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => {
        const name =
          this.technicians().find((t) => t.id === job.assignedTechnicianId)?.name ||
          'technician';
        // After assign, toast with selected tech from refreshed detail
        this.afterMutation(result, (d) =>
          `${d.referenceNumber} assigned to ${d.assignedTechnicianName || name}`,
        );
      });
  }

  openUnassign(): void {
    const job = this.detail();
    if (!job) return;
    this.openConfirm(
      'unassign',
      `Unassign technician from ${job.referenceNumber}?`,
      'The job returns to New and the technician loses access to it.',
      'Unassign',
      `${job.referenceNumber} unassigned`,
      true,
    );
  }

  openPriority(): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(PriorityDialogComponent, { width: '400px', data: { job }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) =>
        this.afterMutation(result, (d) => `${d.referenceNumber} priority set to ${this.priorityLabel(d.priority)}`),
      );
  }

  openWarranty(): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(WarrantyDialogComponent, { width: '400px', data: { job }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => this.afterMutation(result, () => 'Warranty confirmation updated'));
  }

  openAddNote(): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(AddNoteDialogComponent, { width: '640px', data: { job }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) =>
        this.afterMutation(result, (d) => `Note added to ${d.referenceNumber}`),
      );
  }

  triggerUpload(): void {
    this.fileInput?.nativeElement.click();
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []).filter((f) => f.type.startsWith('image/'));
    input.value = '';
    if (!files.length || !this.jobId) return;
    this.uploadErrors.set([]);
    for (const file of files) {
      this.api.uploadAttachment(this.jobId, file).subscribe({
        next: () => {
          this.snack.open(`Uploaded ${file.name}`, undefined, { duration: 2500 });
          this.reloadJobAttachments();
          this.refresh();
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.uploadErrors.update((list) => [...list, `${file.name}: ${mapped.message}`]);
        },
      });
    }
  }

  openLightbox(source: 'job' | 'customer', index: number): void {
    const list = source === 'job' ? this.jobPhotos() : this.customerPhotos();
    if (!list[index]?.objectUrl) return;
    this.lightbox.set({ source, index });
  }

  closeLightbox(): void {
    this.lightbox.set(null);
  }

  lightboxStep(delta: number): void {
    const lb = this.lightbox();
    if (!lb) return;
    const list = (lb.source === 'job' ? this.jobPhotos() : this.customerPhotos())
      .map((p, i) => ({ p, i }))
      .filter((x) => x.p.objectUrl);
    if (!list.length) return;
    const pos = list.findIndex((x) => x.i === lb.index);
    const next = list[(pos + delta + list.length) % list.length];
    this.lightbox.set({ source: lb.source, index: next.i });
  }

  retryPhoto(source: 'job' | 'customer', index: number): void {
    const list = source === 'job' ? [...this.jobPhotos()] : [...this.customerPhotos()];
    const tile = list[index];
    if (!tile) return;
    if (tile.objectUrl) URL.revokeObjectURL(tile.objectUrl);
    list[index] = { ...tile, loading: true, error: false, objectUrl: null };
    if (source === 'job') this.jobPhotos.set(list);
    else this.customerPhotos.set(list);
    this.fetchBlob(source, index, tile.meta);
  }

  formatNoteTime(iso: string | null | undefined): string {
    if (!iso) return '';
    return formatAbsolute(iso);
  }

  private openConfirm(
    action: 'start' | 'resume' | 'unassign',
    title: string,
    body: string,
    confirmLabel: string,
    toast: string,
    danger = false,
  ): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(JobConfirmDialogComponent, {
        width: '480px',
        data: { job, title, body, confirmLabel, action, danger },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((result) => this.afterMutation(result, () => toast));
  }

  private openDialog(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    component: any,
    width: number,
    toast: string,
  ): void {
    const job = this.detail();
    if (!job) return;
    this.dialog
      .open(component, { width: `${width}px`, data: { job }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => this.afterMutation(result, () => toast));
  }

  private afterMutation(
    result: JobDialogResult,
    toast: string | ((d: JobDetail) => string),
  ): void {
    if (result === 'reload') {
      this.staleMark.set(true);
      setTimeout(() => this.staleMark.set(false), 5000);
      this.refresh();
      return;
    }
    if (result === true) {
      this.fetch$(true)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          const d = this.detail();
          if (d) {
            const msg = typeof toast === 'function' ? toast(d) : toast;
            this.snack.open(msg, undefined, { duration: 4000 });
          }
        });
    }
  }

  private fetch$(isRefresh: boolean): Observable<void> {
    if (!this.jobId) {
      this.state.set('notfound');
      return of(void 0);
    }

    if (isRefresh && this.state() === 'ready') {
      this.refetching.set(true);
    } else {
      this.state.set('loading');
      this.revokeAll(this.jobPhotos());
      this.revokeAll(this.customerPhotos());
      this.jobPhotos.set([]);
      this.customerPhotos.set([]);
    }

    return this.api.get(this.jobId).pipe(
      switchMap((detail) =>
        forkJoin({
          detail: of(detail),
          appointments: this.api.listAppointments(this.jobId).pipe(
            catchError(() => {
              this.appointmentError.set(true);
              return of([] as AppointmentSummary[]);
            }),
          ),
          jobAttachments: this.api.listAttachments(this.jobId).pipe(
            catchError(() => of([] as AttachmentMeta[])),
          ),
          requestAttachments: this.requestsApi.listAttachments(detail.requestId).pipe(
            catchError(() => of([] as AttachmentMeta[])),
          ),
        }),
      ),
      tap({
        next: ({ detail, appointments, jobAttachments, requestAttachments }) => {
          this.detail.set(detail);
          this.error.set(null);
          this.state.set('ready');
          this.title.setTitle(`${detail.referenceNumber} · RepairDesk`);
          this.appointmentError.set(false);
          this.appointment.set(this.pickActiveAppointment(appointments));
          this.bootstrapPhotos('job', jobAttachments ?? []);
          this.bootstrapPhotos('customer', requestAttachments ?? []);
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (isRefresh && this.detail()) {
            this.state.set('ready');
          } else {
            this.detail.set(null);
            if (mapped.status === 403) this.state.set('forbidden');
            else if (mapped.status === 404) this.state.set('notfound');
            else this.state.set('error');
          }
          this.title.setTitle('Job detail · RepairDesk');
        },
      }),
      map(() => void 0),
      catchError(() => of(void 0)),
      finalize(() => this.refetching.set(false)),
    );
  }

  private reloadJobAttachments(): void {
    this.api.listAttachments(this.jobId).subscribe({
      next: (list) => this.bootstrapPhotos('job', list ?? []),
      error: () => undefined,
    });
  }

  private pickActiveAppointment(list: AppointmentSummary[]): AppointmentSummary | null {
    const d = this.detail();
    if (d?.currentAppointmentId) {
      const match = list.find((a) => a.id === d.currentAppointmentId);
      if (match) return match;
    }
    return (
      list.find((a) => a.status === 'SCHEDULED' || a.status === 'IN_PROGRESS') ??
      list[0] ??
      null
    );
  }

  private bootstrapPhotos(source: 'job' | 'customer', metas: AttachmentMeta[]): void {
    const images = metas.filter((m) => (m.contentType || '').startsWith('image/'));
    const tiles: PhotoTile[] = images.map((meta) => ({
      meta,
      objectUrl: null,
      loading: true,
      error: false,
    }));
    if (source === 'job') {
      this.revokeAll(this.jobPhotos());
      this.jobPhotos.set(tiles);
    } else {
      this.revokeAll(this.customerPhotos());
      this.customerPhotos.set(tiles);
    }
    tiles.forEach((_, i) => this.fetchBlob(source, i, images[i]));
  }

  private fetchBlob(source: 'job' | 'customer', index: number, meta: AttachmentMeta): void {
    this.api.downloadAttachmentById(meta.id).subscribe({
      next: (blob) => {
        const current = source === 'job' ? this.jobPhotos() : this.customerPhotos();
        if (!current[index] || current[index].meta.id !== meta.id) return;
        const next = [...current];
        next[index] = {
          meta,
          objectUrl: objectUrlForPhoto(blob, meta.contentType),
          loading: false,
          error: false,
        };
        if (source === 'job') this.jobPhotos.set(next);
        else this.customerPhotos.set(next);
      },
      error: () => {
        const current = source === 'job' ? this.jobPhotos() : this.customerPhotos();
        if (!current[index] || current[index].meta.id !== meta.id) return;
        const next = [...current];
        next[index] = { meta, objectUrl: null, loading: false, error: true };
        if (source === 'job') this.jobPhotos.set(next);
        else this.customerPhotos.set(next);
      },
    });
  }

  directionsUrl(location: ServiceLocation | null | undefined): string | null {
    if (!location) return null;
    const address = [location.addressLine, location.landmark, location.city, location.pincode]
      .filter((part): part is string => !!part && part.trim().length > 0)
      .join(', ');
    return googleMapsDirectionsUrl({
      latitude: location.latitude,
      longitude: location.longitude,
      address,
    });
  }

  jobPins(location: ServiceLocation | null | undefined): MapPin[] {
    if (location?.latitude == null || location.longitude == null) return [];
    return [{
      id: 'job',
      latitude: location.latitude,
      longitude: location.longitude,
      title: 'Service address',
      kind: 'job',
    }];
  }

  draftPins(): MapPin[] {
    const latitude = this.draftLatitude();
    const longitude = this.draftLongitude();
    if (latitude == null || longitude == null) return [];
    return [{
      id: 'job-draft',
      latitude,
      longitude,
      title: 'Service address',
      kind: 'job',
    }];
  }

  shownAddress(location: ServiceLocation | null | undefined): string {
    return location?.addressLine?.trim() || 'No address yet';
  }

  startLocationEdit(): void {
    const location = this.detail()?.serviceLocation;
    this.locationQuery.set('');
    this.draftLatitude.set(location?.latitude ?? null);
    this.draftLongitude.set(location?.longitude ?? null);
    this.draftAddress.set(location?.addressLine?.trim() || '');
    const source = location?.pinSource;
    this.draftPinSource.set(source === 'GEOCODED' || source === 'CUSTOMER_LINK' ? source : 'MANUAL_PIN');
    this.editingLocation.set(true);
  }

  cancelLocationEdit(): void {
    this.editingLocation.set(false);
    this.locationQuery.set('');
  }

  /** Stages a dragged or clicked pin. It is stored only when Save is pressed. */
  stageJobPin(point: { latitude: number; longitude: number }): void {
    this.draftLatitude.set(point.latitude);
    this.draftLongitude.set(point.longitude);
    this.draftPinSource.set('MANUAL_PIN');
    this.locationQuery.set('');
    this.placeAssist?.clearQuery();
  }

  applyPickedPlace(place: PickedPlace): void {
    this.draftLatitude.set(place.latitude);
    this.draftLongitude.set(place.longitude);
    this.draftPinSource.set(place.pinSource);
    if (place.addressLine.trim()) this.draftAddress.set(place.addressLine.trim());
    this.locationQuery.set('');
  }

  saveLocationEdit(): void {
    const job = this.detail();
    if (!job) return;
    const query = this.locationQuery().trim();
    if (query) {
      this.locating.set(true);
      this.locationApi.geocode(query).subscribe({
        next: (hit) => {
          this.locating.set(false);
          this.saveJobLocation(job, {
            addressLine: query,
            latitude: hit.latitude,
            longitude: hit.longitude,
            pinSource: 'GEOCODED',
          });
        },
        error: (err) => {
          this.locating.set(false);
          this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
        },
      });
      return;
    }
    const latitude = this.draftLatitude();
    const longitude = this.draftLongitude();
    if (latitude == null || longitude == null) {
      this.snack.open('Search for a place, or drop the pin, before saving.', 'Dismiss', { duration: 4000 });
      return;
    }
    const current = job.serviceLocation;
    this.saveJobLocation(job, {
      addressLine: this.draftAddress().trim() || current?.addressLine,
      landmark: current?.landmark,
      city: current?.city,
      pincode: current?.pincode,
      latitude,
      longitude,
      pinSource: this.draftPinSource(),
    });
  }

  private saveJobLocation(job: JobDetail, location: ServiceLocation): void {
    this.savingLocation.set(true);
    this.api.updateServiceLocation(job.id, location).subscribe({
      next: (updated) => {
        this.detail.set(updated);
        this.savingLocation.set(false);
        this.editingLocation.set(false);
        this.locationQuery.set('');
        this.snack.open('Job location saved', 'Dismiss', { duration: 3000 });
      },
      error: (err) => {
        this.savingLocation.set(false);
        this.snack.open(mapHttpError(err).message, 'Dismiss', { duration: 5000 });
      },
    });
  }

  private revokeAll(tiles: PhotoTile[]): void {
    for (const t of tiles) {
      if (t.objectUrl) URL.revokeObjectURL(t.objectUrl);
    }
  }
}
