import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { AppointmentsApi } from '../../../core/api/appointments.api';
import { mapHttpError } from '../../../core/api/api-error';
import { AppointmentSummary } from '../../../core/models/job.models';
import { formatDateOnly, formatTimeOnly } from '../../../core/models/request.models';

export interface CancelAppointmentData {
  jobId: string;
  referenceNumber: string;
  appointment: AppointmentSummary;
  /** Prefill reason (e.g. reassign flow). */
  reasonPrefill?: string;
}

@Component({
  selector: 'app-cancel-appointment-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Cancel the appointment for {{ data.referenceNumber }}?</h2>
    <mat-dialog-content>
      <p class="body">
        The appointment on {{ when }} will be cancelled and the customer notified.
        <strong>The job itself is not cancelled</strong> — it returns to the unscheduled backlog so you can book it again.
      </p>
      @if (data.reasonPrefill) {
        <p class="body warn">
          The customer will receive a cancellation notice, then a new booking notice when you schedule again.
        </p>
      }
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Reason</mat-label>
        <textarea matInput rows="3" [formControl]="reason" required></textarea>
        <mat-hint>Included in the customer's notification</mat-hint>
        @if (reason.touched && reason.hasError('required')) {
          <mat-error>A reason is required</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Keep appointment</button>
      <button
        mat-flat-button
        color="warn"
        type="button"
        [disabled]="busy || reason.invalid"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Cancel appointment
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); line-height: 1.45; }
    .body.warn { color: #bf360c; }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class CancelAppointmentDialogComponent {
  readonly data = inject<CancelAppointmentData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CancelAppointmentDialogComponent, boolean>);
  private readonly api = inject(AppointmentsApi);
  private readonly fb = inject(FormBuilder);

  readonly reason = this.fb.nonNullable.control(
    this.data.reasonPrefill ?? '',
    Validators.required,
  );
  busy = false;
  error: string | null = null;

  readonly when = (() => {
    const a = this.data.appointment;
    return `${formatDateOnly(a.scheduledDate)}, ${formatTimeOnly(a.startTime)}`;
  })();

  confirm(): void {
    this.reason.markAsTouched();
    if (this.reason.invalid) return;
    this.busy = true;
    this.error = null;
    this.api
      .cancel(this.data.jobId, this.data.appointment.id, this.reason.value.trim())
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => (this.error = mapHttpError(err).message),
      });
  }
}
