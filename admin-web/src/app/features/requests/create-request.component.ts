import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, debounceTime, distinctUntilChanged, finalize, forkJoin, of, switchMap } from 'rxjs';
import { CategoriesApi } from '../../core/api/categories.api';
import { CustomersApi, CreateCustomerResponse } from '../../core/api/customers.api';
import { RequestsApi } from '../../core/api/requests.api';
import { SettingsApi } from '../../core/api/settings.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import {
  DeviceBrand,
  DeviceCategory,
  RepairRequestSummary,
  Priority,
} from '../../core/models/request.models';
import { SchedulingConfig, nextShopSlot } from '../../core/models/scheduling.models';
import {
  CreateApproveDialogComponent,
  GeneratedPasswordDialogComponent,
  NewCustomerDialogComponent,
} from './dialogs/create-request-dialogs';

const MOBILE_PATTERN = /^\+?[0-9 \-]{8,15}$/;
const CHANNELS = ['PHONE', 'WALK_IN', 'WHATSAPP', 'OTHER'] as const;
const CHANNEL_LABEL: Record<(typeof CHANNELS)[number], string> = {
  PHONE: 'Phone',
  WALK_IN: 'Walk-in',
  WHATSAPP: 'WhatsApp',
  OTHER: 'Other',
};

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

