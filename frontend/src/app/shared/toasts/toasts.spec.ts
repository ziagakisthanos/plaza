import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Toasts } from './toasts';
import { NotificationService } from '../../core/services/notification';

describe('Toasts', () => {
  let fixture: ComponentFixture<Toasts>;
  let element: HTMLElement;
  let notifications: NotificationService;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [Toasts] }).compileComponents();
    notifications = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(Toasts);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('shows nothing when there is nothing to say', () => {
    expect(element.querySelectorAll('.toast')).toHaveLength(0);
    expect(element.querySelector('.toasts')?.getAttribute('aria-live')).toBe('polite');
  });

  it('shows an error as an alert', () => {
    notifications.show('error', 'Your session has expired. Please sign in again.');
    fixture.detectChanges();

    const toast = element.querySelector('.toast') as HTMLElement;
    expect(toast.textContent).toContain('Your session has expired');
    expect(toast.classList).toContain('toast-error');
    expect(toast.getAttribute('role')).toBe('alert');
  });

  it('shows good news as a status', () => {
    notifications.show('success', 'Saved');
    fixture.detectChanges();

    const toast = element.querySelector('.toast') as HTMLElement;
    expect(toast.classList).toContain('toast-success');
    expect(toast.getAttribute('role')).toBe('status');
  });

  it('shows several messages at once', () => {
    notifications.show('error', 'first');
    notifications.show('success', 'second');
    fixture.detectChanges();

    expect(Array.from(element.querySelectorAll('.toast-text')).map((t) => t.textContent)).toEqual(['first', 'second']);
  });

  it('lets the user dismiss a message', () => {
    notifications.show('error', 'first');
    notifications.show('error', 'second');
    fixture.detectChanges();

    (element.querySelector('.toast-close') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(Array.from(element.querySelectorAll('.toast-text')).map((t) => t.textContent)).toEqual(['second']);
  });

  it('removes a message by itself after a few seconds', () => {
    notifications.show('error', 'temporary');
    fixture.detectChanges();

    vi.advanceTimersByTime(6000);
    fixture.detectChanges();

    expect(element.querySelectorAll('.toast')).toHaveLength(0);
  });
});
