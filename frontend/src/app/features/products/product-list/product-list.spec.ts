import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ProductList } from './product-list';
import { Product, ProductService } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { AuthService } from '../../../core/services/auth';
import { CartService } from '../../../core/services/cart';

const laptop: Product = {
  id: 'p1',
  name: 'Gaming Laptop',
  description: 'fast',
  price: 1200,
  quantity: 3,
  userId: 's1',
  category: 'Electronics',
  createdAt: '2026-02-02T08:00:00Z',
};

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  let element: HTMLElement;
  let search: ReturnType<typeof vi.fn>;
  let cartAdd: ReturnType<typeof vi.fn>;
  let inCart: ReturnType<typeof signal<Record<string, number>>>;

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(300);
    fixture.detectChanges();
  }

  function type(selector: string, value: string): void {
    const input = element.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function choose(selector: string, value: string): void {
    const select = element.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  function lastFilters() {
    return search.mock.calls[search.mock.calls.length - 1][0];
  }

  async function create(
    products: Product[] = [laptop],
    session: { loggedIn: boolean; role: string | null } = { loggedIn: true, role: 'CLIENT' },
  ): Promise<void> {
    vi.useFakeTimers();
    search = vi.fn().mockReturnValue(of(products));
    cartAdd = vi.fn().mockReturnValue(of({ items: [], itemCount: 1, total: 0 }));
    inCart = signal({});

    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [
        provideRouter([]),
        {
          provide: ProductService,
          useValue: { search, getCategories: () => of(['Books', 'Electronics']) },
        },
        {
          provide: MediaService,
          useValue: { getImagesForProduct: () => of([]), imageUrl: () => '' },
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: () => session.loggedIn, getRole: () => session.role },
        },
        {
          provide: CartService,
          useValue: { add: cartAdd, quantityOf: (id: string) => inCart()[id] ?? 0 },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    element = fixture.nativeElement as HTMLElement;
    await settle();
  }

  afterEach(() => vi.useRealTimers());

  it('loads and shows the products on start', async () => {
    await create();

    expect(search).toHaveBeenCalledTimes(1);
    expect(element.querySelectorAll('.product-card').length).toBe(1);
    expect(element.querySelector('.product-name')?.textContent).toContain('Gaming Laptop');
    expect(element.querySelector('.product-category')?.textContent).toContain('Electronics');
  });

  it('lists the categories in the filter', async () => {
    await create();

    const options = Array.from(element.querySelectorAll('.filter option')).map((o) => o.textContent);
    expect(options.map((o) => o?.trim())).toEqual(['All categories', 'Books', 'Electronics']);
  });

  it('waits for the typing to pause before searching', async () => {
    await create();

    type('input[type="search"]', 'l');
    type('input[type="search"]', 'la');
    type('input[type="search"]', 'lap');
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(200);
    expect(search).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(150);
    expect(search).toHaveBeenCalledTimes(2);
    expect(lastFilters().q).toBe('lap');
  });

  it('sends category, price range, stock and sort choices', async () => {
    await create();

    choose('.filter select', 'Electronics');
    const [min, max] = Array.from(element.querySelectorAll<HTMLInputElement>('.price-range input'));
    min.value = '10';
    min.dispatchEvent(new Event('input'));
    max.value = '2000';
    max.dispatchEvent(new Event('input'));
    (element.querySelector('.chip') as HTMLButtonElement).click();
    choose('.sort select', 'price_desc');
    await settle();

    expect(lastFilters()).toEqual({
      q: '',
      category: 'Electronics',
      minPrice: 10,
      maxPrice: 2000,
      inStock: true,
      sort: 'price_desc',
    });
  });

  it('does not search while the price range is invalid', async () => {
    await create();
    const callsBefore = search.mock.calls.length;

    const [min, max] = Array.from(element.querySelectorAll<HTMLInputElement>('.price-range input'));
    min.value = '500';
    min.dispatchEvent(new Event('input'));
    max.value = '100';
    max.dispatchEvent(new Event('input'));
    await settle();

    expect(search.mock.calls.length).toBe(callsBefore);
    expect(element.querySelector('.filter-error')?.textContent).toContain('minimum price');
  });

  it('rejects a negative price', async () => {
    await create();
    const callsBefore = search.mock.calls.length;

    type('.price-range input', '-5');
    await settle();

    expect(search.mock.calls.length).toBe(callsBefore);
    expect(element.querySelector('.filter-error')).not.toBeNull();
  });

  it('shows the marketplace empty state when nothing is listed', async () => {
    await create([]);

    expect(element.querySelector('.empty-state h3')?.textContent).toContain('marketplace is empty');
  });

  it('shows a no-match state with a way to clear the filters', async () => {
    await create([]);
    type('input[type="search"]', 'zzz');
    await settle();

    expect(element.querySelector('.empty-state h3')?.textContent).toContain('No matches');

    (element.querySelector('.empty-state button') as HTMLButtonElement).click();
    await settle();

    expect(lastFilters().q).toBe('');
  });

  it('shows an error and recovers on the next search', async () => {
    await create();
    search.mockReturnValueOnce(throwError(() => new Error('down')));

    type('input[type="search"]', 'mug');
    await settle();
    expect(element.querySelector('.empty-state h3')?.textContent).toContain('Something went wrong');

    search.mockReturnValue(of([laptop]));
    type('input[type="search"]', 'lap');
    await settle();
    expect(element.querySelectorAll('.product-card').length).toBe(1);
  });

  describe('add to cart', () => {
    function addButton(): HTMLButtonElement {
      return element.querySelector('.add-to-cart') as HTMLButtonElement;
    }

    it('lets a client add a product and confirms it', async () => {
      await create();

      expect(addButton().textContent).toContain('Add to cart');
      addButton().click();
      await settle();

      expect(cartAdd).toHaveBeenCalledWith('p1');
      expect(element.querySelector('.alert-success')?.textContent).toContain(
        'Gaming Laptop was added to your cart.',
      );
    });

    it('hides the confirmation after a few seconds', async () => {
      await create();

      addButton().click();
      await settle();
      expect(element.querySelector('.alert-success')).not.toBeNull();

      await vi.advanceTimersByTimeAsync(4000);
      fixture.detectChanges();

      expect(element.querySelector('.alert-success')).toBeNull();
    });

    it('shows the reason when the cart refuses the product', async () => {
      await create();
      cartAdd.mockReturnValue(
        throwError(() => ({ error: { message: "Not enough stock for 'Gaming Laptop': only 3 left" } })),
      );

      addButton().click();
      await settle();

      expect(element.querySelector('.alert-error')?.textContent).toContain('only 3 left');
      expect(addButton().disabled).toBe(false);
    });

    it('falls back to a friendly message when the server gives no reason', async () => {
      await create();
      cartAdd.mockReturnValue(throwError(() => ({ status: 500 })));

      addButton().click();
      await settle();

      expect(element.querySelector('.alert-error')?.textContent).toContain(
        'We could not add the item to your cart.',
      );
    });

    it('disables a sold out product', async () => {
      await create([{ ...laptop, quantity: 0 }]);

      expect(addButton().disabled).toBe(true);
      expect(addButton().textContent).toContain('Sold out');
    });

    it('disables the button once the whole stock is in the cart', async () => {
      await create();
      inCart.set({ p1: 3 });
      fixture.detectChanges();

      expect(addButton().disabled).toBe(true);
      expect(addButton().textContent).toContain('All in your cart');
    });

    it('offers no cart to a seller', async () => {
      await create([laptop], { loggedIn: true, role: 'SELLER' });

      expect(element.querySelector('.add-to-cart')).toBeNull();
    });

    it('asks a visitor to sign in', async () => {
      await create([laptop], { loggedIn: false, role: null });

      const link = element.querySelector('a.add-to-cart') as HTMLAnchorElement;
      expect(link.textContent).toContain('Sign in to buy');
      expect(link.getAttribute('href')).toBe('/login');
    });
  });
});
