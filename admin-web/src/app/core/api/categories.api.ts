import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DeviceBrand,
  DeviceBrandRequest,
  DeviceCategory,
  DeviceCategoryRequest,
} from '../models/request.models';

@Injectable({ providedIn: 'root' })
export class CategoriesApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listActive(): Observable<DeviceCategory[]> {
    return this.http.get<DeviceCategory[]>(`${this.base}/device-categories`);
  }

  listAll(): Observable<DeviceCategory[]> {
    return this.http.get<DeviceCategory[]>(`${this.base}/device-categories/all`);
  }

  create(body: DeviceCategoryRequest): Observable<DeviceCategory> {
    return this.http.post<DeviceCategory>(`${this.base}/device-categories`, body);
  }

  update(id: string, body: DeviceCategoryRequest): Observable<DeviceCategory> {
    return this.http.put<DeviceCategory>(`${this.base}/device-categories/${id}`, body);
  }

  archive(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/device-categories/${id}/archive`, {});
  }

  unarchive(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/device-categories/${id}/unarchive`, {});
  }

  listBrandsAll(categoryId: string): Observable<DeviceBrand[]> {
    return this.http.get<DeviceBrand[]>(`${this.base}/device-categories/${categoryId}/brands/all`);
  }

  createBrand(categoryId: string, body: DeviceBrandRequest): Observable<DeviceBrand> {
    return this.http.post<DeviceBrand>(
      `${this.base}/device-categories/${categoryId}/brands`,
      body,
    );
  }

  updateBrand(
    categoryId: string,
    brandId: string,
    body: DeviceBrandRequest,
  ): Observable<DeviceBrand> {
    return this.http.put<DeviceBrand>(
      `${this.base}/device-categories/${categoryId}/brands/${brandId}`,
      body,
    );
  }

  archiveBrand(categoryId: string, brandId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/device-categories/${categoryId}/brands/${brandId}/archive`,
      {},
    );
  }

  unarchiveBrand(categoryId: string, brandId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/device-categories/${categoryId}/brands/${brandId}/unarchive`,
      {},
    );
  }
}
