import {Component, computed, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../core/services/auth.service';
import {I18nService} from '../../core/i18n/i18n.service';
import {HealthService} from '../../core/services/health.service';
import {EmptyState} from '../../shared/ui/empty-state/empty-state';
import {DataList, DataListColumn, DataListRow} from '../../shared/ui/data-list/data-list';
import {NoticeBanner} from '../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../shared/ui/section-card/section-card';
import {StatPanel, StatPanelItem} from '../../shared/ui/stat-panel/stat-panel';

@Component({
  selector: 'app-home',
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
  templateUrl: './home.html',
})
export class Home {
  private readonly healthService = inject(HealthService);
  private readonly i18nService = inject(I18nService);
  readonly authService = inject(AuthService);
  readonly currentUser = this.authService.currentUser;
  readonly message = signal('');
  readonly demoCredentials = computed<StatPanelItem[]>(() => {
    this.i18nService.activeLang();

    return [
      {label: this.i18nService.t('home.credentials.email'), value: 'admin@local.dev'},
      {label: this.i18nService.t('home.credentials.password'), value: 'Admin123!'}
    ];
  });
  readonly foundationColumns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'bloc', label: this.i18nService.t('home.foundationColumns.block')},
      {key: 'etat', label: this.i18nService.t('home.foundationColumns.state')},
      {key: 'usage', label: this.i18nService.t('home.foundationColumns.usage')}
    ];
  });
  readonly foundationRows = computed<DataListRow[]>(() => {
    this.i18nService.activeLang();

    return [
      {
        bloc: this.i18nService.t('home.foundationRows.auth.block'),
        etat: this.i18nService.t('home.foundationRows.auth.state'),
        usage: this.i18nService.t('home.foundationRows.auth.usage')
      },
      {
        bloc: this.i18nService.t('home.foundationRows.layouts.block'),
        etat: this.i18nService.t('home.foundationRows.layouts.state'),
        usage: this.i18nService.t('home.foundationRows.layouts.usage')
      },
      {
        bloc: this.i18nService.t('home.foundationRows.sharedUi.block'),
        etat: this.i18nService.t('home.foundationRows.sharedUi.state'),
        usage: this.i18nService.t('home.foundationRows.sharedUi.usage')
      }
    ];
  });

  testCall(): void {
    this.healthService.getHealth().subscribe(value => this.message.set(value));
  }
}
