import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { Icon } from '../../shared/icon/icon';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { Product } from '../../core/models/catalog.model';
import { API_CONFIG } from '../../core/config/api.config';

export interface IncomingOrder {
  id: string;
  customerName: string;
  address: string;
  itemsCount: number;
  total: number;
  status: 'PENDIENTE' | 'EN_PREPARACION' | 'EN_CAMINO' | 'ENTREGADO';
  time: string;
}

interface PedidoRaw {
  id: number;
  usuarioId: number;
  total: number;
  estado: string;
  fechaCreacion: string;
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

  // Mensaje de notificación
  readonly statusMessage = signal<string | null>(null);

  // Pedidos cargados desde el backend
  readonly incomingOrders = signal<IncomingOrder[]>([]);
  readonly isLoadingOrders = signal(false);

  // Productos con stock bajo (< 50)
  readonly lowStockProducts = computed(() =>
    this.catalog.products().filter((p) => (p.stock || 0) < 50)
  );

  ngOnInit(): void {
    this.cargarPedidos();
  }

  cargarPedidos(): void {
    this.isLoadingOrders.set(true);
    this.http
      .get<PedidoRaw[]>(`${API_CONFIG.pedidos}/pedidos`)
      .pipe(catchError(() => of([] as PedidoRaw[])))
      .subscribe((data) => {
        this.isLoadingOrders.set(false);
        const orders: IncomingOrder[] = (data || []).map((p) => ({
          id: `PED-${p.id}`,
          customerName: `Usuario #${p.usuarioId}`,
          address: 'Dirección no disponible',
          itemsCount: 1,
          total: p.total,
          status: this.mapEstado(p.estado),
          time: p.fechaCreacion
            ? this.timeAgo(new Date(p.fechaCreacion))
            : 'Reciente',
        }));
        // Sort newest first
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
    this.incomingOrders.update((orders) =>
      orders.map((o) => (o.id === orderId ? { ...o, status: nextStatus } : o))
    );
    this.showToast(`Estado del pedido ${orderId} actualizado a ${nextStatus}`);
  }

  createProduct(): void {
    if (!this.newSku() || !this.newName() || !this.newPrice()) {
      alert('Por favor completa los campos requeridos SKU, Nombre y Precio');
      return;
    }

    const payload = {
      sku: this.newSku(),
      nombre: this.newName(),
      categoria: this.newCategory(),
      precio: Number(this.newPrice()),
      stock: Number(this.newStock()),
      descripcion: this.newDescription() || 'Producto agregado por el vendedor',
    };

    this.http
      .post(`${API_CONFIG.productos}/productos`, payload)
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.catalog.cargarProductosDeBackend();
        this.showToast(`Producto "${this.newName()}" publicado en el catálogo.`);
        this.resetForm();
        this.activeTab.set('inventory');
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
