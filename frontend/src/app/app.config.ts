import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {httpInterceptor} from './core/interceptors/http.interceptor';
import {provideBoilerplateI18n} from './core/i18n/transloco.providers';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideBoilerplateI18n(),
    provideHttpClient(
      withInterceptors([httpInterceptor])
    )
  ]
};
