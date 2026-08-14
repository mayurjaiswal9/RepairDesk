import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { AuthApi } from '../../../core/api/auth.api';
import { MeApi } from '../../../core/api/me.api';
import { mapHttpError } from '../../../core/api/api-error';
import { UserProfile } from '../../../core/models/dashboard.models';

export type AccountDialogResult = boolean | UserProfile | undefined;

const MOBILE_PATTERN = /^\+?[0-9 \-]{8,15}$/;

@Component({
  selector: 'app-change-mobile-dialog',
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
    <h2 mat-dialog-title>Change mobile number</h2>
    <mat-dialog-content>
      <p class="body">You sign in with this number, so use the new one next time.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>Mobile number</mat-label>
        <input matInput type="tel" [formControl]="mobile" maxlength="20" autocomplete="tel" />
        @if (mobile.touched && mobile.hasError('required')) {
          <mat-error>Mobile number is required</mat-error>
        } @else if (mobile.hasError('pattern')) {
          <mat-error>Enter a valid mobile number</mat-error>
        } @else if (mobile.hasError('server')) {
          <mat-error>{{ mobile.getError('server') }}</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || mobile.invalid || !changed"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Save
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
export class ChangeMobileDialogComponent {
  readonly data = inject<{ mobile: string }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ChangeMobileDialogComponent, AccountDialogResult>);
  private readonly api = inject(MeApi);
  private readonly fb = inject(FormBuilder);

  readonly mobile = this.fb.nonNullable.control(this.data.mobile ?? '', [
    Validators.required,
    Validators.pattern(MOBILE_PATTERN),
  ]);
  busy = false;
  error: string | null = null;

  get changed(): boolean {
    const next = this.mobile.value.replace(/[\s\-]/g, '');
    const prev = (this.data.mobile ?? '').replace(/[\s\-]/g, '');
    return next !== prev;
  }

  confirm(): void {
    this.mobile.markAsTouched();
    if (this.mobile.invalid || !this.changed) return;
    this.busy = true;
    this.error = null;
    this.api
      .updateMobile(this.mobile.value.trim())
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (profile) => this.ref.close(profile),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 409) {
            this.mobile.setErrors({ server: 'That mobile number is already in use.' });
            return;
          }
          if (mapped.fieldErrors['mobile']) {
            this.mobile.setErrors({ server: mapped.fieldErrors['mobile'] });
            return;
          }
          this.error = mapped.message;
        },
      });
  }
}

@Component({
  selector: 'app-account-change-password-dialog',
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
    <h2 mat-dialog-title>Change password</h2>
    <mat-dialog-content>
      <p class="body">
        You'll stay signed in on this browser. If you're signed in anywhere else, those sessions stay
        active too — sign out there if you don't want that.
      </p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Current password</mat-label>
          <input
            matInput
            [type]="show.current ? 'text' : 'password'"
            formControlName="currentPassword"
            autocomplete="current-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="show.current = !show.current"
            [attr.aria-label]="show.current ? 'Hide' : 'Show'"
          >
            <mat-icon>{{ show.current ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (form.controls.currentPassword.touched && form.controls.currentPassword.hasError('required')) {
            <mat-error>Current password is required</mat-error>
          } @else if (form.controls.currentPassword.hasError('server')) {
            <mat-error>{{ form.controls.currentPassword.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>New password</mat-label>
          <input
            matInput
            [type]="show.next ? 'text' : 'password'"
            formControlName="newPassword"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="show.next = !show.next"
            [attr.aria-label]="show.next ? 'Hide' : 'Show'"
          >
            <mat-icon>{{ show.next ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (form.controls.newPassword.touched && form.controls.newPassword.hasError('required')) {
            <mat-error>New password is required</mat-error>
          } @else if (form.controls.newPassword.hasError('minlength')) {
            <mat-error>At least 8 characters</mat-error>
          } @else if (form.controls.newPassword.hasError('same')) {
            <mat-error>New password must differ from the current one</mat-error>
          } @else if (form.controls.newPassword.hasError('server')) {
            <mat-error>{{ form.controls.newPassword.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Confirm new password</mat-label>
          <input
            matInput
            [type]="show.confirm ? 'text' : 'password'"
            formControlName="confirmPassword"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="show.confirm = !show.confirm"
            [attr.aria-label]="show.confirm ? 'Hide' : 'Show'"
          >
            <mat-icon>{{ show.confirm ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (form.controls.confirmPassword.touched && form.controls.confirmPassword.hasError('required')) {
            <mat-error>Confirm your new password</mat-error>
          } @else if (form.controls.confirmPassword.hasError('mismatch')) {
            <mat-error>Passwords do not match</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || form.invalid"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Change password
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); line-height: 1.45; }
    .form { display: flex; flex-direction: column; }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class AccountChangePasswordDialogComponent {
  private readonly ref = inject(
    MatDialogRef<AccountChangePasswordDialogComponent, AccountDialogResult>,
  );
  private readonly api = inject(AuthApi);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });
  show = { current: false, next: false, confirm: false };
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.form.markAllAsTouched();
    const { currentPassword, newPassword, confirmPassword } = this.form.getRawValue();
    if (this.form.invalid) return;
    if (newPassword === currentPassword) {
      this.form.controls.newPassword.setErrors({ same: true });
      return;
    }
    if (newPassword !== confirmPassword) {
      this.form.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }
    this.busy = true;
    this.error = null;
    this.api
      .changePassword({ currentPassword, newPassword })
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          // Wrong current password is 403 — keep it on the field (AP-17 §5.2).
          if (mapped.status === 403) {
            this.form.controls.currentPassword.setErrors({
              server: "That's not your current password.",
            });
            return;
          }
          if (mapped.fieldErrors['newPassword']) {
            this.form.controls.newPassword.setErrors({
              server: mapped.fieldErrors['newPassword'],
            });
          }
          if (mapped.fieldErrors['currentPassword']) {
            this.form.controls.currentPassword.setErrors({
              server: mapped.fieldErrors['currentPassword'],
            });
          }
          if (!Object.keys(mapped.fieldErrors).length) this.error = mapped.message;
        },
      });
  }
}
