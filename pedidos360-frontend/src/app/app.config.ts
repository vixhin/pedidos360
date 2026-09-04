import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { HTTP_INTERCEPTORS, provideHttpClient, withFetch, withInterceptorsFromDi } from '@angular/common/http';
import { IPublicClientApplication } from '@azure/msal-browser';
import { MSAL_INSTANCE, MsalInterceptor } from '@azure/msal-angular';

import { routes } from './app.routes';
import { MSAL_PROVIDERS } from './core/msal.providers';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptorsFromDi()),
    ...MSAL_PROVIDERS,
    { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },
    provideAppInitializer(() =>
      (inject(MSAL_INSTANCE) as unknown as IPublicClientApplication).initialize()
    ),
  ]
};
