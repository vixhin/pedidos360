import { Routes } from '@angular/router';
import { roleGuard } from './core/guards/role.guard';
import { hybridAuthGuard } from './core/guards/hybrid-auth.guard';

/**
 * Rutas de la aplicación Pedidos360.
 *
 * Protección de rutas: se usa `hybridAuthGuard` y `roleGuard`:
 * - `hybridAuthGuard`: verifica sesión activa de BD o sesión MSAL válida.
 * - `roleGuard`: valida autorización por roles (ADMIN, VENDEDOR, CLIENTE).
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
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'perfil',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'carrito',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'notificaciones',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['ADMIN', 'VENDEDOR', 'CLIENTE'] },
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },

  // ─── CLIENTE ──────────────────────────────────────
  {
    path: 'cliente',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/client/client').then((m) => m.Client),
  },

  // ─── VENDEDOR ─────────────────────────────────────
  {
    path: 'vendedor',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['VENDEDOR', 'ADMIN'] },
    loadComponent: () => import('./pages/seller/seller').then((m) => m.Seller),
  },

  // ─── ADMIN ────────────────────────────────────────
  {
    path: 'analitica',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/analytics/analytics').then((m) => m.Analytics),
  },
  {
    path: 'personal',
    canActivate: [hybridAuthGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./pages/staff/staff').then((m) => m.Staff),
  },

  // ─── FALLBACK ─────────────────────────────────────
  { path: '**', redirectTo: '' },
];

