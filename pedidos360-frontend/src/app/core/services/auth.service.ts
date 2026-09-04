import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { MsalService } from '@azure/msal-angular';
import { API_CONFIG } from '../config/api.config';
import { AZURE_AD_CONFIG, isAzureAdConfigured } from '../config/auth.config';
import { ApiResponse, AuthRequest, AuthResponse, RegisterRequest } from '../models/auth.model';

export interface AppUser {
  id?: number;
  name: string;
  email: string;
  avatarInitial: string;
  rol?: string;
  provider: 'microsoft' | 'db';
}

const STORAGE_KEY = 'pedidos360_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _isLoggedIn = signal(false);
  private readonly _user = signal<AppUser | null>(null);
  private readonly _token = signal<string | null>(null);

  readonly isLoggedIn = this._isLoggedIn.asReadonly();
  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();

  constructor(private http: HttpClient, private msal: MsalService) {
    this.restoreSession();
  }

  /**
   * Inicia el flujo OIDC "Authorization Code + PKCE" contra Microsoft Entra
   * ID vía MSAL. Requiere AZURE_AD_CONFIG (clientId/tenantId) configurado en
   * core/config/auth.config.ts.
   */
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

  /** Se llama una vez procesado el redirect de MSAL (ver app.ts). */
  syncFromMsal(): void {
    const account = this.msal.instance.getAllAccounts()[0];
    if (!account) return;
    this.setSession(
      {
        name: account.name || account.username,
        email: account.username,
        avatarInitial: (account.name || account.username).charAt(0).toUpperCase(),
        provider: 'microsoft',
      },
      null
    );
  }

  /** Login real contra usuario-service (cuenta creada en la base de datos). */
  loginWithCredentials(email: string, password: string): Observable<ApiResponse<AuthResponse>> {
    const body: AuthRequest = { email, password };
    return this.http.post<ApiResponse<AuthResponse>>(`${API_CONFIG.usuario}/auth/login`, body).pipe(
      tap((res) => this.applyAuthResponse(res.data, 'db'))
    );
  }

  /** Registro de una cuenta nueva en usuario-service. */
  register(nombre: string, email: string, password: string): Observable<ApiResponse<unknown>> {
    const body: RegisterRequest = { nombre, email, password, rol: 'CLIENTE' };
    return this.http.post<ApiResponse<unknown>>(`${API_CONFIG.usuario}/auth/register`, body);
  }

  logout(): void {
    const wasMicrosoft = this._user()?.provider === 'microsoft';
    this._isLoggedIn.set(false);
    this._user.set(null);
    this._token.set(null);
    localStorage.removeItem(STORAGE_KEY);

    if (wasMicrosoft && isAzureAdConfigured()) {
      this.msal.logoutRedirect({ postLogoutRedirectUri: AZURE_AD_CONFIG.postLogoutRedirectUri });
    }
  }

  private applyAuthResponse(res: AuthResponse, provider: AppUser['provider']): void {
    this.setSession(
      {
        id: res.id,
        name: res.nombre,
        email: res.email,
        avatarInitial: res.nombre?.charAt(0)?.toUpperCase() || '?',
        rol: res.rol,
        provider,
      },
      res.token
    );
  }

  private setSession(user: AppUser, token: string | null): void {
    this._user.set(user);
    this._token.set(token);
    this._isLoggedIn.set(true);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ user, token }));
    } catch {
      // localStorage no disponible (modo privado, etc.) — la sesión sigue en memoria.
    }
  }

  private restoreSession(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const { user, token } = JSON.parse(raw) as { user: AppUser; token: string | null };
      this._user.set(user);
      this._token.set(token);
      this._isLoggedIn.set(true);
    } catch {
      // Sesión guardada corrupta o inaccesible — se ignora, queda deslogueado.
    }
  }
}
