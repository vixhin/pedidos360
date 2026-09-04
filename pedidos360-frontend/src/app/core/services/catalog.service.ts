import { Injectable } from '@angular/core';
import { Category, NeedShortcut, Product, Promotion } from '../models/catalog.model';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  readonly categories: Category[] = [
    { id: 'electronicos', name: 'Electrónicos', icon: '🎧' },
    { id: 'aseo', name: 'Aseo', icon: '🧴' },
    { id: 'comida', name: 'Comida', icon: '🍕' },
    { id: 'mascotas', name: 'Mascotas', icon: '🐾' },
    { id: 'hogar', name: 'Hogar', icon: '🏠' },
    { id: 'belleza', name: 'Belleza', icon: '💄' },
    { id: 'bebidas', name: 'Bebidas', icon: '🥤' },
    { id: 'oficina', name: 'Oficina y estudio', icon: '📚' },
    { id: 'salud', name: 'Salud y cuidado personal', icon: '💊' },
    { id: 'ofertas', name: 'Ofertas', icon: '🏷️' },
  ];

  readonly needShortcuts: NeedShortcut[] = [
    {
      id: 'abastecer',
      title: 'Necesito abastecer mi casa',
      subtitle: 'Combina supermercado, aseo y hogar en un solo pedido.',
      icon: '🧺',
      etaLabel: '25-40 min',
    },
    {
      id: 'mascota',
      title: 'Mi mascota necesita algo',
      subtitle: 'Alimento, arena y accesorios con entrega rápida.',
      icon: '🐶',
      etaLabel: '20-35 min',
    },
    {
      id: 'junta',
      title: 'Preparo una junta hoy',
      subtitle: 'Snacks, bebidas y lo que falte para tu reunión.',
      icon: '🎉',
      etaLabel: '15-30 min',
    },
    {
      id: 'espacio',
      title: 'Quiero mejorar mi espacio',
      subtitle: 'Artículos para el hogar y organización.',
      icon: '🛋️',
      etaLabel: '30-45 min',
    },
    {
      id: 'urgente',
      title: 'Necesito algo urgente',
      subtitle: 'Entrega prioritaria en productos seleccionados.',
      icon: '⚡',
      etaLabel: '10-20 min',
    },
    {
      id: 'novedades',
      title: 'Quiero descubrir novedades',
      subtitle: 'Lo último que llegó a Pedidos360.',
      icon: '✨',
      etaLabel: '25-40 min',
    },
  ];

  readonly promotions: Promotion[] = [
    { id: 'p1', title: 'Hasta 40% de descuento en electrónicos', tag: 'Termina hoy', color: '#fde7c8' },
    { id: 'p2', title: 'Envío gratis en tu primera compra', tag: 'Nuevo', color: '#d7f7e9' },
    { id: 'p3', title: 'Ofertas especiales para tu hogar', tag: 'Destacado', color: '#e4e6ff' },
    { id: 'p4', title: 'Compra 2 y obtén un precio especial', tag: 'Termina hoy', color: '#ffe3e6' },
  ];

  readonly products: Product[] = [
    { id: 'pr1', name: 'Audífonos inalámbricos', brand: 'TuneX', price: 25990, icon: '🎧', rating: 4.7, deliveryMinutes: 25 },
    { id: 'pr2', name: 'Detergente líquido concentrado', brand: 'BioClean', price: 8990, icon: '🧴', rating: 4.5, deliveryMinutes: 20 },
    { id: 'pr3', name: 'Snacks surtidos', brand: 'Crispy', price: 3490, icon: '🍿', rating: 4.3, deliveryMinutes: 18, discountPercent: 15 },
    { id: 'pr4', name: 'Alimento para mascotas', brand: 'PetGourmet', price: 17990, icon: '🐕', rating: 4.8, deliveryMinutes: 30 },
    { id: 'pr5', name: 'Set de skincare', brand: 'Glowtip', price: 14990, icon: '🧴', rating: 4.6, deliveryMinutes: 22 },
    { id: 'pr6', name: 'Accesorios para tecnología', brand: 'TechAccess', price: 9990, icon: '🔌', rating: 4.4, deliveryMinutes: 24, discountPercent: 10 },
    { id: 'pr7', name: 'Café de origen', brand: 'Aroma', price: 6990, icon: '☕', rating: 4.9, deliveryMinutes: 15 },
    { id: 'pr8', name: 'Papel higiénico x12', brand: 'Suavel', price: 7490, icon: '🧻', rating: 4.2, deliveryMinutes: 20 },
  ];
}
