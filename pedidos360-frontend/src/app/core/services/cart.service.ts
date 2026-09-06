import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { Product } from '../models/catalog.model';
import { API_CONFIG } from '../config/api.config';

export interface CartLine {
  product: Product;
  quantity: number;
  backendId?: number; // ID del item en la tabla carrito_items
}

interface CarritoItemDB {
  id: number;
  usuarioId: number;
  productoId: number;
  cantidad: number;
  precioUnitario: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly _lines = signal<CartLine[]>([]);
  private _usuarioId: number | null = null;

  readonly lines = this._lines.asReadonly();

  readonly itemCount = computed(() =>
    this._lines().reduce((total, line) => total + line.quantity, 0)
  );

  readonly subtotal = computed(() =>
    this._lines().reduce((total, line) => total + line.product.price * line.quantity, 0)
  );

  /**
   * Set the current user ID and load their cart from the backend.
   * Called from AuthService / AppComponent after login.
   */
  setUsuario(usuarioId: number | null, productos?: Product[]): void {
    this._usuarioId = usuarioId;
    if (usuarioId) {
      this.cargarCarritoDesdeBackend(usuarioId, productos || []);
    } else {
      this._lines.set([]);
    }
  }

  cargarCarritoDesdeBackend(usuarioId: number, productos: Product[]): void {
    this.http
      .get<CarritoItemDB[]>(`${API_CONFIG.carrito}/carrito/usuario/${usuarioId}`)
      .pipe(catchError(() => of([] as CarritoItemDB[])))
      .subscribe((items) => {
        if (!items || items.length === 0) return;
        const lines: CartLine[] = [];
        for (const item of items) {
          const product = productos.find((p) => p.id === String(item.productoId));
          if (product) {
            lines.push({ product, quantity: item.cantidad, backendId: item.id });
          }
        }
        this._lines.set(lines);
      });
  }

  add(product: Product): void {
    const lines = this._lines();
    const existing = lines.find((l) => l.product.id === product.id);
    if (existing) {
      this.setQuantity(product.id, existing.quantity + 1);
      return;
    }
    const newLine: CartLine = { product, quantity: 1 };
    this._lines.set([...lines, newLine]);

    // Sincronizar con backend si hay usuario
    if (this._usuarioId) {
      this.http
        .post<CarritoItemDB>(`${API_CONFIG.carrito}/carrito`, {
          usuarioId: this._usuarioId,
          productoId: Number(product.id),
          cantidad: 1,
          precioUnitario: product.price,
        })
        .pipe(catchError(() => of(null)))
        .subscribe((res) => {
          if (res) {
            // Actualizar backendId
            this._lines.update((ls) =>
              ls.map((l) =>
                l.product.id === product.id ? { ...l, backendId: res.id } : l
              )
            );
          }
        });
    }
  }

  setQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(productId);
      return;
    }
    this._lines.set(
      this._lines().map((l) => (l.product.id === productId ? { ...l, quantity } : l))
    );
    // No hay endpoint PATCH en el backend, así que solo actualizamos localmente
  }

  remove(productId: string): void {
    const line = this._lines().find((l) => l.product.id === productId);
    this._lines.set(this._lines().filter((l) => l.product.id !== productId));

    // Eliminar del backend si tiene ID
    if (this._usuarioId && line?.backendId) {
      this.http
        .delete(`${API_CONFIG.carrito}/carrito/${line.backendId}`)
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
  }

  clear(): void {
    this._lines.set([]);
    // Vaciar carrito en el backend
    if (this._usuarioId) {
      this.http
        .delete(`${API_CONFIG.carrito}/carrito/usuario/${this._usuarioId}`)
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
  }
}
