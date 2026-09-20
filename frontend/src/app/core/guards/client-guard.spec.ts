import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { clientGuard } from './client-guard';
import { AuthService } from '../services/auth';

describe('clientGuard', () => {
  let navigate: ReturnType<typeof vi.fn>;

  function run(loggedIn: boolean, role: string | null): boolean {
    navigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: { navigate } },
        { provide: AuthService, useValue: { isLoggedIn: () => loggedIn, getRole: () => role } },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      clientGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean;
  }

  it('lets a signed-in client through', () => {
    expect(run(true, 'CLIENT')).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('sends a seller back to the marketplace', () => {
    expect(run(true, 'SELLER')).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/products']);
  });

  it('sends a visitor back to the marketplace', () => {
    expect(run(false, null)).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/products']);
  });

  it('does not trust a role when the session is over', () => {
    expect(run(false, 'CLIENT')).toBe(false);
  });
});
