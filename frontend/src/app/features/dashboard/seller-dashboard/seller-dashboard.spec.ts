import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SellerDashboard } from './seller-dashboard';
import { ProductService } from '../../../core/services/product';
import { MediaService } from '../../../core/services/media';
import { AuthService } from '../../../core/services/auth';
import { NO_CONNECTION_MESSAGE } from '../../../core/utils/api-error';

describe('SellerDashboard', () => {
  let fixture: ComponentFixture<SellerDashboard>;
  let component: SellerDashboard;
  let products: Record<'getAll' | 'getCategories' | 'create' | 'update' | 'delete', ReturnType<typeof vi.fn>>;
  let media: Record<'getImagesForProduct' | 'uploadImage' | 'imageUrl', ReturnType<typeof vi.fn>>;

  async function open(): Promise<void> {
    products = {
      getAll: vi.fn().mockReturnValue(of([])),
      getCategories: vi.fn().mockReturnValue(of(['Books'])),
      create: vi.fn().mockReturnValue(of({})),
      update: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    media = {
      getImagesForProduct: vi.fn().mockReturnValue(of([])),
      uploadImage: vi.fn().mockReturnValue(of({})),
      imageUrl: vi.fn().mockReturnValue(''),
    };
    await TestBed.configureTestingModule({
      imports: [SellerDashboard],
      providers: [
        { provide: ProductService, useValue: products },
        { provide: MediaService, useValue: media },
        { provide: AuthService, useValue: { getUserId: () => 'seller-1' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerDashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function fillProduct(): void {
    component.openCreate();
    component.productForm.setValue({ name: 'Mug', description: '', category: 'Kitchen', price: 5, quantity: 3 });
  }

  const ownProduct = {
    id: 'p1',
    name: 'Mug',
    description: '',
    category: 'Kitchen',
    price: 5,
    quantity: 3,
    userId: 'seller-1',
    createdAt: null,
  };

  it('shows the products of the seller', async () => {
    await open();
    products.getAll.mockReturnValue(of([ownProduct, { ...ownProduct, id: 'p2', userId: 'someone-else' }]));

    component.loadMyProducts();

    expect(component.myProducts().map((p) => p.id)).toEqual(['p1']);
  });

  it('shows the reason when the server refuses a new product', async () => {
    await open();
    products.create.mockReturnValue(
      throwError(() => ({ status: 400, error: { message: 'Validation failed', fieldErrors: { category: 'Please choose a category' } } })),
    );
    fillProduct();

    component.onSubmit();

    expect(component.errorMessage()).toBe('Please choose a category');
    expect(component.saving()).toBe(false);
  });

  it('keeps a helpful message when saving a product fails without a reason', async () => {
    await open();
    products.create.mockReturnValue(throwError(() => ({ status: 500, error: { message: 'Unexpected error occurred' } })));
    fillProduct();

    component.onSubmit();
    expect(component.errorMessage()).toBe('Failed to create product');

    component.startEdit({ ...ownProduct });
    products.update.mockReturnValue(throwError(() => ({ status: 500 })));
    component.onSubmit();
    expect(component.errorMessage()).toBe('Failed to update product');
  });

  it('tells the seller when the server cannot be reached', async () => {
    await open();
    products.create.mockReturnValue(throwError(() => ({ status: 0 })));
    fillProduct();

    component.onSubmit();

    expect(component.errorMessage()).toBe(NO_CONNECTION_MESSAGE);
  });

  it('does not send a product that is missing its category', async () => {
    await open();
    component.openCreate();
    component.productForm.setValue({ name: 'Mug', description: '', category: '', price: 5, quantity: 3 });

    component.onSubmit();

    expect(products.create).not.toHaveBeenCalled();
    expect(component.invalid('category')).toBe(true);
  });

  it('shows the reason when a product cannot be deleted', async () => {
    await open();
    products.delete.mockReturnValue(throwError(() => ({ status: 403, error: { message: 'You can only delete products that belong to you.' } })));
    component.askDelete('p1');

    component.confirmDelete();

    expect(component.errorMessage()).toBe('You can only delete products that belong to you.');
    expect(component.deletingId()).toBeNull();
  });

  it('falls back to a plain message when a delete fails without a reason', async () => {
    await open();
    products.delete.mockReturnValue(throwError(() => ({ status: 500 })));
    component.askDelete('p1');

    component.confirmDelete();

    expect(component.errorMessage()).toBe('Failed to delete product');
  });

  it('shows the size message when the server refuses a file that is too large', async () => {
    await open();
    media.uploadImage.mockReturnValue(throwError(() => ({ status: 413, error: { message: 'The uploaded file is too large' } })));
    component.uploadingFor.set('p1');
    component.selectedFile.set(new File(['x'], 'photo.png', { type: 'image/png' }));

    component.uploadImage();

    expect(component.errorMessage()).toBe('The uploaded file is too large');
    expect(component.selectedFile()).toBeNull();
    expect(component.uploadingFor()).toBeNull();
  });

  it('checks the file in the browser first, before anything is sent', async () => {
    await open();
    const input = (file: File) => ({ target: { files: [file] } }) as unknown as Event;

    component.onFileSelected(input(new File(['x'], 'notes.txt', { type: 'text/plain' })));
    expect(component.errorMessage()).toBe('Only image files are allowed');

    component.onFileSelected(input(new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })));
    expect(component.errorMessage()).toBe('Image must be under 2 MB');
    expect(media.uploadImage).not.toHaveBeenCalled();

    component.onFileSelected(input(new File(['x'], 'ok.png', { type: 'image/png' })));
    expect(component.errorMessage()).toBe('');
    expect(component.selectedFile()?.name).toBe('ok.png');
  });

  it('shows the reason when the products cannot be loaded', async () => {
    await open();
    products.getAll.mockReturnValue(throwError(() => ({ status: 503, error: { message: 'Products are temporarily unavailable, please try again' } })));

    component.loadMyProducts();

    expect(component.errorMessage()).toBe('Products are temporarily unavailable, please try again');
    expect(component.loading()).toBe(false);
  });
});
