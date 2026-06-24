import {Component, ElementRef, OnDestroy, ViewChild, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {Client, StompSubscription} from '@stomp/stompjs';
import {environment} from '../../../environments/environment';
import {PlaybackSyncMessage, WatchRoomAccessResponse} from '../../core/models/watch-room.model';
import {NotificationService} from '../../core/services/notification.service';
import {WatchRoomService} from '../../core/services/watch-room.service';

@Component({
  selector: 'app-watch',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './watch.html',
})
export class Watch implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly watchRoomService = inject(WatchRoomService);
  private readonly notificationService = inject(NotificationService);
  private readonly clientId = crypto.randomUUID();
  private client?: Client;
  private subscription?: StompSubscription;
  private applyingRemote = false;
  private lastSentAt = 0;

  @ViewChild('videoPlayer') videoPlayer?: ElementRef<HTMLVideoElement>;

  readonly shareCode = this.route.snapshot.paramMap.get('shareCode') ?? '';
  readonly pin = signal('');
  readonly room = signal<WatchRoomAccessResponse | null>(null);
  readonly loading = signal(false);
  readonly connected = signal(false);
  readonly subtitleSize = signal(100);
  readonly subtitleBackground = signal<'soft' | 'solid' | 'none'>('soft');
  readonly closeConfirmationOpen = signal(false);
  readonly closing = signal(false);

  unlock(): void {
    const pin = this.pin().trim();
    if (!/^\d{4}$/.test(pin)) {
      this.notificationService.warning('Entre le PIN a 4 chiffres.');
      return;
    }

    this.loading.set(true);
    this.watchRoomService.accessRoom(this.shareCode, pin).subscribe({
      next: room => {
        this.room.set(room);
        this.loading.set(false);
        window.setTimeout(() => {
          const video = this.videoPlayer?.nativeElement;
          if (video) {
            video.currentTime = room.playbackTimeSeconds;
          }
        });
        this.connect();
      },
      error: () => {
        this.loading.set(false);
        this.notificationService.error('PIN invalide ou salon indisponible.');
      }
    });
  }

  sendSync(event: string, force = false): void {
    const video = this.videoPlayer?.nativeElement;
    if (!video || !this.room() || this.applyingRemote) {
      return;
    }

    const now = Date.now();
    if (!force && now - this.lastSentAt < 1200) {
      return;
    }

    this.lastSentAt = now;
    const message: PlaybackSyncMessage = {
      pin: this.pin(),
      clientId: this.clientId,
      currentTime: video.currentTime,
      playing: !video.paused,
      event,
      sentAt: now
    };

    this.client?.publish({
      destination: `/app/rooms/${this.shareCode}/sync`,
      body: JSON.stringify(message)
    });
  }

  subtitleBackgroundColor(): string {
    switch (this.subtitleBackground()) {
      case 'solid':
        return 'rgba(0, 0, 0, 0.78)';
      case 'none':
        return 'transparent';
      default:
        return 'rgba(0, 0, 0, 0.42)';
    }
  }

  askCloseRoom(): void {
    this.closeConfirmationOpen.set(true);
  }

  cancelCloseRoom(): void {
    if (!this.closing()) {
      this.closeConfirmationOpen.set(false);
    }
  }

  closeRoom(): void {
    if (this.closing()) {
      return;
    }

    this.closing.set(true);
    this.watchRoomService.closeRoom(this.shareCode, this.pin()).subscribe({
      next: () => {
        this.notificationService.success('Salon cloture et fichiers supprimes.');
        void this.router.navigateByUrl('/');
      },
      error: () => {
        this.closing.set(false);
        this.notificationService.error('Impossible de cloturer le salon pour le moment.');
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    void this.client?.deactivate();
  }

  private connect(): void {
    if (this.client?.active) {
      return;
    }

    this.client = new Client({
      brokerURL: this.websocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
      onConnect: () => {
        this.connected.set(true);
        this.subscription = this.client?.subscribe(`/topic/rooms/${this.shareCode}`, message => {
          this.applyRemote(JSON.parse(message.body) as PlaybackSyncMessage);
        });
      },
      onWebSocketClose: () => this.connected.set(false),
      onWebSocketError: () => this.connected.set(false),
      onStompError: () => this.connected.set(false)
    });

    this.client.activate();
  }

  private applyRemote(message: PlaybackSyncMessage): void {
    if (message.clientId === this.clientId) {
      return;
    }

    const video = this.videoPlayer?.nativeElement;
    if (!video) {
      return;
    }

    this.applyingRemote = true;
    if (Math.abs(video.currentTime - message.currentTime) > 0.8) {
      video.currentTime = message.currentTime;
    }

    if (message.playing && video.paused) {
      void video.play();
    }

    if (!message.playing && !video.paused) {
      video.pause();
    }

    window.setTimeout(() => this.applyingRemote = false, 250);
  }

  private websocketUrl(): string {
    if (environment.wsUrl) {
      return environment.wsUrl;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }
}
