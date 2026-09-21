import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, debounceTime, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { Product, ProductFilters, ProductService, ProductSort } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { CartService } from '../../../core/services/cart';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { AuthService } from '../../../core/services/auth';

interface ProductWithImage extends Product {
  imageUrl?: string;
}

const SEARCH_DELAY_MS = 300;
const NOTICE_MS = 4000;

interface Notice {
  kind: 'success' | 'error';
  text: string;
}

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList {
  private readonly productService = inject(ProductService);
  private readonly mediaService = inject(MediaService);
  private readonly authService = inject(AuthService);
  private readonly cartService = inject(CartService);

  products = signal<ProductWithImage[]>([]);
  categories = signal<string[]>([]);
  loading = signal(true);
  error = signal('');
  notice = signal<Notice | null>(null);
  addingId = signal<string | null>(null);

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
            catchError((error) => {
              this.fail(error);
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

  private fail(error: unknown): void {
    this.error.set(apiErrorMessage(error, 'We could not load the marketplace. Please try again.'));
    this.loading.set(false);
  }

  isClient(): boolean {
    return this.authService.isLoggedIn() && this.authService.getRole() === 'CLIENT';
  }

  isLoggedIn(): boolean {
    return this.authService.isLoggedIn();
  }

  canAdd(product: Product): boolean {
    return product.quantity > 0 && this.cartService.quantityOf(product.id) < product.quantity;
  }

  addLabel(product: Product): string {
    if (product.quantity === 0) return 'Sold out';
    return this.canAdd(product) ? 'Add to cart' : 'All in your cart';
  }

  addToCart(product: Product): void {
    this.addingId.set(product.id);
    this.cartService.add(product.id).subscribe({
      next: () => {
        this.addingId.set(null);
        this.showNotice('success', `${product.name} was added to your cart.`);
      },
      error: (error) => {
        this.addingId.set(null);
        this.showNotice('error', apiErrorMessage(error, 'We could not add the item to your cart.'));
      },
    });
  }

  private showNotice(kind: Notice['kind'], text: string): void {
    const notice = { kind, text };
    this.notice.set(notice);
    setTimeout(() => {
      if (this.notice() === notice) this.notice.set(null);
    }, NOTICE_MS);
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
