import { Injectable, inject } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

/**
 * Claims del ID Token / Access Token de Microsoft Entra ID.
 * Definidos como interfaz independiente para evitar conflictos de tipo
 * con IdTokenClaims de MSAL (el claim 'aud' puede ser string o string[]).
 */
export interface AzureTokenClaims {
  /** Subject — ID único del usuario */
  sub?: string;

  /** Roles de aplicación asignados (ej: ["ADMIN", "VENDEDOR"]) */
  roles?: string[];

  /** Scopes delegados (ej: "access_as_user") */
  scp?: string;

  /** Object ID del usuario en el directorio */
  oid?: string;

  /** Preferred username (UPN o email) */
  preferred_username?: string;

  /** Display name */
  name?: string;

  /** Audience — puede ser string o array de strings */
  aud?: string | string[];

  /** Issuer */
  iss?: string;

  /** Expiration (Unix timestamp) */
  exp?: number;

  /** Not before (Unix timestamp) */
  nbf?: number;

  /** Issued at (Unix timestamp) */
  iat?: number;

  [key: string]: unknown;
}

/**
 * TokenClaimsService
 *
 * Lee los claims del ID Token de Microsoft Entra ID almacenado
 * en MSAL (in-memory / localStorage).
 *
 * IMPORTANTE:
 * - Angular NO valida criptográficamente el token.
 * - Los claims se usan únicamente para UX (mostrar nombre, rol en UI).
 * - La validación criptográfica real ocurre en el BFF (Spring Security).
 */
@Injectable({ providedIn: 'root' })
export class TokenClaimsService {
  private readonly msal = inject(MsalService);

  /**
   * Retorna los claims del ID Token de la cuenta activa,
   * o null si no hay sesión Microsoft activa.
   */
  getClaims(): AzureTokenClaims | null {
    const account = this.getActiveAccount();
    if (!account) return null;
    return (account.idTokenClaims as AzureTokenClaims) ?? null;
  }

  /**
   * Retorna la cuenta activa en MSAL, o la primera cuenta disponible.
   */
  getActiveAccount(): AccountInfo | null {
    return (
      this.msal.instance.getActiveAccount() ??
      this.msal.instance.getAllAccounts()[0] ??
      null
    );
  }

  /**
   * Retorna los roles de aplicación del token.
   * Ejemplo: ["ADMIN"] → ['ADMIN']
   */
  getRoles(): string[] {
    return this.getClaims()?.roles ?? [];
  }

  /**
   * Retorna los scopes delegados del token como array.
   * El claim "scp" es un string separado por espacios.
   * Ejemplo: "access_as_user openid" → ['access_as_user', 'openid']
   */
  getScopes(): string[] {
    const scp = this.getClaims()?.scp;
    if (!scp) return [];
    return scp.split(' ').filter((s) => s.length > 0);
  }

  /**
   * Retorna true si el token contiene el rol especificado.
   * No distingue mayúsculas.
   */
  hasRole(role: string): boolean {
    return this.getRoles().some(
      (r) => r.toUpperCase() === role.toUpperCase()
    );
  }

  /**
   * Retorna true si el token contiene al menos uno de los roles dados.
   */
  hasAnyRole(roles: string[]): boolean {
    return roles.some((r) => this.hasRole(r));
  }

  /**
   * Retorna true si el token contiene el scope especificado.
   */
  hasScope(scope: string): boolean {
    return this.getScopes().includes(scope);
  }

  /**
   * Retorna el preferred_username (UPN / email) del token.
   */
  getUsername(): string | null {
    return (
      this.getClaims()?.preferred_username ??
      this.getActiveAccount()?.username ??
      null
    );
  }

  /**
   * Retorna el nombre display del usuario.
   */
  getDisplayName(): string | null {
    return (
      this.getClaims()?.name ??
      this.getActiveAccount()?.name ??
      null
    );
  }

  /**
   * Retorna el Object ID (oid) del usuario en el directorio Azure.
   */
  getObjectId(): string | null {
    return this.getClaims()?.oid ?? null;
  }

  /**
   * Retorna true si el token está expirado basándose en el claim "exp".
   * Nota: Esta verificación es solo orientativa para UX.
   * MSAL maneja la renovación automática del token.
   */
  isTokenExpired(): boolean {
    const exp = this.getClaims()?.exp;
    if (!exp) return true;
    return Date.now() / 1000 > exp;
  }

  /**
   * Retorna el issuer del token.
   * Debería ser: https://login.microsoftonline.com/<tenantId>/v2.0
   */
  getIssuer(): string | null {
    return this.getClaims()?.iss ?? null;
  }

  /**
   * Retorna la audience del token.
   */
  getAudience(): string | string[] | null {
    return this.getClaims()?.aud ?? null;
  }

  /**
   * Retorna true si hay una cuenta Microsoft activa en MSAL.
   */
  hasActiveSession(): boolean {
    return this.getActiveAccount() !== null;
  }
}
