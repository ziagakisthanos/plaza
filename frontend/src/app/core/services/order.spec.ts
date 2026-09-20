import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ORDER_STATUSES, ORDER_STATUS_LABELS, OrderService } from './order';

describe('OrderService', () => {
  let service: OrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('places an order with the delivery address and payment method', () => {
    const request = { paymentMethod: 'PAY_ON_DELIVERY' as const, deliveryAddress: '12 Main Street' };
    service.checkout(request).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders/checkout'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush([]);
  });

  it('lists my orders without filters', () => {
    service.list({}).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('sends the search text and status, trimming the text', () => {
    service.list({ q: '  mug ', status: 'SHIPPED' }).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(req.request.params.get('q')).toBe('mug');
    expect(req.request.params.get('status')).toBe('SHIPPED');
    req.flush([]);
  });

  it('skips a blank search and an empty status', () => {
    service.list({ q: '   ', status: '' }).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('lists the orders received by a seller with the same filters', () => {
    service.listReceived({ q: 'book', status: 'PENDING' }).subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders/seller'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('book');
    expect(req.request.params.get('status')).toBe('PENDING');
    req.flush([]);
  });

  it('changes the status of an order', () => {
    service.updateStatus('o1', 'CONFIRMED').subscribe();

    const req = http.expectOne((r) => r.url.endsWith('/orders/o1/status'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'CONFIRMED' });
    req.flush({});
  });

  it('cancels, removes and repeats an order', () => {
    service.cancel('o1').subscribe();
    const cancel = http.expectOne((r) => r.url.endsWith('/orders/o1/cancel'));
    expect(cancel.request.method).toBe('PUT');
    cancel.flush({});

    service.remove('o1').subscribe();
    const remove = http.expectOne((r) => r.url.endsWith('/orders/o1'));
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);

    service.redo('o1').subscribe();
    const redo = http.expectOne((r) => r.url.endsWith('/orders/o1/redo'));
    expect(redo.request.method).toBe('POST');
    redo.flush({});
  });

  it('has a readable label for every status', () => {
    for (const status of ORDER_STATUSES) {
      expect(ORDER_STATUS_LABELS[status]).toBeTruthy();
    }
    expect(ORDER_STATUSES).toHaveLength(5);
  });
});
