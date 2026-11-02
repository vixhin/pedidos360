import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

function getXsrfToken(): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[2]) : null;
}

/**
 * authInterceptor — Adjunta la cabecera Authorization: Bearer <token_local>,
 * activa withCredentials: true para el envío seguro de la cookie de sesión HttpOnly p360_session,
 * y adjunta el token anti-CSRF X-XSRF-TOKEN en solicitudes mutantes (POST/PUT/PATCH/DELETE).
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

  // Double submit anti-CSRF para solicitudes mutantes con cookies
  const isMutating = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method.toUpperCase());
  if (isMutating && !cloned.headers.has('X-XSRF-TOKEN')) {
    const xsrf = getXsrfToken();
    if (xsrf) {
      cloned = cloned.clone({
        headers: cloned.headers.set('X-XSRF-TOKEN', xsrf)
      });
    }
  }

  return next(cloned);
};
