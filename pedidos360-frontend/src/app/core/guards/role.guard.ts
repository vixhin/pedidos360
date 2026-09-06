import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { TokenClaimsService } from '../services/token-claims.service';
import { AuthService } from '../services/auth.service';

/**
 * RoleGuard — Guard de autorización por roles.
 *
 * Debe usarse JUNTO con MsalGuard:
 *   canActivate: [MsalGuard, roleGuard]
 *
 * Lee los roles requeridos desde route.data['roles']:
 *   data: { roles: ['ADMIN', 'VENDEDOR'] }
 *
 * Flujo:
 * 1. Si el usuario se autenticó con Microsoft (provider === 'microsoft'):
 *    → Lee roles del token via TokenClaimsService
 * 2. Si el usuario se autenticó localmente (provider === 'db'):
 *    → Lee rol del AuthService (viene de la BD via JWT interno)
 * 3. Si no hay sesión activa: redirige a /login
 * 4. Si no tiene el rol requerido: redirige a / con un estado de error
 *
 * NOTA: Este guard es únicamente para UX / navegación.
 * La autorización real se aplica en el BFF (Spring Security).
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const tokenClaims = inject(TokenClaimsService);
  const auth        = inject(AuthService);
  const router      = inject(Router);

  const requiredRoles: string[] = route.data?.['roles'] ?? [];

  // Sin roles requeridos en la ruta → acceso libre
  if (requiredRoles.length === 0) return true;

  const user = auth.user();

  // Sin sesión activa
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }

  let userRoles: string[] = [];

  if (user?.provider === 'microsoft') {
    // Flujo Microsoft Entra: roles del token
    userRoles = tokenClaims.getRoles();

    // Si el token no tiene claim "roles", MSAL puede reportar la cuenta
    // pero sin claims de aplicación asignados aún.
    // Como fallback seguro, usamos el rol del AuthService.
    if (userRoles.length === 0 && auth.activeRole()) {
      userRoles = [auth.activeRole()];
    }
  } else {
    // Flujo local (DB): rol del AuthService
    userRoles = auth.activeRole() ? [auth.activeRole()] : [];
  }

  const hasRequiredRole = requiredRoles.some((required) =>
    userRoles.some((r) => r.toUpperCase() === required.toUpperCase())
  );

  if (hasRequiredRole) return true;

  // Sin permisos suficientes → redirigir a home
  console.warn(
    `[RoleGuard] Acceso denegado. Roles requeridos: ${requiredRoles.join(',')} | Roles del usuario: ${userRoles.join(',')}`
  );
  return router.createUrlTree(['/'], {
    queryParams: { error: 'forbidden' },
  });
};
