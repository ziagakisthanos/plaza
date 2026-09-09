import { Component, inject, OnInit, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { ProductService, Product } from '../../../core/services/product';

@Component({
  selector: 'app-product-list',
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList implements OnInit {
  private productService = inject(ProductService);

  products = signal<Product[]>([]);   // signal holds the list
  loading = signal(true);
  error = signal('');

  ngOnInit(): void {
    this.productService.getAll().subscribe({
      next: (data) => {
        this.products.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load products');
        this.loading.set(false);
      },
    });
  }
}