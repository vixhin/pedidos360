import {
  IPublicClientApplication,
  PublicClientApplication,
  InteractionType,
  BrowserCacheLocation,
  LogLevel,
} from '@azure/msal-browser';
import {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration,
  MSAL_GUARD_CONFIG,
  MSAL_INSTANCE,
  MSAL_INTERCEPTOR_CONFIG,
  MsalService,
  MsalGuard,
  MsalBroadcastService,
} from '@azure/msal-angular';
import { Provider } from '@angular/core';
import { AZURE_AD_CONFIG } from './config/auth.config';
import { API_CONFIG } from './config/api.config';

function msalInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: AZURE_AD_CONFIG.clientId,
      authority: `https://login.microsoftonline.com/${AZURE_AD_CONFIG.tenantId || 'common'}`,
      redirectUri: AZURE_AD_CONFIG.redirectUri,
      postLogoutRedirectUri: AZURE_AD_CONFIG.postLogoutRedirectUri,
    },
    cache: {
      cacheLocation: BrowserCacheLocation.LocalStorage,
      storeAuthStateInCookie: false,
    },
    system: {
      loggerOptions: {
        loggerCallback: () => {},
        logLevel: LogLevel.Warning,
        piiLoggingEnabled: false,
      },
    },
  });
}

function msalGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: {
      scopes: ['openid', 'profile', 'email'],
    },
  };
}

function msalInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string> | null>();
  if (AZURE_AD_CONFIG.apiScope) {
    protectedResourceMap.set(API_CONFIG.usuario, [AZURE_AD_CONFIG.apiScope]);
  }
  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap,
  };
}

/** Providers de MSAL para registrar en app.config.ts (bootstrapApplication). */
export const MSAL_PROVIDERS: Provider[] = [
  { provide: MSAL_INSTANCE, useFactory: msalInstanceFactory },
  { provide: MSAL_GUARD_CONFIG, useFactory: msalGuardConfigFactory },
  { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: msalInterceptorConfigFactory },
  MsalService,
  MsalGuard,
  MsalBroadcastService,
];
