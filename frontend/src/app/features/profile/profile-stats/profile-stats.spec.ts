import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ProfileStats } from './profile-stats';
import { ClientStats, SellerStats, StatsService } from '../../../core/services/stats';

@Component({ imports: [ProfileStats], template: '<app-profile-stats [role]="role" />' })
class Host {
  role = 'CLIENT';
}

const clientStats: ClientStats = {
  totalSpent: 46.5,
  orderCount: 3,
  mostBought: [
    { productId: 'p2', name: 'Pen', quantity: 10, amount: 1 },
    { productId: 'p1', name: 'Book', quantity: 1, amount: 12.5 },
  ],
  bestProducts: [
    { productId: 'p1', name: 'Book', quantity: 3, amount: 37.5 },
    { productId: 'p2', name: 'Pen', quantity: 10, amount: 1 },
  ],
};

const sellerStats: SellerStats = {
  totalEarned: 45.5,
  orderCount: 2,
  bestSelling: [
    { productId: 'p1', name: 'Book', quantity: 3, amount: 37.5 },
    { productId: 'p3', name: 'Mug', quantity: 1, amount: 8 },
  ],
};

describe('ProfileStats', () => {
  let fixture: ComponentFixture<Host>;
  let element: HTMLElement;
  let getClientStats: ReturnType<typeof vi.fn>;
  let getSellerStats: ReturnType<typeof vi.fn>;

  async function show(role: string, client = of(clientStats), seller = of(sellerStats)): Promise<void> {
    getClientStats = vi.fn().mockReturnValue(client);
    getSellerStats = vi.fn().mockReturnValue(seller);
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideRouter([]), { provide: StatsService, useValue: { getClientStats, getSellerStats } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Host);
    fixture.componentInstance.role = role;
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function tiles(): [string, string][] {
    return Array.from(element.querySelectorAll('.tile')).map((tile) => [
      tile.querySelector('.tile-label')?.textContent?.trim() ?? '',
      tile.querySelector('.tile-value')?.textContent?.trim() ?? '',
    ]);
  }

  function rows(list: Element, ...columns: string[]): string[][] {
    return Array.from(list.querySelectorAll('li')).map((li) =>
      columns.map((column) => li.querySelector(`.${column}`)?.textContent?.trim() ?? ''),
    );
  }

  it('shows a buyer how much they spent and how many orders they placed', async () => {
    await show('CLIENT');

    expect(element.querySelector('.block-title')?.textContent).toContain('Your shopping');
    expect(tiles()).toEqual([
      ['Total spent', '$46.50'],
      ['Orders placed', '3'],
    ]);
  });

  it('lists what the buyer bought most and where the money went', async () => {
    await show('CLIENT');

    const [mostBought, bySpend] = Array.from(element.querySelectorAll('.rank'));
    expect(rows(mostBought, 'rank-name', 'rank-detail')).toEqual([
      ['Pen', '10 units'],
      ['Book', '1 unit'],
    ]);
    expect(rows(bySpend, 'rank-name', 'rank-amount')).toEqual([
      ['Book', '$37.50'],
      ['Pen', '$1.00'],
    ]);
  });

  it('asks only for the buyer numbers when the user is a buyer', async () => {
    await show('CLIENT');

    expect(getClientStats).toHaveBeenCalledTimes(1);
    expect(getSellerStats).not.toHaveBeenCalled();
  });

  it('shows a seller what they earned and their best sellers', async () => {
    await show('SELLER');

    expect(element.querySelector('.block-title')?.textContent).toContain('Your sales');
    expect(tiles()).toEqual([
      ['Total earned', '$45.50'],
      ['Orders received', '2'],
    ]);
    expect(rows(element.querySelector('.rank') as Element, 'rank-name', 'rank-detail', 'rank-amount')).toEqual([
      ['Book', '3 units sold', '$37.50'],
      ['Mug', '1 unit sold', '$8.00'],
    ]);
    expect(getSellerStats).toHaveBeenCalledTimes(1);
    expect(getClientStats).not.toHaveBeenCalled();
  });

  it('says so, and points to the marketplace, when a buyer has not ordered yet', async () => {
    await show('CLIENT', of({ totalSpent: 0, orderCount: 0, mostBought: [], bestProducts: [] }));

    expect(tiles()).toEqual([
      ['Total spent', '$0.00'],
      ['Orders placed', '0'],
    ]);
    expect(element.querySelector('.empty')?.textContent).toContain('You have not ordered anything yet');
    expect(element.querySelector('.empty a')?.getAttribute('href')).toBe('/products');
    expect(element.querySelector('.rank')).toBeNull();
  });

  it('says so when nobody has ordered from a seller yet', async () => {
    await show('SELLER', of(clientStats), of({ totalEarned: 0, orderCount: 0, bestSelling: [] }));

    expect(element.querySelector('.empty')?.textContent).toContain('No one has ordered from you yet');
    expect(element.querySelector('.rank')).toBeNull();
  });

  it('reminds that cancelled orders are not counted', async () => {
    await show('CLIENT');

    expect(element.querySelector('.block-hint')?.textContent).toContain('Cancelled orders are not counted');
  });

  it('shows a placeholder while the numbers load', async () => {
    const pending = new Subject<ClientStats>();
    await show('CLIENT', pending as never);

    expect(element.querySelector('.skeleton')).not.toBeNull();
    expect(element.querySelector('.tile')).toBeNull();

    pending.next(clientStats);
    fixture.detectChanges();

    expect(element.querySelector('.skeleton')).toBeNull();
    expect(element.querySelectorAll('.tile')).toHaveLength(2);
  });

  it('shows the reason when the numbers cannot be loaded', async () => {
    await show('CLIENT', throwError(() => ({ error: { message: 'Orders are temporarily unavailable' } })) as never);

    expect(element.querySelector('.alert-error')?.textContent).toContain('Orders are temporarily unavailable');
    expect(element.querySelector('.tile')).toBeNull();
  });

  it('falls back to a friendly message when the server gives no reason', async () => {
    await show('SELLER', of(clientStats), throwError(() => ({ status: 0 })) as never);

    expect(element.querySelector('.alert-error')?.textContent).toContain('We could not load your numbers');
  });
});
