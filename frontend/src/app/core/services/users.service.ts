import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse, PageRequest} from '../models/pagination.model';
import {AssignableUser, CreateUserRequest, UpdateUserRequest, UserAdmin} from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users`;

  findPage(request: PageRequest): Observable<PagedResponse<UserAdmin>> {
    return this.http.get<PagedResponse<UserAdmin>>(this.baseUrl, {
      params: this.toParams(request)
    });
  }

  findAll(): Observable<UserAdmin[]> {
    return this.findPage({page: 0, size: 100, sort: 'createdAt,desc'}).pipe(
      map((response) => response.items)
    );
  }

  findAssignable(): Observable<AssignableUser[]> {
    return this.http.get<AssignableUser[]>(`${this.baseUrl}/assignable`);
  }

  create(payload: CreateUserRequest): Observable<UserAdmin> {
    return this.http.post<UserAdmin>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateUserRequest): Observable<UserAdmin> {
    return this.http.put<UserAdmin>(`${this.baseUrl}/${id}`, payload);
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
