import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {guestGuard} from './guest.guard';
import {AuthService} from '../services/auth.service';

describe('guestGuard', () => {
  const createUrlTree = vi.fn();

  beforeEach(() => {
    createUrlTree.mockReset();
    createUrlTree.mockReturnValue({redirect: '/projects'});

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

  it('laisse passer un visiteur non connecte', () => {
    const authService = TestBed.inject(AuthService) as unknown as {hasAccessToken: ReturnType<typeof vi.fn>};
    authService.hasAccessToken.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirige vers projects si deja connecte', () => {
    const authService = TestBed.inject(AuthService) as unknown as {hasAccessToken: ReturnType<typeof vi.fn>};
    authService.hasAccessToken.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));

    expect(createUrlTree).toHaveBeenCalledWith(['/projects']);
    expect(result).toEqual({redirect: '/projects'});
  });
});
