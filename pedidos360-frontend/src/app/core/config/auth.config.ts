/**
 * Configuración de Microsoft Entra ID (Azure AD) para MSAL.
 *
 * Los valores se leen desde src/environments/environment.ts.
 *
 * Para configurar:
 * 1. Edita src/environments/environment.ts con los valores reales de tu tenant.
 * 2. NUNCA hardcodees clientId/tenantId/apiScope directamente aquí.
 *
 * Estructura esperada en environment.ts:
 *   azure: {
 *     clientId:    'Application (client) ID del App Registration SPA'
 *     tenantId:    'Directory (tenant) ID'
 *     apiClientId: 'Application (client) ID de la API / BFF'
 *     apiScope:    'api://<apiClientId>/access_as_user'   (computed getter)
 *     apiAudience: 'api://<apiClientId>'                  (computed getter)
 *     redirectUri: 'http://localhost:4200'
 *     postLogoutRedirectUri: 'http://localhost:4200'
 *   }
 */
import { environment } from '../../../environments/environment';

export const AZURE_AD_CONFIG = {
  clientId:              environment.azure.clientId,
  tenantId:              environment.azure.tenantId,
  apiClientId:           environment.azure.apiClientId,
  apiScope:              environment.azure.apiScope,
  apiAudience:           environment.azure.apiAudience,
  redirectUri:           environment.azure.redirectUri,
  postLogoutRedirectUri: environment.azure.postLogoutRedirectUri,
};

/**
 * Devuelve true únicamente cuando las credenciales reales de Azure
 * han sido configuradas (no son los placeholders vacíos).
 */
export const isAzureAdConfigured = (): boolean =>
  AZURE_AD_CONFIG.clientId.length > 0 &&
  !AZURE_AD_CONFIG.clientId.startsWith('PLACEHOLDER') &&
  AZURE_AD_CONFIG.tenantId.length > 0 &&
  !AZURE_AD_CONFIG.tenantId.startsWith('PLACEHOLDER');
