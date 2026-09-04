export type NotificationCategory = 'pedidos' | 'ofertas' | 'productos' | 'cuenta';

export interface AppNotification {
  id: string;
  category: NotificationCategory;
  title: string;
  message: string;
  time: string;
  read: boolean;
  actionLabel?: string;
}
