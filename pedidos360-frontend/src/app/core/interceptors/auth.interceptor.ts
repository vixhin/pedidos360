import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * authInterceptor — Adjunta la cabecera Authorization: Bearer <token_local>
 * y las cabeceras de contexto de usuario (X-User-Id, X-User-Roles, X-User-Email)
 * a las llamadas HTTP REST.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.token();
  const user = authService.user();

  let modifiedHeaders = req.headers;

  if (token && !modifiedHeaders.has('Authorization')) {
    modifiedHeaders = modifiedHeaders.set('Authorization', `Bearer ${token}`);
  }
  if (user?.id && !modifiedHeaders.has('X-User-Id')) {
    modifiedHeaders = modifiedHeaders.set('X-User-Id', String(user.id));
  }
  if (user?.email && !modifiedHeaders.has('X-User-Email')) {
    modifiedHeaders = modifiedHeaders.set('X-User-Email', user.email);
  }
  const role = user?.rol || authService.activeRole() || 'CLIENTE';
  if (!modifiedHeaders.has('X-User-Roles')) {
    modifiedHeaders = modifiedHeaders.set('X-User-Roles', String(role));
  }

  const cloned = req.clone({ headers: modifiedHeaders });
  return next(cloned);
};
