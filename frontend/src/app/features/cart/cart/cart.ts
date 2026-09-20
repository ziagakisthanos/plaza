import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError } from 'rxjs';
import { CartItem, CartService } from '../../../core/services/cart';
import { OrderService } from '../../../core/services/order';
import { apiErrorMessage } from '../../../core/utils/api-error';

@Component({
  selector: 'app-cart',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class CartPage implements OnInit {
  private readonly cartService = inject(CartService);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  cart = this.cartService.cart;
  loading = signal(true);
  busy = signal(false);
  error = signal('');

  checkoutForm = this.fb.nonNullable.group({
    deliveryAddress: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(300)]],
  });

  hasStockProblem = computed(() => this.cart().items.some((item) => this.overStock(item)));

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

  addressInvalid(): boolean {
    const field = this.checkoutForm.controls.deliveryAddress;
    return field.invalid && (field.touched || field.dirty);
  }

  placeOrder(): void {
    if (this.checkoutForm.invalid) {
      this.checkoutForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.error.set('');
    this.orderService
      .checkout({
        paymentMethod: 'PAY_ON_DELIVERY',
        deliveryAddress: this.checkoutForm.controls.deliveryAddress.value.trim(),
      })
      .subscribe({
        next: (orders) => {
          this.busy.set(false);
          this.refreshCart();
          this.router.navigate(['/orders'], { state: { placed: orders.length } });
        },
        error: (error) => {
          this.busy.set(false);
          this.error.set(apiErrorMessage(error, 'We could not place your order. Please try again.'));
          this.refreshCart();
        },
      });
  }

  private refreshCart(): void {
    this.cartService.load().pipe(catchError(() => EMPTY)).subscribe();
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
