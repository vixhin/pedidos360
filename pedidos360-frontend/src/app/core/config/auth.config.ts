/**
 * Configuración de Microsoft Entra ID (Azure AD) para el flujo OIDC
 * "Authorization Code + PKCE" vía MSAL.
 *
 * Reemplaza estos 3 valores una vez creado el tenant y la aplicación en
 * portal.azure.com > Microsoft Entra ID > Registros de aplicaciones:
 *  - clientId: "Application (client) ID" del app registration.
 *  - tenantId: "Directory (tenant) ID" del tenant.
 *  - redirectUri: debe coincidir EXACTO con una "Redirect URI" (tipo SPA)
 *    configurada en el app registration.
 */
export const AZURE_AD_CONFIG = {
  clientId: '',
  tenantId: '',
  redirectUri: 'http://localhost:4200',
  postLogoutRedirectUri: 'http://localhost:4200',
  // Scope expuesto por el API Manager / backend, ej: "api://<clientId>/access_as_user".
  apiScope: '',
};

export const isAzureAdConfigured = (): boolean =>
  AZURE_AD_CONFIG.clientId.length > 0 && AZURE_AD_CONFIG.tenantId.length > 0;
