import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { CartPage } from './cart';
import { Cart, CartService } from '../../../core/services/cart';
import { OrderService } from '../../../core/services/order';
import { Component } from '@angular/core';

const book = { productId: 'p1', name: 'Book', price: 12.5, quantity: 2, availableStock: 10, lineTotal: 25 };
const pen = { productId: 'p2', name: 'Pen', price: 0.1, quantity: 1, availableStock: 1, lineTotal: 0.1 };

function cartOf(...items: (typeof book)[]): Cart {
  return {
    items,
    itemCount: items.reduce((sum, item) => sum + item.quantity, 0),
    total: items.reduce((sum, item) => sum + item.lineTotal, 0),
  };
}

@Component({ template: '' })
class OrdersStub {}

describe('CartPage', () => {
  let fixture: ComponentFixture<CartPage>;
  let element: HTMLElement;
  let state: ReturnType<typeof signal<Cart>>;
  let service: {
    cart: ReturnType<typeof signal<Cart>>;
    load: ReturnType<typeof vi.fn>;
    setQuantity: ReturnType<typeof vi.fn>;
    remove: ReturnType<typeof vi.fn>;
    clear: ReturnType<typeof vi.fn>;
  };
  let checkout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function create(cart: Cart, load = of(cart)): Promise<void> {
    state = signal(cart);
    service = {
      cart: state,
      load: vi.fn().mockReturnValue(load),
      setQuantity: vi.fn().mockReturnValue(of(cart)),
      remove: vi.fn().mockReturnValue(of(cart)),
      clear: vi.fn().mockReturnValue(of(undefined)),
    };
    checkout = vi.fn().mockReturnValue(of([{ id: 'o1' }]));
    await TestBed.configureTestingModule({
      imports: [CartPage],
      providers: [
        provideRouter([{ path: 'orders', component: OrdersStub }]),
        { provide: CartService, useValue: service },
        { provide: OrderService, useValue: { checkout } },
      ],
    }).compileComponents();

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CartPage);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function emptyCartButton(): HTMLButtonElement {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('.summary button')).find((b) =>
      b.textContent?.includes('Empty cart'),
    ) as HTMLButtonElement;
  }

  function button(label: string, index = 0): HTMLButtonElement {
    return element.querySelectorAll<HTMLButtonElement>(`button[aria-label="${label}"]`)[index];
  }

  it('reloads the cart from the server when it opens', async () => {
    await create(cartOf(book));

    expect(service.load).toHaveBeenCalledTimes(1);
  });

  it('shows every line with its price and quantity, and the total', async () => {
    await create(cartOf(book, pen));

    const names = Array.from(element.querySelectorAll('.line-name')).map((n) => n.textContent?.trim());
    expect(names).toEqual(['Book', 'Pen']);
    expect(element.querySelectorAll('.quantity')[0].textContent).toContain('2');
    expect(element.querySelectorAll('.line-total')[0].textContent).toContain('$25.00');
    expect(element.querySelector('.summary-row.total')?.textContent).toContain('$25.10');
    expect(element.querySelectorAll('.summary-row')[0].textContent).toContain('3');
  });

  it('shows a friendly empty state with a way back to the marketplace', async () => {
    await create(cartOf());

    expect(element.querySelector('.empty-state h3')?.textContent).toContain('Your cart is empty');
    expect(element.querySelector('.empty-state a')?.getAttribute('href')).toBe('/products');
    expect(element.querySelector('.cart-lines')).toBeNull();
  });

  it('shows a loading placeholder until the cart arrives', async () => {
    const pending = new Subject<Cart>();
    state = signal(cartOf());
    service = {
      cart: state,
      load: vi.fn().mockReturnValue(pending),
      setQuantity: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CartPage],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: service },
        { provide: OrderService, useValue: { checkout: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CartPage);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();

    expect(element.querySelector('.cart-skeleton')).not.toBeNull();
    expect(element.querySelector('.empty-state')).toBeNull();

    pending.next(cartOf(book));
    fixture.detectChanges();

    expect(element.querySelector('.cart-skeleton')).toBeNull();
  });

  it('shows the reason when the cart cannot be loaded', async () => {
    await create(cartOf(), throwError(() => ({ error: { message: 'Products are temporarily unavailable' } })));

    expect(element.querySelector('.alert-error')?.textContent).toContain('temporarily unavailable');
  });

  it('increases and decreases the quantity by one', async () => {
    await create(cartOf(book));

    button('Increase quantity').click();
    expect(service.setQuantity).toHaveBeenCalledWith('p1', 3);

    button('Decrease quantity').click();
    expect(service.setQuantity).toHaveBeenCalledWith('p1', 1);
  });

  it('does not let the quantity go below one', async () => {
    await create(cartOf({ ...book, quantity: 1 }));

    expect(button('Decrease quantity').disabled).toBe(true);
  });

  it('does not let the quantity go above the stock', async () => {
    await create(cartOf({ ...book, quantity: 10 }));

    expect(button('Increase quantity').disabled).toBe(true);
  });

  it('warns when the stock dropped below the quantity in the cart', async () => {
    await create(cartOf({ ...book, quantity: 5, availableStock: 2 }));

    expect(element.querySelector('.badge-warning')?.textContent).toContain('Only 2 in stock');
    expect(button('Increase quantity').disabled).toBe(true);
    expect(button('Decrease quantity').disabled).toBe(false);
  });

  it('removes a line', async () => {
    await create(cartOf(book, pen));

    button('Remove Pen').click();

    expect(service.remove).toHaveBeenCalledWith('p2');
  });

  it('empties the cart', async () => {
    await create(cartOf(book));

    emptyCartButton().click();

    expect(service.clear).toHaveBeenCalledTimes(1);
  });

  it('shows the reason when a change is refused, and lets the client carry on', async () => {
    await create(cartOf(book));
    service.setQuantity.mockReturnValue(
      throwError(() => ({ error: { message: "Not enough stock for 'Book': only 2 left" } })),
    );

    button('Increase quantity').click();
    fixture.detectChanges();

    expect(element.querySelector('.alert-error')?.textContent).toContain('only 2 left');
    expect(button('Increase quantity').disabled).toBe(false);
  });

  it('falls back to a friendly message when a removal fails without a reason', async () => {
    await create(cartOf(book));
    service.remove.mockReturnValue(throwError(() => ({ status: 500 })));

    button('Remove Book').click();
    fixture.detectChanges();

    expect(element.querySelector('.alert-error')?.textContent).toContain('We could not remove the item.');
  });

  it('blocks every control while a change is in flight, so requests cannot pile up', async () => {
    await create(cartOf(book, pen));
    const inFlight = new Subject<Cart>();
    service.setQuantity.mockReturnValue(inFlight);

    button('Increase quantity').click();
    fixture.detectChanges();

    expect(button('Increase quantity').disabled).toBe(true);
    expect(button('Remove Book').disabled).toBe(true);
    expect(button('Remove Pen').disabled).toBe(true);
    expect(emptyCartButton().disabled).toBe(true);

    inFlight.next(cartOf(book));
    inFlight.complete();
    fixture.detectChanges();

    expect(button('Remove Book').disabled).toBe(false);
  });

  it('clears an old error when the next change starts', async () => {
    await create(cartOf(book));
    service.remove.mockReturnValueOnce(throwError(() => ({ status: 500 })));

    button('Remove Book').click();
    fixture.detectChanges();
    expect(element.querySelector('.alert-error')).not.toBeNull();

    button('Increase quantity').click();
    fixture.detectChanges();

    expect(element.querySelector('.alert-error')).toBeNull();
  });

  describe('checkout', () => {
    function fillAddress(value: string): void {
      const field = element.querySelector('#delivery-address') as HTMLTextAreaElement;
      field.value = value;
      field.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function submit(): void {
      (element.querySelector('.checkout') as HTMLFormElement).dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    }

    it('explains that the payment happens on delivery', async () => {
      await create(cartOf(book));

      expect(element.querySelector('.payment-note')?.textContent).toContain('Pay on delivery');
    });

    it('asks for a delivery address instead of ordering without one', async () => {
      await create(cartOf(book));

      submit();

      expect(checkout).not.toHaveBeenCalled();
      expect(element.querySelector('.checkout .field-error')?.textContent).toContain('delivered');
      expect(element.querySelector('#delivery-address')?.classList).toContain('is-invalid');
    });

    it('does not accept an address made only of spaces', async () => {
      await create(cartOf(book));
      fillAddress('   ');

      submit();

      expect(checkout).not.toHaveBeenCalled();
      expect(element.querySelector('.checkout .field-error')?.textContent).toContain('delivered');
    });

    it('places the order, refreshes the cart and shows the orders', async () => {
      await create(cartOf(book));
      fillAddress('  12 Main Street, Athens  ');

      submit();

      expect(checkout).toHaveBeenCalledWith({
        paymentMethod: 'PAY_ON_DELIVERY',
        deliveryAddress: '12 Main Street, Athens',
      });
      expect(service.load).toHaveBeenCalledTimes(2);
      expect(navigate).toHaveBeenCalledWith(['/orders'], { state: { placed: 1 } });
    });

    it('tells the orders page how many orders were created', async () => {
      await create(cartOf(book, pen));
      checkout.mockReturnValue(of([{ id: 'o1' }, { id: 'o2' }]));
      fillAddress('12 Main Street');

      submit();

      expect(navigate).toHaveBeenCalledWith(['/orders'], { state: { placed: 2 } });
    });

    it('shows why the order was refused, keeps the client here and refreshes the stock', async () => {
      await create(cartOf(book));
      checkout.mockReturnValue(
        throwError(() => ({ error: { message: "Not enough stock for 'Book': only 1 left" } })),
      );
      fillAddress('12 Main Street');

      submit();

      expect(element.querySelector('.alert-error')?.textContent).toContain('only 1 left');
      expect(navigate).not.toHaveBeenCalled();
      expect(service.load).toHaveBeenCalledTimes(2);
      expect((element.querySelector('.checkout button[type="submit"]') as HTMLButtonElement).disabled).toBe(false);
    });

    it('falls back to a friendly message when the server gives no reason', async () => {
      await create(cartOf(book));
      checkout.mockReturnValue(throwError(() => ({ status: 500 })));
      fillAddress('12 Main Street');

      submit();

      expect(element.querySelector('.alert-error')?.textContent).toContain('We could not place your order');
    });

    it('does not order while a quantity is above the stock', async () => {
      await create(cartOf({ ...book, quantity: 5, availableStock: 2 }));

      const button = element.querySelector('.checkout button[type="submit"]') as HTMLButtonElement;

      expect(button.disabled).toBe(true);
      expect(element.querySelector('.checkout .field-error')?.textContent).toContain('lower the highlighted');
    });

    it('blocks the button while the order is being placed, so it cannot be sent twice', async () => {
      await create(cartOf(book));
      const inFlight = new Subject<unknown[]>();
      checkout.mockReturnValue(inFlight);
      fillAddress('12 Main Street');

      submit();

      expect((element.querySelector('.checkout button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
      inFlight.next([{ id: 'o1' }]);
      inFlight.complete();
    });
  });
});
