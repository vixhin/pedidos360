import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { AppNotification } from '../models/notification.model';
import { API_CONFIG } from '../config/api.config';
import { AuthService } from './auth.service';

export interface NotificacionDB {
  id: number;
  usuarioId: number;
  mensaje: string;
  canal: string;
  fechaEnvio: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _items = signal<AppNotification[]>([]);
  private readonly _activeToast = signal<AppNotification | null>(null);

  readonly items = this._items.asReadonly();
  readonly activeToast = this._activeToast.asReadonly();
  readonly unreadCount = computed(() => this._items().filter((n) => !n.read).length);

  constructor() {
    // Intentar cargar notificaciones del backend cuando el usuario esté disponible
    setTimeout(() => this.cargarDeBackend(), 500);
  }

  cargarDeBackend(): void {
    const user = this.auth.user();
    if (!user?.id) {
      // Sin usuario autenticado: sin notificaciones
      this._items.set([]);
      return;
    }

    this.http
      .get<NotificacionDB[]>(`${API_CONFIG.notificacion}/notificacion/usuario/${user.id}`)
      .pipe(
        catchError(() => {
          return of([] as NotificacionDB[]);
        })
      )
      .subscribe((data) => {
        const mapped: AppNotification[] = (data || []).map((n) => ({
          id: String(n.id),
          category: this.inferCategory(n.canal, n.mensaje),
          title: this.inferTitle(n.canal, n.mensaje),
          message: n.mensaje,
          time: n.fechaEnvio
            ? this.timeAgo(new Date(n.fechaEnvio))
            : 'Reciente',
          read: false,
          actionLabel: n.canal === 'PEDIDOS' ? 'Ver pedido' : 'Ver detalle',
        }));
        this._items.set(mapped);
      });
  }

  enviarNotificacion(usuarioId: number, mensaje: string, canal: string): void {
    const payload = { usuarioId, mensaje, canal };
    this.http
      .post<NotificacionDB>(`${API_CONFIG.notificacion}/notificacion`, payload)
      .pipe(catchError(() => of(null)))
      .subscribe((created) => {
        if (created) {
          const newNotif: AppNotification = {
            id: String(created.id),
            category: this.inferCategory(canal, mensaje),
            title: this.inferTitle(canal, mensaje),
            message: mensaje,
            time: 'Justo ahora',
            read: false,
            actionLabel: 'Ver detalle',
          };
          this._items.set([newNotif, ...this._items()]);
          this._activeToast.set(newNotif);
          setTimeout(() => {
            if (this._activeToast()?.id === newNotif.id) {
              this._activeToast.set(null);
            }
          }, 5000);
        }
      });
  }

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

  pushNotification(notif: Omit<AppNotification, 'id' | 'time' | 'read'>): void {
    const newNotif: AppNotification = {
      ...notif,
      id: 'n_' + Date.now(),
      time: 'Justo ahora',
      read: false,
    };
    this._items.set([newNotif, ...this._items()]);
    this._activeToast.set(newNotif);

    setTimeout(() => {
      if (this._activeToast()?.id === newNotif.id) {
        this._activeToast.set(null);
      }
    }, 5000);
  }

  closeToast(): void {
    this._activeToast.set(null);
  }

  private inferCategory(canal: string, mensaje: string): AppNotification['category'] {
    const c = (canal || '').toUpperCase();
    const m = (mensaje || '').toLowerCase();
    if (c === 'PEDIDOS' || m.includes('pedido') || m.includes('repartidor')) return 'pedidos';
    if (c === 'OFERTAS' || m.includes('oferta') || m.includes('descuento') || m.includes('cupón')) return 'ofertas';
    if (c === 'PRODUCTOS' || m.includes('producto') || m.includes('stock')) return 'productos';
    if (c === 'CUENTA' || m.includes('cuenta') || m.includes('contraseña')) return 'cuenta';
    return 'pedidos';
  }

  private inferTitle(canal: string, mensaje: string): string {
    const c = (canal || '').toUpperCase();
    if (c === 'PEDIDOS') return 'Actualización de pedido';
    if (c === 'OFERTAS') return 'Oferta disponible';
    if (c === 'PRODUCTOS') return 'Producto disponible';
    if (c === 'CUENTA') return 'Aviso de cuenta';
    return mensaje.length > 40 ? mensaje.substring(0, 40) + '...' : mensaje;
  }

  private timeAgo(date: Date): string {
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);
    if (diff < 60) return 'Hace un momento';
    if (diff < 3600) return `Hace ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `Hace ${Math.floor(diff / 3600)} h`;
    return `Hace ${Math.floor(diff / 86400)} días`;
  }
}
