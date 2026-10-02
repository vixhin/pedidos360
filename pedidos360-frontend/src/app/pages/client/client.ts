import { ChangeDetectionStrategy, Component, computed, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CartService } from '../../core/services/cart.service';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { PedidoService } from '../../core/services/pedido.service';

export interface ClientOrder {
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

  // Pedido activo en seguimiento real desde PostgreSQL
  readonly activeOrder = computed<ClientOrder | null>(() => {
    const list = this.pedidoSvc.pedidos();
    return list.find((p) => p.statusStep < 4) || null;
  });

  // Historial de compras reales del cliente
  readonly pastOrders = computed<ClientOrder[]>(() => {
    return this.pedidoSvc.pedidos().filter((p) => p.statusStep === 4);
  });

  // Lista de direcciones guardadas por el cliente
  readonly addresses = signal<DeliveryAddress[]>([]);

  // Modal para agregar dirección
  readonly showAddAddressModal = signal(false);
  readonly newAddressTitle = signal('');

  parseNumericId(idStr?: string): number {
    if (!idStr) return 0;
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

  repeatOrder(order: ClientOrder): void {
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
