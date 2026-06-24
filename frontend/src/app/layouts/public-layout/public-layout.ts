import {Component, computed, inject} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AppLanguage, I18nService} from '../../core/i18n/i18n.service';
import {ThemeService} from '../../core/services/theme.service';

@Component({
  selector: 'app-public-layout',
  imports: [
    RouterLink,
    RouterOutlet,
    TranslocoPipe
  ],
  templateUrl: './public-layout.html',
})
export class PublicLayout {
  private readonly themeService = inject(ThemeService);
  private readonly i18nService = inject(I18nService);
  readonly theme = this.themeService.theme;
  readonly activeLang = this.i18nService.activeLang;
  readonly themeLabel = computed(() => {
    this.activeLang();

    return this.theme() === 'boilerplate'
      ? this.i18nService.t('common.themeLight')
      : this.i18nService.t('common.themeDark');
  });

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  setLanguage(language: AppLanguage): void {
    this.i18nService.setLang(language);
  }
}
