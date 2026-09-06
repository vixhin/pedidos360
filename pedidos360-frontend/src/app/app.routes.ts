import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { roleGuard } from './core/guards/role.guard';
import { isAzureAdConfigured } from './core/config/auth.config';

/**
 * Rutas de la aplicación Pedidos360.
 *
 * Protección de rutas:
 * - MsalGuard: verifica que el usuario esté autenticado con Microsoft Entra ID.
 *   Si no está autenticado, redirige al flujo de login de Microsoft.
 * - roleGuard: verifica que el usuario tenga el rol requerido (data.roles).
 *
 * NOTA IMPORTANTE:
 * MsalGuard solo se activa cuando Azure AD está configurado (isAzureAdConfigured() === true).
 * Durante desarrollo local sin Azure, las rutas funcionan sin restricción de MSAL,
 * pero el roleGuard sigue validando el rol del AuthService (login local).
 *
 * Rutas públicas (sin guard):
 * - / (home)
 * - /login
 *
 * Rutas protegidas (requieren autenticación):
 * - /cuenta, /perfil
 * - /carrito
 * - /notificaciones
 * - /cliente
 * - /analitica (solo ADMIN)
 * - /vendedor (VENDEDOR o ADMIN)
 * - /personal (solo ADMIN)
 */

// Usamos los guards de MSAL solo cuando Azure está configurado
// para no bloquear el desarrollo local sin credenciales Azure.
const msalGuards = isAzureAdConfigured() ? [MsalGuard] : [];

export const routes: Routes = [
  // ─── PÚBLICAS ─────────────────────────────────────
  {
    path: '',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },

  // ─── USUARIO AUTENTICADO (cualquier rol) ──────────
  {
    path: 'cuenta',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'perfil',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'carrito',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'notificaciones',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },

  // ─── CLIENTE ──────────────────────────────────────
  {
    path: 'cliente',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/client/client').then((m) => m.Client),
  },

  // ─── VENDEDOR ─────────────────────────────────────
  {
    path: 'vendedor',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['VENDEDOR', 'ADMIN'] },
    loadComponent: () => import('./pages/seller/seller').then((m) => m.Seller),
  },

  // ─── ADMIN ────────────────────────────────────────
  {
    path: 'analitica',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/analytics/analytics').then((m) => m.Analytics),
  },
  {
    path: 'personal',
    canActivate: [...msalGuards, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/staff/staff').then((m) => m.Staff),
  },

  // ─── FALLBACK ─────────────────────────────────────
  { path: '**', redirectTo: '' },
];
