import {HttpBackend, HttpClient, HttpHeaders} from '@angular/common/http';
import {HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {
  InitiateFileUploadRequest,
  InitiateFileUploadResponse,
  PresignedDownloadResponse,
  StoredFile
} from '../models/file.model';
import {PagedResponse, PageRequest} from '../models/pagination.model';

@Injectable({
  providedIn: 'root',
})
export class FilesService {
  private readonly http = inject(HttpClient);
  private readonly rawHttp = new HttpClient(inject(HttpBackend));
  private readonly baseUrl = `${environment.apiUrl}/files`;

  findPage(request: PageRequest): Observable<PagedResponse<StoredFile>> {
    return this.http.get<PagedResponse<StoredFile>>(this.baseUrl, {
      params: this.toParams(request)
    });
  }

  findAll(): Observable<StoredFile[]> {
    return this.findPage({page: 0, size: 100, sort: 'updatedAt,desc'}).pipe(
      map((response) => response.items)
    );
  }

  requestUpload(payload: InitiateFileUploadRequest): Observable<InitiateFileUploadResponse> {
    return this.http.post<InitiateFileUploadResponse>(`${this.baseUrl}/uploads/presign`, payload);
  }

  uploadToPresignedUrl(uploadUrl: string, file: File, contentType: string): Observable<void> {
    return this.rawHttp.put(uploadUrl, file, {
      headers: new HttpHeaders({
        'Content-Type': contentType
      }),
      responseType: 'text'
    }).pipe(
      map(() => void 0)
    );
  }

  completeUpload(id: number): Observable<StoredFile> {
    return this.http.post<StoredFile>(`${this.baseUrl}/${id}/complete`, {});
  }

  createDownloadUrl(id: number): Observable<PresignedDownloadResponse> {
    return this.http.get<PresignedDownloadResponse>(`${this.baseUrl}/${id}/download-url`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  private toParams(request: PageRequest): HttpParams {
    let params = new HttpParams()
      .set('page', request.page)
      .set('size', request.size);

    if (request.sort) {
      params = params.set('sort', request.sort);
    }

    return params;
  }
}
