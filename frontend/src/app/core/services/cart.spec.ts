import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Cart, CartService } from './cart';
import { AuthService } from './auth';

const oneBook: Cart = {
  items: [
    { productId: 'p1', name: 'Book', price: 12.5, quantity: 2, availableStock: 10, lineTotal: 25 },
  ],
  itemCount: 2,
  total: 25,
};

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;
  let session: { loggedIn: boolean; role: string | null; userId: string | null };

  beforeEach(() => {
    session = { loggedIn: true, role: 'CLIENT', userId: 'client-1' };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: () => session.loggedIn,
            getRole: () => session.role,
            getUserId: () => session.userId,
          },
        },
      ],
    });
    service = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts empty', () => {
    expect(service.cart().items).toEqual([]);
    expect(service.itemCount()).toBe(0);
  });

  it('loads the cart and exposes the count', () => {
    service.load().subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/cart'));
    expect(req.request.method).toBe('GET');
    req.flush(oneBook);

    expect(service.cart()).toEqual(oneBook);
    expect(service.itemCount()).toBe(2);
    expect(service.quantityOf('p1')).toBe(2);
    expect(service.quantityOf('other')).toBe(0);
  });

  it('adds one unit by default and keeps the returned cart', () => {
    service.add('p1').subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/cart/items'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 'p1', quantity: 1 });
    req.flush(oneBook);

    expect(service.itemCount()).toBe(2);
  });

  it('sets a quantity', () => {
    service.setQuantity('p1', 4).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/cart/items/p1'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ quantity: 4 });
    req.flush({ ...oneBook, itemCount: 4 });

    expect(service.itemCount()).toBe(4);
  });

  it('removes a product', () => {
    service.remove('p1').subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/cart/items/p1'));
    expect(req.request.method).toBe('DELETE');
    req.flush({ items: [], itemCount: 0, total: 0 });

    expect(service.itemCount()).toBe(0);
  });

  it('empties the cart', () => {
    service.load().subscribe();
    http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);

    service.clear().subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/cart'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(service.cart().items).toEqual([]);
    expect(service.itemCount()).toBe(0);
  });

  it('leaves the cart unchanged when a change is refused', () => {
    service.load().subscribe();
    http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);

    let failed = false;
    service.add('p1', 50).subscribe({ error: () => (failed = true) });
    http
      .expectOne((r) => r.url.endsWith('/cart/items'))
      .flush({ message: 'Not enough stock' }, { status: 409, statusText: 'Conflict' });

    expect(failed).toBe(true);
    expect(service.itemCount()).toBe(2);
  });

  describe('ensureLoaded', () => {
    it('loads the cart of a signed-in client once', () => {
      service.ensureLoaded();
      http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);
      service.ensureLoaded();
      service.ensureLoaded();

      expect(service.itemCount()).toBe(2);
    });

    it('does not load anything for a seller', () => {
      session.role = 'SELLER';

      service.ensureLoaded();

      http.expectNone((r) => r.url.endsWith('/cart'));
      expect(service.itemCount()).toBe(0);
    });

    it('does not load anything when nobody is signed in', () => {
      session.loggedIn = false;
      session.userId = null;

      service.ensureLoaded();

      http.expectNone((r) => r.url.endsWith('/cart'));
    });

    it('drops the cart on sign-out', () => {
      service.ensureLoaded();
      http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);

      session.loggedIn = false;
      session.userId = null;
      service.ensureLoaded();

      expect(service.itemCount()).toBe(0);
      expect(service.cart().items).toEqual([]);
    });

    it('never shows the previous user cart to the next user', () => {
      service.ensureLoaded();
      http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);

      session.userId = 'client-2';
      service.ensureLoaded();

      expect(service.itemCount()).toBe(0);
      http.expectOne((r) => r.url.endsWith('/cart')).flush({ items: [], itemCount: 0, total: 0 });
    });

    it('tries again on the next call after a failed load', () => {
      service.ensureLoaded();
      http.expectOne((r) => r.url.endsWith('/cart')).flush({}, { status: 503, statusText: 'Down' });

      service.ensureLoaded();

      http.expectOne((r) => r.url.endsWith('/cart')).flush(oneBook);
      expect(service.itemCount()).toBe(2);
    });
  });
});
