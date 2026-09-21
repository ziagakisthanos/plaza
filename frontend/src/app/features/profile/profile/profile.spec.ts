import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NO_CONNECTION_MESSAGE } from '../../../core/utils/api-error';
import { Profile } from './profile';
import { UserService } from '../../../core/services/user';
import { StatsService } from '../../../core/services/stats';

describe('Profile', () => {
  async function open(role: string): Promise<HTMLElement> {
    const stats = {
      getClientStats: vi.fn().mockReturnValue(of({ totalSpent: 10, orderCount: 1, mostBought: [], bestProducts: [] })),
      getSellerStats: vi.fn().mockReturnValue(of({ totalEarned: 20, orderCount: 2, bestSelling: [] })),
    };
    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideRouter([]),
        {
          provide: UserService,
          useValue: { getMe: () => of({ id: 'u1', name: 'Carl Client', email: 'carl@x.io', role, avatar: '2' }) },
        },
        { provide: StatsService, useValue: stats },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the identity of the user together with their shopping numbers when they are a buyer', async () => {
    const element = await open('CLIENT');

    expect(element.querySelector('.identity h2')?.textContent).toContain('Carl Client');
    expect(element.querySelector('app-profile-stats .block-title')?.textContent).toContain('Your shopping');
  });

  it('shows the sales numbers when the user is a seller', async () => {
    const element = await open('SELLER');

    expect(element.querySelector('app-profile-stats .block-title')?.textContent).toContain('Your sales');
    expect(element.querySelector('.tile-value')?.textContent).toContain('$20.00');
  });

  describe('when something goes wrong', () => {
    const stats = {
      getClientStats: () => of({ totalSpent: 0, orderCount: 0, mostBought: [], bestProducts: [] }),
      getSellerStats: () => of({ totalEarned: 0, orderCount: 0, bestSelling: [] }),
    };

    async function create(user: object) {
      await TestBed.configureTestingModule({
        imports: [Profile],
        providers: [
          provideRouter([]),
          { provide: UserService, useValue: user },
          { provide: StatsService, useValue: stats },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(Profile);
      fixture.detectChanges();
      await fixture.whenStable();
      return fixture.componentInstance;
    }

    const me = { id: 'u1', name: 'Carl', email: 'carl@x.io', role: 'CLIENT', avatar: '2' };

    it('shows the reason when the name is refused', async () => {
      const profile = await create({
        getMe: () => of(me),
        updateProfile: () => throwError(() => ({ status: 400, error: { message: 'Validation failed', fieldErrors: { name: 'Name may only contain letters and numbers' } } })),
      });

      profile.onSave();

      expect(profile.error()).toBe('Name may only contain letters and numbers');
      expect(profile.saving()).toBe(false);
    });

    it('tells the user when saving is impossible because the server is unreachable', async () => {
      const profile = await create({ getMe: () => of(me), updateProfile: () => throwError(() => ({ status: 0 })) });

      profile.onSave();

      expect(profile.error()).toBe(NO_CONNECTION_MESSAGE);
    });

    it('falls back to a plain message when saving fails without a reason', async () => {
      const profile = await create({ getMe: () => of(me), updateProfile: () => throwError(() => ({ status: 500 })) });

      profile.onSave();

      expect(profile.error()).toBe('Failed to update your profile');
    });

    it('shows a message when the profile cannot be loaded', async () => {
      const profile = await create({ getMe: () => throwError(() => ({ status: 503, error: { message: 'Users are temporarily unavailable' } })) });

      expect(profile.error()).toBe('Users are temporarily unavailable');
      expect(profile.loading()).toBe(false);
    });

    it('does not save a blank name', async () => {
      const updateProfile = vi.fn();
      const profile = await create({ getMe: () => of(me), updateProfile });
      profile.form.patchValue({ name: '' });

      profile.onSave();

      expect(updateProfile).not.toHaveBeenCalled();
    });
  });
});
