import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';
import { BackendUrlService } from './backend-url.service';
import { AuthService } from './auth.service';
import { NotificationsService } from './notifications.service';
import { CatalogService } from './catalog.service';

export interface PedidoItemBackend {
  id?: number;
  productoId?: number;
  nombre: string;
  icono?: string;
  cantidad: number;
  precioUnitario: number;
}

export interface PedidoBackend {
  id: number;
  usuarioId: number;
  total: number;
  subtotal?: number;
  tarifaServicio?: number;
  costoEnvio?: number;
  estado: string;
  repartidorId?: number | null;
  repartidorNombre?: string | null;
  repartidorTelefono?: string | null;
  direccionEntrega?: string | null;
  fechaCreacion: string;
  items?: PedidoItemBackend[];
}

export interface CrearPedidoRequest {
  usuarioId: number;
  estado: string;
  subtotal?: number;
  total?: number;
  costoEnvio?: number;
  tarifaServicio?: number;
  direccionEntrega?: string;
  items: PedidoItemBackend[];
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
  private readonly notifSvc = inject(NotificationsService);
  private readonly urls = inject(BackendUrlService);
  private readonly catalogSvc = inject(CatalogService);

  readonly pedidos = signal<ClientOrderFromDB[]>([]);
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly lastOrderId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

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

  private mapPedido(p: PedidoBackend): ClientOrderFromDB {
    const step = this.estadoToStep(p.estado);
    const mappedItems = (p.items || []).map((i) => ({
      name: i.nombre,
      icon: i.icono || 'PRD',
      quantity: i.cantidad,
      price: i.precioUnitario,
    }));

    return {
      id: `PED-${p.id}`,
      date: p.fechaCreacion
        ? new Date(p.fechaCreacion).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric' })
        : 'Fecha no disponible',
      items: mappedItems,
      total: p.total,
      statusStep: step,
      statusLabel: this.estadoToLabel(p.estado),
      deliveryEta: step === 4 ? 'Entregado' : 'Pendiente de entrega',
      courierName: p.repartidorNombre || (p.repartidorId ? `Repartidor #${p.repartidorId}` : 'Por asignar'),
      courierPhone: p.repartidorTelefono || 'Sin teléfono registrado',
    };
  }

  cargarPedidosDeUsuario(): void {
    const user = this.auth.user();
    const userId = user?.id || 1;

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.http
      .get<PedidoBackend[]>(`${this.urls.base('pedidos')}/usuario/${userId}`)
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

  crearPedidoDesdeCarrito(
    cartLines: { name: string; icon: string; quantity: number; price: number; productId?: string }[],
    totalAmount?: number,
    subtotalAmount?: number,
    shippingCostAmount?: number,
    serviceFeeAmount?: number,
    direccion?: string
  ): Observable<PedidoBackend | null> {
    const user = this.auth.user();
    const userId = user?.id || 1;

    if (cartLines.length === 0) {
      return of(null);
    }

    const calculatedSubtotal = subtotalAmount ?? cartLines.reduce((acc, l) => acc + (l.price * l.quantity), 0);
    const calculatedShipping = shippingCostAmount ?? (calculatedSubtotal >= 20000 || calculatedSubtotal === 0 ? 0 : 2990);
    const calculatedFee = serviceFeeAmount ?? 1500;
    const calculatedTotal = totalAmount ?? (calculatedSubtotal + calculatedShipping + calculatedFee);

    const itemsPayload: PedidoItemBackend[] = cartLines.map((l) => {
      const cleanId = String(l.productId || '1').replace(/\D/g, '');
      return {
        productoId: cleanId ? Number(cleanId) : 1,
        nombre: l.name,
        icono: l.icon,
        cantidad: l.quantity,
        precioUnitario: l.price,
      };
    });

    const payload: CrearPedidoRequest = {
      usuarioId: userId,
      estado: 'PENDIENTE',
      subtotal: calculatedSubtotal,
      costoEnvio: calculatedShipping,
      tarifaServicio: calculatedFee,
      total: calculatedTotal,
      direccionEntrega: direccion || 'Santiago Centro, Chile',
      items: itemsPayload,
    };

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    return this.http
      .post<PedidoBackend>(`${this.urls.base('pedidos')}`, payload)
      .pipe(
        tap((created) => {
          this.isSubmitting.set(false);
          if (!created) return;

          const mapped: ClientOrderFromDB = {
            ...this.mapPedido(created),
            items: cartLines.map(c => ({ name: c.name, icon: c.icon, quantity: c.quantity, price: c.price })),
            date: 'Hoy, ' + new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' }) + ' hs',
          };
          this.lastOrderId.set(mapped.id);
          this.pedidos.update((list) => [mapped, ...list]);

          // Descontar stock real en PostgreSQL para cada producto del pedido y refrescar catálogo
          for (const item of cartLines) {
            if (item.productId) {
              const rawProdId = String(item.productId).replace(/\D/g, '');
              if (rawProdId) {
                this.http
                  .put<{ success: boolean; data: any }>(
                    `${this.urls.base('productos')}/${rawProdId}/stock/descontar?cantidad=${item.quantity}`,
                    {}
                  )
                  .pipe(
                    catchError((err) => {
                      console.warn(`[PedidoService] Falló descuento REST producto #${rawProdId}:`, err);
                      return of(null);
                    })
                  )
                  .subscribe(() => {
                    this.catalogSvc.cargarProductosDeBackend();
                  });
              }
            }
          }

          // Notificación
          this.notifSvc.enviarNotificacion(
            userId,
            `Tu pedido #${created.id} fue recibido y está siendo preparado. Total confirmado: $${(created.total || calculatedTotal).toLocaleString('es-CL')}`,
            'PEDIDOS'
          );
        }),
        catchError((err) => {
          this.isSubmitting.set(false);
          console.error('[PedidoService] Error al crear pedido:', err);
          this.errorMessage.set('No se pudo procesar la creación del pedido en el servidor. Intente nuevamente.');
          return of(null);
        })
      );
  }
}
