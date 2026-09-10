import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, firstValueFrom, tap } from 'rxjs';
import { MsalService } from '@azure/msal-angular';
import { API_CONFIG } from '../config/api.config';
import { AZURE_AD_CONFIG, isAzureAdConfigured } from '../config/auth.config';
import { ApiResponse, AuthRequest, AuthResponse, RegisterRequest } from '../models/auth.model';

export type UserRole = 'ADMIN' | 'VENDEDOR' | 'CLIENTE';

export interface AppUser {
  id?: number;
  name: string;
  email: string;
  avatarInitial: string;
  rol?: UserRole | string;
  provider: 'microsoft' | 'db';
}

const STORAGE_KEY = 'pedidos360_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly msal = inject(MsalService);

  private readonly _isLoggedIn  = signal(false);
  private readonly _user        = signal<AppUser | null>(null);
  private readonly _token       = signal<string | null>(null);
  private readonly _activeRole  = signal<UserRole>('CLIENTE');

  readonly isLoggedIn  = this._isLoggedIn.asReadonly();
  readonly user        = this._user.asReadonly();
  readonly token       = this._token.asReadonly();
  readonly activeRole  = this._activeRole.asReadonly();

  readonly isAdmin    = computed(() => this._isLoggedIn() && this._activeRole() === 'ADMIN');
  readonly isVendedor = computed(() => this._isLoggedIn() && this._activeRole() === 'VENDEDOR');
  readonly isCliente  = computed(() => !this._isLoggedIn() || this._activeRole() === 'CLIENTE');

  readonly roleLabel = computed(() => {
    switch (this._activeRole()) {
      case 'ADMIN':    return 'Administrador';
      case 'VENDEDOR': return 'Vendedor';
      case 'CLIENTE':  return 'Cliente Comprador';
      default:         return 'Usuario';
    }
  });

  constructor() {
    this.restoreSession();
  }

  // ─────────────────────────────────────────────────
  // MICROSOFT ENTRA ID (MSAL)
  // ─────────────────────────────────────────────────

  loginWithMicrosoft(): void {
    if (!isAzureAdConfigured()) {
      console.warn(
        '[AuthService] AZURE_AD_CONFIG no está configurado. ' +
        'Edita src/environments/environment.ts con los datos del tenant.'
      );
      return;
    }
    const scopes = ['openid', 'profile', 'email'];
    if (AZURE_AD_CONFIG.apiScope) scopes.push(AZURE_AD_CONFIG.apiScope);
    this.msal.loginRedirect({ scopes });
  }

  /**
   * Sincroniza la sesión desde MSAL tras el redirect de Microsoft.
   *
   * Los roles de aplicación (ADMIN/VENDEDOR/CLIENTE) están definidos en el
   * App Registration de la API, no en el del SPA, por lo que NO aparecen en el
   * ID Token sino en el Access Token de la API. Por eso:
   *   1. Se intenta leer "roles" del ID Token.
   *   2. Si no hay, se pide el Access Token de la API vía acquireTokenSilent
   *      y se decodifica su claim "roles" (solo para UX; el BFF valida de verdad).
   */
  async syncFromMsal(): Promise<void> {
    const account = this.msal.instance.getAllAccounts()[0];
    if (!account) return;
    this.msal.instance.setActiveAccount(account);

    let tokenRoles: string[] = [];

    const idClaims = account.idTokenClaims as Record<string, unknown> | undefined;
    if (Array.isArray(idClaims?.['roles'])) {
      tokenRoles = idClaims!['roles'] as string[];
    }

    if (tokenRoles.length === 0 && AZURE_AD_CONFIG.apiScope) {
      try {
        const result = await this.msal.instance.acquireTokenSilent({
          account,
          scopes: [AZURE_AD_CONFIG.apiScope],
        });
        tokenRoles = this.rolesFromJwt(result.accessToken);
      } catch {
        // Sin consentimiento aún o token no disponible: se usa el fallback.
      }
    }

    const assignedRole: UserRole = this.resolveRoleFromClaims(tokenRoles, account.username);
    const displayName = account.name || account.username;
    const email = account.username;

    // Provisioning JIT: crear/buscar el usuario en usuario_db y obtener su id
    // numérico (identidad que usan carrito, pedidos y notificación).
    let dbId: number | undefined;
    try {
      const res = await firstValueFrom(
        this.http.post<ApiResponse<{ id: number }>>(`${API_CONFIG.usuario}/auth/entra-sync`, {
          email,
          nombre: displayName,
          rol: assignedRole,
        })
      );
      dbId = res?.data?.id;
    } catch {
      // Si falla la sincronización, el usuario entra igual pero sin carrito/pedidos persistentes.
    }

    this.setSession(
      {
        id:            dbId,
        name:          displayName,
        email,
        avatarInitial: displayName.charAt(0).toUpperCase(),
        rol:           assignedRole,
        provider:      'microsoft',
      },
      null // El Access Token lo maneja MSAL, no lo almacenamos manualmente
    );
  }

  /** Decodifica el payload de un JWT (sin verificar) y devuelve el claim "roles". */
  private rolesFromJwt(jwt: string): string[] {
    try {
      const payload = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const decoded = JSON.parse(atob(payload)) as { roles?: unknown };
      return Array.isArray(decoded.roles) ? (decoded.roles as string[]) : [];
    } catch {
      return [];
    }
  }

  // ─────────────────────────────────────────────────
  // LOGIN LOCAL (Base de datos)
  // ─────────────────────────────────────────────────

  loginWithCredentials(email: string, password: string): Observable<ApiResponse<AuthResponse>> {
    const body: AuthRequest = { email, password };
    return this.http
      .post<ApiResponse<AuthResponse>>(`${API_CONFIG.usuario}/auth/login`, body)
      .pipe(tap((res) => this.applyAuthResponse(res.data, 'db')));
  }

  register(nombre: string, email: string, password: string): Observable<ApiResponse<unknown>> {
    const body: RegisterRequest = { nombre, email, password, rol: 'CLIENTE' };
    return this.http.post<ApiResponse<unknown>>(`${API_CONFIG.usuario}/auth/register`, body);
  }

  // ─────────────────────────────────────────────────
  // PERFIL Y SESIÓN
  // ─────────────────────────────────────────────────

  updateUserProfile(data: Partial<AppUser>): void {
    const current = this._user();
    if (!current) return;
    const updated: AppUser = {
      ...current,
      ...data,
      avatarInitial: data.name ? data.name.charAt(0).toUpperCase() : current.avatarInitial,
    };
    this._user.set(updated);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ user: updated, token: this._token() }));
    } catch { /* localStorage no disponible */ }
  }

  logout(): void {
    const wasMicrosoft = this._user()?.provider === 'microsoft';
    this._isLoggedIn.set(false);
    this._user.set(null);
    this._token.set(null);
    this._activeRole.set('CLIENTE');
    localStorage.removeItem(STORAGE_KEY);

    if (wasMicrosoft && isAzureAdConfigured()) {
      this.msal.logoutRedirect({
        postLogoutRedirectUri: AZURE_AD_CONFIG.postLogoutRedirectUri,
      });
    }
  }

  // ─────────────────────────────────────────────────
  // PRIVADOS
  // ─────────────────────────────────────────────────

  private applyAuthResponse(res: AuthResponse, provider: AppUser['provider']): void {
    // El rol viene de la BD a través del JWT interno del usuario-service
    let assignedRole: UserRole = (res.rol as UserRole);
    if (!assignedRole) {
      assignedRole = this.determineRoleFromEmail(res.email);
    }
    this._activeRole.set(assignedRole);
    this.setSession(
      {
        id:            res.id,
        name:          res.nombre,
        email:         res.email,
        avatarInitial: res.nombre?.charAt(0)?.toUpperCase() || '?',
        rol:           assignedRole,
        provider,
      },
      res.token
    );
  }

  /**
   * Resuelve el rol UX desde los claims del token de Microsoft.
   * Prioridad: claims "roles" → email heurístico (solo si no hay roles en token).
   */
  private resolveRoleFromClaims(tokenRoles: string[], email: string): UserRole {
    if (tokenRoles.length > 0) {
      if (tokenRoles.some((r) => r.toUpperCase() === 'ADMIN'))    return 'ADMIN';
      if (tokenRoles.some((r) => r.toUpperCase() === 'VENDEDOR')) return 'VENDEDOR';
      if (tokenRoles.some((r) => r.toUpperCase() === 'CLIENTE'))  return 'CLIENTE';
    }
    // Fallback: heurística por email (mantenida para retrocompatibilidad)
    return this.determineRoleFromEmail(email);
  }

  /**
   * Determina el rol basándose en el email.
   * Se usa como fallback cuando el token no tiene claim "roles".
   * Para Microsoft Entra, los roles deben configurarse en Azure Portal.
   */
  private determineRoleFromEmail(email?: string): UserRole {
    if (!email) return 'CLIENTE';
    const lower = email.toLowerCase();
    if (lower.includes('admin'))    return 'ADMIN';
    if (lower.includes('vendedor')) return 'VENDEDOR';
    return 'CLIENTE';
  }

  private setSession(user: AppUser, token: string | null): void {
    const role: UserRole = (user.rol as UserRole) || this.determineRoleFromEmail(user.email);
    this._user.set({ ...user, rol: role });
    this._token.set(token);
    this._isLoggedIn.set(true);
    this._activeRole.set(role);

    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ user: { ...user, rol: role }, token })
      );
    } catch { /* localStorage no disponible */ }
  }

  private restoreSession(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const { user, token } = JSON.parse(raw) as { user: AppUser; token: string | null };
      const role = (user.rol as UserRole) || this.determineRoleFromEmail(user.email);
      this._user.set({ ...user, rol: role });
      this._token.set(token);
      this._isLoggedIn.set(true);
      this._activeRole.set(role);
    } catch { /* Sesión inaccesible — ignorar */ }
  }
}
