import {Component, inject, OnInit} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AuthService} from './core/services/auth.service';
import {I18nService} from './core/i18n/i18n.service';
import {RealtimeService} from './core/services/realtime.service';
import {ToastContainer} from './shared/ui/toast-container/toast-container';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainer],
  templateUrl: './app.html'
})
export class App implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);
  private readonly realtimeService = inject(RealtimeService);

  ngOnInit(): void {
    this.i18nService.bootstrap();
    this.authService.bootstrap();
  }
}
