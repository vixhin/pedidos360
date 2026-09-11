import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { TokenClaimsService } from '../services/token-claims.service';
import { MsalService } from '@azure/msal-angular';

/**
 * HybridAuthGuard — Guard de autenticación híbrida (DB + Microsoft Entra ID).
 *
 * Demuestra la participación activa de MSAL en las rutas protegidas sin romper
 * el flujo de login local (base de datos).
 *
 * Flujo:
 * 1. Si el usuario no está autenticado en AuthService -> Redirige a /login.
 * 2. Si se autenticó con cuenta local (provider === 'db') -> Permite navegación.
 * 3. Si se autenticó con Microsoft Entra ID (provider === 'microsoft'):
 *    -> Verifica que exista una cuenta activa en la instancia de MSAL.
 *    -> Si existe cuenta MSAL -> Permite navegación.
 *    -> Si no existe cuenta MSAL (sesión caducada/inválida) -> Redirige a /login.
 */
export const hybridAuthGuard: CanActivateFn = (_route: ActivatedRouteSnapshot) => {
  const auth        = inject(AuthService);
  const tokenClaims = inject(TokenClaimsService);
  const msal        = inject(MsalService);
  const router      = inject(Router);

  // 1. Verificar sesión activa en AuthService
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }

  const user = auth.user();

  // 2. Login local (DB) -> Permitir sin invocar MSAL
  if (user?.provider === 'db') {
    return true;
  }

  // 3. Login Microsoft Entra ID -> Verificar sesión MSAL activa
  if (user?.provider === 'microsoft') {
    const hasMsalAccount =
      tokenClaims.hasActiveSession() ||
      msal.instance.getActiveAccount() !== null ||
      msal.instance.getAllAccounts().length > 0;

    if (hasMsalAccount) {
      return true;
    }

    console.warn('[HybridAuthGuard] Usuario Microsoft sin sesión MSAL activa. Redirigiendo a /login.');
    return router.createUrlTree(['/login']);
  }

  // Fallback por seguridad
  return router.createUrlTree(['/login']);
};
