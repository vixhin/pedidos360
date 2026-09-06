import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { Icon } from '../../shared/icon/icon';
import { MapModal } from '../../shared/map-modal/map-modal';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { CatalogService } from '../../core/services/catalog.service';
import { FavoritesService } from '../../core/services/favorites.service';
import { NotificationsService } from '../../core/services/notifications.service';
import { NotificationCategory } from '../../core/models/notification.model';
import { Product } from '../../core/models/catalog.model';

interface NotifTab {
  key: NotificationCategory | 'todas';
  label: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, Icon, MapModal, DecimalPipe],
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
  readonly favoritesOpen = signal(false);
  readonly mapOpen = signal(false);
  readonly searchFocused = signal(false);
  readonly currentLocation = signal('Santiago Centro');

  readonly filteredNotifications = computed(() => {
    const tab = this.activeTab();
    const all = this.notifications.items();
    return tab === 'todas' ? all : all.filter((n) => n.category === tab);
  });

  constructor(
    readonly auth: AuthService,
    readonly cart: CartService,
    readonly catalog: CatalogService,
    readonly favorites: FavoritesService,
    readonly notifications: NotificationsService,
    private router: Router
  ) {}

  onSearch(event: Event): void {
    const query = (event.target as HTMLInputElement).value;
    this.catalog.setSearchQuery(query);
  }

  clearSearch(): void {
    this.catalog.setSearchQuery('');
  }

  selectSearchProduct(product: Product): void {
    this.cart.add(product);
    this.catalog.setSearchQuery('');
    this.searchFocused.set(false);
    this.router.navigate(['/carrito']);
  }

  toggleFavorites(): void {
    this.closeMenus();
    this.favoritesOpen.update((v) => !v);
  }

  toggleNotifications(): void {
    this.closeMenus();
    this.notifOpen.update((v) => !v);
  }

  toggleProfile(): void {
    this.closeMenus();
    this.profileOpen.update((v) => !v);
  }

  openMapModal(): void {
    this.closeMenus();
    this.mapOpen.set(true);
  }

  onSelectLocation(location: string): void {
    this.currentLocation.set(location);
  }

  closeMenus(): void {
    this.notifOpen.set(false);
    this.profileOpen.set(false);
    this.favoritesOpen.set(false);
  }

  logout(): void {
    this.closeMenus();
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
