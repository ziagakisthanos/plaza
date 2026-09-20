import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  quantity: number;
  userId: string;
  category: string | null;
  createdAt: string | null;
}

export type ProductSort = 'newest' | 'price_asc' | 'price_desc';

export interface ProductFilters {
  q?: string;
  category?: string;
  minPrice?: number | null;
  maxPrice?: number | null;
  inStock?: boolean;
  sort?: ProductSort;
}

export interface ProductRequest {
  name: string;
  description: string;
  category: string;
  price: number;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getAll(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.apiUrl}/products`);
  }

  search(filters: ProductFilters): Observable<Product[]> {
    let params = new HttpParams();
    const text = filters.q?.trim();
    if (text) params = params.set('q', text);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.minPrice != null) params = params.set('minPrice', filters.minPrice);
    if (filters.maxPrice != null) params = params.set('maxPrice', filters.maxPrice);
    if (filters.inStock) params = params.set('inStock', true);
    if (filters.sort) params = params.set('sort', filters.sort);
    return this.http.get<Product[]>(`${this.apiUrl}/products`, { params });
  }

  getCategories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/products/categories`);
  }

  getById(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/products/${id}`);
  }

  create(product: ProductRequest): Observable<Product> {
    return this.http.post<Product>(`${this.apiUrl}/products`, product);
  }

  update(id: string, product: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/products/${id}`, product);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/products/${id}`);
  }
}
