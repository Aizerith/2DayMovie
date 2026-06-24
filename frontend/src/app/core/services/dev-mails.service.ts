import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {DevMail} from '../models/dev-mail.model';

@Injectable({
  providedIn: 'root',
})
export class DevMailsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/dev-mails`;

  findAll(): Observable<DevMail[]> {
    return this.http.get<DevMail[]>(this.baseUrl);
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
