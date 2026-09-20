import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { CartItem, CartService } from '../../../core/services/cart';
import { apiErrorMessage } from '../../../core/utils/api-error';

@Component({
  selector: 'app-cart',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class CartPage implements OnInit {
  private readonly cartService = inject(CartService);

  cart = this.cartService.cart;
  loading = signal(true);
  busy = signal(false);
  error = signal('');

  ngOnInit(): void {
    this.cartService.load().subscribe({
      next: () => this.loading.set(false),
      error: (error) => {
        this.error.set(apiErrorMessage(error, 'We could not load your cart. Please try again.'));
        this.loading.set(false);
      },
    });
  }

  overStock(item: CartItem): boolean {
    return item.quantity > item.availableStock;
  }

  canIncrease(item: CartItem): boolean {
    return item.quantity < item.availableStock;
  }

  changeQuantity(item: CartItem, change: number): void {
    const quantity = item.quantity + change;
    if (quantity < 1) return;
    this.run(this.cartService.setQuantity(item.productId, quantity), 'We could not change the quantity.');
  }

  remove(item: CartItem): void {
    this.run(this.cartService.remove(item.productId), 'We could not remove the item.');
  }

  clear(): void {
    this.run(this.cartService.clear(), 'We could not empty your cart.');
  }

  private run(request: Observable<unknown>, fallback: string): void {
    this.busy.set(true);
    this.error.set('');
    request.subscribe({
      next: () => this.busy.set(false),
      error: (error) => {
        this.error.set(apiErrorMessage(error, fallback));
        this.busy.set(false);
      },
    });
  }
}
