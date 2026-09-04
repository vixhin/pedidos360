import { Injectable, computed, signal } from '@angular/core';
import { AppNotification } from '../models/notification.model';

const SEED: AppNotification[] = [
  {
    id: 'n1',
    category: 'pedidos',
    title: 'Pedido en camino',
    message: 'Tu repartidor ya retiró tus productos y se dirige a tu dirección.',
    time: 'Hace 1 min',
    read: false,
    actionLabel: 'Seguir pedido',
  },
  {
    id: 'n2',
    category: 'pedidos',
    title: 'Pedido confirmado',
    message: 'Tu pedido #PED-360-0248 ya está siendo preparado.',
    time: 'Hace 3 min',
    read: false,
    actionLabel: 'Ver pedido',
  },
  {
    id: 'n3',
    category: 'ofertas',
    title: 'Envío gratis disponible',
    message: 'Actívalo en tu próximo pedido antes del domingo.',
    time: 'Hace 8 min',
    read: false,
    actionLabel: 'Usar beneficio',
  },
  {
    id: 'n4',
    category: 'ofertas',
    title: '10% en productos para mascotas',
    message: 'Activa el cupón antes de que expire.',
    time: 'Hace 8 min',
    read: true,
    actionLabel: 'Usar beneficio',
  },
  {
    id: 'n5',
    category: 'productos',
    title: 'Producto disponible cerca de ti',
    message: 'Los audífonos que guardaste ya tienen entrega rápida.',
    time: 'Hace 12 min',
    read: true,
    actionLabel: 'Agregar al carrito',
  },
];

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly _items = signal<AppNotification[]>(SEED);

  readonly items = this._items.asReadonly();

  readonly unreadCount = computed(() => this._items().filter((n) => !n.read).length);

  byCategory(category: AppNotification['category'] | 'todas') {
    return computed(() =>
      category === 'todas' ? this._items() : this._items().filter((n) => n.category === category)
    );
  }

  markAllRead(): void {
    this._items.set(this._items().map((n) => ({ ...n, read: true })));
  }

  markRead(id: string): void {
    this._items.set(this._items().map((n) => (n.id === id ? { ...n, read: true } : n)));
  }
}
