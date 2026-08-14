import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { AppointmentsApi } from '../../core/api/appointments.api';
import { JobsApi } from '../../core/api/jobs.api';
import { RequestsApi } from '../../core/api/requests.api';
import { SettingsApi } from '../../core/api/settings.api';
import { UsersApi } from '../../core/api/users.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { UserProfile } from '../../core/models/dashboard.models';
import { AppointmentSummary, JobDetail } from '../../core/models/job.models';
import {
  SchedulingConfig,
  addDays,
  endTimeForStart,
  formatTimeLabel,
  hoursForDate,
  parseDateInput,
  preferenceValidity,
  prefReasonLabel,
  shopDayOfWeek,
  slotStartsForDate,
  startOfToday,
  timesOverlap,
  toDateInputValue,
  weekdayShort,
} from '../../core/models/scheduling.models';
import {
  formatDateOnly,
  formatTimeOnly,
  statusChipClass,
  statusLabel,
} from '../../core/models/request.models';
import { CancelAppointmentDialogComponent } from './dialogs/cancel-appointment-dialog.component';

type PageState =
  | 'loading'
  | 'ready'
  | 'error'
  | 'forbidden'
  | 'notfound'
  | 'blocked_terminal'
  | 'blocked_techs'
  | 'blocked_config'
  | 'blocked_closed';

type Mode = 'schedule' | 'reschedule';

@Component({
  selector: 'app-schedule-appointment',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './schedule-appointment.component.html',
  styleUrl: './schedule-appointment.component.scss',
})
export class ScheduleAppointmentComponent implements OnInit {
  private readonly jobsApi = inject(JobsApi);
  private readonly apptsApi = inject(AppointmentsApi);
  private readonly settingsApi = inject(SettingsApi);
  private readonly usersApi = inject(UsersApi);
  private readonly requestsApi = inject(RequestsApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly title = inject(Title);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusChipClass = statusChipClass;
  readonly statusLabel = statusLabel;
  readonly formatDateOnly = formatDateOnly;
  readonly formatTimeOnly = formatTimeOnly;
  readonly formatTimeLabel = formatTimeLabel;

  readonly state = signal<PageState>('loading');
  readonly error = signal<MappedApiError | null>(null);
  readonly job = signal<JobDetail | null>(null);
  readonly config = signal<SchedulingConfig | null>(null);
  readonly technicians = signal<UserProfile[]>([]);
  readonly currentAppt = signal<AppointmentSummary | null>(null);
  readonly mode = signal<Mode>('schedule');
  readonly slots = signal<string[]>([]);
  readonly slotClearedNotice = signal(false);
  readonly conflict = signal<AppointmentSummary | null>(null);
  readonly formError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly preference = signal<{ date: string; time: string } | null>(null);
  readonly prefReason = signal<string | null>(null);
  readonly outsideHoursNote = signal(false);

  readonly form = this.fb.nonNullable.group({
    technicianId: ['', Validators.required],
    scheduledDate: ['', Validators.required],
    startTime: ['', Validators.required],
    notes: [''],
  });

  readonly isAdmin = computed(() => this.session.role() === 'ADMIN');
  readonly isStaffOrAdmin = computed(
    () => this.session.role() === 'ADMIN' || this.session.role() === 'STAFF',
  );
  readonly pageTitle = computed(() =>
    this.mode() === 'reschedule' ? 'Reschedule appointment' : 'Schedule appointment',
  );
  readonly saveLabel = computed(() => (this.mode() === 'reschedule' ? 'Reschedule' : 'Schedule'));
  readonly minDate = computed(() => toDateInputValue(startOfToday()));
  readonly maxDate = computed(() => {
    const c = this.config();
    if (!c) return this.minDate();
    return toDateInputValue(addDays(startOfToday(), c.bookingHorizonDays));
  });
  readonly hoursHint = computed(() => {
    const c = this.config();
    const dateStr = this.form.controls.scheduledDate.value;
    const start = this.form.controls.startTime.value;
    if (!c || !dateStr || !start) return '';
    const date = parseDateInput(dateStr);
    if (!date) return '';
    const hours = hoursForDate(c, date);
    if (!hours || hours.isClosed) return '';
    const end = endTimeForStart(start, c.slotDurationMinutes);
    const day = weekdayShort(shopDayOfWeek(date));
    return `Ends ${formatTimeLabel(end)} · ${day} hours ${formatTimeLabel(hours.openTime || '00:00')} – ${formatTimeLabel(hours.closeTime || '00:00')} (shop local)`;
  });
  readonly canUsePreference = computed(() => !!this.preference() && !this.prefReason());

  private jobId = '';
  private initialSnapshot = '';

  ngOnInit(): void {
    if (!this.isStaffOrAdmin()) {
      this.state.set('forbidden');
      this.title.setTitle('Schedule · RepairDesk');
      return;
    }

    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.jobId = params.get('id') ?? '';
      this.load();
    });

