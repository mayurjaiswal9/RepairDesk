import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { UsersApi } from '../../../core/api/users.api';
import { mapHttpError } from '../../../core/api/api-error';
import { UserProfile } from '../../../core/models/dashboard.models';

export type CustomerDialogResult = boolean | 'stale' | undefined;

@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Reset password — {{ displayName }}</h2>
    <mat-dialog-content>
      <p class="body">
        Set a new password for this account. The customer will need this password to sign in, so share it with them
        directly.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>New password</mat-label>
        <input matInput [type]="show ? 'text' : 'password'" [formControl]="password" autocomplete="new-password" />
        <button mat-icon-button matSuffix type="button" (click)="show = !show" [attr.aria-label]="show ? 'Hide' : 'Show'">
          <mat-icon>{{ show ? 'visibility_off' : 'visibility' }}</mat-icon>
        </button>
        @if (password.touched && password.hasError('required')) {
          <mat-error>Password is required</mat-error>
        } @else if (password.touched && password.hasError('minlength')) {
          <mat-error>At least 8 characters</mat-error>
        } @else if (password.hasError('server')) {
          <mat-error>{{ password.getError('server') }}</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || password.invalid"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Reset password
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); line-height: 1.45; }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class ResetPasswordDialogComponent {
  readonly data = inject<{ customer: UserProfile }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ResetPasswordDialogComponent, CustomerDialogResult>);
  private readonly api = inject(UsersApi);
  private readonly fb = inject(FormBuilder);

  readonly displayName = this.data.customer.name || this.data.customer.username;
  readonly password = this.fb.nonNullable.control('', [
    Validators.required,
    Validators.minLength(8),
  ]);
  show = false;
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.password.markAsTouched();
    if (this.password.invalid) return;
    this.busy = true;
    this.error = null;
    const value = this.password.value;
    this.api
      .resetPassword(this.data.customer.id, value)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => {
          this.password.reset('');
          this.ref.close(true);
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 404 || mapped.status === 409) {
            this.ref.close('stale');
            return;
          }
          if (mapped.fieldErrors['newPassword']) {
            this.password.setErrors({ server: mapped.fieldErrors['newPassword'] });
          } else {
            this.error = mapped.message;
          }
        },
      });
  }
}

@Component({
  selector: 'app-delete-customer-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Delete {{ displayName }}'s account?</h2>
    <mat-dialog-content>
      <p class="body">
        This is a <strong>logical delete</strong>. Their access is revoked immediately and any active session ends, but
        @if (hasCounts) {
          the {{ requestCount }} request{{ requestCount === 1 ? '' : 's' }} and
          {{ jobCount }} job{{ jobCount === 1 ? '' : 's' }} shown on this page are kept so history stays intact.
        } @else {
          their repair requests and jobs are kept so history and reporting stay intact.
        }
        Their username and mobile become available for a new account.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Delete account
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 8px; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class DeleteCustomerDialogComponent {
  readonly data = inject<{
    customer: UserProfile;
    requestCount?: number;
    jobCount?: number;
  }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DeleteCustomerDialogComponent, CustomerDialogResult>);
  private readonly api = inject(UsersApi);

  readonly displayName = this.data.customer.name || this.data.customer.username;
  readonly requestCount = this.data.requestCount ?? 0;
  readonly jobCount = this.data.jobCount ?? 0;
  readonly hasCounts =
    this.data.requestCount !== undefined && this.data.jobCount !== undefined;
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .deleteUser(this.data.customer.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 404 || mapped.status === 409) {
            this.ref.close('stale');
            return;
          }
          this.error = mapped.message;
        },
      });
  }
}
