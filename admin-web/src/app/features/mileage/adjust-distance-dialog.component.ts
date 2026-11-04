import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { MileageApi, TripResponse } from '../../core/api/mileage.api';
import { mapHttpError } from '../../core/api/api-error';
import { formatKm } from './mileage-format';

@Component({
  selector: 'app-adjust-distance-dialog',
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
    <h2 mat-dialog-title>Adjust km · {{ data.trip.jobReference }}</h2>
    <mat-dialog-content>
      <dl class="facts">
        <div>
          <dt>Tracked</dt>
          <dd>{{ km(data.trip.trackedDistanceM + data.trip.estimatedGapM) }} km</dd>
        </div>
        <div>
          <dt>Google route</dt>
          <dd>{{ data.trip.plannedDistanceM == null ? '—' : km(data.trip.plannedDistanceM) + ' km' }}</dd>
        </div>
        <div>
          <dt>Payable now</dt>
          <dd>{{ km(data.trip.payableDistanceM) }} km</dd>
        </div>
      </dl>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <form [formGroup]="form" (ngSubmit)="confirm()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>New km</mat-label>
          <input matInput type="number" inputmode="decimal" min="0" max="1000" step="0.1" formControlName="distanceKm" required />
          <span matTextSuffix>km</span>
          @if (form.controls.distanceKm.touched && form.controls.distanceKm.invalid) {
            <mat-error>{{ distanceError() }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Reason</mat-label>
          <textarea matInput rows="3" maxlength="500" formControlName="reason" required></textarea>
          <mat-hint align="end">{{ form.controls.reason.value.length }}/500</mat-hint>
          @if (form.controls.reason.touched && form.controls.reason.invalid) {
            <mat-error>{{ reasonError() }}</mat-error>
          }
        </mat-form-field>
      </form>
      <p class="body">The change and the reason are saved in the audit log.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 4px 0 0; color: var(--color-text-secondary); font-size: 13px; }
    .full { width: 100%; }
    .facts {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 0 0 16px;
      dt { font-size: 12px; font-weight: 600; color: var(--color-text-secondary); }
      dd { margin: 2px 0 0; font-size: 15px; font-weight: 700; }
    }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class AdjustDistanceDialogComponent {
  readonly data = inject<{ trip: TripResponse }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<AdjustDistanceDialogComponent, TripResponse | undefined>);
  private readonly api = inject(MileageApi);
  private readonly fb = inject(FormBuilder);

  readonly km = formatKm;
  readonly form = this.fb.nonNullable.group({
    distanceKm: this.fb.control<number | null>(Math.round(this.data.trip.payableDistanceM / 100) / 10, [
      Validators.required,
      Validators.min(0),
      Validators.max(1000),
      Validators.pattern(/^\d+(\.\d)?$/),
    ]),
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
  });
  busy = false;
  error: string | null = null;

  distanceError(): string {
    const control = this.form.controls.distanceKm;
    if (control.hasError('required')) return 'Enter the km';
    if (control.hasError('server')) return control.getError('server');
    return 'Use 0 to 1000 km with at most one decimal';
  }

  reasonError(): string {
    const control = this.form.controls.reason;
    if (control.hasError('server')) return control.getError('server');
    if (control.hasError('maxlength')) return 'Keep the reason under 500 characters';
    return 'Give a reason of at least 3 characters';
  }

  confirm(): void {
    const reason = this.form.controls.reason.value.trim();
    if (reason.length < 3 && this.form.controls.reason.valid) {
      this.form.controls.reason.setErrors({ minlength: true });
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const distanceKm = Number(this.form.controls.distanceKm.value);
    this.busy = true;
    this.error = null;
    this.api
      .adjustDistance(this.data.trip.id, distanceKm, reason)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (trip) => this.ref.close(trip),
        error: (err) => {
          const mapped = mapHttpError(err);
          const distance = mapped.fieldErrors['distanceKm'];
          const reasonError = mapped.fieldErrors['reason'];
          if (distance) this.form.controls.distanceKm.setErrors({ server: distance });
          if (reasonError) this.form.controls.reason.setErrors({ server: reasonError });
          this.error = mapped.message;
        },
      });
  }
}
