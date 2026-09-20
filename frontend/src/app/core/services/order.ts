import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type PaymentMethod = 'PAY_ON_DELIVERY';

export const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING',
  'CONFIRMED',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export interface OrderItem {
  productId: string;
  name: string;
  price: number;
  quantity: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  buyerId: string;
  sellerId: string;
  items: OrderItem[];
  total: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  deliveryAddress: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderFilters {
  q?: string;
  status?: OrderStatus | '';
}

export interface CheckoutRequest {
  paymentMethod: PaymentMethod;
  deliveryAddress: string;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  checkout(request: CheckoutRequest): Observable<Order[]> {
    return this.http.post<Order[]>(`${this.apiUrl}/orders/checkout`, request);
  }

  list(filters: OrderFilters): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/orders`, { params: this.paramsOf(filters) });
  }

  listReceived(filters: OrderFilters): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/orders/seller`, {
      params: this.paramsOf(filters),
    });
  }

  updateStatus(id: string, status: OrderStatus): Observable<Order> {
    return this.http.put<Order>(`${this.apiUrl}/orders/${id}/status`, { status });
  }

  cancel(id: string): Observable<Order> {
    return this.http.put<Order>(`${this.apiUrl}/orders/${id}/cancel`, {});
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/orders/${id}`);
  }

  redo(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/orders/${id}/redo`, {});
  }

  private paramsOf(filters: OrderFilters): HttpParams {
    let params = new HttpParams();
    const text = filters.q?.trim();
    if (text) params = params.set('q', text);
    if (filters.status) params = params.set('status', filters.status);
    return params;
  }
}
