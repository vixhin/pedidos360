export interface Category {
  id: string;
  name: string;
  icon: string;
}

export interface Product {
  id: string;
  name: string;
  brand: string;
  price: number;
  icon: string;
  rating: number;
  deliveryMinutes: number;
  discountPercent?: number;
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
