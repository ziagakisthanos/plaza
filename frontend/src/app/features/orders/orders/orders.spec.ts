import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { OrdersPage } from './orders';
import { Order, OrderService, OrderStatus } from '../../../core/services/order';

function orderOf(id: string, status: OrderStatus, name = 'Field Guide'): Order {
  return {
    id,
    buyerId: 'client-1',
    sellerId: 'seller-1',
    items: [{ productId: 'p1', name, price: 10, quantity: 1, lineTotal: 10 }],
    total: 10,
    status,
    paymentMethod: 'PAY_ON_DELIVERY',
    deliveryAddress: '12 Main Street',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
  };
}

describe('OrdersPage', () => {
  let element: HTMLElement;
  let harness: RouterTestingHarness;
  let service: Record<'list' | 'listReceived' | 'updateStatus' | 'cancel' | 'remove' | 'redo', ReturnType<typeof vi.fn>>;

  async function open(
    mode: 'client' | 'seller',
    orders: Order[] = [],
    state?: Record<string, unknown>,
  ): Promise<void> {
    vi.useFakeTimers();
    service = {
      list: vi.fn().mockReturnValue(of(orders)),
      listReceived: vi.fn().mockReturnValue(of(orders)),
      updateStatus: vi.fn().mockReturnValue(of(orders[0])),
      cancel: vi.fn().mockReturnValue(of(orders[0])),
      remove: vi.fn().mockReturnValue(of(undefined)),
      redo: vi.fn().mockReturnValue(of(orders[0])),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'orders', component: OrdersPage, data: { mode: 'client' } },
          { path: 'seller/orders', component: OrdersPage, data: { mode: 'seller' } },
        ]),
        { provide: OrderService, useValue: service },
      ],
    });
    harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigate([mode === 'seller' ? '/seller/orders' : '/orders'], { state });
    element = harness.routeNativeElement as HTMLElement;
    await settle();
  }

  async function settle(): Promise<void> {
    harness.detectChanges();
    await vi.advanceTimersByTimeAsync(300);
    harness.detectChanges();
  }

  function labels(): string[] {
    return Array.from(element.querySelectorAll('.order-actions .btn')).map((b) => b.textContent?.trim() ?? '');
  }

  function click(label: string): void {
    const button = Array.from(element.querySelectorAll<HTMLButtonElement>('.order-actions button')).find((b) =>
      b.textContent?.includes(label),
    );
    button?.click();
    harness.detectChanges();
  }

  function lastFilters() {
    const calls = service.list.mock.calls;
    return calls[calls.length - 1][0];
  }

  afterEach(() => vi.useRealTimers());

  describe('as a buyer', () => {
    it('loads and shows my orders, newest first as sent by the server', async () => {
      await open('client', [orderOf('o2', 'PENDING'), orderOf('o1', 'DELIVERED')]);

      expect(service.list).toHaveBeenCalledTimes(1);
      expect(service.listReceived).not.toHaveBeenCalled();
      expect(element.querySelector('h1')?.textContent).toContain('Your orders');
      expect(element.querySelectorAll('app-order-card')).toHaveLength(2);
      expect(element.querySelector('.result-count')?.textContent).toContain('2 orders');
    });

    it('shows an empty state with a way to start shopping', async () => {
      await open('client', []);

      expect(element.querySelector('.empty-state h3')?.textContent).toContain('No orders yet');
      expect(element.querySelector('.empty-state a')?.getAttribute('href')).toBe('/products');
    });

    it('welcomes a new order after checkout', async () => {
      await open('client', [orderOf('o1', 'PENDING')], { placed: 1 });

      expect(element.querySelector('.alert-success')?.textContent).toContain('Your order was placed');
    });

    it('explains when checkout created one order per seller', async () => {
      await open('client', [orderOf('o1', 'PENDING')], { placed: 2 });

      expect(element.querySelector('.alert-success')?.textContent).toContain('2 orders, one for each seller');
    });

    it('shows no welcome when the page is opened normally', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);

      expect(element.querySelector('.alert-success')).toBeNull();
    });

    it('offers the right actions for every status', async () => {
      await open('client', [
        orderOf('o1', 'PENDING'),
        orderOf('o2', 'CONFIRMED'),
        orderOf('o3', 'SHIPPED'),
        orderOf('o4', 'DELIVERED'),
        orderOf('o5', 'CANCELLED'),
      ]);

      const perCard = Array.from(element.querySelectorAll('app-order-card')).map((card) =>
        Array.from(card.querySelectorAll('.order-actions .btn')).map((b) => b.textContent?.trim()),
      );
      expect(perCard).toEqual([
        ['blockCancel order'],
        ['blockCancel order'],
        [],
        ['replayOrder again'],
        ['replayOrder again', 'delete_outlineRemove order'],
      ]);
    });

    it('asks before cancelling, and only then cancels', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);

      click('Cancel order');
      expect(service.cancel).not.toHaveBeenCalled();
      expect(element.querySelector('.confirm-text')?.textContent).toContain('Are you sure?');

      click('Yes, cancel order');
      await settle();

      expect(service.cancel).toHaveBeenCalledWith('o1');
      expect(element.querySelector('.alert-success')?.textContent).toContain('The order was cancelled');
      expect(service.list.mock.calls.length).toBeGreaterThan(1);
    });

    it('lets the buyer change their mind about cancelling', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);

      click('Cancel order');
      click('Keep it');

      expect(service.cancel).not.toHaveBeenCalled();
      expect(element.querySelector('.confirm-text')).toBeNull();
    });

    it('asks before removing a cancelled order, and then removes it', async () => {
      await open('client', [orderOf('o1', 'CANCELLED')]);

      click('Remove order');
      expect(service.remove).not.toHaveBeenCalled();
      click('Yes, remove order');
      await settle();

      expect(service.remove).toHaveBeenCalledWith('o1');
      expect(element.querySelector('.alert-success')?.textContent).toContain('The order was removed');
    });

    it('orders the same items again without asking', async () => {
      await open('client', [orderOf('o1', 'DELIVERED')]);

      click('Order again');
      await settle();

      expect(service.redo).toHaveBeenCalledWith('o1');
      expect(element.querySelector('.alert-success')?.textContent).toContain('A new order was placed');
    });

    it('shows the reason when the shop refuses, and reloads the list', async () => {
      await open('client', [orderOf('o1', 'DELIVERED')]);
      service.redo.mockReturnValue(
        throwError(() => ({ error: { message: "Not enough stock for 'Field Guide': only 0 left" } })),
      );
      const before = service.list.mock.calls.length;

      click('Order again');
      await settle();

      expect(element.querySelector('.alert-error')?.textContent).toContain('only 0 left');
      expect(service.list.mock.calls.length).toBeGreaterThan(before);
    });

    it('falls back to a friendly message when the server gives no reason', async () => {
      await open('client', [orderOf('o1', 'DELIVERED')]);
      service.redo.mockReturnValue(throwError(() => ({ status: 500 })));

      click('Order again');
      await settle();

      expect(element.querySelector('.alert-error')?.textContent).toContain('We could not complete that action');
    });

    it('blocks the actions while a request is running', async () => {
      await open('client', [orderOf('o1', 'DELIVERED')]);
      const inFlight = new Subject<unknown>();
      service.redo.mockReturnValue(inFlight);

      click('Order again');

      const button = element.querySelector('.order-actions button') as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      inFlight.next({});
      inFlight.complete();
    });

    it('hides the message after a few seconds', async () => {
      await open('client', [orderOf('o1', 'DELIVERED')]);

      click('Order again');
      await settle();
      expect(element.querySelector('.alert-success')).not.toBeNull();

      await vi.advanceTimersByTimeAsync(5000);
      harness.detectChanges();

      expect(element.querySelector('.alert-success')).toBeNull();
    });

    it('waits for the typing to pause before searching', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);
      const callsBefore = service.list.mock.calls.length;
      const input = element.querySelector('input[type="search"]') as HTMLInputElement;

      input.value = 'mu';
      input.dispatchEvent(new Event('input'));
      input.value = 'mug';
      input.dispatchEvent(new Event('input'));
      harness.detectChanges();
      await vi.advanceTimersByTimeAsync(200);
      expect(service.list.mock.calls.length).toBe(callsBefore);

      await vi.advanceTimersByTimeAsync(150);
      expect(service.list.mock.calls.length).toBe(callsBefore + 1);
      expect(lastFilters()).toEqual({ q: 'mug', status: '' });
    });

    it('filters by status', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);

      const select = element.querySelector('.filter select') as HTMLSelectElement;
      select.value = 'SHIPPED';
      select.dispatchEvent(new Event('change'));
      await settle();

      expect(lastFilters()).toEqual({ q: '', status: 'SHIPPED' });
    });

    it('lists every status in the filter', async () => {
      await open('client', []);

      const options = Array.from(element.querySelectorAll('.filter option')).map((o) => o.textContent?.trim());

      expect(options).toEqual(['All statuses', 'Pending', 'Confirmed', 'Shipped', 'Delivered', 'Cancelled']);
    });

    it('says when nothing matches, and clears the filters', async () => {
      await open('client', [orderOf('o1', 'PENDING')]);
      service.list.mockReturnValue(of([]));
      const input = element.querySelector('input[type="search"]') as HTMLInputElement;
      input.value = 'unicorn';
      input.dispatchEvent(new Event('input'));
      await settle();

      expect(element.querySelector('.empty-state h3')?.textContent).toContain('No matches');

      (element.querySelector('.empty-state button') as HTMLButtonElement).click();
      await settle();

      expect(lastFilters()).toEqual({ q: '', status: '' });
    });

    it('shows a friendly error when the orders cannot be loaded, and recovers', async () => {
      await open('client', []);
      service.list.mockReturnValue(throwError(() => ({ error: { message: 'Orders are down' } })));
      const input = element.querySelector('input[type="search"]') as HTMLInputElement;
      input.value = 'a';
      input.dispatchEvent(new Event('input'));
      await settle();

      expect(element.querySelector('.empty-state')?.textContent).toContain('Orders are down');

      service.list.mockReturnValue(of([orderOf('o1', 'PENDING')]));
      input.value = 'ab';
      input.dispatchEvent(new Event('input'));
      await settle();

      expect(element.querySelectorAll('app-order-card')).toHaveLength(1);
    });
  });

  describe('as a seller', () => {
    it('loads the orders received, not the ones placed', async () => {
      await open('seller', [orderOf('o1', 'PENDING')]);

      expect(service.listReceived).toHaveBeenCalledTimes(1);
      expect(service.list).not.toHaveBeenCalled();
      expect(element.querySelector('h1')?.textContent).toContain('Orders for your products');
    });

    it('explains the empty state to a seller', async () => {
      await open('seller', []);

      expect(element.querySelector('.empty-state p')?.textContent).toContain('When a buyer orders');
      expect(element.querySelector('.empty-state a')).toBeNull();
    });

    it('offers the next step and cancelling, according to the status', async () => {
      await open('seller', [
        orderOf('o1', 'PENDING'),
        orderOf('o2', 'CONFIRMED'),
        orderOf('o3', 'SHIPPED'),
        orderOf('o4', 'DELIVERED'),
        orderOf('o5', 'CANCELLED'),
      ]);

      const perCard = Array.from(element.querySelectorAll('app-order-card')).map((card) =>
        Array.from(card.querySelectorAll('.order-actions .btn')).map((b) => b.textContent?.trim()),
      );
      expect(perCard).toEqual([
        ['arrow_forwardConfirm order', 'blockCancel order'],
        ['arrow_forwardMark as shipped', 'blockCancel order'],
        ['arrow_forwardMark as delivered'],
        [],
        [],
      ]);
    });

    it('moves the order to the next status', async () => {
      await open('seller', [orderOf('o1', 'PENDING'), orderOf('o2', 'SHIPPED')]);

      click('Confirm order');
      await settle();
      expect(service.updateStatus).toHaveBeenCalledWith('o1', 'CONFIRMED');
      expect(element.querySelector('.alert-success')?.textContent).toContain('The order was updated');

      click('Mark as delivered');
      await settle();
      expect(service.updateStatus).toHaveBeenCalledWith('o2', 'DELIVERED');
    });

    it('asks before cancelling an order it received', async () => {
      await open('seller', [orderOf('o1', 'CONFIRMED')]);

      click('Cancel order');
      expect(service.cancel).not.toHaveBeenCalled();
      click('Yes, cancel order');
      await settle();

      expect(service.cancel).toHaveBeenCalledWith('o1');
    });

    it('shows the reason when the change is refused', async () => {
      await open('seller', [orderOf('o1', 'PENDING')]);
      service.updateStatus.mockReturnValue(
        throwError(() => ({ error: { message: 'An order that is CANCELLED cannot be changed to CONFIRMED' } })),
      );

      click('Confirm order');
      await settle();

      expect(element.querySelector('.alert-error')?.textContent).toContain('cannot be changed');
    });

    it('searches and filters the orders received', async () => {
      await open('seller', [orderOf('o1', 'PENDING')]);
      const input = element.querySelector('input[type="search"]') as HTMLInputElement;
      input.value = 'guide';
      input.dispatchEvent(new Event('input'));
      await settle();

      const calls = service.listReceived.mock.calls;
      expect(calls[calls.length - 1][0]).toEqual({ q: 'guide', status: '' });
    });
  });
});
