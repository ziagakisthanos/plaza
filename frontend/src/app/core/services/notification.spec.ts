import { TestBed } from '@angular/core/testing';
import { NOTIFICATION_MS, NotificationService } from './notification';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => vi.useRealTimers());

  it('starts with nothing to show', () => {
    expect(service.notifications()).toEqual([]);
  });

  it('shows a message', () => {
    service.show('error', 'Your session has expired');

    expect(service.notifications()).toEqual([{ id: 1, kind: 'error', text: 'Your session has expired' }]);
  });

  it('keeps several different messages, each with its own id', () => {
    service.show('error', 'first');
    service.show('success', 'second');

    expect(service.notifications().map((n) => [n.id, n.kind, n.text])).toEqual([
      [1, 'error', 'first'],
      [2, 'success', 'second'],
    ]);
  });

  it('does not repeat a message that is already on the screen', () => {
    service.show('error', 'Your session has expired');
    service.show('error', 'Your session has expired');
    service.show('error', 'Your session has expired');

    expect(service.notifications()).toHaveLength(1);
  });

  it('can show the same message again once the first one is gone', () => {
    service.show('error', 'again');
    service.dismiss(1);
    service.show('error', 'again');

    expect(service.notifications().map((n) => n.id)).toEqual([2]);
  });

  it('dismisses one message and keeps the others', () => {
    service.show('error', 'first');
    service.show('error', 'second');

    service.dismiss(1);

    expect(service.notifications().map((n) => n.text)).toEqual(['second']);
  });

  it('ignores a dismissal of something that is not there', () => {
    service.show('error', 'first');

    service.dismiss(99);

    expect(service.notifications()).toHaveLength(1);
  });

  it('removes a message by itself after a few seconds', () => {
    service.show('error', 'temporary');

    vi.advanceTimersByTime(NOTIFICATION_MS - 1);
    expect(service.notifications()).toHaveLength(1);

    vi.advanceTimersByTime(1);
    expect(service.notifications()).toHaveLength(0);
  });
});
