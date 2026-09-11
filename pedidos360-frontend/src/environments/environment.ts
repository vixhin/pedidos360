/**
 * Environment de DESARROLLO local.
 *
 * Pasos para configurar Microsoft Entra ID:
 * 1. Ve a portal.azure.com > Microsoft Entra ID > Registros de aplicaciones.
 * 2. Crea (o usa) el App Registration de la SPA (Frontend):
 *    - Copia el "Application (client) ID" → azureClientId
 *    - Copia el "Directory (tenant) ID"  → azureTenantId
 *    - Tipo de plataforma: SPA, Redirect URI: http://localhost:4200
 * 3. Crea (o usa) el App Registration de la API (BFF):
 *    - Copia su "Application (client) ID" → azureApiClientId
 *    - Expone un scope: "access_as_user"
 * 4. Reemplaza los valores de PLACEHOLDER_ abajo.
 * 5. En el App Registration SPA, concede permiso a ese scope.
 */
export const environment = {
  production: false,

  // ──────────────────────────────────────────────
  //  Microsoft Entra ID (Azure AD)
  //  Reemplaza PLACEHOLDER_* con los valores reales
  // ──────────────────────────────────────────────
  azure: {
    /** Application (client) ID del App Registration SPA (Pedidos360-SPA) */
    clientId: '6c20ed27-9d26-4615-9cc8-c7aaef529ffb',

    /** Directory (tenant) ID */
    tenantId: 'a50f6528-499a-4d94-bcad-ed9b200f7c7b',

    /** Application (client) ID del App Registration de la API/BFF (Pedidos360-API) */
    apiClientId: '5febc8e2-ee14-4452-8914-7d237eb5a6f5',

    /** Scope expuesto por la API: api://<apiClientId>/access_as_user */
    get apiScope(): string {
      return `api://${this.apiClientId}/access_as_user`;
    },

    /** Audience esperada en el Access Token (igual al apiClientId) */
    get apiAudience(): string {
      return `api://${this.apiClientId}`;
    },

    redirectUri: 'http://localhost:4200',
    postLogoutRedirectUri: 'http://localhost:4200',
  },

  // ──────────────────────────────────────────────
  //  URLs de los servicios
  // ──────────────────────────────────────────────
  api: {
    bff: 'http://localhost:8090/api/bff',
    usuario: 'http://localhost:8081/api',
    productos: 'http://localhost:8085/api',
    pedidos: 'http://localhost:8082/api',
    carrito: 'http://localhost:8083/api',
    notificacion: 'http://localhost:8086/api',
    analitica: 'http://localhost:8084/api',
  },

  /**
   * Cuando true, los usuarios autenticados con Microsoft Entra ID consumen los
   * datos a través del BFF (que valida el JWT de Azure). Los usuarios con
   * cuenta de BD y los anónimos siguen llamando directo a los microservicios.
   * La decisión por-recurso la toma BackendUrlService.
   */
  useBff: true,
};
