import {HttpClient, HttpErrorResponse, HttpEventType} from '@angular/common/http';
import {Component, computed, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {Observable, concat, finalize, last, switchMap, tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {NotificationService} from '../../core/services/notification.service';
import {CreateWatchRoomResponse, WatchRoomService} from '../../core/services/watch-room.service';

@Component({
  selector: 'app-home',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './home.html',
})
export class Home {
  private readonly watchRoomService = inject(WatchRoomService);
  private readonly http = inject(HttpClient);
  private readonly notificationService = inject(NotificationService);

  readonly title = signal('');
  readonly pin = signal('');
  readonly videoFile = signal<File | null>(null);
  readonly subtitleFile = signal<File | null>(null);
  readonly generatedPin = signal('');
  readonly creating = signal(false);
  readonly uploadProgress = signal(0);
  readonly createdRoom = signal<CreateWatchRoomResponse | null>(null);
  readonly shareLink = computed(() => this.createdRoom()?.shareUrl ?? '');

  selectVideo(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.videoFile.set(input.files?.[0] ?? null);
  }

  selectSubtitle(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.subtitleFile.set(input.files?.[0] ?? null);
  }

  createRoom(): void {
    const video = this.videoFile();
    const subtitle = this.subtitleFile();

    if (!video) {
      this.notificationService.warning('Choisis une video avant de creer le salon.');
      return;
    }

    const pin = this.generatePin();
    this.pin.set(pin);
    this.generatedPin.set(pin);
    this.creating.set(true);
    this.uploadProgress.set(0);
    this.createdRoom.set(null);

    this.watchRoomService.createRoom({
      title: this.title().trim() || video.name,
      pin,
      video: this.toAsset(video),
      subtitle: subtitle ? this.toAsset(subtitle, 'text/vtt') : null
    }).pipe(
      switchMap(room => this.uploadAssets(room, video, subtitle).pipe(
        last(),
        switchMap(() => this.watchRoomService.completeRoom(room.shareCode, pin)),
        tap(() => {
          this.createdRoom.set(this.withFrontendShareUrl(room));
          this.uploadProgress.set(100);
          this.notificationService.success('Salon pret. Le lien peut etre partage.');
        })
      )),
      finalize(() => this.creating.set(false))
    ).subscribe({
      error: error => this.notificationService.error(this.errorMessage(error))
    });
  }

  copyLink(): void {
    const link = this.shareLink();
    if (!link) {
      return;
    }

    void navigator.clipboard.writeText(link);
    this.notificationService.success('Lien copie.');
  }

  private uploadAssets(room: CreateWatchRoomResponse, video: File, subtitle: File | null): Observable<unknown> {
    const uploads = [
      {url: room.videoUpload.url, file: video}
    ];

    if (subtitle && room.subtitleUpload) {
      uploads.push({url: room.subtitleUpload.url, file: subtitle});
    }

    let completed = 0;
    return concat(...uploads.map(upload => this.http.put(upload.url, upload.file, {
      reportProgress: true,
      observe: 'events',
      headers: {'Content-Type': upload.file.type || 'application/octet-stream'}
    }).pipe(
      tap(event => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          const base = (completed / uploads.length) * 100;
          const current = (event.loaded / event.total) * (100 / uploads.length);
          this.uploadProgress.set(Math.min(99, Math.round(base + current)));
        }

        if (event.type === HttpEventType.Response) {
          completed += 1;
          this.uploadProgress.set(Math.round((completed / uploads.length) * 100));
        }
      })
    )));
  }

  private toAsset(file: File, fallbackType = 'application/octet-stream') {
    return {
      originalFilename: file.name,
      contentType: file.type || fallbackType,
      sizeBytes: file.size
    };
  }

  private withFrontendShareUrl(room: CreateWatchRoomResponse): CreateWatchRoomResponse {
    const origin = environment.frontendUrl || window.location.origin;
    return {
      ...room,
      shareUrl: `${origin}/watch/${room.shareCode}`
    };
  }

  private generatePin(): string {
    return Math.floor(1000 + Math.random() * 9000).toString();
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error?.message === 'string') {
        return error.error.message;
      }

      if (error.status === 0) {
        return 'Upload impossible: verifie la connexion au serveur ou la configuration proxy/MinIO.';
      }

      if (error.status === 413) {
        return 'La video est trop volumineuse pour la configuration actuelle du proxy.';
      }
    }

    return 'Impossible de creer le salon pour le moment.';
  }
}
