import {Component, computed, inject} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../core/services/auth.service';
import {AppLanguage, I18nService} from '../../../core/i18n/i18n.service';
import {ThemeService} from '../../../core/services/theme.service';

@Component({
  selector: 'app-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslocoPipe
  ],
  templateUrl: './app-shell.html',
})
export class AppShell {
  private readonly authService = inject(AuthService);
  private readonly themeService = inject(ThemeService);
  private readonly i18nService = inject(I18nService);

  readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.authService.getCurrentUserRole() === 'ADMIN');
  readonly isManager = computed(() => this.authService.getCurrentUserRole() === 'MANAGER');
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
