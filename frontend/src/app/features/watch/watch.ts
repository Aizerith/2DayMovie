import {Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {Client, StompSubscription} from '@stomp/stompjs';
import {Subscription} from 'rxjs';
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
export class Watch implements OnDestroy, OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly watchRoomService = inject(WatchRoomService);
  private readonly notificationService = inject(NotificationService);
  private readonly clientId = crypto.randomUUID();
  private client?: Client;
  private subscription?: StompSubscription;
  private routeSubscription?: Subscription;
  private applyingRemote = false;
  private lastSentAt = 0;

  @ViewChild('videoPlayer') videoPlayer?: ElementRef<HTMLVideoElement>;
  @ViewChild('externalAudio') externalAudio?: ElementRef<HTMLAudioElement>;

  readonly shareCode = signal(this.route.snapshot.paramMap.get('shareCode') ?? '');
  readonly pin = signal('');
  readonly room = signal<WatchRoomAccessResponse | null>(null);
  readonly loading = signal(false);
  readonly connected = signal(false);
  readonly subtitleSize = signal(100);
  readonly subtitleBackground = signal<'soft' | 'solid' | 'none'>('soft');
  readonly selectedAudioTrackUrl = signal<string | null>(null);
  readonly closeConfirmationOpen = signal(false);
  readonly closing = signal(false);
  readonly roomMessage = signal<string | null>(null);

  unlock(): void {
    const pin = this.pin().trim();
    if (!/^\d{4}$/.test(pin)) {
      this.notificationService.warning('Entre le PIN a 4 chiffres.');
      return;
    }

    this.loading.set(true);
    this.watchRoomService.accessRoom(this.shareCode(), pin).subscribe({
      next: room => {
        this.roomMessage.set(null);
        this.room.set(room);
        this.selectedAudioTrackUrl.set(room.audioTracks[0]?.url ?? null);
        this.loading.set(false);
        window.setTimeout(() => {
          const video = this.videoPlayer?.nativeElement;
          if (video) {
            video.currentTime = room.playbackTimeSeconds;
            this.syncExternalAudio();
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
      destination: `/app/rooms/${this.shareCode()}/sync`,
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

  handleVideoPlay(): void {
    this.syncExternalAudio(true);
    this.sendSync('play', true);
  }

  handleVideoPause(): void {
    this.externalAudio?.nativeElement.pause();
    this.sendSync('pause', true);
  }

  handleVideoSeeked(): void {
    this.syncExternalAudio();
    this.sendSync('seek', true);
  }

  handleVideoTimeUpdate(): void {
    this.syncExternalAudio();
    this.sendSync('time');
  }

  handleVideoVolumeChange(): void {
    this.syncExternalAudio();
  }

  selectAudioTrack(url: string): void {
    this.selectedAudioTrackUrl.set(url || null);
    window.setTimeout(() => this.syncExternalAudio(true));
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
    this.watchRoomService.closeRoom(this.shareCode(), this.pin()).subscribe({
      next: () => {
        this.stopRoomPlayback();
        this.notificationService.success('Salon cloture et fichiers supprimes.');
        void this.router.navigateByUrl('/', {replaceUrl: true});
      },
      error: error => {
        if (error?.status === 400 || error?.status === 404) {
          this.showClosedRoom('Salon introuvable ou deja ferme.');
          return;
        }

        this.closing.set(false);
        this.notificationService.error('Impossible de cloturer le salon pour le moment.');
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    this.subscription?.unsubscribe();
    void this.client?.deactivate();
  }

  ngOnInit(): void {
    this.routeSubscription = this.route.paramMap.subscribe(params => {
      const nextShareCode = params.get('shareCode') ?? '';
      if (nextShareCode === this.shareCode()) {
        return;
      }

      this.stopRoomPlayback();
      this.pin.set('');
      this.loading.set(false);
      this.roomMessage.set(null);
      this.shareCode.set(nextShareCode);
    });
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
        this.subscription = this.client?.subscribe(`/topic/rooms/${this.shareCode()}`, message => {
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
    if (message.event === 'closed') {
      this.showClosedRoom('Le salon a ete cloture.');
      return;
    }

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

    this.syncExternalAudio(message.playing);

    window.setTimeout(() => this.applyingRemote = false, 250);
  }

  private showClosedRoom(message: string): void {
    this.stopRoomPlayback();
    this.roomMessage.set(message);
    this.notificationService.warning(message);
  }

  private stopRoomPlayback(): void {
    const video = this.videoPlayer?.nativeElement;
    const audio = this.externalAudio?.nativeElement;

    video?.pause();
    audio?.pause();
    if (video) {
      video.removeAttribute('src');
      video.load();
    }
    if (audio) {
      audio.removeAttribute('src');
      audio.load();
    }

    this.subscription?.unsubscribe();
    this.subscription = undefined;
    void this.client?.deactivate();
    this.client = undefined;
    this.room.set(null);
    this.selectedAudioTrackUrl.set(null);
    this.closeConfirmationOpen.set(false);
    this.closing.set(false);
  }

  private syncExternalAudio(playWhenVideoPlays = false): void {
    const video = this.videoPlayer?.nativeElement;
    const audio = this.externalAudio?.nativeElement;
    const hasExternalAudio = !!this.selectedAudioTrackUrl();

    if (video) {
      video.muted = hasExternalAudio;
    }

    if (!video || !audio || !hasExternalAudio) {
      return;
    }

    if (Math.abs(audio.currentTime - video.currentTime) > 0.35) {
      audio.currentTime = video.currentTime;
    }

    audio.volume = video.volume;
    audio.muted = false;

    if (playWhenVideoPlays || !video.paused) {
      void audio.play().catch(() => undefined);
      return;
    }

    audio.pause();
  }

  private websocketUrl(): string {
    if (environment.wsUrl) {
      return environment.wsUrl;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }
}
