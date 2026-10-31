import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { Icon } from '../../shared/icon/icon';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { BackendUrlService } from '../../core/services/backend-url.service';

export interface IncomingOrder {
  id: string;
  customerName: string;
  address: string;
  itemsCount: number;
  total: number;
  status: 'PENDIENTE' | 'EN_PREPARACION' | 'EN_CAMINO' | 'ENTREGADO';
  time: string;
}

interface PedidoItemRaw {
  id?: number;
  nombre: string;
  cantidad: number;
  precioUnitario: number;
}

interface PedidoRaw {
  id: number;
  usuarioId: number;
  total: number;
  subtotal?: number;
  direccionEntrega?: string;
  estado: string;
  fechaCreacion: string;
  items?: PedidoItemRaw[];
}

@Component({
  selector: 'app-seller',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seller.html',
  styleUrl: './seller.css',
})
export class Seller implements OnInit {
  private readonly urls = inject(BackendUrlService);
  private readonly http = inject(HttpClient);
  readonly catalog = inject(CatalogService);
  readonly auth = inject(AuthService);

  readonly activeTab = signal<'inventory' | 'orders' | 'new-product'>('inventory');

  // Formulario nuevo producto
  readonly newSku = signal('');
  readonly newName = signal('');
  readonly newCategory = signal('LACTEOS_Y_HUEVOS');
  readonly newPrice = signal(1990);
  readonly newStock = signal(100);
  readonly newDescription = signal('');
  readonly isSubmittingProduct = signal(false);

  // Mensaje de notificación
  readonly statusMessage = signal<string | null>(null);

  // Pedidos cargados desde el backend
  readonly incomingOrders = signal<IncomingOrder[]>([]);
  readonly isLoadingOrders = signal(false);
  readonly ordersErrorMessage = signal<string | null>(null);

  // Productos con stock bajo (< 50)
  readonly lowStockProducts = computed(() =>
    this.catalog.products().filter((p) => (p.stock || 0) < 50)
  );

  ngOnInit(): void {
    this.cargarPedidos();
  }

  cargarPedidos(): void {
    this.isLoadingOrders.set(true);
    this.ordersErrorMessage.set(null);

    this.http
      .get<PedidoRaw[]>(`${this.urls.base('pedidos')}`)
      .pipe(
        catchError((err) => {
          console.error('[Seller] Error al cargar pedidos:', err);
          this.ordersErrorMessage.set('No se pudo cargar la lista de pedidos desde el servidor.');
          return of([] as PedidoRaw[]);
        })
      )
      .subscribe((data) => {
        this.isLoadingOrders.set(false);
        const orders: IncomingOrder[] = (data || []).map((p) => {
          const totalItems = (p.items || []).reduce((acc, it) => acc + (it.cantidad || 1), 0);
          return {
            id: `PED-${p.id}`,
            customerName: `Cliente #${p.usuarioId}`,
            address: p.direccionEntrega || 'Dirección no especificada',
            itemsCount: totalItems > 0 ? totalItems : 1,
            total: p.total,
            status: this.mapEstado(p.estado),
            time: p.fechaCreacion
              ? this.timeAgo(new Date(p.fechaCreacion))
              : 'Reciente',
          };
        });

        // Ordenar con los más recientes primero
        this.incomingOrders.set(orders.sort((a, b) => b.id.localeCompare(a.id)));
      });
  }

  private mapEstado(estado: string): IncomingOrder['status'] {
    switch ((estado || '').toUpperCase()) {
      case 'EN_PREPARACION': return 'EN_PREPARACION';
      case 'EN_CAMINO': return 'EN_CAMINO';
      case 'ENTREGADO': return 'ENTREGADO';
      default: return 'PENDIENTE';
    }
  }

  private timeAgo(date: Date): string {
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);
    if (diff < 60) return 'Hace un momento';
    if (diff < 3600) return `Hace ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `Hace ${Math.floor(diff / 3600)} h`;
    return `Hace ${Math.floor(diff / 86400)} días`;
  }

  setTab(tab: 'inventory' | 'orders' | 'new-product'): void {
    this.activeTab.set(tab);
    if (tab === 'orders') this.cargarPedidos();
  }

  updateOrderStatus(orderId: string, nextStatus: IncomingOrder['status']): void {
    const rawId = orderId.replace('PED-', '');
    const url = `${this.urls.base('pedidos')}/${rawId}/estado?nuevoEstado=${nextStatus}`;

    this.http
      .put(url, {})
      .pipe(
        catchError((err) => {
          console.error('[Seller] Error actualizando estado en backend:', err);
          alert('No se pudo actualizar el estado en el servidor. ' + (err.error?.message || 'Error de conexión'));
          return of(null);
        })
      )
      .subscribe((res) => {
        if (res) {
          this.incomingOrders.update((orders) =>
            orders.map((o) => (o.id === orderId ? { ...o, status: nextStatus } : o))
          );
          this.showToast(`Estado del pedido ${orderId} actualizado a ${nextStatus} en PostgreSQL y RabbitMQ!`);
        }
      });
  }

  createProduct(): void {
    if (!this.newSku().trim() || !this.newName().trim() || !this.newPrice()) {
      alert('Por favor completa los campos requeridos: SKU, Nombre y Precio');
      return;
    }

    const payload = {
      sku: this.newSku().trim().toUpperCase(),
      nombre: this.newName().trim(),
      categoria: this.newCategory(),
      precio: Number(this.newPrice()),
      stock: Number(this.newStock()),
      descripcion: this.newDescription().trim() || 'Producto agregado por el vendedor',
      rating: 4.8,
      deliveryMinutes: 25,
    };

    this.isSubmittingProduct.set(true);

    this.http
      .post<{ success: boolean; message: string; data: any }>(`${this.urls.base('productos')}`, payload)
      .subscribe({
        next: (res) => {
          this.isSubmittingProduct.set(false);
          this.catalog.cargarProductosDeBackend();
          this.showToast(`¡Producto "${payload.nombre}" (${payload.sku}) publicado exitosamente en el catálogo!`);
          this.resetForm();
          this.activeTab.set('inventory');
        },
        error: (err) => {
          this.isSubmittingProduct.set(false);
          const errorMsg =
            err.error?.message ||
            (err.status === 409 ? `El SKU '${payload.sku}' ya existe en el sistema` : 'Error al registrar el producto en el backend');
          alert('Error al publicar el producto: ' + errorMsg);
        },
      });
  }

  resetForm(): void {
    this.newSku.set('');
    this.newName.set('');
    this.newPrice.set(1990);
    this.newStock.set(100);
    this.newDescription.set('');
  }

  private showToast(msg: string): void {
    this.statusMessage.set(msg);
    setTimeout(() => this.statusMessage.set(null), 4000);
  }
}
