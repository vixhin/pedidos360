import { environment } from '../../../environments/environment';

/**
 * URLs de los microservicios y del BFF.
 *
 * - Las URLs directas (8081-8086) se usan cuando environment.useBff === false
 *   (desarrollo sin Azure / modo legacy).
 * - La URL del BFF (8090) se usa cuando environment.useBff === true
 *   y el usuario está autenticado con Microsoft Entra ID.
 *
 * El MsalInterceptor adjuntará automáticamente el Bearer token
 * a todas las llamadas que coincidan con bff.
 */
export const API_CONFIG = {
  // BFF — punto de entrada con autenticación Azure AD
  bff:          environment.api.bff,

  // Microservicios directos (compatibilidad + desarrollo local)
  usuario:      environment.api.usuario,
  productos:    environment.api.productos,
  pedidos:      environment.api.pedidos,
  carrito:      environment.api.carrito,
  notificacion: environment.api.notificacion,
  analitica:    environment.api.analitica,
};
