import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ProductService, Product } from '../../../core/services/product';
import { AuthService } from '../../../core/services/auth';

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

  myProducts = signal<Product[]>([]);
  loading = signal(true);
  errorMessage = signal('');

  productForm = this.fb.group({
    name: ['', [Validators.required]],
    description: [''],
    price: [null as number | null, [Validators.required, Validators.min(0.01)]],
    quantity: [null as number | null, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.loadMyProducts();
  }

  loadMyProducts(): void {
    const myId = this.authService.getUserId();
    this.productService.getAll().subscribe({
      next: (products) => {
        this.myProducts.set(products.filter(p => p.userId === myId));
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load products');
        this.loading.set(false);
      },
    });
  }

  onCreate(): void {
    if (this.productForm.invalid) return;

    const product = {
      name: this.productForm.value.name!,
      description: this.productForm.value.description ?? '',
      price: this.productForm.value.price!,
      quantity: this.productForm.value.quantity!,
    };

    this.productService.create(product).subscribe({
      next: () => {
        this.productForm.reset();
        this.loadMyProducts();       // refresh the list
      },
      error: () => {
        this.errorMessage.set('Failed to create product');
      },
    });
  }

  onDelete(id: string): void {
    this.productService.delete(id).subscribe({
      next: () => this.loadMyProducts(),
      error: () => this.errorMessage.set('Failed to delete product'),
    });
  }
}