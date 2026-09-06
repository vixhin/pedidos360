/**
 * Environment de PRODUCCIÓN.
 * Los valores son inyectados en tiempo de build mediante variables de entorno
 * o mediante un archivo de reemplazo en la pipeline CI/CD.
 */
export const environment = {
  production: true,

  azure: {
    clientId: process.env['AZURE_SPA_CLIENT_ID'] || 'PLACEHOLDER_SPA_CLIENT_ID',
    tenantId: process.env['AZURE_TENANT_ID'] || 'PLACEHOLDER_TENANT_ID',
    apiClientId: process.env['AZURE_API_CLIENT_ID'] || 'PLACEHOLDER_API_CLIENT_ID',

    get apiScope(): string {
      return `api://${this.apiClientId}/access_as_user`;
    },
    get apiAudience(): string {
      return `api://${this.apiClientId}`;
    },

    redirectUri: process.env['FRONTEND_URL'] || 'https://pedidos360.cl',
    postLogoutRedirectUri: process.env['FRONTEND_URL'] || 'https://pedidos360.cl',
  },

  api: {
    bff: process.env['BFF_URL'] || 'https://api.pedidos360.cl/api/bff',
    usuario: 'http://usuario:8081/api',
    productos: 'http://productos:8085/api',
    pedidos: 'http://pedidos:8082/api',
    carrito: 'http://carrito:8083/api',
    notificacion: 'http://notificacion:8086/api',
    analitica: 'http://analitica:8084/api',
  },

  useBff: true,
};
