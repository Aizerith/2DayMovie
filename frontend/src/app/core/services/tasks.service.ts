import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse, PageRequest} from '../models/pagination.model';
import {CreateTaskRequest, Task, UpdateTaskRequest} from '../models/task.model';

@Injectable({
  providedIn: 'root',
})
export class TasksService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tasks`;

  findPage(request: PageRequest, projectId?: number | null): Observable<PagedResponse<Task>> {
    let params = this.toParams(request);

    if (projectId) {
      params = params.set('projectId', String(projectId));
    }

    return this.http.get<PagedResponse<Task>>(this.baseUrl, {params});
  }

  findAll(projectId?: number | null): Observable<Task[]> {
    return this.findPage({page: 0, size: 100, sort: 'updatedAt,desc'}, projectId).pipe(
      map((response) => response.items)
    );
  }

  create(payload: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`${this.baseUrl}/${id}`, payload);
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
