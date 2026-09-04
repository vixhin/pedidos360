export type OrderStatus = 'confirmado' | 'preparando' | 'en_camino' | 'entregado';

export interface OrderStep {
  key: OrderStatus;
  label: string;
}

export interface Order {
  id: string;
  status: OrderStatus;
  etaMinutes: number;
  itemsCount: number;
  storesCount: number;
  total: number;
  date: string;
}

export const ORDER_STEPS: OrderStep[] = [
  { key: 'confirmado', label: 'Confirmado' },
  { key: 'preparando', label: 'Preparando' },
  { key: 'en_camino', label: 'En camino' },
  { key: 'entregado', label: 'Entregado' },
];
