import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * authInterceptor — Adjunta la cabecera Authorization: Bearer <token_local>
 * y activa withCredentials: true para el envío seguro de la cookie de sesión HttpOnly p360_session.
 * La identidad y roles son derivados estrictamente del token verificado o sesión en backend,
 * nunca de cabeceras arbitrarias del cliente.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.token();

  let cloned = req.clone({
    withCredentials: true
  });

  if (token && !cloned.headers.has('Authorization')) {
    cloned = cloned.clone({
      headers: cloned.headers.set('Authorization', `Bearer ${token}`)
    });
  }

  return next(cloned);
};
