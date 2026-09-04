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
    path: 'carrito',
    loadComponent: () => import('./pages/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'notificaciones',
    loadComponent: () => import('./pages/notifications/notifications').then((m) => m.Notifications),
  },
  { path: '**', redirectTo: '' },
];
