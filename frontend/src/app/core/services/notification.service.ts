import {Injectable, signal} from '@angular/core';

export type NotificationTone = 'info' | 'success' | 'warning' | 'error';

export interface NotificationItem {
  id: number;
  tone: NotificationTone;
  message: string;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly durationMs = 4000;
  private readonly notificationsSignal = signal<NotificationItem[]>([]);
  private nextId = 1;

  readonly notifications = this.notificationsSignal.asReadonly();

  show(message: string, tone: NotificationTone = 'info'): void {
    const id = this.nextId++;
    this.notificationsSignal.update(items => [...items, {id, tone, message}]);

    window.setTimeout(() => this.dismiss(id), this.durationMs);
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  warning(message: string): void {
    this.show(message, 'warning');
  }

  info(message: string): void {
    this.show(message, 'info');
  }

  dismiss(id: number): void {
    this.notificationsSignal.update(items => items.filter(item => item.id !== id));
  }
}
