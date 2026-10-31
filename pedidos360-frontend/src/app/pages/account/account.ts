import { ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Icon, IconName } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { PedidoService, ClientOrderFromDB } from '../../core/services/pedido.service';
import { FavoritesService } from '../../core/services/favorites.service';
import { CartService } from '../../core/services/cart.service';
import { BackendUrlService } from '../../core/services/backend-url.service';
import { ORDER_STEPS } from '../../core/models/order.model';

type Section =
  | 'perfil' | 'resumen' | 'pedidos' | 'favoritos' | 'direcciones'
  | 'pagos' | 'preferencias' | 'ayuda' | 'seguridad';

interface NavItem {
  key: Section;
  label: string;
  icon: IconName;
}

export interface UserAddress {
  id: string;
  label: string;
  detail: string;
  isPrimary: boolean;
}

export interface UserPaymentMethod {
  id: string;
  brand: string;
  last4: string;
  kind: string;
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [Icon, DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.html',
  styleUrl: './account.css',
})
export class Account implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(BackendUrlService);
  readonly auth = inject(AuthService);
  readonly catalog = inject(CatalogService);
  readonly pedidoSvc = inject(PedidoService);
  readonly favorites = inject(FavoritesService);
  readonly cart = inject(CartService);

  readonly navItems: NavItem[] = [
    { key: 'perfil', label: 'Mi Perfil & Datos', icon: 'user' },
    { key: 'resumen', label: 'Resumen', icon: 'home' },
    { key: 'pedidos', label: 'Mis pedidos', icon: 'package' },
    { key: 'favoritos', label: 'Favoritos', icon: 'heart' },
    { key: 'direcciones', label: 'Direcciones', icon: 'map-pin' },
    { key: 'pagos', label: 'Métodos de pago', icon: 'credit-card' },
    { key: 'preferencias', label: 'Preferencias', icon: 'settings' },
    { key: 'ayuda', label: 'Ayuda y soporte', icon: 'help-circle' },
    { key: 'seguridad', label: 'Configuración de seguridad', icon: 'lock' },
  ];

  readonly activeSection = signal<Section>('perfil');
  readonly orderSteps = ORDER_STEPS;

  // Signals para edición de datos personales (cargados del usuario real)
  readonly editNombre = signal('');
  readonly editEmail = signal('');
  readonly editTelefono = signal('');
  readonly editDireccion = signal('');

  // Signals para cambio de contraseña
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly showCurrentPassword = signal(false);
  readonly showNewPassword = signal(false);
  readonly showConfirmPassword = signal(false);

  readonly statusToast = signal<string | null>(null);
  readonly statusToastType = signal<'success' | 'error'>('success');
  readonly isSavingProfile = signal(false);
  readonly isChangingPassword = signal(false);

  // Pedidos reales sincronizados desde PostgreSQL
  readonly currentOrder = computed<ClientOrderFromDB | null>(() => {
    const list = this.pedidoSvc.pedidos();
    return list.find((p) => p.statusStep < 4) || null;
  });

  readonly pastOrders = computed<ClientOrderFromDB[]>(() => {
    return this.pedidoSvc.pedidos().filter((p) => p.statusStep === 4);
  });

  // Direcciones reales del usuario
  readonly addresses = signal<UserAddress[]>([]);

  // Métodos de pago reales (inicialmente vacíos si el usuario no tiene registrados)
  readonly paymentMethods = signal<UserPaymentMethod[]>([]);

  // Estadísticas reales calculadas exclusivamente desde datos del backend
  readonly stats = computed(() => {
    const totalPedidos = this.pedidoSvc.pedidos().length;
    const enCamino = this.pedidoSvc.pedidos().filter((p) => p.statusStep === 3).length;
    const favCount = this.favorites.favoriteCount();
    const cartCount = this.cart.itemCount();

    return [
      {
        label: 'Pedidos realizados',
        value: String(totalPedidos),
        hint: totalPedidos > 0 ? 'Historial registrado en sistema.' : 'Aún no realizas pedidos.',
        icon: 'package' as const,
      },
      {
        label: 'Pedidos en camino',
        value: String(enCamino),
        hint: enCamino > 0 ? 'Repartidor asignado en ruta.' : 'Sin entregas pendientes.',
        icon: 'truck' as const,
      },
      {
        label: 'Productos favoritos',
        value: String(favCount),
        hint: 'Guardados en tu lista personal.',
        icon: 'heart' as const,
      },
      {
        label: 'Artículos en carrito',
        value: String(cartCount),
        hint: 'Listos para finalizar compra.',
        icon: 'gift' as const,
      },
    ];
  });

  readonly preferenceToggles = signal([
    { key: 'pedidos', label: 'Actualizaciones de pedidos', on: true },
    { key: 'promos', label: 'Promociones personalizadas', on: true },
    { key: 'favoritos', label: 'Productos favoritos', on: false },
    { key: 'cercania', label: 'Novedades cercanas', on: true },
  ]);

  constructor() {
    effect(() => {
      const u = this.auth.user();
      if (u) {
        this.editNombre.set(u.name || '');
        this.editEmail.set(u.email || '');
      }
    });
  }

  ngOnInit(): void {
    this.cargarDatosReales();
  }

  cargarDatosReales(): void {
    const u = this.auth.user();
    if (!u) return;

    // Cargar pedidos de usuario
    this.pedidoSvc.cargarPedidosDeUsuario();

    // Cargar perfil completo desde backend para teléfono y dirección
    const userUrl = u.id
      ? `${this.urls.base('usuario')}/${u.id}`
      : `${this.urls.base('usuario')}/me`;

    this.http.get<{ success: boolean; data: any }>(userUrl).subscribe({
      next: (res) => {
        if (res && res.data) {
          const data = res.data;
          if (data.nombre) this.editNombre.set(data.nombre);
          if (data.email) this.editEmail.set(data.email);
          if (data.telefono) this.editTelefono.set(data.telefono);
          if (data.direccion) {
            this.editDireccion.set(data.direccion);
            this.addresses.set([
              {
                id: 'addr-main',
                label: 'Dirección Principal',
                detail: data.direccion,
                isPrimary: true,
              },
            ]);
          } else {
            this.addresses.set([]);
          }
        }
      },
      error: () => {
        // En caso de que aún no exista perfil en usuario-service (ej. usuario Entra nuevo)
      },
    });
  }

  select(section: Section): void {
    this.activeSection.set(section);
  }

  stepIndex(statusStep: number): number {
    return Math.max(0, statusStep - 1);
  }

  togglePreference(key: string): void {
    this.preferenceToggles.update((list) =>
      list.map((p) => (p.key === key ? { ...p, on: !p.on } : p))
    );
  }

  saveProfileData(): void {
    if (!this.editNombre().trim()) {
      this.showToast('El nombre no puede estar vacío.', 'error');
      return;
    }

    const u = this.auth.user();
    if (!u) {
      this.showToast('Debes iniciar sesión para actualizar tu perfil.', 'error');
      return;
    }

    const payload = {
      nombre: this.editNombre().trim(),
      email: this.editEmail().trim(),
      telefono: this.editTelefono().trim() || null,
      direccion: this.editDireccion().trim() || null,
    };

    const targetUrl = u.id
      ? `${this.urls.base('usuario')}/${u.id}`
      : `${this.urls.base('usuario')}/me`;

    this.isSavingProfile.set(true);

    this.http.put<{ success: boolean; message: string; data: any }>(targetUrl, payload).subscribe({
      next: (res) => {
        this.isSavingProfile.set(false);
        this.auth.updateUserProfile({
          name: payload.nombre,
          email: payload.email,
        });

        if (payload.direccion) {
          this.addresses.set([
            {
              id: 'addr-main',
              label: 'Dirección Principal',
              detail: payload.direccion,
              isPrimary: true,
            },
          ]);
        } else {
          this.addresses.set([]);
        }

        this.showToast('¡Tus datos personales fueron actualizados correctamente en el servidor!', 'success');
      },
      error: (err) => {
        this.isSavingProfile.set(false);
        const errorMsg = err.error?.message || 'Error de conexión al actualizar el perfil en el backend.';
        this.showToast(errorMsg, 'error');
      },
    });
  }

  changePassword(): void {
    if (!this.currentPassword()) {
      this.showToast('Ingresa tu contraseña actual.', 'error');
      return;
    }
    if (!this.newPassword() || this.newPassword().length < 6) {
      this.showToast('La nueva contraseña debe tener al menos 6 caracteres.', 'error');
      return;
    }
    if (this.newPassword() !== this.confirmPassword()) {
      this.showToast('La confirmación de contraseña no coincide.', 'error');
      return;
    }

    const u = this.auth.user();
    if (!u) {
      this.showToast('Debes iniciar sesión para cambiar la contraseña.', 'error');
      return;
    }

    const payload = {
      currentPassword: this.currentPassword(),
      newPassword: this.newPassword(),
    };

    const targetUrl = u.id
      ? `${this.urls.base('usuario')}/${u.id}/password`
      : `${this.urls.base('usuario')}/me/password`;

    this.isChangingPassword.set(true);

    this.http.put<{ success: boolean; message: string }>(targetUrl, payload).subscribe({
      next: (res) => {
        this.isChangingPassword.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.showToast(res.message || '¡Tu contraseña ha sido actualizada con éxito en el servidor!', 'success');
      },
      error: (err) => {
        this.isChangingPassword.set(false);
        const errorMsg = err.error?.message || 'No se pudo actualizar la contraseña en el servidor.';
        this.showToast(errorMsg, 'error');
      },
    });
  }

  private showToast(msg: string, type: 'success' | 'error'): void {
    this.statusToast.set(msg);
    this.statusToastType.set(type);
    setTimeout(() => this.statusToast.set(null), 5000);
  }
}
