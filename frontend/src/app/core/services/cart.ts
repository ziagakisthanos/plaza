import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

export interface CartItem {
  productId: string;
  name: string;
  price: number;
  quantity: number;
  availableStock: number;
  lineTotal: number;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  total: number;
}

const EMPTY_CART: Cart = { items: [], itemCount: 0, total: 0 };

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = environment.apiUrl;

  private loadedFor: string | null = null;
  private readonly state = signal<Cart>(EMPTY_CART);

  readonly cart = this.state.asReadonly();
  readonly itemCount = computed(() => this.state().itemCount);

  ensureLoaded(): void {
    const userId = this.currentClientId();
    if (userId === this.loadedFor) return;

    this.loadedFor = userId;
    this.state.set(EMPTY_CART);
    if (userId) {
      this.load().subscribe({ error: () => (this.loadedFor = null) });
    }
  }

  quantityOf(productId: string): number {
    return this.state().items.find((item) => item.productId === productId)?.quantity ?? 0;
  }

  load(): Observable<Cart> {
    return this.http.get<Cart>(`${this.apiUrl}/cart`).pipe(tap((cart) => this.state.set(cart)));
  }

  add(productId: string, quantity = 1): Observable<Cart> {
    return this.http
      .post<Cart>(`${this.apiUrl}/cart/items`, { productId, quantity })
      .pipe(tap((cart) => this.state.set(cart)));
  }

  setQuantity(productId: string, quantity: number): Observable<Cart> {
    return this.http
      .put<Cart>(`${this.apiUrl}/cart/items/${productId}`, { quantity })
      .pipe(tap((cart) => this.state.set(cart)));
  }

  remove(productId: string): Observable<Cart> {
    return this.http
      .delete<Cart>(`${this.apiUrl}/cart/items/${productId}`)
      .pipe(tap((cart) => this.state.set(cart)));
  }

  clear(): Observable<void> {
    return this.http
      .delete<void>(`${this.apiUrl}/cart`)
      .pipe(tap(() => this.state.set(EMPTY_CART)));
  }

  private currentClientId(): string | null {
    if (!this.authService.isLoggedIn() || this.authService.getRole() !== 'CLIENT') return null;
    return this.authService.getUserId();
  }
}
