import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { Product, ProductService } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { AuthService } from '../../../core/services/auth';

interface ProductWithImage extends Product {
  imageUrl?: string;
}

type SortKey = 'newest' | 'price-asc' | 'price-desc' | 'name';

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList implements OnInit {
  private productService = inject(ProductService);
  private mediaService = inject(MediaService);
  private authService = inject(AuthService);

  products = signal<ProductWithImage[]>([]);
  loading = signal(true);
  error = signal('');

  query = signal('');
  sort = signal<SortKey>('newest');
  inStockOnly = signal(false);

  readonly sorts: { key: SortKey; label: string }[] = [
    { key: 'newest', label: 'Featured' },
    { key: 'price-asc', label: 'Price: low to high' },
    { key: 'price-desc', label: 'Price: high to low' },
    { key: 'name', label: 'Name A–Z' },
  ];

  visibleProducts = computed(() => {
    const term = this.query().trim().toLowerCase();
    let list = this.products();

    if (term) {
      list = list.filter(
        (p) =>
          p.name.toLowerCase().includes(term) || (p.description ?? '').toLowerCase().includes(term),
      );
    }
    if (this.inStockOnly()) {
      list = list.filter((p) => p.quantity > 0);
    }

    const sorted = [...list];
    switch (this.sort()) {
      case 'price-asc':
        return sorted.sort((a, b) => a.price - b.price);
      case 'price-desc':
        return sorted.sort((a, b) => b.price - a.price);
      case 'name':
        return sorted.sort((a, b) => a.name.localeCompare(b.name));
      default:
        return sorted;
    }
  });

  isFiltered = computed(() => this.query().trim().length > 0 || this.inStockOnly());
  showSellerCta = computed(() => !this.authService.isLoggedIn());

  ngOnInit(): void {
    this.productService.getAll().subscribe({
      next: (products) => {
        if (products.length === 0) {
          this.products.set([]);
          this.loading.set(false);
          return;
        }

        // Fetch each product's first image, tolerating media failures per product.
        const withImages$ = products.map((product) =>
          this.mediaService.getImagesForProduct(product.id).pipe(
            map((images) => ({
              ...product,
              imageUrl: images.length ? this.mediaService.imageUrl(images[0].id) : undefined,
            })),
            catchError(() => of({ ...product, imageUrl: undefined })),
          ),
        );

        forkJoin(withImages$).subscribe({
          next: (result) => {
            this.products.set(result);
            this.loading.set(false);
          },
          error: () => this.fail(),
        });
      },
      error: () => this.fail(),
    });
  }

  private fail(): void {
    this.error.set('We could not load the marketplace. Please try again.');
    this.loading.set(false);
  }

  onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  setSort(event: Event): void {
    this.sort.set((event.target as HTMLSelectElement).value as SortKey);
  }

  toggleInStock(): void {
    this.inStockOnly.update((only) => !only);
  }

  clearFilters(): void {
    this.query.set('');
    this.inStockOnly.set(false);
  }
}
