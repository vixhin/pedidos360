/**
 * Environment de PRODUCCIÓN (build con `ng build --configuration production`).
 *
 * __PUBLIC_URL__ y __BFF_PUBLIC_URL__ son marcadores reemplazados en tiempo de build
 * por el Dockerfile del frontend (build-args PUBLIC_URL y BFF_PUBLIC_URL).
 */
const PUBLIC_URL = '__PUBLIC_URL__';
const RAW_BFF_URL = '__BFF_PUBLIC_URL__';

const BFF_PUBLIC_URL = (RAW_BFF_URL && RAW_BFF_URL !== '__BFF_PUBLIC_URL__')
  ? RAW_BFF_URL
  : `${PUBLIC_URL}/api/bff`;

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
    bff: BFF_PUBLIC_URL,
    usuario: `${PUBLIC_URL}/api`,
    productos: `${PUBLIC_URL}/api`,
    pedidos: `${PUBLIC_URL}/api`,
    carrito: `${PUBLIC_URL}/api`,
    notificacion: `${PUBLIC_URL}/api`,
    analitica: `${PUBLIC_URL}/api`,
    chat: `${PUBLIC_URL}/api`,
    chatWs: `${PUBLIC_URL}/ws-chat`,
  },

  useBff: true,
};
