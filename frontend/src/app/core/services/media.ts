import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MediaResponse {
  id: string;
  productId: string;
  url: string;
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  uploadImage(file: File, productId: string): Observable<MediaResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('productId', productId);

    return this.http.post<MediaResponse>(`${this.apiUrl}/media/images`, formData);
  }

  imageUrl(id: string): string {
    return `${this.apiUrl}/media/images/${id}`;
  }

  getImagesForProduct(productId: string): Observable<MediaResponse[]> {
    return this.http.get<MediaResponse[]>(`${this.apiUrl}/media/product/${productId}`);
  }
}
