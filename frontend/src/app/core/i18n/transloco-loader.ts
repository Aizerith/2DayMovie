import {Injectable} from '@angular/core';
import {TranslocoLoader} from '@jsverse/transloco';

@Injectable({providedIn: 'root'})
export class AppTranslocoLoader implements TranslocoLoader {
  getTranslation(lang: string) {
    switch (lang) {
      case 'fr':
        return import('./translations/fr').then(module => module.default);
      case 'en':
      default:
        return import('./translations/en').then(module => module.default);
    }
  }
}
