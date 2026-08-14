import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { RequestsApi } from '../../../core/api/requests.api';
import { mapHttpError } from '../../../core/api/api-error';
import { DecisionTarget, REJECT_REASON_CODES } from '../../../core/models/request.models';

export interface RejectDialogData {
  row: DecisionTarget;
}

@Component({
  selector: 'app-reject-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Reject {{ data.row.referenceNumber }}?</h2>
    <mat-dialog-content>
      <p class="body">The customer will see your message. This cannot be undone.</p>
      @if (error) {
        <div class="err" role="alert">
          {{ error }}
          @if (stale) {
            <button type="button" class="link" (click)="reload()">Reload</button>
          }
        </div>
      }
      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Reason code</mat-label>
          <mat-select formControlName="reasonCode">
            <mat-option value="" disabled>Select a reason…</mat-option>
            @for (r of reasons; track r.value) {
              <mat-option [value]="r.value">{{ r.label }}</mat-option>
            }
          </mat-select>
          @if (form.controls.reasonCode.touched && form.controls.reasonCode.hasError('required')) {
            <mat-error>Choose a reason code.</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Message to customer</mat-label>
          <textarea matInput rows="3" formControlName="customerMessage"></textarea>
          @if (form.controls.customerMessage.touched && form.controls.customerMessage.hasError('required')) {
            <mat-error>Enter a message the customer will see.</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Internal reason</mat-label>
          <textarea matInput rows="2" formControlName="internalReason" placeholder="Optional — not shown to customer"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Reject
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 16px; color: var(--color-text-secondary); }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
    .link {
      display: inline; margin-left: 8px; border: none; background: none;
      color: var(--color-primary); font-weight: 600; cursor: pointer;
    }
  `,
})
export class RejectDialogComponent {
  readonly data = inject<RejectDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RejectDialogComponent, boolean | 'reload'>);
  private readonly api = inject(RequestsApi);
  private readonly fb = inject(FormBuilder);

  readonly reasons = REJECT_REASON_CODES;
  busy = false;
  error: string | null = null;
  stale = false;

  readonly form = this.fb.nonNullable.group({
    reasonCode: ['', Validators.required],
    customerMessage: ['', Validators.required],
    internalReason: [''],
  });

  confirm(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error = null;
    this.stale = false;
    this.busy = true;
    const { reasonCode, customerMessage } = this.form.getRawValue();
    this.api
      .reject(this.data.row.id, {
        reasonCode,
        customerMessage: customerMessage.trim(),
      })
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.stale = true;
            this.error = 'This request was already actioned by someone else';
          } else {
            this.error = mapped.message;
          }
        },
      });
  }

  reload(): void {
    this.ref.close('reload');
  }
}
