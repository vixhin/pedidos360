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
import { AZURE_AD_CONFIG, isAzureAdConfigured } from './config/auth.config';
import { API_CONFIG } from './config/api.config';

/**
 * Factory para PublicClientApplication.
 * Si Azure no está configurado (placeholders), usa 'common' como authority
 * para evitar errores de arranque.
 */
function msalInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId:              AZURE_AD_CONFIG.clientId || '00000000-0000-0000-0000-000000000000',
      authority:             `https://login.microsoftonline.com/${AZURE_AD_CONFIG.tenantId || 'common'}`,
      redirectUri:           AZURE_AD_CONFIG.redirectUri,
      postLogoutRedirectUri: AZURE_AD_CONFIG.postLogoutRedirectUri,
    },
    cache: {
      cacheLocation:         BrowserCacheLocation.LocalStorage,
      storeAuthStateInCookie: false,
    },
    system: {
      loggerOptions: {
        // No loggear tokens completos. Solo warnings+
        loggerCallback: (level, message, containsPii) => {
          if (containsPii) return;
          if (level <= LogLevel.Warning) {
            console.warn('[MSAL]', message);
          }
        },
        logLevel:         LogLevel.Warning,
        piiLoggingEnabled: false,
      },
    },
  });
}

/**
 * Factory para MsalGuardConfiguration.
 * Incluye el apiScope para que MSAL solicite el Access Token correcto
 * (no solo el ID Token de OpenID Connect).
 */
function msalGuardConfigFactory(): MsalGuardConfiguration {
  const scopes: string[] = ['openid', 'profile', 'email'];

  // Agregar el scope de la API solo cuando está configurado
  if (isAzureAdConfigured() && AZURE_AD_CONFIG.apiScope) {
    scopes.push(AZURE_AD_CONFIG.apiScope);
  }

  return {
    interactionType: InteractionType.Redirect,
    authRequest: {
      scopes,
    },
    loginFailedRoute: '/login',
  };
}

/**
 * Factory para MsalInterceptorConfiguration.
 *
 * Mapea las URLs que requieren el Bearer token:
 * - BFF (8090): recibe el Access Token de Azure AD
 * - Microservicios directos: null (sin token, compatibilidad)
 *
 * El interceptor adjunta automáticamente:
 *   Authorization: Bearer <ACCESS_TOKEN>
 */
function msalInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string> | null>();

  const apiScope = isAzureAdConfigured() ? AZURE_AD_CONFIG.apiScope : null;

  // BFF — principal punto de entrada con autenticación
  if (apiScope) {
    protectedResourceMap.set(API_CONFIG.bff, [apiScope]);
  }

  // Microservicios directos — sin token para compatibilidad con login local
  // (null = ignorar, no adjuntar token)
  protectedResourceMap.set(API_CONFIG.usuario,      null);
  protectedResourceMap.set(API_CONFIG.productos,    null);
  protectedResourceMap.set(API_CONFIG.pedidos,      null);
  protectedResourceMap.set(API_CONFIG.carrito,      null);
  protectedResourceMap.set(API_CONFIG.notificacion, null);
  protectedResourceMap.set(API_CONFIG.analitica,    null);

  return {
    interactionType:     InteractionType.Redirect,
    protectedResourceMap,
  };
}

/** Providers de MSAL para registrar en app.config.ts (bootstrapApplication). */
export const MSAL_PROVIDERS: Provider[] = [
  { provide: MSAL_INSTANCE,          useFactory: msalInstanceFactory },
  { provide: MSAL_GUARD_CONFIG,      useFactory: msalGuardConfigFactory },
  { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: msalInterceptorConfigFactory },
  MsalService,
  MsalGuard,
  MsalBroadcastService,
];
