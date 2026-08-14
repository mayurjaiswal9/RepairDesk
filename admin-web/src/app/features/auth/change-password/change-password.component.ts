import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { AuthApi } from '../../../core/api/auth.api';
import { mapHttpError } from '../../../core/api/api-error';
import { SessionService } from '../../../core/auth/session.service';
import { branding } from '../../../core/branding/branding';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly branding = branding;

  readonly submitting = signal(false);
  readonly show = signal({ current: false, next: false, confirm: false });
  readonly errorBanner = signal<string | null>(null);
  readonly correlationId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });

  toggle(field: 'current' | 'next' | 'confirm'): void {
    this.show.update((s) => ({ ...s, [field]: !s[field] }));
  }

  signOut(): void {
    this.session.clear();
    this.authApi.logout().subscribe({ error: () => undefined });
    void this.router.navigate(['/login'], { queryParams: { reason: 'signed-out' } });
  }

  submit(): void {
    this.errorBanner.set(null);
    this.correlationId.set(null);
    this.form.markAllAsTouched();
    const { currentPassword, newPassword, confirmPassword } = this.form.getRawValue();

    if (this.form.invalid) return;
    if (newPassword !== confirmPassword) {
      this.form.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }

    this.submitting.set(true);
    this.authApi
      .changePassword({ currentPassword, newPassword })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.session.clearMustChangePassword();
          this.snackBar.open('Password updated', undefined, { duration: 4000 });
          void this.router.navigateByUrl(this.session.defaultLanding(), { replaceUrl: true });
        },
        error: (err) => {
          const mapped = mapHttpError(err);
          // Wrong current password returns 403 from the API — keep it as a field/banner
          // error, never the page-level forbidden panel (AP-01 §10 Q4).
          if (mapped.status === 403) {
            this.form.controls.currentPassword.setErrors({
              server: 'Current password is incorrect.',
            });
            return;
          }
          if (mapped.status === 422) {
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
            this.errorBanner.set(mapped.message);
            return;
          }
          this.errorBanner.set(mapped.message);
          this.correlationId.set(mapped.correlationId ?? null);
        },
      });
  }
}
