import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { API_CONFIG } from '../config/api.config';
import { BackendProductDTO, Category, NeedShortcut, Product, Promotion } from '../models/catalog.model';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly _allProducts = signal<Product[]>([]);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _searchQuery = signal<string>('');
  private readonly _selectedCategory = signal<string>('TODAS');

  readonly products = computed(() => {
    const rawQuery = this.normalizeText(this._searchQuery());
    const category = this._selectedCategory();
    let list = this._allProducts();

    if (category !== 'TODAS') {
      list = list.filter((p) => p.categoria === category);
    }

    if (!rawQuery) {
      return list;
    }

    // Mapeo semántico de palabras clave a conceptos y categorías
    const semanticMatches: string[] = [];
    if (this.matchesAny(rawQuery, ['chela', 'gaseosa', 'refresco', 'bebida', 'jugo', 'coca', 'fanta', 'sprite', 'cerveza', 'agua', 'trago'])) {
      semanticMatches.push('BEBIDAS');
    }
    if (this.matchesAny(rawQuery, ['leche', 'queso', 'yogurt', 'mantequilla', 'huevo', 'colun', 'soprole', 'lacteo'])) {
      semanticMatches.push('LACTEOS_Y_HUEVOS');
    }
    if (this.matchesAny(rawQuery, ['carne', 'pollo', 'vacuno', 'pescado', 'salmon', 'asado', 'ariztia', 'lomo'])) {
      semanticMatches.push('CARNES_Y_PESCADOS');
    }
    if (this.matchesAny(rawQuery, ['pan', 'hallulla', 'marraqueta', 'molde', 'ideal'])) {
      semanticMatches.push('PANADERIA');
    }
    if (this.matchesAny(rawQuery, ['arroz', 'fideos', 'aceite', 'azucar', 'harina', 'tucapel', 'carozzi', 'atun', 'nescafe', 'abarrotes'])) {
      semanticMatches.push('ABARROTES');
    }
    if (this.matchesAny(rawQuery, ['fruta', 'verdura', 'manzana', 'platano', 'tomate', 'palta'])) {
      semanticMatches.push('FRUTAS_Y_VERDURAS');
    }
    if (this.matchesAny(rawQuery, ['limpieza', 'cloro', 'detergente', 'omo', 'jabon', 'papel', 'cif', 'aseo'])) {
      semanticMatches.push('LIMPIEZA');
    }

    return list.filter((p) => {
      const normName = this.normalizeText(p.name);
      const normDesc = this.normalizeText(p.descripcion || '');
      const normCat = this.normalizeText(p.categoria || '');
      const normSku = this.normalizeText(p.sku || '');

      const directMatch =
        normName.includes(rawQuery) ||
        normDesc.includes(rawQuery) ||
        normCat.includes(rawQuery) ||
        normSku.includes(rawQuery);

      const semanticMatch = !!p.categoria && semanticMatches.includes(p.categoria);

      return directMatch || semanticMatch;
    });
  });

  readonly isLoading = this._isLoading.asReadonly();
  readonly searchQuery = this._searchQuery.asReadonly();
  readonly selectedCategory = this._selectedCategory.asReadonly();

  readonly categories: Category[] = [
    { id: 'TODAS', name: 'Todos los productos', icon: 'ALL' },
    { id: 'LACTEOS_Y_HUEVOS', name: 'Lácteos y Huevos', icon: 'LAC' },
    { id: 'ABARROTES', name: 'Abarrotes', icon: 'ABA' },
    { id: 'BEBIDAS', name: 'Bebidas y Licores', icon: 'BEB' },
    { id: 'FRUTAS_Y_VERDURAS', name: 'Frutas y Verduras', icon: 'FRU' },
    { id: 'CARNES_Y_PESCADOS', name: 'Carnes y Pescados', icon: 'CAR' },
    { id: 'PANADERIA', name: 'Panadería', icon: 'PAN' },
    { id: 'LIMPIEZA', name: 'Limpieza y Hogar', icon: 'LIM' },
  ];

  readonly needShortcuts: NeedShortcut[] = [
    {
      id: 'abastecer',
      title: 'Necesito abastecer mi casa',
      subtitle: 'Combina supermercado, aseo y hogar en un solo pedido.',
      icon: '01',
      etaLabel: '25-40 min',
    },
    {
      id: 'mascota',
      title: 'Mi mascota necesita algo',
      subtitle: 'Alimento, arena y accesorios con entrega rápida.',
      icon: '02',
      etaLabel: '20-35 min',
    },
    {
      id: 'junta',
      title: 'Preparo una junta hoy',
      subtitle: 'Snacks, bebidas y lo que falte para tu reunión.',
      icon: '03',
      etaLabel: '15-30 min',
    },
    {
      id: 'espacio',
      title: 'Quiero mejorar mi espacio',
      subtitle: 'Artículos para el hogar y organización.',
      icon: '04',
      etaLabel: '30-45 min',
    },
    {
      id: 'urgente',
      title: 'Necesito algo urgente',
      subtitle: 'Entrega prioritaria en productos seleccionados.',
      icon: '05',
      etaLabel: '10-20 min',
    },
    {
      id: 'novedades',
      title: 'Quiero descubrir novedades',
      subtitle: 'Lo último que llegó a Pedidos360.',
      icon: '06',
      etaLabel: '25-40 min',
    },
  ];

  readonly promotions: Promotion[] = [
    { id: 'p1', title: 'Hasta 40% de descuento en productos de supermercado', tag: 'Termina hoy', color: '#fde7c8' },
    { id: 'p2', title: 'Envío gratis en tu primera compra de abarrotes', tag: 'Nuevo', color: '#d7f7e9' },
    { id: 'p3', title: 'Ofertas especiales en carnes y lácteos', tag: 'Destacado', color: '#e4e6ff' },
    { id: 'p4', title: 'Compra 2 y obtén un precio especial en bebidas', tag: 'Termina hoy', color: '#ffe3e6' },
  ];

  constructor(private http: HttpClient) {
    this.cargarProductosDeBackend();
  }

  setSearchQuery(query: string): void {
    this._searchQuery.set(query);
    if (query.trim()) {
      this.http
        .get<ApiResponse<BackendProductDTO[]>>(`${API_CONFIG.productos}/productos/buscar?nombre=${encodeURIComponent(query)}`)
        .pipe(catchError(() => of(null)))
        .subscribe((res) => {
          if (res?.success && res.data && res.data.length > 0) {
            const mapped = res.data.map((dto) => this.mapDtoToProduct(dto));
            this.mergeProducts(mapped);
          }
        });
    }
  }

  selectCategory(categoryId: string): void {
    this._selectedCategory.set(categoryId);
  }

  cargarProductosDeBackend(): void {
    this._isLoading.set(true);
    this.http
      .get<ApiResponse<BackendProductDTO[]>>(`${API_CONFIG.productos}/productos`)
      .pipe(
        catchError((error) => {
          console.warn('[CatalogService] Fallo conectando a productos. Usando catálogo local.', error);
          return of({ success: false, message: 'Fallback', data: [] });
        })
      )
      .subscribe((res) => {
        this._isLoading.set(false);
        if (res.success && res.data && res.data.length > 0) {
          const mapped = res.data.map((dto) => this.mapDtoToProduct(dto));
          this._allProducts.set(mapped);
        } else {
          this._allProducts.set(this.obtenerProductosFallback());
        }
      });
  }

  buscarPorNombre(nombre: string): void {
    this.setSearchQuery(nombre);
  }

  filtrarPorCategoria(categoria: string): void {
    this.selectCategory(categoria);
  }

  private mergeProducts(newProducts: Product[]): void {
    const current = [...this._allProducts()];
    newProducts.forEach((np) => {
      if (!current.some((cp) => cp.id === np.id)) {
        current.push(np);
      }
    });
    this._allProducts.set(current);
  }

  private normalizeText(text: string): string {
    return text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  private matchesAny(target: string, keywords: string[]): boolean {
    return keywords.some((kw) => target.includes(kw));
  }

  private mapDtoToProduct(dto: BackendProductDTO): Product {
    const iconMap: Record<string, string> = {
      'Leche Entera Colun 1L': 'COL',
      'Queso Gauda Soprole Laminado 250g': 'SOP',
      'Huevos Blancos L 12 Unidades': 'HUE',
      'Yogurt Batido Soprole Frutilla 120g': 'YOG',
      'Mantequilla Colun Con Sal 250g': 'MAN',
      'Arroz Grado 1 Tucapel 1kg': 'TUC',
      'Aceite Vegetal Belmont 1L': 'BEL',
      'Fideos Tallarines Lucchetti N°5 400g': 'LUC',
      'Salsa de Tomate Carozzi Tuco Italiana 200g': 'CAR',
      'Harina con Polvos de Hornear Selecta 1kg': 'SEL',
      'Atún al Agua San José 160g': 'ATU',
      'Café Nescafé Tradición 170g': 'NES',
      'Azúcar Blanca Iansa 1kg': 'IAN',
      'Coca-Cola Sabor Original 2.5L': 'COC',
      'Agua Mineral Cachantun Sin Gas 1.5L': 'CAC',
      'Néctar Watt\'s Durazno 1.5L': 'WAT',
      'Cerveza Heineken Pack 6 Latas 354cc': 'HEI',
      'Manzana Red Delicious 1kg': 'MAN',
      'Plátano Importado 1kg': 'PLA',
      'Tomate Larga Vida 1kg': 'TOM',
      'Pechuga de Pollo Desosada Ariztía 1kg': 'ARZ',
      'Lomo Vetado de Vacuno 1kg': 'VAC',
      'Pan Molde Blanco Ideal 560g': 'IDE',
      'Detergente Líquido Omo Multiacción 3L': 'OMO',
      'Papel Higiénico Elite Doble Hoja 8 Rollos': 'ELI',
      'Lavaloza Cif Gel Limón 750ml': 'CIF',
    };

    const categoryIcons: Record<string, string> = {
      LACTEOS_Y_HUEVOS: 'LAC',
      ABARROTES: 'ABA',
      BEBIDAS: 'BEB',
      FRUTAS_Y_VERDURAS: 'FRU',
      CARNES_Y_PESCADOS: 'CAR',
      PANADERIA: 'PAN',
      LIMPIEZA: 'LIM',
    };

    return {
      id: String(dto.id),
      sku: dto.sku,
      name: dto.nombre,
      brand: dto.categoria.replace(/_/g, ' '),
      price: dto.precio,
      icon: iconMap[dto.nombre] || categoryIcons[dto.categoria] || 'P360',
      rating: 4.8,
      deliveryMinutes: 20,
      categoria: dto.categoria,
      descripcion: dto.descripcion,
      stock: dto.stock,
      imagenUrl: dto.imagenUrl,
    };
  }

  private obtenerProductosFallback(): Product[] {
    return [
      { id: '1', sku: 'SUP-LAC-001', name: 'Leche Entera Colun 1L', brand: 'LÁCTEOS', price: 1190, icon: 'COL', rating: 4.8, deliveryMinutes: 20, categoria: 'LACTEOS_Y_HUEVOS', stock: 150 },
      { id: '2', sku: 'SUP-LAC-002', name: 'Queso Gauda Soprole 250g', brand: 'LÁCTEOS', price: 2890, icon: 'SOP', rating: 4.7, deliveryMinutes: 20, categoria: 'LACTEOS_Y_HUEVOS', stock: 80 },
      { id: '6', sku: 'SUP-ABA-001', name: 'Arroz Grado 1 Tucapel 1kg', brand: 'ABARROTES', price: 1590, icon: 'TUC', rating: 4.9, deliveryMinutes: 25, categoria: 'ABARROTES', stock: 200 },
      { id: '14', sku: 'SUP-BEB-001', name: 'Coca-Cola Sabor Original 2.5L', brand: 'BEBIDAS', price: 2490, icon: 'COC', rating: 4.8, deliveryMinutes: 15, categoria: 'BEBIDAS', stock: 180 },
      { id: '21', sku: 'SUP-CAR-001', name: 'Pechuga de Pollo Ariztía 1kg', brand: 'CARNES', price: 5990, icon: 'ARZ', rating: 4.9, deliveryMinutes: 30, categoria: 'CARNES_Y_PESCADOS', stock: 60 },
      { id: '24', sku: 'SUP-LIM-001', name: 'Detergente Líquido Omo 3L', brand: 'LIMPIEZA', price: 8990, icon: 'OMO', rating: 4.6, deliveryMinutes: 25, categoria: 'LIMPIEZA', stock: 45 },
    ];
  }
}
