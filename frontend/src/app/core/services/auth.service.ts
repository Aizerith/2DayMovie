import {HttpBackend, HttpClient} from '@angular/common/http';
import {computed, inject, Injectable, signal} from '@angular/core';
import {catchError, finalize, map, Observable, of, shareReplay, switchMap, tap, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';
import {
  AuthResponse,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  RefreshTokenRequest,
  ResendVerificationRequest,
  ResetPasswordRequest,
  User,
  VerifyEmailRequest
} from '../models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly rawHttp = new HttpClient(inject(HttpBackend));
  private readonly baseUrl = `${environment.apiUrl}/auth`;
  private readonly accessTokenKey = 'bp_access_token';
  private readonly refreshTokenKey = 'bp_refresh_token';
  private readonly currentUserSignal = signal<User | null>(null);
  private refreshRequest$?: Observable<AuthResponse>;

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null || this.hasAccessToken());

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.rawHttp.post<AuthResponse>(`${this.baseUrl}/login`, payload).pipe(
      tap((response) => this.storeSession(response))
    );
  }

  register(payload: RegisterRequest): Observable<void> {
    return this.rawHttp.post<void>(`${this.baseUrl}/register`, payload);
  }

  loadCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/me`).pipe(
      tap((user) => this.currentUserSignal.set(user))
    );
  }

  refreshSession(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();

    if (!refreshToken) {
      this.clearSession();
      return throwError(() => new Error('No refresh token available'));
    }

    if (!this.refreshRequest$) {
      const payload: RefreshTokenRequest = {refreshToken};
      this.refreshRequest$ = this.rawHttp.post<AuthResponse>(`${this.baseUrl}/refresh`, payload).pipe(
        tap((response) => this.storeSession(response)),
        shareReplay(1),
        finalize(() => {
          this.refreshRequest$ = undefined;
        })
      );
    }

    return this.refreshRequest$;
  }

  bootstrap(): void {
    if (!this.hasAccessToken()) {
      return;
    }

    this.loadCurrentUser().pipe(
      catchError(() => this.refreshSession().pipe(
        switchMap(() => this.loadCurrentUser()),
        catchError((error) => {
          this.clearSession();
          return throwError(() => error);
        })
      ))
    ).subscribe({
      error: () => this.clearSession()
    });
  }

  logout(): Observable<void> {
    const refreshToken = this.getRefreshToken();

    if (!refreshToken) {
      this.clearSession();
      return of(void 0);
    }

    return this.rawHttp.post<void>(`${this.baseUrl}/logout`, {refreshToken}).pipe(
      tap(() => this.clearSession()),
      catchError((error) => {
        this.clearSession();
        return throwError(() => error);
      })
    );
  }

  forgotPassword(payload: ForgotPasswordRequest): Observable<void> {
    return this.rawHttp.post<void>(`${this.baseUrl}/forgot-password`, payload);
  }

  resetPassword(payload: ResetPasswordRequest): Observable<void> {
    return this.rawHttp.post<void>(`${this.baseUrl}/reset-password`, payload);
  }

  resendVerification(payload: ResendVerificationRequest): Observable<void> {
    return this.rawHttp.post<void>(`${this.baseUrl}/resend-verification`, payload);
  }

  verifyEmail(payload: VerifyEmailRequest): Observable<void> {
    return this.rawHttp.post<void>(`${this.baseUrl}/verify-email`, payload);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  hasAccessToken(): boolean {
    return !!this.getAccessToken();
  }

  getCurrentUserRole(): string | null {
    return this.currentUserSignal()?.role ?? this.decodeTokenPayload()?.['role'] ?? null;
  }

  clearSession(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    this.currentUserSignal.set(null);
  }

  refreshAccessToken(): Observable<string> {
    return this.refreshSession().pipe(map((response) => response.accessToken));
  }

  private storeSession(response: AuthResponse): void {
    localStorage.setItem(this.accessTokenKey, response.accessToken);
    localStorage.setItem(this.refreshTokenKey, response.refreshToken);
    this.currentUserSignal.set(response.user);
  }

  private decodeTokenPayload(): Record<string, string> | null {
    const token = this.getAccessToken();

    if (!token) {
      return null;
    }

    try {
      const payload = token.split('.')[1];

      if (!payload) {
        return null;
      }

      const normalizedPayload = payload
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(payload.length / 4) * 4, '=');

      return JSON.parse(atob(normalizedPayload)) as Record<string, string>;
    } catch {
      return null;
    }
  }
}
