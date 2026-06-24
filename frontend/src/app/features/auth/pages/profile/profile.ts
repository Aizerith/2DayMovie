import {Component, computed, inject, signal} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../../core/services/auth.service';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {HealthService} from '../../../../core/services/health.service';
import {EmptyState} from '../../../../shared/ui/empty-state/empty-state';
import {DataList, DataListColumn, DataListRow} from '../../../../shared/ui/data-list/data-list';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';
import {StatPanel} from '../../../../shared/ui/stat-panel/stat-panel';

@Component({
  selector: 'app-profile',
  imports: [
    RouterLink,
    TranslocoPipe,
    EmptyState,
    DataList,
    NoticeBanner,
    PageIntro,
    SectionCard,
    StatPanel
  ],
  templateUrl: './profile.html',
})
export class Profile {
  private readonly authService = inject(AuthService);
  private readonly healthService = inject(HealthService);
  private readonly i18nService = inject(I18nService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly healthMessage = signal('');
  readonly errorMessage = signal('');
  readonly sessionColumns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'cle', label: this.i18nService.t('profile.rows.key')},
      {key: 'valeur', label: this.i18nService.t('profile.rows.value')}
    ];
  });

  testProtectedCall(): void {
    this.healthService.getHealth().subscribe({
      next: (value) => {
        this.healthMessage.set(value);
        this.errorMessage.set('');
      },
      error: () => this.errorMessage.set(this.i18nService.t('profile.apiUnexpected'))
    });
  }

  refreshSession(): void {
    this.authService.refreshSession().subscribe({
      next: () => this.errorMessage.set(this.i18nService.t('profile.sessionRefreshed')),
      error: () => this.errorMessage.set(this.i18nService.t('profile.sessionRefreshError'))
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login'])
    });
  }

  sessionRows(user: {email: string; role: string}): DataListRow[] {
    this.i18nService.activeLang();

    return [
      {cle: this.i18nService.t('profile.rows.theme'), valeur: localStorage.getItem('bp_theme') ?? 'boilerplate'},
      {cle: this.i18nService.t('profile.rows.user'), valeur: user.email},
      {cle: this.i18nService.t('profile.rows.role'), valeur: user.role}
    ];
  }
}
