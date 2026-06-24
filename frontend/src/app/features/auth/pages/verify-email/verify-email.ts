import {Component, computed, inject, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {NotificationService} from '../../../../core/services/notification.service';
import {AuthService} from '../../../../core/services/auth.service';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';

@Component({
  selector: 'app-verify-email',
  imports: [
    RouterLink,
    TranslocoPipe,
    NoticeBanner,
    PageIntro,
    SectionCard
  ],
  templateUrl: './verify-email.html',
})
export class VerifyEmail {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);
  private readonly notificationService = inject(NotificationService);

  readonly token = signal(this.route.snapshot.queryParamMap.get('token') ?? '');
  readonly loading = signal(false);
  readonly success = signal(false);
  readonly errorMessage = signal('');
  readonly tokenMissing = computed(() => !this.token().trim().length);

  constructor() {
    if (this.tokenMissing()) {
      this.errorMessage.set(this.i18nService.t('verifyEmail.missingToken'));
      return;
    }

    this.loading.set(true);
    this.authService.verifyEmail({token: this.token()}).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        this.notificationService.success(this.i18nService.t('verifyEmail.success'));
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(error?.error?.message ?? this.i18nService.t('verifyEmail.error'));
      }
    });
  }
}
