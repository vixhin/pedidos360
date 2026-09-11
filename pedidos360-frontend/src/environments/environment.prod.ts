/**
 * Environment de PRODUCCIÓN (build con `ng build --configuration production`).
 *
 * __PUBLIC_URL__ es un marcador que el Dockerfile del frontend reemplaza en
 * tiempo de build por la URL pública real (build-arg PUBLIC_URL), p.ej.
 * https://34-207-191-208.nip.io
 *
 * Caddy enruta en esa URL:
 *   /api/bff/*  → BFF (8090)
 *   /api/*      → microservicios (auth/usuario→8081, carrito→8083, etc.)
 *   resto       → este frontend (nginx)
 */
const PUBLIC_URL = '__PUBLIC_URL__';

export const environment = {
  production: true,

  azure: {
    clientId: '6c20ed27-9d26-4615-9cc8-c7aaef529ffb',
    tenantId: 'a50f6528-499a-4d94-bcad-ed9b200f7c7b',
    apiClientId: '5febc8e2-ee14-4452-8914-7d237eb5a6f5',

    get apiScope(): string {
      return `api://${this.apiClientId}/access_as_user`;
    },
    get apiAudience(): string {
      return `api://${this.apiClientId}`;
    },

    redirectUri: PUBLIC_URL,
    postLogoutRedirectUri: PUBLIC_URL,
  },

  api: {
    bff: `${PUBLIC_URL}/api/bff`,
    usuario: `${PUBLIC_URL}/api`,
    productos: `${PUBLIC_URL}/api`,
    pedidos: `${PUBLIC_URL}/api`,
    carrito: `${PUBLIC_URL}/api`,
    notificacion: `${PUBLIC_URL}/api`,
    analitica: `${PUBLIC_URL}/api`,
  },

  useBff: true,
};
