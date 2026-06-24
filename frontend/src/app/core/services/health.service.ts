import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {environment} from '../../../environments/environment';

type HealthResponse = {
  status: string;
};

@Injectable({
  providedIn: 'root',
})
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  getHealth(): Observable<string> {
    return this.http.get<HealthResponse>(`${this.baseUrl}/health`).pipe(
      map((response) => response.status)
    );
  }
}
