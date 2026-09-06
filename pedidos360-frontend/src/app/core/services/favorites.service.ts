import { Injectable, computed, signal } from '@angular/core';
import { Product } from '../models/catalog.model';

const FAVORITES_STORAGE_KEY = 'pedidos360_favorites';

@Injectable({ providedIn: 'root' })
export class FavoritesService {
  private readonly _favorites = signal<Product[]>([]);

  readonly favorites = this._favorites.asReadonly();
  readonly favoriteCount = computed(() => this._favorites().length);
  readonly favoriteIds = computed(() => new Set(this._favorites().map((p) => p.id)));

  constructor() {
    this.restoreFavorites();
  }

  isFavorite(productId: string): boolean {
    return this.favoriteIds().has(productId);
  }

  toggleFavorite(product: Product): void {
    const current = this._favorites();
    const exists = current.some((p) => p.id === product.id);
    let updated: Product[];

    if (exists) {
      updated = current.filter((p) => p.id !== product.id);
    } else {
      updated = [product, ...current];
    }

    this._favorites.set(updated);
    this.saveFavorites(updated);
  }

  removeFavorite(productId: string): void {
    const updated = this._favorites().filter((p) => p.id !== productId);
    this._favorites.set(updated);
    this.saveFavorites(updated);
  }

  clearFavorites(): void {
    this._favorites.set([]);
    this.saveFavorites([]);
  }

  private saveFavorites(list: Product[]): void {
    try {
      localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(list));
    } catch {
      // localStorage no disponible
    }
  }

  private restoreFavorites(): void {
    try {
      const raw = localStorage.getItem(FAVORITES_STORAGE_KEY);
      if (raw) {
        const list = JSON.parse(raw) as Product[];
        this._favorites.set(list);
      }
    } catch {
      // Error leyendo storage
    }
  }
}
