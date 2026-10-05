import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * authInterceptor — Adjunta la cabecera Authorization: Bearer <token_local>
 * a las llamadas HTTP REST cuando el usuario ha iniciado sesión localmente (provider === 'db').
 * No interfiere con MsalInterceptor cuando el provider es Microsoft.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.token();
  const user = authService.user();

  if (user?.provider === 'db' && token && !req.headers.has('Authorization')) {
    const cloned = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`),
    });
    return next(cloned);
  }

  return next(req);
};
