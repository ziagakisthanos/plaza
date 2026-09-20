import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderAction, OrderCard } from './order-card';
import { Order } from '../../../core/services/order';

const order: Order = {
  id: '6ab01b4d3795455fd707fa5c',
  buyerId: 'client-1',
  sellerId: 'seller-1',
  items: [
    { productId: 'p1', name: 'Field Guide', price: 12.5, quantity: 2, lineTotal: 25 },
    { productId: 'p2', name: 'Pocket Pen', price: 0.1, quantity: 3, lineTotal: 0.3 },
  ],
  total: 25.3,
  status: 'SHIPPED',
  paymentMethod: 'PAY_ON_DELIVERY',
  deliveryAddress: '12 Main Street\nAthens',
  createdAt: '2026-03-01T10:00:00Z',
  updatedAt: '2026-03-01T10:00:00Z',
};

const cancel: OrderAction = { key: 'cancel', label: 'Cancel order', icon: 'block', tone: 'danger', confirm: true };
const redo: OrderAction = { key: 'redo', label: 'Order again', icon: 'replay', tone: 'primary' };

@Component({
  imports: [OrderCard],
  template: `<app-order-card
    [order]="order()"
    [actions]="actions()"
    [busy]="busy()"
    [confirmingKey]="confirmingKey()"
    (act)="acted.push($event)"
    (dismiss)="dismissed = dismissed + 1"
  />`,
})
class Host {
  order = signal(order);
  actions = signal<OrderAction[]>([]);
  busy = signal(false);
  confirmingKey = signal<string | null>(null);
  acted: string[] = [];
  dismissed = 0;
}

describe('OrderCard', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('shows a short order number and the status', () => {
    expect(element.querySelector('.order-title')?.textContent).toContain('Order #07FA5C');
    expect(element.querySelector('.status')?.textContent).toContain('Shipped');
    expect(element.querySelector('.status')?.classList).toContain('status-shipped');
  });

  it('lists the items with quantity, price and line total', () => {
    const rows = Array.from(element.querySelectorAll('.order-items li'));

    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Field Guide');
    expect(rows[0].textContent).toContain('2 × $12.50');
    expect(rows[0].textContent).toContain('$25.00');
    expect(rows[1].textContent).toContain('$0.30');
  });

  it('shows the delivery address, the payment and the total', () => {
    const text = element.querySelector('.order-meta')?.textContent ?? '';

    expect(text).toContain('12 Main Street');
    expect(text).toContain('Pay on delivery');
    expect(text).toContain('$25.30');
  });

  it('colours every status in its own way', () => {
    for (const status of ['PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED'] as const) {
      host.order.set({ ...order, status });
      fixture.detectChanges();

      expect(element.querySelector('.status')?.classList).toContain(`status-${status.toLowerCase()}`);
    }
  });

  it('has no action bar when there is nothing to do', () => {
    expect(element.querySelector('.order-actions')).toBeNull();
  });

  it('emits the key of the action that was clicked', () => {
    host.actions.set([redo, cancel]);
    fixture.detectChanges();

    const buttons = element.querySelectorAll<HTMLButtonElement>('.order-actions button');
    buttons[0].click();
    buttons[1].click();

    expect(host.acted).toEqual(['redo', 'cancel']);
    expect(buttons[0].textContent).toContain('Order again');
  });

  it('asks for confirmation before a risky action, and lets the user back out', () => {
    host.actions.set([cancel]);
    host.confirmingKey.set('cancel');
    fixture.detectChanges();

    expect(element.querySelector('.confirm-text')?.textContent).toContain('Are you sure?');
    const [yes, keep] = Array.from(element.querySelectorAll<HTMLButtonElement>('.order-actions button'));
    expect(yes.textContent).toContain('Yes, cancel order');

    keep.click();
    yes.click();

    expect(host.dismissed).toBe(1);
    expect(host.acted).toEqual(['cancel']);
  });

  it('disables the actions while something is in progress', () => {
    host.actions.set([redo, cancel]);
    host.busy.set(true);
    fixture.detectChanges();

    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('.order-actions button'));

    expect(buttons.every((button) => button.disabled)).toBe(true);
  });
});
