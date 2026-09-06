export interface Category {
  id: string;
  name: string;
  icon: string;
}

export interface Product {
  id: string;
  sku?: string;
  name: string;
  brand: string;
  price: number;
  icon: string;
  rating: number;
  deliveryMinutes: number;
  discountPercent?: number;
  categoria?: string;
  descripcion?: string;
  stock?: number;
  imagenUrl?: string;
}

export interface BackendProductDTO {
  id: number;
  sku: string;
  nombre: string;
  categoria: string;
  descripcion: string;
  precio: number;
  stock: number;
  imagenUrl: string;
}

export interface Promotion {
  id: string;
  title: string;
  tag: string;
  color: string;
}

export interface NeedShortcut {
  id: string;
  title: string;
  subtitle: string;
  icon: string;
  etaLabel: string;
}
