import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { NotificationsService } from '../../core/services/notifications.service';
import { NotificationCategory } from '../../core/models/notification.model';

interface NotifTab {
  key: NotificationCategory | 'todas';
  label: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {
  readonly notifTabs: NotifTab[] = [
    { key: 'todas', label: 'Todas' },
    { key: 'pedidos', label: 'Mis pedidos' },
    { key: 'ofertas', label: 'Ofertas y beneficios' },
    { key: 'productos', label: 'Productos y novedades' },
    { key: 'cuenta', label: 'Cuenta y seguridad' },
  ];

  readonly activeTab = signal<NotifTab['key']>('todas');
  readonly notifOpen = signal(false);
  readonly profileOpen = signal(false);

  readonly filteredNotifications = computed(() => {
    const tab = this.activeTab();
    const all = this.notifications.items();
    return tab === 'todas' ? all : all.filter((n) => n.category === tab);
  });

  constructor(
    readonly auth: AuthService,
    readonly cart: CartService,
    readonly notifications: NotificationsService,
    private router: Router
  ) {}

  toggleNotifications(): void {
    this.profileOpen.set(false);
    this.notifOpen.update((v) => !v);
  }

  toggleProfile(): void {
    this.notifOpen.set(false);
    this.profileOpen.update((v) => !v);
  }

  closeMenus(): void {
    this.notifOpen.set(false);
    this.profileOpen.set(false);
  }

  setTab(tab: NotifTab['key']): void {
    this.activeTab.set(tab);
  }

  goToNotifications(): void {
    this.closeMenus();
    this.router.navigate(['/notificaciones']);
  }

  logout(): void {
    this.auth.logout();
    this.closeMenus();
    this.router.navigate(['/']);
  }
}
