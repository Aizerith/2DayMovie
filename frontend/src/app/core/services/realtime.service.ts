import {effect, inject, Injectable, NgZone, signal} from '@angular/core';
import {Client, IMessage, StompSubscription} from '@stomp/stompjs';
import {Observable, Subject} from 'rxjs';
import {environment} from '../../../environments/environment';
import {RealtimeNotification} from '../models/realtime.model';
import {AuthService} from './auth.service';
import {NotificationService} from './notification.service';

@Injectable({
  providedIn: 'root',
})
export class RealtimeService {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly zone = inject(NgZone);
  private readonly notificationsSubject = new Subject<RealtimeNotification>();
  private client?: Client;
  private notificationSubscription?: StompSubscription;

  readonly connected = signal(false);
  readonly notifications$: Observable<RealtimeNotification> = this.notificationsSubject.asObservable();

  constructor() {
    effect(() => {
      if (this.authService.currentUser()) {
        this.connect();
        return;
      }

      this.disconnect();
    });
  }

  connect(): void {
    if (this.client?.active) {
      return;
    }

    this.client = new Client({
      brokerURL: this.websocketUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
      beforeConnect: () => {
        const accessToken = this.authService.getAccessToken();

        if (!accessToken || !this.authService.currentUser()) {
          throw new Error('Realtime connection skipped without an authenticated session.');
        }

        this.client!.connectHeaders = {
          Authorization: `Bearer ${accessToken}`,
        };
      },
      onConnect: () => {
        this.zone.run(() => {
          this.connected.set(true);
          this.notificationSubscription = this.client?.subscribe(
            '/user/queue/notifications',
            (message) => this.handleNotification(message)
          );
        });
      },
      onStompError: () => this.markDisconnected(),
      onWebSocketClose: () => this.markDisconnected(),
      onWebSocketError: () => this.markDisconnected(),
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.connected()) {
      this.notificationSubscription?.unsubscribe();
    }

    this.notificationSubscription = undefined;
    this.connected.set(false);

    if (!this.client?.active) {
      this.client = undefined;
      return;
    }

    void this.client.deactivate();
    this.client = undefined;
  }

  private handleNotification(message: IMessage): void {
    try {
      const notification = JSON.parse(message.body) as RealtimeNotification;

      this.zone.run(() => {
        this.notificationsSubject.next(notification);
        this.notificationService.info(notification.message);
      });
    } catch {
      this.notificationService.warning('Realtime notification ignored.');
    }
  }

  private markDisconnected(): void {
    this.zone.run(() => this.connected.set(false));
  }

  private websocketUrl(): string {
    if (environment.wsUrl) {
      return environment.wsUrl;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }
}
