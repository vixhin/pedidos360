import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { BackendUrlService } from '../../core/services/backend-url.service';
import { catchError, of } from 'rxjs';

export interface CourierOrder {
  id: number;
  usuarioId: number;
  total: number;
  estado: string;
  repartidorId?: number | null;
  fechaCreacion: string;
}

import { ChatComponent } from '../../shared/components/chat/chat.component';

@Component({
  selector: 'app-courier',
  standalone: true,
  imports: [CommonModule, Icon, CurrencyPipe, DatePipe, ChatComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './courier.html',
  styleUrl: './courier.css',
})
export class Courier implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(BackendUrlService);
  readonly auth = inject(AuthService);

  readonly activeTab = signal<'available' | 'active' | 'chat' | 'history'>('available');
  readonly orders = signal<CourierOrder[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // Pedidos disponibles para tomar (sin repartidor o listos para reparto)
  readonly availableOrders = computed(() =>
    this.orders().filter(
      (o) => (!o.repartidorId || o.repartidorId === 0) && (o.estado === 'PENDIENTE' || o.estado === 'EN_PREPARACION' || o.estado === 'LISTO')
    )
  );

  // Pedido activo asignado al repartidor actual
  readonly activeOrders = computed(() => {
    const currentUserId = this.auth.user()?.id;
    if (!currentUserId) return [];
    return this.orders().filter((o) => o.repartidorId === currentUserId && o.estado === 'EN_CAMINO');
  });

  // Historial de entregas completadas por el repartidor actual
  readonly historyOrders = computed(() => {
    const currentUserId = this.auth.user()?.id;
    if (!currentUserId) return [];
    return this.orders().filter((o) => o.repartidorId === currentUserId && o.estado === 'ENTREGADO');
  });

  ngOnInit(): void {
    this.cargarPedidos();
  }

  cargarPedidos(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.http
      .get<CourierOrder[]>(`${this.urls.base('pedidos')}`)
      .pipe(
        catchError((err) => {
          console.error('[Courier] Error al cargar pedidos:', err);
          this.errorMessage.set('No se pudo cargar la información de pedidos desde el backend.');
          return of([] as CourierOrder[]);
        })
      )
      .subscribe((data) => {
        this.isLoading.set(false);
        this.orders.set(data || []);
      });
  }

  aceptarPedido(orderId: number): void {
    const user = this.auth.user();
    if (!user?.id) {
      alert('Debes iniciar sesión con una cuenta de repartidor.');
      return;
    }

    const url = `${this.urls.base('pedidos')}/${orderId}/repartidor?repartidorId=${user.id}&nombreRepartidor=${encodeURIComponent(user.name)}`;
    this.http
      .put<CourierOrder>(url, {})
      .pipe(
        catchError((err) => {
          console.error('[Courier] Error al aceptar pedido:', err);
          alert('No se pudo asignar el pedido. Revisa el estado en el backend.');
          return of(null);
        })
      )
      .subscribe((updated) => {
        if (updated) {
          this.showToast(`¡Pedido #${orderId} asignado exitosamente!`);
          this.cargarPedidos();
          this.activeTab.set('active');
        }
      });
  }

  marcarEntregado(orderId: number): void {
    const url = `${this.urls.base('pedidos')}/${orderId}/estado?nuevoEstado=ENTREGADO`;
    this.http
      .put<CourierOrder>(url, {})
      .pipe(
        catchError((err) => {
          console.error('[Courier] Error al entregar pedido:', err);
          alert('No se pudo actualizar el estado del pedido.');
          return of(null);
        })
      )
      .subscribe((updated) => {
        if (updated) {
          this.showToast(`¡Pedido #${orderId} marcado como ENTREGADO!`);
          this.cargarPedidos();
          this.activeTab.set('history');
        }
      });
  }

  private showToast(msg: string): void {
    this.successMessage.set(msg);
    setTimeout(() => this.successMessage.set(null), 4000);
  }
}
