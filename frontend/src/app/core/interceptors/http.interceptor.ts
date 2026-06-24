import {HttpErrorResponse, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {catchError, switchMap, throwError} from 'rxjs';
import {AuthService} from '../services/auth.service';

export const httpInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const accessToken = authService.getAccessToken();
  const isAuthRequest = req.url.includes('/auth/login')
    || req.url.includes('/auth/register')
    || req.url.includes('/auth/refresh')
    || req.url.includes('/auth/logout')
    || req.url.includes('/auth/forgot-password')
    || req.url.includes('/auth/reset-password')
    || req.url.includes('/auth/resend-verification')
    || req.url.includes('/auth/verify-email');

  const authorizedRequest = accessToken && !isAuthRequest
    ? req.clone({
      setHeaders: {
        Authorization: `Bearer ${accessToken}`
      }
    })
    : req;

  return next(authorizedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthRequest || !authService.getRefreshToken()) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((newAccessToken) => next(req.clone({
          setHeaders: {
            Authorization: `Bearer ${newAccessToken}`
          }
        }))),
        catchError((refreshError) => {
          authService.clearSession();
          return throwError(() => refreshError);
        })
      );
    })
  );
};
