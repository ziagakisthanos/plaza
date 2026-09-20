import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, debounceTime, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { Product, ProductFilters, ProductService, ProductSort } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { AuthService } from '../../../core/services/auth';

interface ProductWithImage extends Product {
  imageUrl?: string;
}

const SEARCH_DELAY_MS = 300;

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList {
  private productService = inject(ProductService);
  private mediaService = inject(MediaService);
  private authService = inject(AuthService);

  products = signal<ProductWithImage[]>([]);
  categories = signal<string[]>([]);
  loading = signal(true);
  error = signal('');

  query = signal('');
  category = signal('');
  minPrice = signal<number | null>(null);
  maxPrice = signal<number | null>(null);
  sort = signal<ProductSort>('newest');
  inStockOnly = signal(false);

  readonly sorts: { key: ProductSort; label: string }[] = [
    { key: 'newest', label: 'Newest' },
    { key: 'price_asc', label: 'Price: low to high' },
    { key: 'price_desc', label: 'Price: high to low' },
  ];

  filters = computed<ProductFilters>(() => ({
    q: this.query(),
    category: this.category(),
    minPrice: this.minPrice(),
    maxPrice: this.maxPrice(),
    inStock: this.inStockOnly(),
    sort: this.sort(),
  }));

  priceError = computed(() => {
    const min = this.minPrice();
    const max = this.maxPrice();
    if ((min !== null && (Number.isNaN(min) || min < 0)) || (max !== null && (Number.isNaN(max) || max < 0))) {
      return 'Prices must be numbers of 0 or more.';
    }
    if (min !== null && max !== null && min > max) {
      return 'The minimum price cannot be higher than the maximum price.';
    }
    return '';
  });

  isFiltered = computed(
    () =>
      this.query().trim().length > 0 ||
      this.category() !== '' ||
      this.minPrice() !== null ||
      this.maxPrice() !== null ||
      this.inStockOnly(),
  );
  showSkeleton = computed(() => this.loading() && this.products().length === 0);
  showSellerCta = computed(() => !this.authService.isLoggedIn());

  constructor() {
    toObservable(this.filters)
      .pipe(
        debounceTime(SEARCH_DELAY_MS),
        filter(() => this.priceError() === ''),
        tap(() => {
          this.loading.set(true);
          this.error.set('');
        }),
        switchMap((filters) =>
          this.productService.search(filters).pipe(
            switchMap((products) => this.withImages(products)),
            catchError(() => {
              this.fail();
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((products) => {
        this.products.set(products);
        this.loading.set(false);
      });

    this.productService.getCategories().subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.categories.set([]),
    });
  }

  private withImages(products: Product[]) {
    if (products.length === 0) {
      return of<ProductWithImage[]>([]);
    }
    return forkJoin(
      products.map((product) =>
        this.mediaService.getImagesForProduct(product.id).pipe(
          map((images) => ({
            ...product,
            imageUrl: images.length ? this.mediaService.imageUrl(images[0].id) : undefined,
          })),
          catchError(() => of({ ...product, imageUrl: undefined })),
        ),
      ),
    );
  }

  private fail(): void {
    this.error.set('We could not load the marketplace. Please try again.');
    this.loading.set(false);
  }

  private priceFrom(event: Event): number | null {
    const value = (event.target as HTMLInputElement).value;
    return value === '' ? null : Number(value);
  }

  onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  setCategory(event: Event): void {
    this.category.set((event.target as HTMLSelectElement).value);
  }

  setMinPrice(event: Event): void {
    this.minPrice.set(this.priceFrom(event));
  }

  setMaxPrice(event: Event): void {
    this.maxPrice.set(this.priceFrom(event));
  }

  setSort(event: Event): void {
    this.sort.set((event.target as HTMLSelectElement).value as ProductSort);
  }

  toggleInStock(): void {
    this.inStockOnly.update((only) => !only);
  }

  clearFilters(): void {
    this.query.set('');
    this.category.set('');
    this.minPrice.set(null);
    this.maxPrice.set(null);
    this.inStockOnly.set(false);
  }
}
