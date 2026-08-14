import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { finalize } from 'rxjs';
import { UsersApi } from '../../../core/api/users.api';
import { mapHttpError } from '../../../core/api/api-error';
import { UserProfile } from '../../../core/models/dashboard.models';

export type StaffDialogResult = boolean | 'stale' | 'last_admin' | UserProfile | undefined;

const ROLE_OPTIONS = [
  { value: 'STAFF', label: 'Staff' },
  { value: 'TECHNICIAN', label: 'Technician' },
  { value: 'ADMIN', label: 'Admin' },
] as const;

const MOBILE_PATTERN = /^[+\d][\d\s-]{6,19}$/;

function displayName(u: UserProfile): string {
  return u.name?.trim() || u.username;
}

function isLastAdminConflict(mapped: { errorCode?: string; message: string }): boolean {
  if (mapped.errorCode === 'LAST_ADMIN') return true;
  return /last active admin/i.test(mapped.message);
}

function bindConflictFields(
  message: string,
  setUsername?: (msg: string) => void,
  setMobile?: (msg: string) => void,
): string | null {
  const lower = message.toLowerCase();
  if (lower.includes('username') && setUsername) {
    setUsername(message.includes('taken') ? `The username is already taken.` : message);
    return null;
  }
  if (lower.includes('mobile') && setMobile) {
    setMobile(
      lower.includes('use') || lower.includes('registered')
        ? 'That mobile number is already in use.'
        : message,
    );
    return null;
  }
  return message;
}