    this.form.controls.scheduledDate.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.onDateChanged());
    this.form.controls.technicianId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshConflict());
    this.form.controls.startTime.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshConflict());
  }

  load(): void {
    if (!this.jobId) {
      this.state.set('notfound');
      return;
    }
    this.state.set('loading');
    this.formError.set(null);

    forkJoin({
      job: this.jobsApi.get(this.jobId),
      config: this.settingsApi.getSchedulingConfig(),
      technicians: this.usersApi.listByRole('TECHNICIAN'),
      appointments: this.jobsApi.listAppointments(this.jobId).pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ job, config, technicians, appointments }) => {
          if (job.status === 'COMPLETED' || job.status === 'CANCELLED') {
            this.job.set(job);
            this.state.set('blocked_terminal');
            this.title.setTitle(`${job.referenceNumber} · Schedule`);
            return;
          }

          const hours = config.workingHours ?? [];
          if (!hours.length) {
            this.state.set('blocked_config');
            this.error.set({
              status: 0,
              message: "Working hours aren't configured, so appointments can't be booked yet",
              fieldErrors: {},
              raw: null as never,
            });
            return;
          }
          if (hours.every((h) => h.isClosed)) {
            this.state.set('blocked_closed');
            return;
          }

          const techs = [...(technicians ?? [])].sort((a, b) =>
            (a.name || a.username).localeCompare(b.name || b.username),
          );
          if (!techs.length) {
            this.job.set(job);
            this.state.set('blocked_techs');
            return;
          }

          this.job.set(job);
          this.config.set(config);
          this.technicians.set(techs);

          const mode: Mode = job.currentAppointmentId ? 'reschedule' : 'schedule';
          this.mode.set(mode);
          const current =
            appointments.find((a) => a.id === job.currentAppointmentId) ??
            appointments.find((a) => a.status === 'SCHEDULED' || a.status === 'IN_PROGRESS') ??
            null;
          this.currentAppt.set(current);

          if (
            current &&
            config &&
            (() => {
              const d = parseDateInput(current.scheduledDate);
              if (!d) return false;
              const h = hoursForDate(config, d);
              if (!h || h.isClosed) return true;
              const slots = slotStartsForDate(config, current.scheduledDate);
              return !slots.includes(current.startTime.slice(0, 5));
            })()
          ) {
            this.outsideHoursNote.set(true);
          } else {
            this.outsideHoursNote.set(false);
          }

          const techId =
            mode === 'reschedule'
              ? current?.technicianId || job.assignedTechnicianId || ''
              : job.assignedTechnicianId || '';
          this.form.reset({
            technicianId: techId,
            scheduledDate: '',
            startTime: '',
            notes: '',
          });
          if (mode === 'reschedule') {
            this.form.controls.technicianId.disable();
          } else {
            this.form.controls.technicianId.enable();
          }

          this.state.set('ready');
          this.title.setTitle(`${this.pageTitle()} · ${job.referenceNumber}`);
          this.initialSnapshot = JSON.stringify(this.form.getRawValue());
          this.loadPreference(job.requestId, config);
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error.set(mapped);
          if (mapped.status === 403) this.state.set('forbidden');
          else if (mapped.status === 404) this.state.set('notfound');
          else this.state.set('error');
        },
      });
  }

  usePreference(): void {
    const pref = this.preference();
    if (!pref || this.prefReason()) return;
    this.form.patchValue({
      scheduledDate: pref.date,
      startTime: pref.time.slice(0, 5),
    });
    this.onDateChanged(false);
  }

  isDateClosed(dateStr: string): boolean {
    const c = this.config();
    const date = parseDateInput(dateStr);
    if (!c || !date) return false;
    const hours = hoursForDate(c, date);
    return !hours || hours.isClosed;
  }

  onDateInput(): void {
    const dateStr = this.form.controls.scheduledDate.value;
    if (dateStr && this.isDateClosed(dateStr)) {
      this.formError.set('That day is closed — pick another date.');
      this.form.controls.scheduledDate.setValue('');
      this.slots.set([]);
      return;
    }
    this.formError.set(null);
    this.onDateChanged();
  }

  save(): void {
    this.form.markAllAsTouched();
    this.formError.set(null);
    if (this.form.invalid || this.saving()) return;

    const job = this.job();
    const raw = this.form.getRawValue();
    if (!job) return;

    if (this.mode() === 'reschedule') {
      this.confirmReschedule(job, raw.scheduledDate, raw.startTime, raw.notes.trim());
      return;
    }

    this.saving.set(true);
    this.form.disable();
    this.apptsApi
      .create(job.id, {
        technicianId: raw.technicianId,
        scheduledDate: raw.scheduledDate,
        startTime: raw.startTime,
        notes: raw.notes.trim() || undefined,
      })
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.form.enable();
          if (this.mode() === 'reschedule') this.form.controls.technicianId.disable();
        }),
      )
      .subscribe({
        next: () => {
          const tech =
            this.technicians().find((t) => t.id === raw.technicianId)?.name ||
            this.technicians().find((t) => t.id === raw.technicianId)?.username ||
            'technician';
          this.snack.open(
            `${job.referenceNumber} scheduled for ${formatDateOnly(raw.scheduledDate)}, ${formatTimeLabel(raw.startTime)} with ${tech}`,
            undefined,
            { duration: 5000 },
          );
          this.initialSnapshot = JSON.stringify(this.form.getRawValue());
          void this.router.navigate(['/jobs', job.id]);
        },
        error: (err) => this.onSaveError(err),
      });
  }

  cancelForm(): void {
    if (this.isDirty() && !confirm('Discard unsaved changes?')) return;
    const job = this.job();
    void this.router.navigate(job ? ['/jobs', job.id] : ['/jobs']);
  }

  openCancelAppointment(forReassign = false): void {
    const job = this.job();
    const appt = this.currentAppt();
    if (!job || !appt) return;
    this.dialog
      .open(CancelAppointmentDialogComponent, {
        width: '560px',
        data: {
          jobId: job.id,
          referenceNumber: job.referenceNumber,
          appointment: appt,
          reasonPrefill: forReassign ? 'Reassigning to a different technician' : undefined,
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.snack.open(`Appointment for ${job.referenceNumber} cancelled`, undefined, {
          duration: 4000,
        });
        if (forReassign) {
          this.load();
        } else {
          void this.router.navigate(['/jobs', job.id]);
        }
      });
  }

  private confirmReschedule(
    job: JobDetail,
    scheduledDate: string,
    startTime: string,
    notes: string,
  ): void {
    const current = this.currentAppt();
    if (!current) {
      this.formError.set('No appointment to reschedule — reload the page.');
      return;
    }
    const rollback =
      job.status === 'IN_PROGRESS'
        ? '\n\nNote: the job status will return to Scheduled.'
        : '';
    const ok = confirm(
      `Move ${job.referenceNumber} from ${formatDateOnly(current.scheduledDate)}, ${formatTimeLabel(current.startTime)} to ${formatDateOnly(scheduledDate)}, ${formatTimeLabel(startTime)}?\n\nThe customer will be notified of the new time.${rollback}`,
    );
    if (!ok) return;

    this.saving.set(true);
    this.form.disable();
    this.apptsApi
      .reschedule(job.id, current.id, {
        scheduledDate,
        startTime,
        notes: notes || undefined,
      })
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.form.enable();
          this.form.controls.technicianId.disable();
        }),
      )
      .subscribe({
        next: () => {
          this.snack.open(
            `${job.referenceNumber} moved to ${formatDateOnly(scheduledDate)}, ${formatTimeLabel(startTime)}`,
            undefined,
            { duration: 5000 },
          );
          this.initialSnapshot = JSON.stringify(this.form.getRawValue());
          void this.router.navigate(['/jobs', job.id]);
        },
        error: (err) => this.onSaveError(err),
      });
  }

  private onSaveError(err: unknown): void {
    const mapped = mapHttpError(err);
    if (mapped.status === 409) {
      this.formError.set(mapped.message);
      return;
    }
    if (mapped.status === 422) {
      this.formError.set(mapped.message);
      if (/past/i.test(mapped.message)) {
        this.form.controls.scheduledDate.setErrors({ server: true });
      } else if (/horizon/i.test(mapped.message)) {
        this.form.controls.scheduledDate.setErrors({ server: true });
      } else if (/technician/i.test(mapped.message)) {
        this.form.controls.technicianId.setErrors({ server: true });
      }
      return;
    }
    if (mapped.status === 404) {
      this.state.set('notfound');
      return;
    }
    if (mapped.status === 403) {
      this.state.set('forbidden');
      return;
    }
    this.formError.set(mapped.message);
  }

  private onDateChanged(clearInvalidSlot = true): void {
    const c = this.config();
    const dateStr = this.form.controls.scheduledDate.value;
    if (!c || !dateStr) {
      this.slots.set([]);
      return;
    }
    const nextSlots = slotStartsForDate(c, dateStr);
    this.slots.set(nextSlots);
    const current = this.form.controls.startTime.value;
    if (current && !nextSlots.includes(current)) {
      if (clearInvalidSlot) {
        this.form.controls.startTime.setValue('');
        this.slotClearedNotice.set(true);
      }
    } else {
      this.slotClearedNotice.set(false);
    }
    this.refreshConflict();
  }

  private refreshConflict(): void {
    const c = this.config();
    const techId = this.form.getRawValue().technicianId;
    const date = this.form.controls.scheduledDate.value;
    const start = this.form.controls.startTime.value;
    const job = this.job();
    if (!c || !techId || !date || !start || !job) {
      this.conflict.set(null);
      return;
    }
    const end = endTimeForStart(start, c.slotDurationMinutes);
    this.apptsApi.calendar(date, date).subscribe({
      next: (list) => {
        const hit =
          list.find(
            (a) =>
              a.technicianId === techId &&
              a.id !== this.currentAppt()?.id &&
              a.jobId !== job.id &&
              timesOverlap(a.startTime, a.endTime, start, end),
          ) ?? null;
        this.conflict.set(hit);
      },
      error: () => this.conflict.set(null),
    });
  }

  private loadPreference(requestId: string, config: SchedulingConfig): void {
    this.requestsApi.get(requestId).subscribe({
      next: (req) => {
        if (!req.preferredDate) {
          this.preference.set(null);
          this.prefReason.set(null);
          return;
        }
        const time = req.preferredTime || '';
        this.preference.set({ date: req.preferredDate, time });
        const reason = preferenceValidity(config, req.preferredDate, time || null);
        this.prefReason.set(reason ? prefReasonLabel(reason) : null);
      },
      error: () => {
        this.preference.set(null);
        this.prefReason.set(null);
      },
    });
  }

  private isDirty(): boolean {
    return JSON.stringify(this.form.getRawValue()) !== this.initialSnapshot;
  }
}
