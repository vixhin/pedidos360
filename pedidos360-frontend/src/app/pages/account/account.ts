import { ChangeDetectionStrategy, Component, effect, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Icon, IconName } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { Order, ORDER_STEPS } from '../../core/models/order.model';

type Section =
  | 'perfil' | 'resumen' | 'pedidos' | 'favoritos' | 'direcciones'
  | 'pagos' | 'preferencias' | 'ayuda' | 'seguridad';

interface NavItem {
  key: Section;
  label: string;
  icon: IconName;
}

interface Address {
  id: string;
  label: string;
  detail: string;
  isPrimary: boolean;
}

interface PaymentMethod {
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
export class Account {
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

  // Signals para edición de datos personales
  readonly editNombre = signal('');
  readonly editEmail = signal('');
  readonly editTelefono = signal('+56 9 8765 4321');
  readonly editDireccion = signal('Av. Providencia 1234, Depto 502, Santiago');

  // Signals para cambio de contraseña
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly showCurrentPassword = signal(false);
  readonly showNewPassword = signal(false);
  readonly showConfirmPassword = signal(false);

  readonly statusToast = signal<string | null>(null);
  readonly statusToastType = signal<'success' | 'error'>('success');

  readonly currentOrder: Order = {
    id: 'PED-360-0248',
    status: 'en_camino',
    etaMinutes: 32,
    itemsCount: 4,
    storesCount: 2,
    total: 122000,
    date: 'Hoy',
  };

  readonly pastOrders: Order[] = [
    { id: 'PED-360-0247', status: 'entregado', etaMinutes: 0, itemsCount: 5, storesCount: 1, total: 226000, date: '28-06-2026' },
    { id: 'PED-360-0241', status: 'entregado', etaMinutes: 0, itemsCount: 1, storesCount: 1, total: 228000, date: '27-06-2026' },
  ];

  readonly addresses: Address[] = [
    { id: 'a1', label: 'Casa', detail: 'Santiago Centro', isPrimary: true },
    { id: 'a2', label: 'Universidad', detail: 'Santiago Centro', isPrimary: false },
  ];

  readonly paymentMethods: PaymentMethod[] = [
    { id: 'pm1', brand: 'Visa crédito', last4: '4582', kind: 'Crédito' },
    { id: 'pm2', brand: 'Mastercard débito', last4: '9031', kind: 'Débito' },
  ];

  readonly stats = [
    { label: 'Pedidos realizados', value: '12', hint: 'Sigue descubriendo productos.', icon: 'package' as const },
    { label: 'Pedidos en camino', value: '1', hint: 'Llega en aprox. 32 min.', icon: 'truck' as const },
    { label: 'Productos favoritos', value: '8', hint: 'Disponibles para comprar rápido.', icon: 'heart' as const },
    { label: 'Beneficios disponibles', value: '3', hint: 'Activa tus promociones.', icon: 'gift' as const },
  ];

  readonly preferenceToggles = signal([
    { key: 'pedidos', label: 'Actualizaciones de pedidos', on: true },
    { key: 'promos', label: 'Promociones personalizadas', on: true },
    { key: 'favoritos', label: 'Productos favoritos', on: false },
    { key: 'cercania', label: 'Novedades cercanas', on: true },
  ]);

  constructor(readonly auth: AuthService, readonly catalog: CatalogService) {
    effect(() => {
      const u = this.auth.user();
      if (u) {
        this.editNombre.set(u.name || '');
        this.editEmail.set(u.email || '');
      }
    });
  }

  select(section: Section): void {
    this.activeSection.set(section);
  }

  stepIndex(status: Order['status']): number {
    return this.orderSteps.findIndex((s) => s.key === status);
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
    this.auth.updateUserProfile({
      name: this.editNombre().trim(),
      email: this.editEmail().trim(),
    });
    this.showToast('¡Tus datos personales fueron actualizados correctamente!', 'success');
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
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.showToast('¡Tu contraseña ha sido actualizada con éxito!', 'success');
  }

  private showToast(msg: string, type: 'success' | 'error'): void {
    this.statusToast.set(msg);
    this.statusToastType.set(type);
    setTimeout(() => this.statusToast.set(null), 4000);
  }
}
