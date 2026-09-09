import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ProductService, Product } from '../../../core/services/product';
import { AuthService } from '../../../core/services/auth';
import { MediaService } from '../../../core/services/media';

@Component({
  selector: 'app-seller-dashboard',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './seller-dashboard.html',
  styleUrl: './seller-dashboard.css',
})
export class SellerDashboard implements OnInit {
  private productService = inject(ProductService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private mediaService = inject(MediaService);

  myProducts = signal<Product[]>([]);
  loading = signal(true);
  errorMessage = signal('');
  editingId = signal<string | null>(null);
  selectedFile = signal<File | null>(null);
  uploadingFor = signal<string | null>(null); 

  productForm = this.fb.group({
    name: ['', [Validators.required]],
    description: [''],
    price: [null as number | null, [Validators.required, Validators.min(0.01)]],
    quantity: [null as number | null, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void { this.loadMyProducts(); }

  loadMyProducts(): void {
    const myId = this.authService.getUserId();
    this.productService.getAll().subscribe({
      next: (products) => {
        this.myProducts.set(products.filter(p => p.userId === myId));
        this.loading.set(false);
      },
      error: () => { this.errorMessage.set('Failed to load products'); this.loading.set(false); },
    });
  }

  startEdit(product: Product): void {
    this.editingId.set(product.id);
    this.productForm.setValue({
      name: product.name,
      description: product.description ?? '',
      price: product.price,
      quantity: product.quantity,
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.productForm.reset();
  }

  onSubmit(): void {
    if (this.productForm.invalid) return;

    const product = {
      name: this.productForm.value.name!,
      description: this.productForm.value.description ?? '',
      price: this.productForm.value.price!,
      quantity: this.productForm.value.quantity!,
    };

    const id = this.editingId();
    const request$ = id
      ? this.productService.update(id, product)     // edit mode
      : this.productService.create(product);        // create mode

    request$.subscribe({
      next: () => {
        this.productForm.reset();
        this.editingId.set(null);
        this.loadMyProducts();
      },
      error: () => this.errorMessage.set(id ? 'Failed to update product' : 'Failed to create product'),
    });
  }

  onDelete(id: string): void {
    this.productService.delete(id).subscribe({
      next: () => this.loadMyProducts(),
      error: () => this.errorMessage.set('Failed to delete product'),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      // client-side validation (mirrors backend: image/*, <= 2MB)
      if (!file.type.startsWith('image/')) {
        this.errorMessage.set('Only image files are allowed');
        return;
      }
      if (file.size > 2 * 1024 * 1024) {
        this.errorMessage.set('Image must be under 2 MB');
        return;
      }
      this.selectedFile.set(file);
    }
  }

  uploadImage(productId: string): void {
    const file = this.selectedFile();
    if (!file) { this.errorMessage.set('Please select an image first'); return; }

    this.mediaService.uploadImage(file, productId).subscribe({
      next: () => {
        this.selectedFile.set(null);
        this.uploadingFor.set(null);
        this.errorMessage.set('');
      },
      error: () => this.errorMessage.set('Failed to upload image'),
    });
  }
}