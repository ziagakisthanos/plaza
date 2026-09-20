import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { catchError, forkJoin, map, of } from 'rxjs';
import { Product, ProductService } from '../../../core/services/product';
import { AuthService } from '../../../core/services/auth';
import { MediaService } from '../../../core/services/media';

interface SellerProduct extends Product {
  imageUrl?: string;
}

@Component({
  selector: 'app-seller-dashboard',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './seller-dashboard.html',
  styleUrl: './seller-dashboard.css',
})
export class SellerDashboard implements OnInit {
  private productService = inject(ProductService);
  private authService = inject(AuthService);
  private mediaService = inject(MediaService);
  private fb = inject(FormBuilder);

  myProducts = signal<SellerProduct[]>([]);
  categories = signal<string[]>([]);
  loading = signal(true);
  errorMessage = signal('');
  successMessage = signal('');

  panelOpen = signal(false);
  editingId = signal<string | null>(null);
  saving = signal(false);
  deletingId = signal<string | null>(null);

  uploadingFor = signal<string | null>(null);
  selectedFile = signal<File | null>(null);

  // --- headline numbers ----------------------------------------------------
  totalProducts = computed(() => this.myProducts().length);
  totalStock = computed(() => this.myProducts().reduce((sum, p) => sum + p.quantity, 0));
  inventoryValue = computed(() =>
    this.myProducts().reduce((sum, p) => sum + p.price * p.quantity, 0),
  );
  outOfStock = computed(() => this.myProducts().filter((p) => p.quantity === 0).length);

  productForm = this.fb.group({
    name: ['', [Validators.required]],
    description: [''],
    category: ['', [Validators.required, Validators.maxLength(50)]],
    price: [null as number | null, [Validators.required, Validators.min(0.01)]],
    quantity: [null as number | null, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.loadMyProducts();
    this.loadCategories();
  }

  private loadCategories(): void {
    this.productService.getCategories().subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.categories.set([]),
    });
  }

  loadMyProducts(): void {
    const myId = this.authService.getUserId();
    this.productService.getAll().subscribe({
      next: (products) => {
        const mine = products.filter((p) => p.userId === myId);
        if (mine.length === 0) {
          this.myProducts.set([]);
          this.loading.set(false);
          return;
        }
        forkJoin(
          mine.map((product) =>
            this.mediaService.getImagesForProduct(product.id).pipe(
              map((images) => ({
                ...product,
                imageUrl: images.length ? this.mediaService.imageUrl(images[0].id) : undefined,
              })),
              catchError(() => of({ ...product, imageUrl: undefined })),
            ),
          ),
        ).subscribe({
          next: (result) => {
            this.myProducts.set(result);
            this.loading.set(false);
          },
          error: () => this.fail('Failed to load your products'),
        });
      },
      error: () => this.fail('Failed to load your products'),
    });
  }

  private fail(message: string): void {
    this.errorMessage.set(message);
    this.loading.set(false);
  }

  invalid(control: string): boolean {
    const field = this.productForm.get(control);
    return !!field && field.invalid && (field.touched || field.dirty);
  }

  // --- create / edit panel -------------------------------------------------

  openCreate(): void {
    this.editingId.set(null);
    this.productForm.reset();
    this.panelOpen.set(true);
  }

  startEdit(product: Product): void {
    this.editingId.set(product.id);
    this.productForm.setValue({
      name: product.name,
      description: product.description ?? '',
      category: product.category ?? '',
      price: product.price,
      quantity: product.quantity,
    });
    this.panelOpen.set(true);
  }

  closePanel(): void {
    this.panelOpen.set(false);
    this.editingId.set(null);
    this.productForm.reset();
  }

  onSubmit(): void {
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    const product = {
      name: this.productForm.value.name!,
      description: this.productForm.value.description ?? '',
      category: this.productForm.value.category!.trim(),
      price: this.productForm.value.price!,
      quantity: this.productForm.value.quantity!,
    };

    const id = this.editingId();
    this.saving.set(true);
    this.errorMessage.set('');

    const request$ = id
      ? this.productService.update(id, product)
      : this.productService.create(product);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.flash(id ? 'Product updated' : 'Product added');
        this.closePanel();
        this.loadMyProducts();
        this.loadCategories();
      },
      error: () => {
        this.saving.set(false);
        this.errorMessage.set(id ? 'Failed to update product' : 'Failed to create product');
      },
    });
  }

  // --- delete --------------------------------------------------------------

  askDelete(id: string): void {
    this.deletingId.set(id);
  }

  cancelDelete(): void {
    this.deletingId.set(null);
  }

  confirmDelete(): void {
    const id = this.deletingId();
    if (!id) return;
    this.productService.delete(id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.flash('Product deleted');
        this.loadMyProducts();
      },
      error: () => {
        this.deletingId.set(null);
        this.errorMessage.set('Failed to delete product');
      },
    });
  }

  deleteTarget(): SellerProduct | undefined {
    return this.myProducts().find((p) => p.id === this.deletingId());
  }

  // --- image upload --------------------------------------------------------

  /** Opens the picker for one product; the selection then applies to it alone. */
  chooseImage(productId: string, input: HTMLInputElement): void {
    this.uploadingFor.set(productId);
    this.selectedFile.set(null);
    input.value = '';
    input.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    // Mirrors the backend rules: images only, 2 MB ceiling.
    if (!file.type.startsWith('image/')) {
      this.errorMessage.set('Only image files are allowed');
      this.uploadingFor.set(null);
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.errorMessage.set('Image must be under 2 MB');
      this.uploadingFor.set(null);
      return;
    }

    this.errorMessage.set('');
    this.selectedFile.set(file);
  }

  uploadImage(): void {
    const file = this.selectedFile();
    const productId = this.uploadingFor();
    if (!file || !productId) return;

    this.mediaService.uploadImage(file, productId).subscribe({
      next: () => {
        this.selectedFile.set(null);
        this.uploadingFor.set(null);
        this.flash('Image uploaded');
        this.loadMyProducts();
      },
      error: () => {
        this.errorMessage.set('Failed to upload image');
        this.selectedFile.set(null);
        this.uploadingFor.set(null);
      },
    });
  }

  cancelUpload(): void {
    this.selectedFile.set(null);
    this.uploadingFor.set(null);
  }

  private flash(message: string): void {
    this.successMessage.set(message);
    setTimeout(() => this.successMessage.set(''), 3200);
  }
}
