import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductService } from './product';

describe('ProductService', () => {
  let service: ProductService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches without parameters when no filter is set', () => {
    service.search({}).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/products'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('sends every filter that is set', () => {
    service
      .search({
        q: 'lap',
        category: 'Electronics',
        minPrice: 10,
        maxPrice: 500,
        inStock: true,
        sort: 'price_asc',
      })
      .subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/products'));
    expect(req.request.params.get('q')).toBe('lap');
    expect(req.request.params.get('category')).toBe('Electronics');
    expect(req.request.params.get('minPrice')).toBe('10');
    expect(req.request.params.get('maxPrice')).toBe('500');
    expect(req.request.params.get('inStock')).toBe('true');
    expect(req.request.params.get('sort')).toBe('price_asc');
    req.flush([]);
  });

  it('trims the text and skips blank, empty and false filters', () => {
    service
      .search({ q: '  mug  ', category: '', minPrice: null, maxPrice: null, inStock: false })
      .subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/products'));
    expect(req.request.params.get('q')).toBe('mug');
    expect(req.request.params.keys()).toEqual(['q']);
    req.flush([]);
  });

  it('keeps a minimum price of zero', () => {
    service.search({ minPrice: 0 }).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/products'));
    expect(req.request.params.get('minPrice')).toBe('0');
    req.flush([]);
  });

  it('loads the categories', () => {
    let result: string[] = [];
    service.getCategories().subscribe((categories) => (result = categories));

    const req = http.expectOne((r) => r.url.endsWith('/products/categories'));
    req.flush(['Books', 'Toys']);
    expect(result).toEqual(['Books', 'Toys']);
  });
});