@Component({
  selector: 'app-create-request',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './create-request.component.html',
  styleUrl: './create-request.component.scss',
})
export class CreateRequestComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly customers = inject(CustomersApi);
  private readonly requests = inject(RequestsApi);
  private readonly categoriesApi = inject(CategoriesApi);
  private readonly settingsApi = inject(SettingsApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly channels = CHANNELS;
  readonly channelLabel = CHANNEL_LABEL;

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly submitting = signal(false);
  readonly searching = signal(false);
  readonly searchHits = signal<UserProfile[]>([]);
  readonly searchDone = signal(false);
  readonly customer = signal<UserProfile | null>(null);
  readonly history = signal<RepairRequestSummary[]>([]);
  readonly categories = signal<DeviceCategory[]>([]);
  readonly brands = signal<DeviceBrand[]>([]);
  readonly photos = signal<File[]>([]);
  readonly formError = signal('');
  /** Bumps when reactive-form validity changes — `form.valid` is not a signal. */
  private readonly formTick = signal(0);

  private config: SchedulingConfig | null = null;
  private searchSeq = 0;

  readonly form = this.fb.nonNullable.group({
    q: [''],
    contactName: ['', [Validators.required, Validators.maxLength(100)]],
    contactMobile: ['', [Validators.required, Validators.pattern(MOBILE_PATTERN)]],
    contactEmail: ['', [Validators.email]],
    deviceCategoryId: ['', Validators.required],
    deviceBrand: [''],
    deviceModel: [''],
    issueDescription: ['', [Validators.required, Validators.minLength(10)]],
    intakeChannel: this.fb.nonNullable.control<(typeof CHANNELS)[number]>('PHONE'),
  });

  readonly canSubmit = computed(() => {
    this.formTick();
    const c = this.form.controls;
    return (
      !!this.customer() &&
      this.state() === 'ready' &&
      !this.submitting() &&
      c.contactName.valid &&
      c.contactMobile.valid &&
      c.deviceCategoryId.valid &&
      c.issueDescription.valid &&
      c.contactEmail.valid
    );
  });

  readonly selectedCategory = computed(() => {
    const id = this.form.controls.deviceCategoryId.value;
    return this.categories().find((c) => c.id === id) ?? null;
  });

  readonly brandLocked = computed(() => !this.form.controls.deviceCategoryId.value);
  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');

  readonly initials = computed(() => {
    const c = this.customer();
    const src = (c?.name || c?.username || '?').trim();
    const parts = src.split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
  });

  ngOnInit(): void {
    const role = this.session.role();
    if (role !== 'ADMIN' && role !== 'STAFF') {
      this.state.set('forbidden');
      return;
    }

    forkJoin({
      categories: this.categoriesApi.listActive(),
      config: this.settingsApi.getSchedulingConfig(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ categories, config }) => {
          this.categories.set(categories);
          this.config = config;
          this.state.set('ready');
          const prefill = this.route.snapshot.queryParamMap.get('customerId');
          if (prefill) this.selectById(prefill);
        },
        error: (err) => {
          this.error.set(mapHttpError(err));
          this.state.set('error');
        },
      });

    this.form.statusChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.formTick.update((n) => n + 1));
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.formTick.update((n) => n + 1));

    this.form.controls.q.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.search(q));

    this.form.controls.deviceBrand.disable({ emitEvent: false });
    this.form.controls.deviceCategoryId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => this.onCategoryChange(id));
  }

  search(raw: string): void {
    const q = raw.trim();
    if (this.customer()) return;
    if (q.length < 2) {
      this.searchHits.set([]);
      this.searchDone.set(false);
      return;
    }
    const seq = ++this.searchSeq;
    this.searching.set(true);
    this.customers
      .list(q)
      .pipe(finalize(() => this.searching.set(false)))
      .subscribe({
        next: (rows) => {
          if (seq !== this.searchSeq) return;
          this.searchHits.set(rows.filter((r) => r.status === 'ACTIVE'));
          this.searchDone.set(true);
        },
        error: () => {
          if (seq !== this.searchSeq) return;
          this.searchHits.set([]);
          this.searchDone.set(true);
        },
      });
  }

  selectCustomer(row: UserProfile): void {
    if (row.status !== 'ACTIVE') return;
    this.customer.set(row);
    this.searchHits.set([]);
    this.searchDone.set(false);
    this.form.controls.q.setValue('', { emitEvent: false });
    this.form.patchValue({
      contactName: row.name?.trim() || row.username,
      contactMobile: row.mobile,
    });
    this.form.controls.contactName.markAsTouched();
    this.form.controls.contactMobile.markAsTouched();
    this.formTick.update((n) => n + 1);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { customerId: row.id },
      replaceUrl: true,
    });
    this.customers.history(row.id).subscribe({
      next: (h) => this.history.set(h.requests ?? []),
      error: () => this.history.set([]),
    });
  }

  selectById(id: string): void {
    this.customers.get(id).subscribe({
      next: (p) => {
        if (p.status !== 'ACTIVE') {
          this.formError.set('That customer is not active. Pick someone else.');
          return;
        }
        this.selectCustomer(p);
      },
      error: () => {
        this.formError.set('That customer could not be loaded. Search for someone else.');
      },
    });
  }

  changeCustomer(): void {
    if (this.form.dirty && !confirm('Discard contact and request edits for this customer?')) {
      return;
    }
    this.customer.set(null);
    this.history.set([]);
    this.form.patchValue({ contactName: '', contactMobile: '', contactEmail: '' });
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  openNewCustomer(): void {
    this.dialog
      .open(NewCustomerDialogComponent, { width: '480px', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((res: CreateCustomerResponse | { useId: string } | undefined) => {
        if (!res) return;
        if ('useId' in res) {
          this.snack.open('That customer already exists — selected them.', 'OK', { duration: 4000 });
          this.selectById(res.useId);
          return;
        }
        this.snack.open(`${res.name} added`, 'OK', { duration: 3000 });
        if (res.generatedPassword) {
          this.dialog.open(GeneratedPasswordDialogComponent, {
            width: '440px',
            data: { name: res.name, username: res.username, password: res.generatedPassword },
          });
        }
        this.selectCustomer({
          id: res.id,
          username: res.username,
          name: res.name,
          mobile: res.mobile,
          role: 'CUSTOMER',
          status: res.status,
          mustChangePassword: res.mustChangePassword,
          createdAt: new Date().toISOString(),
          repairCount: 0,
        });
      });
  }

  onCategoryChange(id: string): void {
    this.form.controls.deviceBrand.setValue('');
    this.brands.set([]);
    if (!id) {
      this.form.controls.deviceBrand.disable({ emitEvent: false });
      return;
    }
    this.form.controls.deviceBrand.enable({ emitEvent: false });
    this.categoriesApi.listActiveBrands(id).subscribe({
      next: (rows) => this.brands.set(rows.filter((b) => !b.archived)),
      error: () => this.brands.set([]),
    });
  }

  setChannel(ch: (typeof CHANNELS)[number]): void {
    this.form.controls.intakeChannel.setValue(ch);
  }

  onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const incoming = Array.from(input.files ?? []);
    input.value = '';
    const next = [...this.photos()];
    for (const file of incoming) {
      if (next.length >= 5) {
        this.snack.open('You can attach up to 5 photos.', 'OK', { duration: 3000 });
        break;
      }
      if (file.size > 10 * 1024 * 1024) {
        this.snack.open(`${file.name} is larger than 10 MB.`, 'OK', { duration: 3000 });
        continue;
      }
      if (!/^image\/(jpeg|png|jpg|webp)$/i.test(file.type) && !/\.(jpe?g|png|webp)$/i.test(file.name)) {
        this.snack.open(`${file.name} must be a JPG or PNG.`, 'OK', { duration: 3000 });
        continue;
      }
      next.push(file);
    }
    this.photos.set(next);
  }

  removePhoto(i: number): void {
    this.photos.update((list) => list.filter((_, idx) => idx !== i));
  }

  cancel(): void {
    if (this.form.dirty && !confirm('Leave without creating this request?')) return;
    void this.router.navigateByUrl(this.session.role() === 'STAFF' ? '/dashboard' : '/requests');
  }

  saveForReview(): void {
    this.submit(false);
  }

  createRequest(): void {
    this.submit(false);
  }

  createAndApprove(): void {
    const c = this.customer();
    if (!c || !this.canSubmit()) return;
    this.dialog
      .open(CreateApproveDialogComponent, {
        width: '480px',
        data: { customerName: c.name?.trim() || c.username },
      })
      .afterClosed()
      .subscribe((res: { priority: Priority; note?: string } | undefined) => {
        if (!res) return;
        this.submit(true, res.priority, res.note);
      });
  }

  private submit(approve: boolean, priority?: Priority, note?: string): void {
    const owner = this.customer();
    if (!owner) {
      this.formError.set('Select a customer this request belongs to.');
      return;
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const slot = this.config ? nextShopSlot(this.config) : null;
    if (!slot) {
      this.formError.set('No remaining shop slot in the booking horizon. Check business hours.');
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set('');
    this.requests
      .create({
        customerId: owner.id,
        deviceCategoryId: v.deviceCategoryId,
        contactName: v.contactName.trim(),
        contactMobile: v.contactMobile.trim(),
        contactEmail: v.contactEmail.trim() || undefined,
        deviceBrand: v.deviceBrand.trim() || undefined,
        deviceModel: v.deviceModel.trim() || undefined,
        issueDescription: v.issueDescription.trim(),
        preferredDate: slot.date,
        preferredTime: slot.time,
        approveOnCreate: approve || undefined,
        priority: approve ? priority : undefined,
        note: approve ? note : undefined,
        intakeChannel: v.intakeChannel,
      })
      .pipe(
        switchMap((created) => {
          const files = this.photos();
          if (!files.length) return of(created);
          return forkJoin(
            files.map((f) =>
              this.requests.uploadAttachment(created.id, f).pipe(catchError(() => of(null))),
            ),
          ).pipe(
            switchMap((results) => {
              if (results.some((r) => r == null)) {
                this.snack.open('Request created, but a photo could not be uploaded.', 'OK', {
                  duration: 5000,
                });
              }
              return of(created);
            }),
          );
        }),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (created) => {
          const ref = created.referenceNumber;
          if (approve && created.jobId) {
            this.snack.open(`${ref} approved · job created`, 'OK', { duration: 4000 });
            void this.router.navigate(['/jobs', created.jobId]);
            return;
          }
          this.snack.open(`${ref} created`, 'OK', { duration: 4000 });
          const role = this.session.role();
          if (role === 'ADMIN') {
            void this.router.navigate(['/requests', created.id]);
          } else {
            void this.router.navigate(['/customers', owner.id]);
          }
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.formError.set(mapped.message);
          for (const [field, msg] of Object.entries(mapped.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: msg });
          }
        },
      });
  }

  displayName(u: UserProfile): string {
    return u.name?.trim() || u.username;
  }
}
