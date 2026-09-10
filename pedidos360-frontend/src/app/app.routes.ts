import { Routes } from '@angular/router';
import { roleGuard } from './core/guards/role.guard';

/**
 * Rutas de la aplicación Pedidos360.
 *
 * Protección de rutas: se usa únicamente `roleGuard` (guard propio), que:
 * - Redirige a /login si no hay sesión activa (auth.isLoggedIn()).
 * - Funciona con AMBOS métodos de login: cuenta de BD (provider 'db') y
 *   Microsoft Entra ID (provider 'microsoft').
 * - Valida el rol requerido desde route.data['roles'].
 *
 * No se usa MsalGuard directamente porque forzaría el login de Microsoft
 * incluso a usuarios que entraron con cuenta de la base de datos.
 * La validación criptográfica real de los JWT de Entra ID ocurre en el BFF
 * (Spring Security OAuth2 Resource Server).
 *
 * Rutas públicas: / (home), /login
 */

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
    canActivate: [roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'perfil',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'carrito',
    canActivate: [roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'notificaciones',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },

  // ─── CLIENTE ──────────────────────────────────────
  {
    path: 'cliente',
    canActivate: [roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/client/client').then((m) => m.Client),
  },

  // ─── VENDEDOR ─────────────────────────────────────
  {
    path: 'vendedor',
    canActivate: [roleGuard],
    data: { roles: ['VENDEDOR', 'ADMIN'] },
    loadComponent: () => import('./pages/seller/seller').then((m) => m.Seller),
  },

  // ─── ADMIN ────────────────────────────────────────
  {
    path: 'analitica',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/analytics/analytics').then((m) => m.Analytics),
  },
  {
    path: 'personal',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/staff/staff').then((m) => m.Staff),
  },

  // ─── FALLBACK ─────────────────────────────────────
  { path: '**', redirectTo: '' },
];
