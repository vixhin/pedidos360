import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { Product } from '../models/catalog.model';
import { AuthService } from './auth.service';
import { BackendUrlService } from './backend-url.service';

const FAVORITES_STORAGE_KEY = 'pedidos360_favorites';

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class FavoritesService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly urls = inject(BackendUrlService);

  private readonly _favorites = signal<Product[]>([]);

  readonly favorites = this._favorites.asReadonly();
  readonly favoriteCount = computed(() => this._favorites().length);
  readonly favoriteIds = computed(() => new Set(this._favorites().map((p) => p.id)));

  constructor() {
    this.restoreFavorites();

    // Reaccionar al cambio de usuario para sincronizar con PostgreSQL
    effect(() => {
      const user = this.auth.user();
      if (user?.id) {
        this.cargarFavoritosDeBackend(user.id);
      }
    });
  }

  isFavorite(productId: string): boolean {
    return this.favoriteIds().has(productId);
  }

  cargarFavoritosDeBackend(usuarioId: number): void {
    this.http
      .get<ApiResponse<any[]>>(`${this.urls.base('productos')}/favoritos/${usuarioId}`)
      .pipe(catchError(() => of(null)))
      .subscribe((res) => {
        if (res?.success && Array.isArray(res.data)) {
          const mapped: Product[] = res.data.map((dto) => ({
            id: String(dto.id),
            sku: dto.sku,
            name: dto.nombre,
            brand: (dto.categoria || '').replace(/_/g, ' '),
            price: dto.precio,
            icon: dto.categoria?.substring(0, 3) || 'P360',
            rating: dto.rating ?? 4.8,
            deliveryMinutes: dto.deliveryMinutes ?? 25,
            categoria: dto.categoria,
            descripcion: dto.descripcion,
            stock: dto.stock,
            imagenUrl: dto.imagenUrl,
          }));
          this._favorites.set(mapped);
          this.saveFavorites(mapped);
        }
      });
  }

  toggleFavorite(product: Product): void {
    const current = this._favorites();
    const exists = current.some((p) => p.id === product.id);
    let updated: Product[];

    const user = this.auth.user();

    if (exists) {
      updated = current.filter((p) => p.id !== product.id);
      this._favorites.set(updated);
      this.saveFavorites(updated);

      if (user?.id) {
        this.http
          .delete(`${this.urls.base('productos')}/favoritos/${user.id}/${product.id}`)
          .pipe(catchError(() => of(null)))
          .subscribe();
      }
    } else {
      updated = [product, ...current];
      this._favorites.set(updated);
      this.saveFavorites(updated);

      if (user?.id) {
        this.http
          .post(`${this.urls.base('productos')}/favoritos`, {
            usuarioId: user.id,
            productoId: Number(product.id),
          })
          .pipe(catchError(() => of(null)))
          .subscribe();
      }
    }
  }

  removeFavorite(productId: string): void {
    const updated = this._favorites().filter((p) => p.id !== productId);
    this._favorites.set(updated);
    this.saveFavorites(updated);

    const user = this.auth.user();
    if (user?.id) {
      this.http
        .delete(`${this.urls.base('productos')}/favoritos/${user.id}/${productId}`)
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
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
