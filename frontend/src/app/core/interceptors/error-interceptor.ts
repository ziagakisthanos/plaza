import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../services/notification';

export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const wasSignedIn = req.headers.has('Authorization');
      const isSignInRequest = req.url.includes('/auth/');
      if (error.status === 401 && wasSignedIn && !isSignInRequest) {
        notifications.show('error', SESSION_EXPIRED_MESSAGE);
      }
      return throwError(() => error);
    }),
  );
};
