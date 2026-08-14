import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { RequestsApi } from '../../../core/api/requests.api';
import { mapHttpError } from '../../../core/api/api-error';
import { DecisionTarget } from '../../../core/models/request.models';

export interface InfoDialogData {
  row: DecisionTarget;
}

@Component({
  selector: 'app-request-info-dialog',
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
    <h2 mat-dialog-title>Request more information</h2>
    <mat-dialog-content>
      <p class="body">
        {{ data.row.referenceNumber }} will move to Needs info.
        {{ data.row.contactName || 'The customer' }} will be notified.
      </p>
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
          <mat-label>Message to customer</mat-label>
          <textarea
            matInput
            rows="4"
            formControlName="message"
            placeholder="What do you need from the customer?"
          ></textarea>
          @if (form.controls.message.touched && form.controls.message.hasError('required')) {
            <mat-error>Enter a message for the customer.</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Send
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
export class RequestInfoDialogComponent {
  readonly data = inject<InfoDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RequestInfoDialogComponent, boolean | 'reload'>);
  private readonly api = inject(RequestsApi);
  private readonly fb = inject(FormBuilder);

  busy = false;
  error: string | null = null;
  stale = false;

  readonly form = this.fb.nonNullable.group({
    message: ['', Validators.required],
  });

  confirm(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error = null;
    this.stale = false;
    this.busy = true;
    this.api
      .requestInfo(this.data.row.id, { message: this.form.controls.message.value.trim() })
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
