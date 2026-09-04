import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Icon } from '../../shared/icon/icon';
import { NotificationsService } from '../../core/services/notifications.service';
import { NotificationCategory } from '../../core/models/notification.model';

type Tab = NotificationCategory | 'todas';
type ReadFilter = 'todas' | 'no_leidas' | 'leidas';
type TimeFilter = 'hoy' | 'semana';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notifications.html',
  styleUrl: './notifications.css',
})
export class Notifications {
  readonly tabs: { key: Tab; label: string }[] = [
    { key: 'todas', label: 'Todas' },
    { key: 'pedidos', label: 'Pedidos' },
    { key: 'ofertas', label: 'Promociones y beneficios' },
    { key: 'productos', label: 'Productos y disponibilidad' },
    { key: 'cuenta', label: 'Cuenta y seguridad' },
  ];

  readonly activeTab = signal<Tab>('todas');
  readonly readFilter = signal<ReadFilter>('todas');
  readonly timeFilter = signal<TimeFilter>('hoy');

  readonly alertToggles = signal([
    { key: 'pedidos', label: 'Actualizaciones de pedidos', on: true },
    { key: 'promos', label: 'Promociones personalizadas', on: true },
    { key: 'favoritos', label: 'Productos favoritos', on: true },
    { key: 'cercania', label: 'Novedades cercanas', on: true },
  ]);

  readonly filtered = computed(() => {
    let list = this.notifications.items();
    const tab = this.activeTab();
    if (tab !== 'todas') {
      list = list.filter((n) => n.category === tab);
    }
    const rf = this.readFilter();
    if (rf === 'no_leidas') list = list.filter((n) => !n.read);
    if (rf === 'leidas') list = list.filter((n) => n.read);
    return list;
  });

  constructor(readonly notifications: NotificationsService) {}

  setTab(tab: Tab): void {
    this.activeTab.set(tab);
  }

  toggleAlert(key: string): void {
    this.alertToggles.update((list) => list.map((a) => (a.key === key ? { ...a, on: !a.on } : a)));
  }
}
