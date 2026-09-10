import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import {
  CreateCustomerResponse,
  CustomersApi,
} from '../../../core/api/customers.api';
import { mapHttpError } from '../../../core/api/api-error';
import { Priority } from '../../../core/models/request.models';

const MOBILE_PATTERN = /^\+?[0-9 \-]{8,15}$/;

@Component({
  selector: 'app-new-customer-dialog',
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
    <h2 mat-dialog-title>New customer</h2>
    <mat-dialog-content>
      <p class="body">Create an account so this request belongs to them. Name and mobile are enough.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <form [formGroup]="form" class="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" />
          @if (form.controls.name.touched && form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Mobile</mat-label>
          <input matInput formControlName="mobile" placeholder="+91 98765 43210" />
          @if (form.controls.mobile.touched && form.controls.mobile.invalid) {
            <mat-error>Enter a valid mobile number</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Username (optional)</mat-label>
          <input matInput formControlName="username" placeholder="Derived from mobile if empty" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Password (optional)</mat-label>
          <input matInput type="password" formControlName="password" placeholder="Generated if empty" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined" [disabled]="busy">Cancel</button>
      <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="busy">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        } @else {
          Create customer
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); }
    .form { display: flex; flex-direction: column; gap: 4px; min-width: 320px; }
    .full { width: 100%; }
    .err { color: var(--color-error); margin-bottom: 8px; font-size: 13px; }
  `,
})
export class NewCustomerDialogComponent {
  private readonly api = inject(CustomersApi);
  private readonly fb = inject(FormBuilder);
  private readonly ref = inject(MatDialogRef<NewCustomerDialogComponent, CreateCustomerResponse | { useId: string } | undefined>);

  busy = false;
  error = '';
  existingId: string | null = null;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    mobile: ['', [Validators.required, Validators.pattern(MOBILE_PATTERN)]],
    username: [''],
    password: [''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy) return;
    const v = this.form.getRawValue();
    this.busy = true;
    this.error = '';
    this.api
      .create({
        name: v.name.trim(),
        mobile: v.mobile.trim(),
        username: v.username.trim() || undefined,
        password: v.password.trim() || undefined,
      })
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (res) => this.ref.close(res),
        error: (err) => {
          const mapped = mapHttpError(err);
          this.error = mapped.message;
          if (mapped.existingCustomerId) {
            this.existingId = mapped.existingCustomerId;
            this.ref.close({ useId: mapped.existingCustomerId });
          }
        },
      });
  }
}

@Component({
  selector: 'app-generated-password-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Give this password to the customer</h2>
    <mat-dialog-content>
      <p class="body">
        {{ data.name }} can sign in as <strong>{{ '@' + data.username }}</strong>.
        This password will not be shown again.
      </p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Temporary password</mat-label>
        <input matInput readonly [value]="data.password" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="copy()">Copy</button>
      <button mat-flat-button color="primary" mat-dialog-close>Done</button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; }
    .full { width: 100%; min-width: 280px; }
  `,
})
export class GeneratedPasswordDialogComponent {
  readonly data = inject<{ name: string; username: string; password: string }>(MAT_DIALOG_DATA);

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.data.password);
    } catch {
      /* ignore */
    }
  }
}

@Component({
  selector: 'app-create-approve-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Create and approve</h2>
    <mat-dialog-content>
      <p class="body">
        This creates the request and accepts it so a job exists.
        {{ data.customerName }} will be notified that the work was accepted — not that it is still under review.
        Scheduling and assignment happen on the job.
      </p>
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Priority</mat-label>
          <mat-select formControlName="priority">
            <mat-option value="LOW">Low</mat-option>
            <mat-option value="NORMAL">Normal</mat-option>
            <mat-option value="HIGH">High</mat-option>
            <mat-option value="URGENT">Urgent</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Internal note (optional)</mat-label>
          <textarea matInput formControlName="note" rows="3"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined">Back</button>
      <button mat-flat-button color="primary" type="button" (click)="confirm()">Create and approve</button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); max-width: 420px; }
    .form { display: flex; flex-direction: column; }
    .full { width: 100%; }
  `,
})
export class CreateApproveDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ref = inject(
    MatDialogRef<CreateApproveDialogComponent, { priority: Priority; note?: string } | undefined>,
  );
  readonly data = inject<{ customerName: string }>(MAT_DIALOG_DATA);

  readonly form = this.fb.nonNullable.group({
    priority: this.fb.nonNullable.control<Priority>('NORMAL'),
    note: [''],
  });

  confirm(): void {
    const v = this.form.getRawValue();
    this.ref.close({ priority: v.priority, note: v.note.trim() || undefined });
  }
}
