import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';

/** Requires an authenticated internal-role session. */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url, reason: 'session' },
    });
  }

  if (session.mustChangePassword()) {
    return router.createUrlTree(['/change-password']);
  }

  const role = session.role();
  if (!session.isInternalRole(role)) {
    session.clear();
    return router.createUrlTree(['/login']);
  }

  // Technicians land on Jobs, not Dashboard (AP-03 §7).
  if (role === 'TECHNICIAN' && (state.url === '/dashboard' || state.url.startsWith('/dashboard?'))) {
    return router.createUrlTree(['/jobs']);
  }

  if (!session.canAccessRoute(state.url, role)) {
    return router.createUrlTree(['/403']);
  }

  return true;
};

/** Public routes: bounce authenticated users away from login. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.isAuthenticated() && session.isInternalRole(session.role())) {
    if (session.mustChangePassword()) {
      return router.createUrlTree(['/change-password']);
    }
    return router.createUrlTree([session.defaultLanding()]);
  }
  return true;
};

/** Forced password-change step — provisional session required. */
export const mustChangeGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (!session.mustChangePassword()) {
    return router.createUrlTree([session.defaultLanding()]);
  }
  return true;
};
