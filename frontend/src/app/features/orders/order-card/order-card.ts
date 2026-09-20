import { Component, computed, input, output } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ORDER_STATUS_LABELS, Order } from '../../../core/services/order';

export interface OrderAction {
  key: string;
  label: string;
  icon: string;
  tone: 'primary' | 'secondary' | 'danger';
  confirm?: boolean;
}

@Component({
  selector: 'app-order-card',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './order-card.html',
  styleUrl: './order-card.css',
})
export class OrderCard {
  readonly order = input.required<Order>();
  readonly actions = input<OrderAction[]>([]);
  readonly busy = input(false);
  readonly confirmingKey = input<string | null>(null);

  readonly act = output<string>();
  readonly dismiss = output<void>();

  readonly shortId = computed(() => this.order().id.slice(-6).toUpperCase());
  readonly statusLabel = computed(() => ORDER_STATUS_LABELS[this.order().status]);
}
