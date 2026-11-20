import { ChangeDetectionStrategy, Component, computed, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CartService } from '../../core/services/cart.service';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { PedidoService, ClientOrderFromDB } from '../../core/services/pedido.service';

export interface DeliveryAddress {
  id: string;
  title: string;
  detail: string;
  comuna: string;
  isPrimary: boolean;
}

import { ChatComponent } from '../../shared/components/chat/chat.component';

@Component({
  selector: 'app-client',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe, FormsModule, ChatComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './client.html',
  styleUrl: './client.css',
})
export class Client implements OnInit {
  readonly activeTab = signal<'active-order' | 'history' | 'addresses'>('active-order');

  // ID del pedido seleccionado manualmente por el usuario
  readonly selectedOrderId = signal<string | null>(null);

  // Estado de cancelación
  readonly isCancelling = signal(false);

  // Pedidos activos en seguimiento real desde PostgreSQL
  readonly activeOrders = computed<ClientOrderFromDB[]>(() => {
    return this.pedidoSvc.pedidos().filter((p) => p.statusStep < 4 && p.estado !== 'CANCELADO');
  });

  // Pedido activo enfocado (prioriza el que esté 'EN CAMINO' para chatear o el seleccionado)
  readonly selectedActiveOrder = computed<ClientOrderFromDB | null>(() => {
    const list = this.activeOrders();
    if (list.length === 0) return null;
    const selId = this.selectedOrderId();
    if (selId) {
      const found = list.find((p) => p.id === selId);
      if (found) return found;
    }
    // Preferir pedidos 'EN CAMINO' (statusStep === 3) para habilitar chat de inmediato
    return list.find((p) => p.statusStep === 3) || list[0];
  });

  // Historial de compras reales del cliente (incluye entregados y cancelados)
  readonly pastOrders = computed<ClientOrderFromDB[]>(() => {
    return this.pedidoSvc.pedidos().filter((p) => p.statusStep === 4 || p.estado === 'CANCELADO');
  });

  // Lista de direcciones guardadas por el cliente
  readonly addresses = signal<DeliveryAddress[]>([]);

  // Modal para agregar dirección
  readonly showAddAddressModal = signal(false);
  readonly newAddressTitle = signal('');

  parseNumericId(idStr?: string | number): number {
    if (!idStr) return 0;
    if (typeof idStr === 'number') return idStr;
    return Number(idStr.replace(/[^0-9]/g, '')) || 0;
  }
  readonly newAddressStreet = signal('');
  readonly newAddressDepto = signal('');
  readonly newAddressComuna = signal('Santiago Centro');
  readonly newAddressNotes = signal('');

  // Toast de notificación
  readonly toastMessage = signal<string | null>(null);

  constructor(
    readonly auth: AuthService,
    readonly cart: CartService,
    readonly catalog: CatalogService,
    readonly pedidoSvc: PedidoService,
  ) {}

  ngOnInit(): void {
    this.pedidoSvc.cargarPedidosDeUsuario();
  }

  setTab(tab: 'active-order' | 'history' | 'addresses'): void {
    this.activeTab.set(tab);
  }

  toggleAddAddressModal(): void {
    this.showAddAddressModal.update((v) => !v);
  }

  saveNewAddress(): void {
    if (!this.newAddressStreet()) {
      alert('Por favor ingresa la calle y número de la dirección');
      return;
    }

    const title = this.newAddressTitle() || `Dirección en ${this.newAddressComuna()}`;
    const deptoStr = this.newAddressDepto() ? `, ${this.newAddressDepto()}` : '';
    const detail = `${this.newAddressStreet()}${deptoStr}, ${this.newAddressComuna()}`;

    const newAddr: DeliveryAddress = {
      id: `ADDR-${Date.now()}`,
      title,
      detail,
      comuna: this.newAddressComuna(),
      isPrimary: true,
    };

    this.addresses.update((current) => [
      newAddr,
      ...current.map((a) => ({ ...a, isPrimary: false })),
    ]);

    this.toastMessage.set(`¡Dirección "${title}" agregada correctamente como dirección activa!`);
    setTimeout(() => this.toastMessage.set(null), 4000);

    this.newAddressTitle.set('');
    this.newAddressStreet.set('');
    this.newAddressDepto.set('');
    this.newAddressNotes.set('');
    this.showAddAddressModal.set(false);
  }

  setPrimaryAddress(id: string): void {
    this.addresses.update((current) =>
      current.map((a) => ({ ...a, isPrimary: a.id === id }))
    );
    this.toastMessage.set('Dirección de entrega predeterminada actualizada');
    setTimeout(() => this.toastMessage.set(null), 3000);
  }

  selectOrder(orderId: string): void {
    this.selectedOrderId.set(orderId);
  }

  cancelarPedido(order: ClientOrderFromDB): void {
    if (!confirm(`¿Estás seguro de que deseas cancelar el pedido ${order.id}?`)) {
      return;
    }
    this.isCancelling.set(true);
    this.pedidoSvc.cancelarPedido(order.numericId).subscribe({
      next: (ok) => {
        this.isCancelling.set(false);
        if (ok) {
          this.toastMessage.set(`El pedido ${order.id} fue cancelado exitosamente.`);
          setTimeout(() => this.toastMessage.set(null), 4000);
        } else {
          alert('No se pudo cancelar el pedido. Verifica que no haya sido entregado.');
        }
      },
      error: (err) => {
        this.isCancelling.set(false);
        console.error('[Client] Error al cancelar pedido:', err);
        alert('Ocurrió un error al intentar cancelar el pedido.');
      }
    });
  }

  repeatOrder(order: ClientOrderFromDB): void {
    order.items.forEach((item) => {
      const match = this.catalog.products().find((p) => p.name === item.name);
      if (match) {
        this.cart.add(match);
      }
    });

    this.toastMessage.set(`¡${order.items.length} productos agregados al carrito!`);
    setTimeout(() => this.toastMessage.set(null), 4000);
  }
}
