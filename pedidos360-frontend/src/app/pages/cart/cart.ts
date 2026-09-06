import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CartService } from '../../core/services/cart.service';
import { CatalogService } from '../../core/services/catalog.service';
import { PedidoService } from '../../core/services/pedido.service';
import { AuthService } from '../../core/services/auth.service';
import { Product } from '../../core/models/catalog.model';

const FREE_SHIPPING_THRESHOLD = 20000;
const SERVICE_FEE = 1500;

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class Cart {
  readonly couponCode = signal('');
  readonly appliedDiscount = signal(0);
  readonly deliveryOption = signal<'ahora' | 'programar'>('ahora');
  readonly selectedAddress = signal('a1');

  readonly addresses = [
    { id: 'a1', label: 'Casa · Santiago Centro', detail: 'Av. Ejemplo 1234, Depto. 506' },
    { id: 'a2', label: 'Universidad · Santiago Centro', detail: 'Sede Duoc UC' },
  ];

  readonly shippingCost = computed(() =>
    this.cart.subtotal() >= FREE_SHIPPING_THRESHOLD || this.cart.subtotal() === 0 ? 0 : 2990
  );

  readonly total = computed(() =>
    Math.max(this.cart.subtotal() + SERVICE_FEE + this.shippingCost() - this.appliedDiscount(), 0)
  );

  readonly amountToFreeShipping = computed(() =>
    Math.max(FREE_SHIPPING_THRESHOLD - this.cart.subtotal(), 0)
  );

  readonly suggestions = computed(() =>
    this.catalog.products().filter((p) => !this.cart.lines().some((l) => l.product.id === p.id)).slice(0, 4)
  );

  readonly orderSuccess = signal(false);
  readonly confirmedOrderId = signal<string | null>(null);

  constructor(
    readonly cart: CartService,
    readonly catalog: CatalogService,
    readonly pedidoSvc: PedidoService,
    readonly auth: AuthService,
  ) {}

  addSuggestion(product: Product): void {
    this.cart.add(product);
  }

  applyCoupon(): void {
    if (this.couponCode().trim().toUpperCase() === 'BIENVENIDA360') {
      this.appliedDiscount.set(2000);
    } else {
      this.appliedDiscount.set(0);
    }
  }

  confirmarPedido(): void {
    if (this.cart.itemCount() === 0) return;

    const cartLines = this.cart.lines().map((l) => ({
      name: l.product.name,
      icon: l.product.icon,
      quantity: l.quantity,
      price: l.product.price,
    }));

    this.pedidoSvc.crearPedidoDesdeCarrito(cartLines);
    // Wait a tick for the service to update lastOrderId, then clear cart and show success
    setTimeout(() => {
      this.confirmedOrderId.set(this.pedidoSvc.lastOrderId());
      this.cart.clear();
      this.orderSuccess.set(true);
    }, 600);
  }
}
