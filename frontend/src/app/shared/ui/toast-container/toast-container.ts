import {Component, inject} from '@angular/core';
import {NotificationService} from '../../../core/services/notification.service';

@Component({
  selector: 'app-toast-container',
  templateUrl: './toast-container.html',
})
export class ToastContainer {
  private readonly notificationService = inject(NotificationService);

  readonly notifications = this.notificationService.notifications;

  dismiss(id: number): void {
    this.notificationService.dismiss(id);
  }
}
