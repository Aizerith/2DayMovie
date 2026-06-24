import {computed, inject, Injectable, signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import en from './translations/en';
import fr from './translations/fr';

export type AppLanguage = 'fr' | 'en';

@Injectable({
  providedIn: 'root',
})
export class I18nService {
  private readonly storageKey = 'bp_lang';
  private readonly translocoService = inject(TranslocoService);
  private readonly activeLanguageSignal = signal<AppLanguage>('fr');
  private translationsRegistered = false;

  readonly activeLang = this.activeLanguageSignal.asReadonly();
  readonly locale = computed(() => this.activeLang() === 'fr' ? 'fr-FR' : 'en-US');

  constructor() {
    this.registerTranslations();
    this.translocoService.setActiveLang('fr');
  }

  bootstrap(): void {
    const storedLanguage = localStorage.getItem(this.storageKey);
    const browserLanguage = navigator.language.toLowerCase().startsWith('fr') ? 'fr' : 'en';
    const initialLanguage = this.normalizeLanguage(storedLanguage ?? browserLanguage);

    this.setLang(initialLanguage);
  }

  setLang(language: AppLanguage): void {
    this.registerTranslations();
    this.activeLanguageSignal.set(language);
    this.translocoService.setActiveLang(language);
    localStorage.setItem(this.storageKey, language);
    document.documentElement.lang = language;
  }

  t(key: string, params?: Record<string, string | number>): string {
    return this.translocoService.translate(key, params);
  }

  private registerTranslations(): void {
    if (this.translationsRegistered) {
      return;
    }

    this.translocoService.setTranslation(fr, 'fr', {merge: false});
    this.translocoService.setTranslation(en, 'en', {merge: false});
    this.translationsRegistered = true;
  }

  private normalizeLanguage(value: string): AppLanguage {
    return value === 'en' ? 'en' : 'fr';
  }
}
