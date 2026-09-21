import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SESSION_EXPIRED_MESSAGE, errorInterceptor } from './error-interceptor';
import { NotificationService } from '../services/notification';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => controller.verify());

  const signedIn = { Authorization: 'Bearer token' };

  function fail(url: string, status: number, headers: Record<string, string> = {}): { error?: { status: number } } {
    const outcome: { error?: { status: number } } = {};
    http.get(url, { headers }).subscribe({ error: (error) => (outcome.error = error) });
    controller.expectOne(url).flush({ message: 'nope' }, { status, statusText: 'Failed' });
    return outcome;
  }

  it('explains that the session ended when a signed-in request is refused', () => {
    fail('/orders', 401, signedIn);

    expect(notifications.notifications().map((n) => n.text)).toEqual([SESSION_EXPIRED_MESSAGE]);
    expect(notifications.notifications()[0].kind).toBe('error');
  });

  it('still hands the error on to whoever asked', () => {
    const outcome = fail('/orders', 401, signedIn);

    expect(outcome.error?.status).toBe(401);
  });

  it('says it only once when several requests fail together', () => {
    fail('/orders', 401, signedIn);
    fail('/cart', 401, signedIn);
    fail('/users/me', 401, signedIn);

    expect(notifications.notifications()).toHaveLength(1);
  });

  it('stays quiet when signing in fails, because that is not an expired session', () => {
    fail('/auth/login', 401, signedIn);
    fail('/auth/login', 401);

    expect(notifications.notifications()).toEqual([]);
  });

  it('stays quiet for a visitor who was never signed in', () => {
    fail('/cart', 401);

    expect(notifications.notifications()).toEqual([]);
  });

  it.each([400, 403, 404, 409, 413, 500, 503])('leaves a %i answer to the screen that asked', (status) => {
    const outcome = fail('/orders', status, signedIn);

    expect(notifications.notifications()).toEqual([]);
    expect(outcome.error?.status).toBe(status);
  });

  it('does nothing when the request works', () => {
    let body: unknown;
    http.get('/orders', { headers: signedIn }).subscribe((result) => (body = result));
    controller.expectOne('/orders').flush([{ id: 'o1' }]);

    expect(body).toEqual([{ id: 'o1' }]);
    expect(notifications.notifications()).toEqual([]);
  });
});
