import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
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
   * Lee los roles del claim "roles" del ID Token.
   * Mantiene compatibilidad con login local existente.
   */
  syncFromMsal(): void {
    const account = this.msal.instance.getAllAccounts()[0];
    if (!account) return;

    // Leer roles desde el claim "roles" del ID Token
    // (requiere que los roles de aplicación estén configurados en Azure y asignados al usuario)
    const claims = account.idTokenClaims as Record<string, unknown>;
    const tokenRoles: string[] = Array.isArray(claims?.['roles'])
      ? (claims['roles'] as string[])
      : [];

    // Determinar rol para UX
    let assignedRole: UserRole = this.resolveRoleFromClaims(tokenRoles, account.username);

    this.setSession(
      {
        name:          account.name || account.username,
        email:         account.username,
        avatarInitial: (account.name || account.username).charAt(0).toUpperCase(),
        rol:           assignedRole,
        provider:      'microsoft',
      },
      null // El Access Token lo maneja MSAL, no lo almacenamos manualmente
    );
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
