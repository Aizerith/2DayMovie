import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {roleGuard} from './role.guard';
import {AuthService} from '../services/auth.service';

describe('roleGuard', () => {
  const createUrlTree = vi.fn();

  beforeEach(() => {
    createUrlTree.mockReset();
    createUrlTree.mockReturnValue({redirect: '/projects'});

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            getCurrentUserRole: vi.fn()
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

  it('autorise une route sans roles declares', () => {
    const result = TestBed.runInInjectionContext(() => roleGuard({data: {}} as never, {} as never));

    expect(result).toBe(true);
  });

  it('autorise le role present dans la liste', () => {
    const authService = TestBed.inject(AuthService) as unknown as {getCurrentUserRole: ReturnType<typeof vi.fn>};
    authService.getCurrentUserRole.mockReturnValue('ADMIN');

    const result = TestBed.runInInjectionContext(() => roleGuard({data: {roles: ['ADMIN']}} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirige si le role n est pas autorise', () => {
    const authService = TestBed.inject(AuthService) as unknown as {getCurrentUserRole: ReturnType<typeof vi.fn>};
    authService.getCurrentUserRole.mockReturnValue('USER');

    const result = TestBed.runInInjectionContext(() => roleGuard({data: {roles: ['ADMIN']}} as never, {} as never));

    expect(createUrlTree).toHaveBeenCalledWith(['/projects']);
    expect(result).toEqual({redirect: '/projects'});
  });
});
