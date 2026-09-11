/**
 * Environment de PRODUCCIÓN.
 * Los valores son inyectados en tiempo de build mediante variables de entorno
 * o mediante un archivo de reemplazo en la pipeline CI/CD.
 */
export const environment = {
  production: true,

  azure: {
    clientId: process.env['AZURE_SPA_CLIENT_ID'] || '6c20ed27-9d26-4615-9cc8-c7aaef529ffb',
    tenantId: process.env['AZURE_TENANT_ID'] || 'a50f6528-499a-4d94-bcad-ed9b200f7c7b',
    apiClientId: process.env['AZURE_API_CLIENT_ID'] || '5febc8e2-ee14-4452-8914-7d237eb5a6f5',

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
