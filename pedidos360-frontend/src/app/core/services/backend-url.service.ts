import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { API_CONFIG } from '../config/api.config';
import { AuthService } from './auth.service';

type DirectResource = 'productos' | 'pedidos' | 'carrito' | 'notificacion' | 'analitica';

/**
 * Decide la URL base de cada recurso del backend:
 *
 * - Usuario autenticado con Microsoft Entra ID  → vía BFF (API Manager).
 *   El MsalInterceptor adjunta el Access Token; el BFF valida el JWT.
 * - Usuario con cuenta de BD (o sin sesión)     → directo al microservicio.
 *   (El BFF exige un token de Azure que estos usuarios no tienen.)
 *
 * Se activa solo si environment.useBff === true.
 */
@Injectable({ providedIn: 'root' })
export class BackendUrlService {
  private readonly auth = inject(AuthService);

  /** Prefijo completo del recurso, ej: base('carrito') → ".../carrito" */
  base(resource: DirectResource): string {
    if (environment.useBff && this.auth.user()?.provider === 'microsoft') {
      // BFF expone /api/bff/<recurso>/**  (notificaciones en plural en el BFF)
      const bffSegment = resource === 'notificacion' ? 'notificaciones' : resource;
      return `${API_CONFIG.bff}/${bffSegment}`;
    }
    // Directo: API_CONFIG.<recurso> = "http://host:puerto/api", se le suma "/<recurso>"
    return `${API_CONFIG[resource]}/${resource}`;
  }
}