@Component({
  selector: 'app-create-user-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Add user</h2>
    <mat-dialog-content>
      <p class="body">Create an internal account. Usernames can't be changed later.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <form [formGroup]="form" class="form" (ngSubmit)="confirm()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="name" maxlength="100" autocomplete="name" />
          @if (form.controls.name.touched && form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          } @else if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Username</mat-label>
          <input matInput formControlName="username" autocomplete="off" />
          <mat-hint>Usernames can't be changed later.</mat-hint>
          @if (form.controls.username.touched && form.controls.username.hasError('required')) {
            <mat-error>Username is required</mat-error>
          } @else if (form.controls.username.hasError('minlength')) {
            <mat-error>At least 3 characters</mat-error>
          } @else if (form.controls.username.hasError('server')) {
            <mat-error>{{ form.controls.username.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Mobile</mat-label>
          <input matInput formControlName="mobile" autocomplete="tel" />
          @if (form.controls.mobile.touched && form.controls.mobile.hasError('required')) {
            <mat-error>Mobile is required</mat-error>
          } @else if (form.controls.mobile.hasError('pattern')) {
            <mat-error>Enter a valid mobile number</mat-error>
          } @else if (form.controls.mobile.hasError('server')) {
            <mat-error>{{ form.controls.mobile.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Temporary password</mat-label>
          <input
            matInput
            [type]="show ? 'text' : 'password'"
            formControlName="password"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="show = !show"
            [attr.aria-label]="show ? 'Hide' : 'Show'"
          >
            <mat-icon>{{ show ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          <mat-hint>Share this with the user directly — RepairDesk doesn't send it.</mat-hint>
          @if (form.controls.password.touched && form.controls.password.hasError('required')) {
            <mat-error>Password is required</mat-error>
          } @else if (form.controls.password.hasError('minlength')) {
            <mat-error>At least 8 characters</mat-error>
          } @else if (form.controls.password.hasError('server')) {
            <mat-error>{{ form.controls.password.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Role</mat-label>
          <mat-select formControlName="role">
            @for (r of roles; track r.value) {
              <mat-option [value]="r.value">{{ r.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || form.invalid || form.pristine"
        (click)="confirm()"
      >
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Create user
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0 0 12px; color: var(--color-text-secondary); line-height: 1.45; }
    .form { display: flex; flex-direction: column; gap: 4px; }
    .full { width: 100%; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class CreateUserDialogComponent {
  private readonly ref = inject(MatDialogRef<CreateUserDialogComponent, StaffDialogResult>);
  private readonly api = inject(UsersApi);
  private readonly fb = inject(FormBuilder);

  readonly roles = ROLE_OPTIONS;
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    mobile: ['', [Validators.required, Validators.pattern(MOBILE_PATTERN)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['STAFF' as string, Validators.required],
  });
  show = false;
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy = true;
    this.error = null;
    const v = this.form.getRawValue();
    this.api
      .create(v)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (created) => this.ref.close(created),
        error: (err) => {
          const mapped = mapHttpError(err);
          for (const [field, msg] of Object.entries(mapped.fieldErrors)) {
            const ctrl = this.form.get(field);
            if (ctrl) ctrl.setErrors({ server: msg });
          }
          const leftover = bindConflictFields(
            mapped.message,
            (m) => this.form.controls.username.setErrors({ server: m }),
            (m) => this.form.controls.mobile.setErrors({ server: m }),
          );
          if (leftover && Object.keys(mapped.fieldErrors).length === 0) this.error = leftover;
        },
      });
  }
}

@Component({
  selector: 'app-edit-user-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  template: `
    <h2 mat-dialog-title>Edit {{ displayName }}</h2>
    <mat-dialog-content>
      @if (lastAdmin) {
        <div class="err" role="alert">
          RepairDesk must always have at least one active administrator, and this is the only one.
          Give another user the Admin role first, then try again.
        </div>
      } @else if (stale) {
        <div class="err" role="alert">
          This record changed since you opened it.
          <button mat-button type="button" color="primary" (click)="reload()">Reload</button>
        </div>
      } @else if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
      <p class="username mono">{{ '@' + data.user.username }}</p>
      <form [formGroup]="form" class="form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="name" maxlength="100" />
          @if (form.controls.name.touched && form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Mobile</mat-label>
          <input matInput formControlName="mobile" />
          @if (form.controls.mobile.touched && form.controls.mobile.hasError('required')) {
            <mat-error>Mobile is required</mat-error>
          } @else if (form.controls.mobile.hasError('pattern')) {
            <mat-error>Enter a valid mobile number</mat-error>
          } @else if (form.controls.mobile.hasError('server')) {
            <mat-error>{{ form.controls.mobile.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field
          appearance="outline"
          class="full"
          [matTooltip]="roleLockedReason || ''"
          [matTooltipDisabled]="!roleLockedReason"
        >
          <mat-label>Role</mat-label>
          <mat-select formControlName="role">
            @for (r of roles; track r.value) {
              <mat-option [value]="r.value">{{ r.label }}</mat-option>
            }
          </mat-select>
          @if (form.controls.role.hasError('server')) {
            <mat-error>{{ form.controls.role.getError('server') }}</mat-error>
          }
        </mat-form-field>
        @if (data.user.role === 'TECHNICIAN' && form.controls.role.value !== 'TECHNICIAN') {
          <p class="note">
            Existing jobs assigned to this technician stay assigned, but they won't be selectable for
            new assignments.
          </p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="busy || lastAdmin || form.invalid || form.pristine"
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
    .username { margin: 0 0 12px; color: var(--color-text-secondary); font-size: 13px; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
    .form { display: flex; flex-direction: column; }
    .full { width: 100%; }
    .note { margin: 0 0 8px; font-size: 12px; color: var(--color-text-secondary); line-height: 1.45; }
    .err {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
      display: flex; flex-wrap: wrap; align-items: center; gap: 8px;
    }
  `,
})
export class EditUserDialogComponent {
  readonly data = inject<{
    user: UserProfile;
    isSelf: boolean;
    isSoleAdmin: boolean;
  }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<EditUserDialogComponent, StaffDialogResult>);
  private readonly api = inject(UsersApi);
  private readonly fb = inject(FormBuilder);

  readonly roles = ROLE_OPTIONS;
  readonly displayName = displayName(this.data.user);
  readonly roleLockedReason = this.data.isSelf
    ? "You can't change your own role. Ask another administrator to do it."
    : this.data.isSoleAdmin && this.data.user.role === 'ADMIN'
      ? 'This is the only active administrator.'
      : '';

  readonly form = this.fb.nonNullable.group({
    name: [this.data.user.name ?? '', [Validators.required, Validators.maxLength(100)]],
    mobile: [
      this.data.user.mobile,
      [Validators.required, Validators.pattern(MOBILE_PATTERN)],
    ],
    role: [{ value: this.data.user.role, disabled: !!this.roleLockedReason }, Validators.required],
  });

  busy = false;
  error: string | null = null;
  lastAdmin = false;
  stale = false;

  reload(): void {
    this.ref.close('stale');
  }

  confirm(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.lastAdmin) return;
    this.busy = true;
    this.error = null;
    const v = this.form.getRawValue();
    this.api
      .update(this.data.user.id, v)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (updated) => this.ref.close(updated),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (isLastAdminConflict(mapped)) {
            this.lastAdmin = true;
            this.form.controls.role.setValue('ADMIN');
            this.form.controls.role.setErrors({
              server: 'This is the only active administrator.',
            });
            return;
          }
          if (mapped.status === 409 && /changed|conflict|version/i.test(mapped.message)) {
            this.stale = true;
            return;
          }
          for (const [field, msg] of Object.entries(mapped.fieldErrors)) {
            const ctrl = this.form.get(field);
            if (ctrl) ctrl.setErrors({ server: msg });
          }
          const leftover = bindConflictFields(mapped.message, undefined, (m) =>
            this.form.controls.mobile.setErrors({ server: m }),
          );
          if (leftover && Object.keys(mapped.fieldErrors).length === 0) this.error = leftover;
        },
      });
  }
}

@Component({
  selector: 'app-disable-user-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Disable {{ displayName }}?</h2>
    <mat-dialog-content>
      @if (lastAdmin) {
        <div class="err" role="alert">
          RepairDesk must always have at least one active administrator, and this is the only one.
          Give another user the Admin role first, then try again.
        </div>
      } @else {
        @if (data.isSelf) {
          <p class="warn">This is your own account. You will be signed out of RepairDesk immediately.</p>
        }
        <p class="body">
          They will be signed out immediately and won't be able to sign in until you enable the
          account again. Nothing is deleted — their record, and any jobs assigned to them, are kept.
        </p>
        @if (error) {
          <div class="err" role="alert">{{ error }}</div>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (lastAdmin) {
        <button mat-button type="button" mat-dialog-close>Close</button>
      } @else {
        <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
          @if (busy) {
            <mat-spinner diameter="18"></mat-spinner>
          }
          Disable
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .warn {
      margin: 0 0 10px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c;
      font-size: 13px; font-weight: 600; line-height: 1.45;
    }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class DisableUserDialogComponent {
  readonly data = inject<{ user: UserProfile; isSelf: boolean }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DisableUserDialogComponent, StaffDialogResult>);
  private readonly api = inject(UsersApi);

  readonly displayName = displayName(this.data.user);
  busy = false;
  error: string | null = null;
  lastAdmin = false;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .deactivate(this.data.user.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (updated) => this.ref.close(updated),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (isLastAdminConflict(mapped)) {
            this.lastAdmin = true;
            return;
          }
          this.error = mapped.message;
        },
      });
  }
}

@Component({
  selector: 'app-enable-user-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Enable {{ displayName }}?</h2>
    <mat-dialog-content>
      <p class="body">{{ displayName }} will be able to sign in again.</p>
      @if (error) {
        <div class="err" role="alert">{{ error }}</div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="busy" (click)="confirm()">
        @if (busy) {
          <mat-spinner diameter="18"></mat-spinner>
        }
        Enable
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class EnableUserDialogComponent {
  readonly data = inject<{ user: UserProfile }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<EnableUserDialogComponent, StaffDialogResult>);
  private readonly api = inject(UsersApi);

  readonly displayName = displayName(this.data.user);
  busy = false;
  error: string | null = null;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .activate(this.data.user.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (updated) => this.ref.close(updated),
        error: (err) => {
          this.error = mapHttpError(err).message;
        },
      });
  }
}

@Component({
  selector: 'app-delete-staff-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Delete {{ displayName }}'s account?</h2>
    <mat-dialog-content>
      @if (lastAdmin) {
        <div class="err" role="alert">
          RepairDesk must always have at least one active administrator, and this is the only one.
          Give another user the Admin role first, then try again.
        </div>
      } @else {
        <p class="body">
          This is a <strong>logical delete</strong>. Their access is revoked immediately and they are
          signed out, but their history — the jobs they worked on and the audit trail — is retained
          and stays attributable to them. Their username and mobile become available for a new
          account.
        </p>
        @if (error) {
          <div class="err" role="alert">{{ error }}</div>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (lastAdmin) {
        <button mat-button type="button" mat-dialog-close>Close</button>
      } @else {
        <button mat-button type="button" [disabled]="busy" mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" type="button" [disabled]="busy" (click)="confirm()">
          @if (busy) {
            <mat-spinner diameter="18"></mat-spinner>
          }
          Delete account
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .body { margin: 0; color: var(--color-text-secondary); line-height: 1.5; }
    .err {
      margin-top: 12px; padding: 10px 12px; border-radius: 12px;
      background: #fff1f0; border: 1px solid #ffc9c4; color: #b71c1c; font-size: 13px;
    }
  `,
})
export class DeleteStaffDialogComponent {
  readonly data = inject<{ user: UserProfile }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DeleteStaffDialogComponent, StaffDialogResult>);
  private readonly api = inject(UsersApi);

  readonly displayName = displayName(this.data.user);
  busy = false;
  error: string | null = null;
  lastAdmin = false;

  confirm(): void {
    this.busy = true;
    this.error = null;
    this.api
      .deleteUser(this.data.user.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (isLastAdminConflict(mapped)) {
            this.lastAdmin = true;
            return;
          }
          this.error = mapped.message;
        },
      });
  }
}
