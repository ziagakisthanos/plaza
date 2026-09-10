import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();

  // if we have a token, clone the request and add the Authorization header
  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  // send the (possibly modified) request, and handle auth errors on the response
  return next(authReq).pipe(
    catchError((error) => {
      if (error.status === 401) {
        // token invalid/expired -> log out and send to login
        authService.logout();
        router.navigate(['/login']);
      }
      // 403 = authenticated but not allowed (e.g. CLIENT hitting seller route)
      // we let that propagate so the component can show a message; no redirect
      return throwError(() => error);
    }),
  );
};
