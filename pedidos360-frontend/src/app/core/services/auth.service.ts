import { Injectable, computed, signal } from '@angular/core';
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
  private readonly _isLoggedIn = signal(false);
  private readonly _user = signal<AppUser | null>(null);
  private readonly _token = signal<string | null>(null);
  private readonly _activeRole = signal<UserRole>('CLIENTE');

  readonly isLoggedIn = this._isLoggedIn.asReadonly();
  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
  readonly activeRole = this._activeRole.asReadonly();

  readonly isAdmin = computed(() => this._isLoggedIn() && this._activeRole() === 'ADMIN');
  readonly isVendedor = computed(() => this._isLoggedIn() && this._activeRole() === 'VENDEDOR');
  readonly isCliente = computed(() => !this._isLoggedIn() || this._activeRole() === 'CLIENTE');

  readonly roleLabel = computed(() => {
    switch (this._activeRole()) {
      case 'ADMIN':
        return 'Administrador';
      case 'VENDEDOR':
        return 'Vendedor';
      case 'CLIENTE':
        return 'Cliente Comprador';
      default:
        return 'Usuario';
    }
  });

  constructor(private http: HttpClient, private msal: MsalService) {
    this.restoreSession();
  }

  loginWithMicrosoft(): void {
    if (!isAzureAdConfigured()) {
      console.warn(
        '[AuthService] AZURE_AD_CONFIG no está configurado (clientId/tenantId vacíos). ' +
          'Completa src/app/core/config/auth.config.ts con los datos del tenant de Entra ID.'
      );
      return;
    }
    this.msal.loginRedirect({ scopes: ['openid', 'profile', 'email'] });
  }

  syncFromMsal(): void {
    const account = this.msal.instance.getAllAccounts()[0];
    if (!account) return;
    this.setSession(
      {
        name: account.name || account.username,
        email: account.username,
        avatarInitial: (account.name || account.username).charAt(0).toUpperCase(),
        rol: 'ADMIN',
        provider: 'microsoft',
      },
      null
    );
  }

  loginWithCredentials(email: string, password: string): Observable<ApiResponse<AuthResponse>> {
    const body: AuthRequest = { email, password };
    return this.http.post<ApiResponse<AuthResponse>>(`${API_CONFIG.usuario}/auth/login`, body).pipe(
      tap((res) => this.applyAuthResponse(res.data, 'db'))
    );
  }

  register(nombre: string, email: string, password: string): Observable<ApiResponse<unknown>> {
    const body: RegisterRequest = { nombre, email, password, rol: 'CLIENTE' };
    return this.http.post<ApiResponse<unknown>>(`${API_CONFIG.usuario}/auth/register`, body);
  }

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
    } catch {
      // localStorage no disponible
    }
  }

  logout(): void {
    const wasMicrosoft = this._user()?.provider === 'microsoft';
    this._isLoggedIn.set(false);
    this._user.set(null);
    this._token.set(null);
    this._activeRole.set('CLIENTE');
    localStorage.removeItem(STORAGE_KEY);

    if (wasMicrosoft && isAzureAdConfigured()) {
      this.msal.logoutRedirect({ postLogoutRedirectUri: AZURE_AD_CONFIG.postLogoutRedirectUri });
    }
  }

  private applyAuthResponse(res: AuthResponse, provider: AppUser['provider']): void {
    let assignedRole: UserRole = (res.rol as UserRole);
    if (!assignedRole) {
      assignedRole = this.determineRoleFromEmail(res.email);
    }
    this._activeRole.set(assignedRole);
    this.setSession(
      {
        id: res.id,
        name: res.nombre,
        email: res.email,
        avatarInitial: res.nombre?.charAt(0)?.toUpperCase() || '?',
        rol: assignedRole,
        provider,
      },
      res.token
    );
  }

  private determineRoleFromEmail(email?: string): UserRole {
    if (!email) return 'CLIENTE';
    const lower = email.toLowerCase();
    if (lower.includes('admin')) return 'ADMIN';
    if (lower.includes('vendedor')) return 'VENDEDOR';
    return 'CLIENTE';
  }

  private setSession(user: AppUser, token: string | null): void {
    let assignedRole: UserRole = (user.rol as UserRole) || this.determineRoleFromEmail(user.email);
    this._user.set({ ...user, rol: assignedRole });
    this._token.set(token);
    this._isLoggedIn.set(true);
    this._activeRole.set(assignedRole);

    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ user: { ...user, rol: assignedRole }, token }));
    } catch {
      // localStorage no disponible — la sesión sigue en memoria.
    }
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
    } catch {
      // Sesión inaccesible — ignorar.
    }
  }
}
