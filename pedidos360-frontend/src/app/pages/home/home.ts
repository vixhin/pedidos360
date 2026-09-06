import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CatalogService } from '../../core/services/catalog.service';
import { CartService } from '../../core/services/cart.service';
import { FavoritesService } from '../../core/services/favorites.service';
import { Product } from '../../core/models/catalog.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  readonly steps = [
    { icon: 'list' as const, title: 'Selecciona productos', text: 'Explora todas las categorías y arma tu pedido.' },
    { icon: 'package' as const, title: 'Agrupa todo', text: 'Unimos tus productos de distintas tiendas cercanas.' },
    { icon: 'truck' as const, title: 'Sigue la preparación', text: 'Te avisamos en cada etapa de tu pedido.' },
    { icon: 'home' as const, title: 'Recibe rápidamente', text: 'Entrega en la puerta de tu casa sin perder tiempo.' },
  ];

  constructor(
    readonly catalog: CatalogService,
    readonly cart: CartService,
    readonly favorites: FavoritesService
  ) {}

  filterByCategory(categoryId: string): void {
    this.catalog.selectCategory(categoryId);
  }

  showAllProducts(): void {
    this.catalog.selectCategory('TODAS');
    this.catalog.setSearchQuery('');
  }

  clearSearch(): void {
    this.catalog.setSearchQuery('');
  }

  addToCart(product: Product): void {
    this.cart.add(product);
  }
}
