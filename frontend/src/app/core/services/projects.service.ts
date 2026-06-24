import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse, PageRequest} from '../models/pagination.model';
import {CreateProjectRequest, Project, UpdateProjectRequest} from '../models/project.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/projects`;

  findPage(request: PageRequest): Observable<PagedResponse<Project>> {
    return this.http.get<PagedResponse<Project>>(this.baseUrl, {
      params: this.toParams(request)
    });
  }

  findAll(): Observable<Project[]> {
    return this.findPage({page: 0, size: 100, sort: 'updatedAt,desc'}).pipe(
      map((response) => response.items)
    );
  }

  create(payload: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/${id}`, payload);
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
