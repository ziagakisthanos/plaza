import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
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
});
