import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {authGuard} from './auth.guard';
import {AuthService} from '../services/auth.service';

describe('authGuard', () => {
  const createUrlTree = vi.fn();

  beforeEach(() => {
    createUrlTree.mockReset();
    createUrlTree.mockReturnValue({redirect: '/login'});

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            hasAccessToken: vi.fn()
          }
        },
        {
          provide: Router,
          useValue: {
            createUrlTree
          }
        }
      ]
    });
  });

  it('autorise l acces si un token existe', () => {
    const authService = TestBed.inject(AuthService) as unknown as {hasAccessToken: ReturnType<typeof vi.fn>};
    authService.hasAccessToken.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirige vers login sans session', () => {
    const authService = TestBed.inject(AuthService) as unknown as {hasAccessToken: ReturnType<typeof vi.fn>};
    authService.hasAccessToken.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    expect(createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toEqual({redirect: '/login'});
  });
});
