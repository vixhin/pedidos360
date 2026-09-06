import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
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

@Component({
  selector: 'app-client',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './client.html',
  styleUrl: './client.css',
})
export class Client implements OnInit {
  readonly activeTab = signal<'active-order' | 'history' | 'addresses'>('active-order');

  // Pedido activo en seguimiento en vivo
  readonly activeOrder = signal<ClientOrder>({
    id: 'PED-360-9921',
    date: 'Hoy, 13:40 hs',
    items: [
      { name: 'Leche Entera Colun 1L', icon: 'LAC', quantity: 2, price: 1190 },
      { name: 'Coca-Cola Sabor Original 2.5L', icon: 'BEB', quantity: 1, price: 2490 },
      { name: 'Arroz Grado 1 Tucapel 1kg', icon: 'ABA', quantity: 1, price: 1590 },
    ],
    total: 6460,
    statusStep: 3, // En camino
    statusLabel: 'Repartidor en camino a tu domicilio',
    deliveryEta: '15-20 min',
    courierName: 'Rodrigo M. (Moto)',
    courierPhone: '+56 9 8765 4321',
  });

  // Historial de compras del cliente
  readonly pastOrders = signal<ClientOrder[]>([
    {
      id: 'PED-360-8410',
      date: '02/09/2026',
      items: [
        { name: 'Pechuga de Pollo Ariztía 1kg', icon: 'CAR', quantity: 1, price: 5990 },
        { name: 'Pan Molde Blanco Ideal 560g', icon: 'PAN', quantity: 1, price: 2190 },
      ],
      total: 8180,
      statusStep: 4,
      statusLabel: 'Entregado exitosamente',
      deliveryEta: 'Completado',
      courierName: 'Carlos G.',
      courierPhone: '+56 9 1122 3344',
    },
    {
      id: 'PED-360-7812',
      date: '28/08/2026',
      items: [
        { name: 'Detergente Líquido Omo 3L', icon: 'LIM', quantity: 1, price: 8990 },
        { name: 'Papel Higiénico Elite 8 Rollos', icon: 'LIM', quantity: 1, price: 4590 },
      ],
      total: 13580,
      statusStep: 4,
      statusLabel: 'Entregado exitosamente',
      deliveryEta: 'Completado',
      courierName: 'Andrea P.',
      courierPhone: '+56 9 9988 7766',
    },
  ]);

  // Lista de direcciones guardadas
  readonly addresses = signal<DeliveryAddress[]>([
    { id: 'ADDR-1', title: 'Santiago Centro - Casa Principal', detail: 'Av. Providencia 1240, Departamento 402, Santiago', comuna: 'Santiago Centro', isPrimary: true },
    { id: 'ADDR-2', title: 'Oficina Providencia', detail: 'Av. Andrés Bello 2451, Piso 8, Providencia', comuna: 'Providencia', isPrimary: false },
  ]);

  // Modal para agregar dirección
  readonly showAddAddressModal = signal(false);
  readonly newAddressTitle = signal('');
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
