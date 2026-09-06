import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'cuenta',
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'perfil',
    loadComponent: () => import('./pages/account/account').then((m) => m.Account),
  },
  {
    path: 'carrito',
    loadComponent: () => import('./pages/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'notificaciones',
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },
  {
    path: 'analitica',
    loadComponent: () => import('./pages/analytics/analytics').then((m) => m.Analytics),
  },
  {
    path: 'vendedor',
    loadComponent: () => import('./pages/seller/seller').then((m) => m.Seller),
  },
  {
    path: 'cliente',
    loadComponent: () => import('./pages/client/client').then((m) => m.Client),
  },
  {
    path: 'personal',
    loadComponent: () => import('./pages/staff/staff').then((m) => m.Staff),
  },
  { path: '**', redirectTo: '' },
];
