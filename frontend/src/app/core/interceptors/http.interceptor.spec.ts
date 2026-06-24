import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {AuthService} from '../services/auth.service';
import {httpInterceptor} from './http.interceptor';

describe('httpInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: {
    getAccessToken: ReturnType<typeof vi.fn>;
    getRefreshToken: ReturnType<typeof vi.fn>;
    refreshAccessToken: ReturnType<typeof vi.fn>;
    clearSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = {
      getAccessToken: vi.fn(),
      getRefreshToken: vi.fn(),
      refreshAccessToken: vi.fn(),
      clearSession: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpInterceptor])),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: authService}
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('ajoute le bearer token aux requetes protegees', () => {
    authService.getAccessToken.mockReturnValue('access-token');

    http.get('/api/projects').subscribe();

    const request = httpMock.expectOne('/api/projects');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush([]);
  });

  it('n ajoute pas le token sur les routes auth publiques', () => {
    authService.getAccessToken.mockReturnValue('access-token');

    http.post('/api/auth/login', {email: 'user@local.dev', password: 'Secret123!'}).subscribe();

    const request = httpMock.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('rafraichit la session et rejoue la requete apres un 401', () => {
    authService.getAccessToken.mockReturnValue('expired-token');
    authService.getRefreshToken.mockReturnValue('refresh-token');
    authService.refreshAccessToken.mockReturnValue(of('fresh-token'));

    http.get('/api/projects').subscribe();

    const initialRequest = httpMock.expectOne('/api/projects');
    expect(initialRequest.request.headers.get('Authorization')).toBe('Bearer expired-token');
    initialRequest.flush({message: 'Unauthorized'}, {status: 401, statusText: 'Unauthorized'});

    const retriedRequest = httpMock.expectOne('/api/projects');
    expect(authService.refreshAccessToken).toHaveBeenCalled();
    expect(retriedRequest.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    retriedRequest.flush([]);
  });

  it('vide la session si le refresh echoue', () => {
    authService.getAccessToken.mockReturnValue('expired-token');
    authService.getRefreshToken.mockReturnValue('refresh-token');
    authService.refreshAccessToken.mockReturnValue(
      throwError(() => new Error('refresh failed'))
    );

    const errors: unknown[] = [];

    http.get('/api/projects').subscribe({
      error: (error) => errors.push(error)
    });

    const request = httpMock.expectOne('/api/projects');
    request.flush({message: 'Unauthorized'}, {status: 401, statusText: 'Unauthorized'});

    expect(authService.clearSession).toHaveBeenCalled();
    expect(errors).toHaveLength(1);
  });
});
