import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {
  CreateWatchRoomRequest,
  CreateWatchRoomResponse,
  WatchRoomAccessResponse
} from '../models/watch-room.model';

export type {CreateWatchRoomResponse};

@Injectable({
  providedIn: 'root'
})
export class WatchRoomService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/rooms`;

  createRoom(request: CreateWatchRoomRequest) {
    return this.http.post<CreateWatchRoomResponse>(this.apiUrl, request);
  }

  completeRoom(shareCode: string, pin: string) {
    return this.http.post<WatchRoomAccessResponse>(`${this.apiUrl}/${shareCode}/complete`, {pin});
  }

  accessRoom(shareCode: string, pin: string) {
    return this.http.post<WatchRoomAccessResponse>(`${this.apiUrl}/${shareCode}/access`, {pin});
  }

  closeRoom(shareCode: string, pin: string) {
    return this.http.post<{shareCode: string; closed: boolean}>(`${this.apiUrl}/${shareCode}/close`, {pin});
  }
}
