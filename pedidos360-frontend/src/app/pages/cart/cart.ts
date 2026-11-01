import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CartService } from '../../core/services/cart.service';
import { CatalogService } from '../../core/services/catalog.service';
import { CotizacionItemRequest, CotizacionResponse, CuponItem, PedidoService } from '../../core/services/pedido.service';
import { AuthService } from '../../core/services/auth.service';
import { Product } from '../../core/models/catalog.model';

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
  readonly couponMessage = signal<string | null>(null);
  readonly couponError = signal<string | null>(null);
  readonly cuponesDisponibles = signal<CuponItem[]>([]);
  readonly deliveryOption = signal<'ahora' | 'programar'>('ahora');
  readonly selectedAddress = signal('a1');
  readonly selectedComuna = signal('Santiago');

  readonly cotizacion = signal<CotizacionResponse | null>(null);

  readonly addresses = [
    { id: 'a1', label: 'Dirección Principal', detail: 'Santiago Centro, Chile' },
  ];

  readonly shippingCost = computed(() =>
    this.cotizacion()?.costoEnvio ?? (this.cart.subtotal() >= 20000 || this.cart.subtotal() === 0 ? 0 : 2990)
  );

  readonly serviceFee = computed(() =>
    this.cotizacion()?.tarifaServicio ?? 1500
  );

  readonly appliedDiscount = computed(() =>
    this.cotizacion()?.descuentoCupon ?? 0
  );

  readonly total = computed(() =>
    this.cotizacion()?.total ?? Math.max(this.cart.subtotal() + this.serviceFee() + this.shippingCost() - this.appliedDiscount(), 0)
  );

  readonly amountToFreeShipping = computed(() =>
    this.cotizacion()?.montoFaltanteParaEnvioGratis ?? Math.max(20000 - this.cart.subtotal(), 0)
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
  ) {
    // Re-cotizar automáticamente con el motor comercial backend cuando cambian los ítems del carrito
    effect(() => {
      const lines = this.cart.lines();
      if (lines.length > 0) {
        this.actualizarCotizacion();
      } else {
        this.cotizacion.set(null);
      }
    });

    this.cargarCuponesDisponibles();
  }

  cargarCuponesDisponibles(): void {
    this.pedidoSvc.obtenerCuponesActivos().subscribe({
      next: (cupones) => this.cuponesDisponibles.set(cupones || []),
      error: () => this.cuponesDisponibles.set([])
    });
  }

  aplicarCuponSugerido(codigo: string): void {
    this.couponCode.set(codigo);
    this.actualizarCotizacion();
  }

  addSuggestion(product: Product): void {
    this.cart.add(product);
  }

  actualizarCotizacion(): void {
    const lines = this.cart.lines();
    if (lines.length === 0) {
      this.cotizacion.set(null);
      return;
    }

    const items: CotizacionItemRequest[] = lines.map((l) => ({
      productoId: Number(String(l.product.id).replace(/\D/g, '') || 1),
      nombre: l.product.name,
      cantidad: l.quantity,
      precioUnitario: l.product.price,
    }));

    this.pedidoSvc
      .cotizar({
        items,
        codigoCupon: this.couponCode().trim() || undefined,
        comuna: this.selectedComuna(),
      })
      .subscribe({
        next: (cot) => {
          this.cotizacion.set(cot);
          if (cot.mensajeCupon) {
            if (cot.descuentoCupon > 0) {
              this.couponMessage.set(cot.mensajeCupon);
              this.couponError.set(null);
            } else {
              this.couponError.set(cot.mensajeCupon);
              this.couponMessage.set(null);
            }
          } else {
            this.couponMessage.set(null);
            this.couponError.set(null);
          }
        },
        error: (err) => {
          console.warn('[Cart] Error al cotizar en backend:', err);
        },
      });
  }

  applyCoupon(): void {
    this.actualizarCotizacion();
  }

  confirmarPedido(): void {
    if (this.cart.itemCount() === 0) return;

    if (!this.auth.isLoggedIn()) {
      alert('Debes iniciar sesión con tu cuenta para confirmar el pedido.');
      return;
    }

    const cartLines = this.cart.lines().map((l) => ({
      productId: l.product.id,
      name: l.product.name,
      icon: l.product.icon,
      quantity: l.quantity,
      price: l.product.price,
    }));

    this.pedidoSvc
      .crearPedidoDesdeCarrito(
        cartLines,
        this.total(),
        this.cart.subtotal(),
        this.shippingCost(),
        this.serviceFee(),
        this.addresses[0]?.detail || 'Santiago, Chile'
      )
      .subscribe({
        next: (created) => {
          if (created) {
            this.confirmedOrderId.set(`PED-${created.id}`);
            this.cart.clear();
            this.orderSuccess.set(true);
            this.catalog.cargarProductosDeBackend();
          } else {
            alert('No se pudo confirmar el pedido en el servidor. Por favor verifica que tu sesión esté activa.');
          }
        },
        error: (err) => {
          console.error('[Cart] Error al confirmar pedido:', err);
          alert('Ocurrió un error al procesar el pedido. Intente nuevamente.');
        },
      });
  }
}
