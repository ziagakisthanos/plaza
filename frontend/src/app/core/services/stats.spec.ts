import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StatsService } from './stats';

describe('StatsService', () => {
  let service: StatsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StatsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the shopping numbers of a buyer', () => {
    let result: unknown;
    service.getClientStats().subscribe((stats) => (result = stats));

    const req = http.expectOne((r) => r.url.endsWith('/orders/stats/client'));
    expect(req.request.method).toBe('GET');
    const body = { totalSpent: 46.5, orderCount: 3, mostBought: [], bestProducts: [] };
    req.flush(body);

    expect(result).toEqual(body);
  });

  it('loads the sales numbers of a seller', () => {
    let result: unknown;
    service.getSellerStats().subscribe((stats) => (result = stats));

    const req = http.expectOne((r) => r.url.endsWith('/orders/stats/seller'));
    expect(req.request.method).toBe('GET');
    const body = { totalEarned: 45.5, orderCount: 2, bestSelling: [] };
    req.flush(body);

    expect(result).toEqual(body);
  });
});
