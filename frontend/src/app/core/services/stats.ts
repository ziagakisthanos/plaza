import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductStat {
  productId: string;
  name: string;
  quantity: number;
  amount: number;
}

export interface ClientStats {
  totalSpent: number;
  orderCount: number;
  mostBought: ProductStat[];
  bestProducts: ProductStat[];
}

export interface SellerStats {
  totalEarned: number;
  orderCount: number;
  bestSelling: ProductStat[];
}

@Injectable({ providedIn: 'root' })
export class StatsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  getClientStats(): Observable<ClientStats> {
    return this.http.get<ClientStats>(`${this.apiUrl}/orders/stats/client`);
  }

  getSellerStats(): Observable<SellerStats> {
    return this.http.get<SellerStats>(`${this.apiUrl}/orders/stats/seller`);
  }
}
