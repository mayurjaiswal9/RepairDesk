import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { AuthApi } from '../../../core/api/auth.api';
import { mapHttpError } from '../../../core/api/api-error';
import { SessionService } from '../../../core/auth/session.service';
import { branding } from '../../../core/branding/branding';

type LoginStep = 'sign-in' | 'forgot' | 'forgot-done';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly branding = branding;

  readonly step = signal<LoginStep>('sign-in');
  readonly submitting = signal(false);
  readonly showPassword = signal(false);
  readonly showNewPassword = signal(false);
  readonly errorBanner = signal<string | null>(null);
  readonly sessionNotice = signal<string | null>(null);
  readonly correlationId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  readonly forgotForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });

  ngOnInit(): void {
    const reason = this.route.snapshot.queryParamMap.get('reason');
    if (reason === 'session') {
      this.sessionNotice.set('Your session is no longer valid. Please sign in again.');
    } else if (reason === 'signed-out') {
      this.sessionNotice.set('Sign out complete.');
    }
  }

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  toggleNewPassword(): void {
    this.showNewPassword.update((v) => !v);
  }

  openForgot(): void {
    this.errorBanner.set(null);
    this.correlationId.set(null);
    this.forgotForm.reset({
      username: this.form.controls.username.value,
      newPassword: '',
      confirmPassword: '',
    });
    this.step.set('forgot');
  }

  backToSignIn(): void {
    this.errorBanner.set(null);
    this.correlationId.set(null);
    this.form.patchValue({
      username: this.forgotForm.controls.username.value || this.form.controls.username.value,
      password: '',
    });
    this.step.set('sign-in');
  }

  submitSignIn(): void {
    this.errorBanner.set(null);
    this.correlationId.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.form.disable({ emitEvent: false });

    this.authApi
      .login({ username: username.trim(), password })
      .pipe(
        finalize(() => {
          this.submitting.set(false);
          this.form.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: (res) => this.handleLoginSuccess(res),
        error: (err) => this.handleLoginError(err),
      });
  }

  submitForgot(): void {
    this.errorBanner.set(null);
    this.correlationId.set(null);
    this.forgotForm.markAllAsTouched();
    const { username, newPassword, confirmPassword } = this.forgotForm.getRawValue();

    if (this.forgotForm.invalid) return;
    if (newPassword !== confirmPassword) {
      this.forgotForm.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }

    this.submitting.set(true);
    this.authApi
      .resetPassword({ username: username.trim(), newPassword })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.step.set('forgot-done'),
        error: (err) => {
          const mapped = mapHttpError(err);
          if (mapped.status === 422) {
            if (mapped.fieldErrors['newPassword']) {
              this.forgotForm.controls.newPassword.setErrors({
                server: mapped.fieldErrors['newPassword'],
              });
            }
            this.errorBanner.set(mapped.message);
          } else if (mapped.status === 0) {
            this.errorBanner.set(mapped.message);
          } else {
            this.errorBanner.set(mapped.message);
            this.correlationId.set(mapped.correlationId ?? null);
          }
        },
      });
  }

  private handleLoginSuccess(res: import('../../../core/models/auth.models').AuthResponse): void {
    if (res.role === 'CUSTOMER') {
      // Authenticate then refuse — never persist a panel session.
      this.session.discardToken();
      this.authApi.logout().subscribe({ error: () => undefined });
      this.errorBanner.set(
        'This panel is for staff. Customers should use the RepairDesk mobile app to submit and track repairs.',
      );
      this.form.patchValue({ password: '' });
      return;
    }

    if (!this.session.isInternalRole(res.role)) {
      this.errorBanner.set('This panel is for staff.');
      this.form.patchValue({ password: '' });
      return;
    }

    this.session.establish(res);
    this.sessionNotice.set(null);

    if (res.mustChangePassword) {
      void this.router.navigateByUrl('/change-password', { replaceUrl: true });
      return;
    }

    void this.router.navigateByUrl(this.resolveReturnUrl(res.role), { replaceUrl: true });
  }

  private handleLoginError(err: unknown): void {
    const mapped = mapHttpError(err, 'Username or password is incorrect.');
    this.form.patchValue({ password: '' });

    if (mapped.status === 401) {
      this.errorBanner.set('Username or password is incorrect.');
      return;
    }
    if (mapped.status === 403) {
      this.errorBanner.set('This account is not active. Contact your administrator.');
      return;
    }
    if (mapped.status === 429) {
      this.errorBanner.set('Too many sign-in attempts. Wait a moment and try again.');
      return;
    }
    if (mapped.status === 0) {
      this.errorBanner.set(mapped.message);
      return;
    }
    this.errorBanner.set(mapped.message || 'Something went wrong. Please try again.');
    this.correlationId.set(mapped.correlationId ?? null);
  }

  private resolveReturnUrl(role: import('../../../core/models/auth.models').UserRole): string {
    const raw = this.route.snapshot.queryParamMap.get('returnUrl');
    if (!raw) return this.session.defaultLanding(role);

    // Reject absolute / protocol-relative / off-panel values.
    if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('://')) {
      return this.session.defaultLanding(role);
    }

    if (!this.session.canAccessRoute(raw, role)) {
      return this.session.defaultLanding(role);
    }
    return raw;
  }
}
