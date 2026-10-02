import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, tap } from 'rxjs';
import { BackendUrlService } from './backend-url.service';
import { AuthService } from './auth.service';
import { CartService } from './cart.service';
import { NotificationsService } from './notifications.service';

export interface PedidoBackend {
  id: number;
  usuarioId: number;
  total: number;
  estado: string;
  fechaCreacion: string;
}

export interface CrearPedidoRequest {
  usuarioId: number;
  total: number;
  estado: string;
}

export interface ClientOrderFromDB {
  id: string;
  date: string;
  items: { name: string; icon: string; quantity: number; price: number }[];
  total: number;
  statusStep: 1 | 2 | 3 | 4;
  statusLabel: string;
  deliveryEta: string;
  courierName: string;
  courierPhone: string;
}

@Injectable({ providedIn: 'root' })
export class PedidoService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);
  private readonly notifSvc = inject(NotificationsService);
  private readonly urls = inject(BackendUrlService);

  readonly pedidos = signal<ClientOrderFromDB[]>([]);
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly lastOrderId = signal<string | null>(null);

  private estadoToStep(estado: string): 1 | 2 | 3 | 4 {
    switch (estado?.toUpperCase()) {
      case 'PENDIENTE': return 1;
      case 'EN_PREPARACION': return 2;
      case 'EN_CAMINO': return 3;
      case 'ENTREGADO': return 4;
      default: return 1;
    }
  }

  private estadoToLabel(estado: string): string {
    switch (estado?.toUpperCase()) {
      case 'PENDIENTE': return 'Pedido recibido, esperando confirmación';
      case 'EN_PREPARACION': return 'Preparando tu pedido';
      case 'EN_CAMINO': return 'Repartidor en camino a tu domicilio';
      case 'ENTREGADO': return 'Entregado exitosamente';
      default: return 'Pedido en proceso';
    }
  }

  readonly errorMessage = signal<string | null>(null);

  private mapPedido(p: PedidoBackend & { repartidorId?: number }): ClientOrderFromDB {
    const step = this.estadoToStep(p.estado);
    return {
      id: `PED-${p.id}`,
      date: p.fechaCreacion
        ? new Date(p.fechaCreacion).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric' })
        : 'Fecha no disponible',
      items: [],
      total: p.total,
      statusStep: step,
      statusLabel: this.estadoToLabel(p.estado),
      deliveryEta: step === 4 ? 'Entregado' : 'Pendiente de entrega',
      courierName: p.repartidorId ? `Repartidor #${p.repartidorId}` : 'Por asignar',
      courierPhone: p.repartidorId ? `+56 9 8765 000${p.repartidorId}` : 'Sin teléfono',
    };
  }

  cargarPedidosDeUsuario(): void {
    const user = this.auth.user();
    if (!user?.id) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.http
      .get<PedidoBackend[]>(`${this.urls.base('pedidos')}/usuario/${user.id}`)
      .pipe(
        catchError((err) => {
          console.error('[PedidoService] No se pudieron cargar pedidos del backend:', err);
          this.errorMessage.set('No se pudo cargar la información de pedidos desde el backend.');
          return of([] as PedidoBackend[]);
        })
      )
      .subscribe((data) => {
        this.isLoading.set(false);
        const mapped = (data || []).map((p) => this.mapPedido(p));
        this.pedidos.set(mapped);
      });
  }

  crearPedidoDesdeCarrito(cartLines: { name: string; icon: string; quantity: number; price: number }[]): void {
    const user = this.auth.user();

    if (!user?.id || cartLines.length === 0) return;

    const subtotal = cartLines.reduce((acc, item) => acc + item.price * item.quantity, 0);
    const total = subtotal + 1500; // subtotal + tarifa servicio
    const payload: CrearPedidoRequest = {
      usuarioId: user.id,
      total,
      estado: 'PENDIENTE',
    };

    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.http
      .post<PedidoBackend>(`${this.urls.base('pedidos')}`, payload)
      .pipe(
        tap((created) => {
          const mapped: ClientOrderFromDB = {
            ...this.mapPedido(created),
            items: cartLines,
            date: 'Hoy, ' + new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' }) + ' hs',
          };
          this.lastOrderId.set(mapped.id);
          this.pedidos.update((list) => [mapped, ...list]);
          // Registrar notificación en DB
          if (user?.id) {
            this.notifSvc.enviarNotificacion(
              user.id,
              `Tu pedido #${created.id} fue recibido y está siendo preparado. Total: $${total.toLocaleString('es-CL')}`,
              'PEDIDOS'
            );
          }
        }),
        catchError((err) => {
          console.error('[PedidoService] Error al crear pedido:', err);
          this.errorMessage.set('No se pudo procesar la creación del pedido en el servidor. Intente nuevamente.');
          return of(null);
        })
      )
      .subscribe(() => {
        this.isSubmitting.set(false);
      });
  }
}
