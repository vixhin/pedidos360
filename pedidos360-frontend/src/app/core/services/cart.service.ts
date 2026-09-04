import { Injectable, computed, signal } from '@angular/core';
import { Product } from '../models/catalog.model';

export interface CartLine {
  product: Product;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly _lines = signal<CartLine[]>([]);

  readonly lines = this._lines.asReadonly();

  readonly itemCount = computed(() =>
    this._lines().reduce((total, line) => total + line.quantity, 0)
  );

  readonly subtotal = computed(() =>
    this._lines().reduce((total, line) => total + line.product.price * line.quantity, 0)
  );

  add(product: Product): void {
    const lines = this._lines();
    const existing = lines.find((l) => l.product.id === product.id);
    if (existing) {
      this.setQuantity(product.id, existing.quantity + 1);
      return;
    }
    this._lines.set([...lines, { product, quantity: 1 }]);
  }

  setQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(productId);
      return;
    }
    this._lines.set(
      this._lines().map((l) => (l.product.id === productId ? { ...l, quantity } : l))
    );
  }

  remove(productId: string): void {
    this._lines.set(this._lines().filter((l) => l.product.id !== productId));
  }

  clear(): void {
    this._lines.set([]);
  }
}
