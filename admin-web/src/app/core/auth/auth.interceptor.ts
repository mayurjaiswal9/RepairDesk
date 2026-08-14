import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';

let handlingUnauthorized = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const token = session.token();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthEndpoint =
        req.url.includes('/auth/login')
        || req.url.includes('/auth/reset-password')
        || req.url.includes('/auth/register');

      if (err.status === 401 && !isAuthEndpoint && session.isAuthenticated()) {
        if (!handlingUnauthorized) {
          handlingUnauthorized = true;
          const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
          session.clear();
          void router.navigate(['/login'], {
            queryParams: {
              reason: 'session',
              ...(returnUrl ? { returnUrl } : {}),
            },
          }).finally(() => {
            handlingUnauthorized = false;
          });
        }
      }
      return throwError(() => err);
    }),
  );
};
