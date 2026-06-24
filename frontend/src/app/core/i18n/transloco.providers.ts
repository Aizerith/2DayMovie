import {isDevMode, makeEnvironmentProviders} from '@angular/core';
import {provideTransloco, translocoConfig} from '@jsverse/transloco';
import {AppTranslocoLoader} from './transloco-loader';

export function provideBoilerplateI18n() {
  return makeEnvironmentProviders([
    provideTransloco({
      config: translocoConfig({
        availableLangs: ['fr', 'en'],
        defaultLang: 'fr',
        fallbackLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      }),
      loader: AppTranslocoLoader,
    })
  ]);
}
