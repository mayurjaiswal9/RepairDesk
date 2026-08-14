import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { SettingsApi, BusinessSettings } from '../../core/api/settings.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import {
  WorkingHoursDay,
  normalizeTimeValue,
  normalizeWorkingHoursWeek,
  parseTimeToMinutes,
  weekdayFull,
} from '../../core/models/scheduling.models';
import { formatAbsolute } from '../../core/models/request.models';

type PageState = 'loading' | 'ready' | 'error' | 'forbidden';

interface DayControls {
  dayOfWeek: number;
  open: boolean;
  openTime: string;
  closeTime: string;
  lastOpen: string;
  lastClose: string;
}

const SLOT_OPTIONS = [30, 60, 90, 120] as const;
const DEFAULT_OPEN = '09:00';
const DEFAULT_CLOSE = '18:00';

@Component({
  selector: 'app-business-settings',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './business-settings.component.html',
  styleUrl: './business-settings.component.scss',
})
export class BusinessSettingsComponent implements OnInit {
  private readonly api = inject(SettingsApi);
  private readonly session = inject(SessionService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly slotOptions = SLOT_OPTIONS;
  readonly weekdayFull = weekdayFull;
  readonly formatAbsolute = formatAbsolute;

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly saving = signal(false);
  readonly updatedAt = signal<string | null>(null);
  readonly hoursIncompleteNote = signal(false);
  readonly settingsSavedMark = signal(false);
  readonly hoursNotSavedMark = signal(false);
  readonly partialBanner = signal<{
    message: string;
    correlationId?: string;
  } | null>(null);
  readonly formError = signal<string | null>(null);
  /** Bumps when form or days change so dirty computeds re-run. */
  readonly revision = signal(0);

  private baselineSettings = '';
  private baselineHours = '';
  private snapshotSettings: BusinessSettings | null = null;

  readonly form = this.fb.nonNullable.group({
    businessName: ['', [Validators.required, Validators.maxLength(150)]],
    businessAddress: ['', Validators.maxLength(500)],
    businessPhone: ['', Validators.maxLength(20)],
    businessEmail: ['', [Validators.maxLength(255), Validators.email]],
    slotDurationMinutes: [60 as number, Validators.required],
    bookingHorizonDays: [
      60,
      [Validators.required, Validators.min(1), Validators.max(365)],
    ],
  });

  readonly days = signal<DayControls[]>([]);
  readonly dayErrors = signal<Record<number, string>>({});

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');

  readonly settingsDirty = computed(() => {
    this.revision();
    if (this.state() !== 'ready') return false;
    return JSON.stringify(this.currentSettingsPayload()) !== this.baselineSettings;
  });

  readonly hoursDirty = computed(() => {
    this.revision();
    if (this.state() !== 'ready') return false;
    return JSON.stringify(this.currentHoursPayload()) !== this.baselineHours;
  });

  readonly dirty = computed(() => this.settingsDirty() || this.hoursDirty());

  readonly dirtySummary = computed(() => {
    this.revision();
    const parts: string[] = [];
    if (this.settingsDirty()) {
      const s = this.form.getRawValue();
      const snap = this.snapshotSettings;
      const bizChanged =
        !!snap &&
        (s.businessName !== (snap.businessName ?? '') ||
          s.businessAddress !== (snap.businessAddress ?? '') ||
          s.businessPhone !== (snap.businessPhone ?? '') ||
          s.businessEmail !== (snap.businessEmail ?? ''));
      const schedChanged =
        !!snap &&
        (Number(s.slotDurationMinutes) !== snap.slotDurationMinutes ||
          Number(s.bookingHorizonDays) !== snap.bookingHorizonDays);
      if (bizChanged) parts.push('Business information');
      if (schedChanged) parts.push('Scheduling');
      if (!bizChanged && !schedChanged) parts.push('Business information');
    }
    if (this.hoursDirty()) parts.push('Working hours');
    if (!parts.length) return '';
    return `Unsaved changes in ${parts.join(', ')}`;
  });

  ngOnInit(): void {
    if (!this.isAdmin()) {
      this.state.set('forbidden');
      return;
    }
    this.form.valueChanges.subscribe(() => this.revision.update((n) => n + 1));
    this.load();
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.dirty()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }

  load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.partialBanner.set(null);
    forkJoin({
      settings: this.api.getSettings(),
      config: this.api.getSchedulingConfig(),
    })
      .pipe(
        catchError((err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          this.state.set(mapped.status === 403 ? 'forbidden' : 'error');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (!result) return;
        this.applyLoaded(result.settings, result.config.workingHours);
        this.state.set('ready');
      });
  }

  retry(): void {
    this.load();
  }

  toggleDay(dayOfWeek: number, open: boolean): void {
    this.days.update((list) =>
      list.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        if (!open) {
          return {
            ...d,
            open: false,
            lastOpen: d.openTime || d.lastOpen,
            lastClose: d.closeTime || d.lastClose,
            openTime: '',
            closeTime: '',
          };
        }
        return {
          ...d,
          open: true,
          openTime: d.lastOpen || DEFAULT_OPEN,
          closeTime: d.lastClose || DEFAULT_CLOSE,
        };
      }),
    );
    this.clearDayError(dayOfWeek);
    this.revision.update((n) => n + 1);
  }

  setDayTime(dayOfWeek: number, field: 'openTime' | 'closeTime', value: string): void {
    this.days.update((list) =>
      list.map((d) => (d.dayOfWeek === dayOfWeek ? { ...d, [field]: value } : d)),
    );
    this.clearDayError(dayOfWeek);
    this.revision.update((n) => n + 1);
  }

  cancel(): void {
    if (!this.dirty()) return;
    if (!confirm('Discard unsaved changes?')) return;
    if (!this.snapshotSettings) return;
    this.applyLoaded(
      this.snapshotSettings,
      JSON.parse(this.baselineHours) as WorkingHoursDay[],
    );
  }

  save(): void {
    if (this.saving() || !this.dirty()) return;
    this.form.markAllAsTouched();
    this.formError.set(null);

    const horizon = Number(this.form.controls.bookingHorizonDays.value);
    if (
      this.settingsDirty() &&
      (this.form.invalid ||
        !Number.isInteger(horizon) ||
        horizon < 1 ||
        horizon > 365)
    ) {
      if (!Number.isInteger(horizon) || horizon < 1 || horizon > 365) {
        this.form.controls.bookingHorizonDays.setErrors({ range: true });
      }
      this.formError.set('Fix the highlighted fields before saving.');
      return;
    }

    const hourErrors = this.validateHours();
    if (this.hoursDirty() && Object.keys(hourErrors).length) {
      this.dayErrors.set(hourErrors);
      this.formError.set('Fix the working-hours errors before saving.');
      return;
    }
    this.dayErrors.set({});

    const hoursPayload = this.currentHoursPayload();
    if (this.daysClosing().length) {
      const ok = confirm(
        `Existing appointments on days you're closing are not cancelled — review them in Appointments.\n\nContinue saving?`,
      );
      if (!ok) return;
    }
    if (this.hoursDirty() && hoursPayload.every((h) => h.isClosed)) {
      const ok = confirm(
        "Every day is set to closed. Customers won't be able to request any appointment date. Continue?",
      );
      if (!ok) return;
    }

    const saveSettings = this.settingsDirty();
    const saveHours = this.hoursDirty();
    this.saving.set(true);
    this.partialBanner.set(null);
    const settingsBody = this.currentSettingsPayload();

    const finishHours = (settingsAlreadySaved: boolean) => {
      this.api
        .updateWorkingHours(hoursPayload)
        .pipe(finalize(() => this.saving.set(false)))
        .subscribe({
          next: () => {
            this.snack.open('Business settings saved', undefined, { duration: 4000 });
            this.load();
          },
          error: (err) => {
            const mapped = mapHttpError(err);
            if (settingsAlreadySaved) {
              this.baselineSettings = JSON.stringify(settingsBody);
              this.settingsSavedMark.set(true);
              this.hoursNotSavedMark.set(true);
              this.partialBanner.set({
                message: mapped.message,
                correlationId: mapped.correlationId,
              });
              this.updatedAt.set(new Date().toISOString());
              this.revision.update((n) => n + 1);
              this.bindHourFieldErrors(mapped);
            } else {
              this.formError.set(mapped.message);
            }
          },
        });
    };

    if (saveSettings) {
      this.api.updateSettings(settingsBody).subscribe({
        next: (saved) => {
          this.snapshotSettings = saved;
          this.updatedAt.set(saved.updatedAt ?? null);
          this.applySettingsForm(saved);
          this.baselineSettings = JSON.stringify(this.currentSettingsPayload());
          this.settingsSavedMark.set(true);
          this.revision.update((n) => n + 1);
          if (saveHours) {
            finishHours(true);
          } else {
            this.saving.set(false);
            this.snack.open('Business settings saved', undefined, { duration: 4000 });
            this.load();
          }
        },
        error: (err) => {
          this.saving.set(false);
          this.formError.set(mapHttpError(err).message);
        },
      });
    } else if (saveHours) {
      finishHours(false);
    } else {
      this.saving.set(false);
    }
  }

  retryHours(): void {
    if (this.saving() || !this.hoursDirty()) return;
    const hourErrors = this.validateHours();
    if (Object.keys(hourErrors).length) {
      this.dayErrors.set(hourErrors);
      return;
    }
    this.saving.set(true);
    this.api
      .updateWorkingHours(this.currentHoursPayload())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.partialBanner.set(null);
          this.hoursNotSavedMark.set(false);
          this.snack.open('Business settings saved', undefined, { duration: 4000 });
          this.load();
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.partialBanner.set({
            message: mapped.message,
            correlationId: mapped.correlationId,
          });
          this.bindHourFieldErrors(mapped);
        },
      });
  }

  private applyLoaded(settings: BusinessSettings, hours: WorkingHoursDay[]): void {
    this.snapshotSettings = settings;
    this.updatedAt.set(settings.updatedAt ?? null);
    this.applySettingsForm(settings);

    const rawCount = hours?.length ?? 0;
    const week = normalizeWorkingHoursWeek(hours);
    this.hoursIncompleteNote.set(rawCount < 7);
    this.days.set(
      week.map((h) => ({
        dayOfWeek: h.dayOfWeek,
        open: !h.isClosed,
        openTime: normalizeTimeValue(h.openTime),
        closeTime: normalizeTimeValue(h.closeTime),
        lastOpen: normalizeTimeValue(h.openTime) || DEFAULT_OPEN,
        lastClose: normalizeTimeValue(h.closeTime) || DEFAULT_CLOSE,
      })),
    );

    this.baselineSettings = JSON.stringify(this.currentSettingsPayload());
    this.baselineHours = JSON.stringify(this.currentHoursPayload());
    this.settingsSavedMark.set(false);
    this.hoursNotSavedMark.set(false);
    this.dayErrors.set({});
    this.formError.set(null);
    this.partialBanner.set(null);
    this.revision.update((n) => n + 1);
  }

  private applySettingsForm(settings: BusinessSettings): void {
    this.form.setValue({
      businessName: settings.businessName ?? '',
      businessAddress: settings.businessAddress ?? '',
      businessPhone: settings.businessPhone ?? '',
      businessEmail: settings.businessEmail ?? '',
      slotDurationMinutes: settings.slotDurationMinutes,
      bookingHorizonDays: settings.bookingHorizonDays,
    });
    this.form.markAsPristine();
  }

  private currentSettingsPayload() {
    const v = this.form.getRawValue();
    return {
      businessName: v.businessName.trim(),
      businessAddress: v.businessAddress.trim() || null,
      businessPhone: v.businessPhone.trim() || null,
      businessEmail: v.businessEmail.trim() || null,
      slotDurationMinutes: Number(v.slotDurationMinutes),
      bookingHorizonDays: Number(v.bookingHorizonDays),
    };
  }

  private currentHoursPayload(): WorkingHoursDay[] {
    return this.days().map((d) => ({
      dayOfWeek: d.dayOfWeek,
      isClosed: !d.open,
      openTime: d.open ? d.openTime || null : null,
      closeTime: d.open ? d.closeTime || null : null,
    }));
  }

  private validateHours(): Record<number, string> {
    const errors: Record<number, string> = {};
    for (const d of this.days()) {
      const name = weekdayFull(d.dayOfWeek);
      if (!d.open) continue;
      if (!d.openTime) {
        errors[d.dayOfWeek] = `Set an opening time for ${name}`;
        continue;
      }
      if (!d.closeTime) {
        errors[d.dayOfWeek] = `Set a closing time for ${name}`;
        continue;
      }
      const open = parseTimeToMinutes(d.openTime);
      const close = parseTimeToMinutes(d.closeTime);
      if (open == null || close == null || close <= open) {
        errors[d.dayOfWeek] = `${name}'s closing time must be after its opening time`;
      }
    }
    return errors;
  }

  private daysClosing(): number[] {
    if (!this.hoursDirty()) return [];
    const baseline = JSON.parse(this.baselineHours) as WorkingHoursDay[];
    return this.currentHoursPayload()
      .filter((h) => {
        const prev = baseline.find((b) => b.dayOfWeek === h.dayOfWeek);
        return h.isClosed && prev && !prev.isClosed;
      })
      .map((h) => h.dayOfWeek);
  }

  private clearDayError(dayOfWeek: number): void {
    const next = { ...this.dayErrors() };
    delete next[dayOfWeek];
    this.dayErrors.set(next);
  }

  private bindHourFieldErrors(mapped: MappedApiError): void {
    for (const [field, msg] of Object.entries(mapped.fieldErrors)) {
      const m = /(\d)/.exec(field);
      if (m) {
        const day = Number(m[1]);
        this.dayErrors.update((e) => ({ ...e, [day]: msg }));
      }
    }
    if (!Object.keys(mapped.fieldErrors).length) {
      this.formError.set(mapped.message);
    }
  }
}
