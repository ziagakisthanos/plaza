import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, merge, switchMap, tap } from 'rxjs';
import {
  ORDER_STATUSES,
  ORDER_STATUS_LABELS,
  Order,
  OrderFilters,
  OrderService,
  OrderStatus,
} from '../../../core/services/order';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { OrderAction, OrderCard } from '../order-card/order-card';

type Mode = 'client' | 'seller';

interface Notice {
  kind: 'success' | 'error';
  text: string;
}

const SEARCH_DELAY_MS = 300;
const NOTICE_MS = 5000;

const ADVANCE_LABELS: Partial<Record<OrderStatus, string>> = {
  PENDING: 'Confirm order',
  CONFIRMED: 'Mark as shipped',
  SHIPPED: 'Mark as delivered',
};

const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  PENDING: 'CONFIRMED',
  CONFIRMED: 'SHIPPED',
  SHIPPED: 'DELIVERED',
};

@Component({
  selector: 'app-orders',
  imports: [RouterLink, OrderCard],
  templateUrl: './orders.html',
  styleUrl: './orders.css',
})
export class OrdersPage {
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  private readonly reload = new Subject<void>();

  readonly mode: Mode = inject(ActivatedRoute).snapshot.data['mode'] === 'seller' ? 'seller' : 'client';
  readonly statuses = ORDER_STATUSES.map((status) => ({ key: status, label: ORDER_STATUS_LABELS[status] }));

  orders = signal<Order[]>([]);
  loading = signal(true);
  error = signal('');
  notice = signal<Notice | null>(null);
  busy = signal(false);
  confirming = signal<{ id: string; action: string } | null>(null);

  query = signal('');
  status = signal<OrderStatus | ''>('');

  filters = computed<OrderFilters>(() => ({ q: this.query(), status: this.status() }));
  isFiltered = computed(() => this.query().trim().length > 0 || this.status() !== '');
  showSkeleton = computed(() => this.loading() && this.orders().length === 0);

  constructor() {
    const placed = this.router.currentNavigation()?.extras.state?.['placed'];
    if (typeof placed === 'number' && placed > 0) {
      this.showNotice('success', this.placedMessage(placed));
    }

    merge(toObservable(this.filters).pipe(debounceTime(SEARCH_DELAY_MS)), this.reload)
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set('');
        }),
        switchMap(() =>
          this.fetch().pipe(
            catchError((error) => {
              this.error.set(apiErrorMessage(error, 'We could not load your orders. Please try again.'));
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      });
  }

  onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  setStatus(event: Event): void {
    this.status.set((event.target as HTMLSelectElement).value as OrderStatus | '');
  }

  clearFilters(): void {
    this.query.set('');
    this.status.set('');
  }

  actionsFor(order: Order): OrderAction[] {
    return this.mode === 'seller' ? this.sellerActions(order.status) : this.clientActions(order.status);
  }

  confirmingKey(order: Order): string | null {
    const current = this.confirming();
    return current?.id === order.id ? current.action : null;
  }

  cancelConfirmation(): void {
    this.confirming.set(null);
  }

  onAction(order: Order, key: string): void {
    const action = this.actionsFor(order).find((candidate) => candidate.key === key);
    if (!action) return;

    if (action.confirm && this.confirmingKey(order) !== key) {
      this.confirming.set({ id: order.id, action: key });
      return;
    }

    this.confirming.set(null);
    this.perform(order, action);
  }

  private clientActions(status: OrderStatus): OrderAction[] {
    const cancel: OrderAction = { key: 'cancel', label: 'Cancel order', icon: 'block', tone: 'danger', confirm: true };
    const remove: OrderAction = { key: 'remove', label: 'Remove order', icon: 'delete_outline', tone: 'danger', confirm: true };
    const redo: OrderAction = { key: 'redo', label: 'Order again', icon: 'replay', tone: 'primary' };

    switch (status) {
      case 'PENDING':
      case 'CONFIRMED':
        return [cancel];
      case 'DELIVERED':
        return [redo];
      case 'CANCELLED':
        return [redo, remove];
      default:
        return [];
    }
  }

  private sellerActions(status: OrderStatus): OrderAction[] {
    const advance = ADVANCE_LABELS[status];
    const actions: OrderAction[] = advance
      ? [{ key: 'advance', label: advance, icon: 'arrow_forward', tone: 'primary' }]
      : [];
    if (status === 'PENDING' || status === 'CONFIRMED') {
      actions.push({ key: 'cancel', label: 'Cancel order', icon: 'block', tone: 'danger', confirm: true });
    }
    return actions;
  }

  private perform(order: Order, action: OrderAction): void {
    this.busy.set(true);
    this.notice.set(null);
    this.requestFor(order, action.key).subscribe({
      next: () => {
        this.busy.set(false);
        this.showNotice('success', this.doneMessage(action.key));
        this.reload.next();
      },
      error: (error) => {
        this.busy.set(false);
        this.showNotice('error', apiErrorMessage(error, 'We could not complete that action.'));
        this.reload.next();
      },
    });
  }

  private requestFor(order: Order, key: string): Observable<unknown> {
    switch (key) {
      case 'cancel':
        return this.orderService.cancel(order.id);
      case 'remove':
        return this.orderService.remove(order.id);
      case 'redo':
        return this.orderService.redo(order.id);
      default:
        return this.orderService.updateStatus(order.id, NEXT_STATUS[order.status] as OrderStatus);
    }
  }

  private fetch(): Observable<Order[]> {
    return this.mode === 'seller'
      ? this.orderService.listReceived(this.filters())
      : this.orderService.list(this.filters());
  }

  private doneMessage(key: string): string {
    switch (key) {
      case 'cancel':
        return 'The order was cancelled.';
      case 'remove':
        return 'The order was removed.';
      case 'redo':
        return 'A new order was placed with the same items.';
      default:
        return 'The order was updated.';
    }
  }

  private placedMessage(count: number): string {
    return count === 1
      ? 'Your order was placed. You will pay on delivery.'
      : `Your order was placed as ${count} orders, one for each seller. You will pay on delivery.`;
  }

  private showNotice(kind: Notice['kind'], text: string): void {
    const notice = { kind, text };
    this.notice.set(notice);
    setTimeout(() => {
      if (this.notice() === notice) this.notice.set(null);
    }, NOTICE_MS);
  }
}
