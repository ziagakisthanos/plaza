import { Component, inject, OnInit, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { ProductService, Product } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { forkJoin, of, map, catchError } from 'rxjs';

interface ProductWithImage extends Product {
  imageUrl?: string;
}

@Component({
  selector: 'app-product-list',
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList implements OnInit {
  private productService = inject(ProductService);
  private mediaService = inject(MediaService);

  products = signal<ProductWithImage[]>([]);
  loading = signal(true);
  error = signal('');

  ngOnInit(): void {
    this.productService.getAll().subscribe({
      next: (products) => {
        if (products.length === 0) {
          this.products.set([]);
          this.loading.set(false);
          return;
        }

        const withImages$ = products.map(product =>
          this.mediaService.getImagesForProduct(product.id).pipe(
            map(images => ({
              ...product,
              imageUrl: images.length > 0
                ? this.mediaService.imageUrl(images[0].id)
                : undefined,
            })),
            catchError(() => of({ ...product, imageUrl: undefined }))
          )
        );
        // join them when all the requests have returned
        forkJoin(withImages$).subscribe({
          next: (result) => { this.products.set(result); this.loading.set(false); },
          error: () => { this.error.set('Failed to load products'); this.loading.set(false); },
        });
      },
      error: () => { this.error.set('Failed to load products'); this.loading.set(false); },
    });
  }
}