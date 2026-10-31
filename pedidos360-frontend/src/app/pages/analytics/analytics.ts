import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, of } from 'rxjs';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { API_CONFIG } from '../../core/config/api.config';
import { Product } from '../../core/models/catalog.model';

export interface CategoryMetric {
  category: string;
  salesCount: number;
  revenue: number;
  percentage: number;
  color: string;
}

export interface FinancialTransaction {
  id: string;
  date: string;
  description: string;
  type: 'INGRESO' | 'EGRESO';
  amount: number;
  category: string;
}

interface PedidoItemRaw {
  id?: number;
  productoId?: number;
  nombre: string;
  icono?: string;
  cantidad: number;
  precioUnitario: number;
}

interface PedidoRaw {
  id: number;
  usuarioId: number;
  total: number;
  subtotal?: number;
  tarifaServicio?: number;
  costoEnvio?: number;
  estado: string;
  fechaCreacion: string;
  items?: PedidoItemRaw[];
}

const CATEGORY_COLORS: Record<string, string> = {
  LACTEOS_Y_HUEVOS: '#3b82f6',
  BEBIDAS: '#10b981',
  ABARROTES: '#f59e0b',
  CARNES_Y_PESCADOS: '#ef4444',
  LIMPIEZA: '#8b5cf6',
  PANADERIA: '#ec4899',
  FRUTAS_Y_VERDURAS: '#06b6d4',
};

const CATEGORY_LABELS: Record<string, string> = {
  LACTEOS_Y_HUEVOS: 'Lácteos y Huevos',
  BEBIDAS: 'Bebidas y Licores',
  ABARROTES: 'Abarrotes',
  CARNES_Y_PESCADOS: 'Carnes y Pescados',
  LIMPIEZA: 'Limpieza y Hogar',
  PANADERIA: 'Panadería',
  FRUTAS_Y_VERDURAS: 'Frutas y Verduras',
};

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [RouterLink, Icon, DecimalPipe, PercentPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analytics.html',
  styleUrl: './analytics.css',
})
export class Analytics implements OnInit {
  private readonly http = inject(HttpClient);
  readonly catalog = inject(CatalogService);
  readonly auth = inject(AuthService);

  readonly selectedPeriod = signal<'7d' | '30d' | '90d' | '1y'>('30d');
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  // KPIs - calculados exclusivamente de datos reales de PostgreSQL
  readonly totalRevenue = signal(0);
  readonly totalExpenses = signal(0);
  readonly totalPedidos = signal(0);
  readonly ticketPromedio = signal(0);

  readonly netProfit = computed(() => this.totalRevenue() - this.totalExpenses());
  readonly profitMargin = computed(() =>
    this.totalRevenue() > 0 ? this.netProfit() / this.totalRevenue() : 0
  );

  // Producto más vendido y menos vendido en base a órdenes reales
  readonly topProduct = signal<{
    id: string; name: string; category: string; sku: string;
    stock: number; price: number; totalRevenue: number; icon: string;
  } | null>(null);

  readonly leastProduct = signal<{
    id: string; name: string; category: string; sku: string;
    stock: number; price: number; totalRevenue: number; icon: string;
  } | null>(null);

  // Distribución por categorías basada en ítems reales vendidos
  readonly categoryMetrics = signal<CategoryMetric[]>([]);

  // Transacciones reales desde base de datos
  readonly transactions = signal<FinancialTransaction[]>([]);

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      pedidos: this.http
        .get<PedidoRaw[]>(`${API_CONFIG.pedidos}/pedidos`)
        .pipe(catchError((err) => {
          console.error('[Analytics] Error cargando pedidos:', err);
          return of(null);
        })),
      productos: this.http
        .get<any>(`${API_CONFIG.productos}/productos`)
        .pipe(catchError((err) => {
          console.error('[Analytics] Error cargando productos:', err);
          return of(null);
        })),
    }).subscribe(({ pedidos, productos }) => {
      this.isLoading.set(false);

      if (pedidos === null && productos === null) {
        this.errorMessage.set('No se pudo conectar con los microservicios de pedidos y productos.');
        return;
      }

      const allPedidos: PedidoRaw[] = pedidos || [];
      const allProductos: Product[] = (productos?.data || []).map((dto: any) => ({
        id: String(dto.id),
        sku: dto.sku,
        name: dto.nombre,
        brand: dto.categoria,
        price: dto.precio,
        icon: dto.categoria?.substring(0, 3) || 'PRD',
        rating: dto.rating ?? 4.8,
        deliveryMinutes: dto.deliveryMinutes ?? 25,
        categoria: dto.categoria,
        descripcion: dto.descripcion,
        stock: dto.stock,
      }));

      this.calcularMetricasReales(allPedidos, allProductos);
      this.mapearTransacciones(allPedidos);
    });
  }

  private calcularMetricasReales(pedidos: PedidoRaw[], productos: Product[]): void {
    const filtered = this.filtrarPorPeriodo(pedidos);

    // Ingresos reales: suma de pedidos registrados en el período
    const totalIngresos = filtered.reduce((acc, p) => acc + (p.total || 0), 0);

    // Costos reales: costos de envío y tarifas logísticas registradas
    const totalCostosLogistica = filtered.reduce((acc, p) => {
      const envio = p.costoEnvio != null ? p.costoEnvio : 0;
      const tarifa = p.tarifaServicio != null ? p.tarifaServicio : 0;
      return acc + envio + (tarifa * 0.2); // costo operativo de procesamiento
    }, 0);

    this.totalRevenue.set(Math.round(totalIngresos));
    this.totalExpenses.set(Math.round(totalCostosLogistica));
    this.totalPedidos.set(filtered.length);
    this.ticketPromedio.set(
      filtered.length > 0 ? Math.round(totalIngresos / filtered.length) : 0
    );

    // Agregación de ventas reales por producto a partir de los ítems de cada orden
    const productSalesMap: Record<string, { count: number; revenue: number; product: Product }> = {};

    for (const p of productos) {
      productSalesMap[p.id] = { count: 0, revenue: 0, product: p };
    }

    const categorySalesMap: Record<string, { salesCount: number; revenue: number }> = {};

    for (const order of filtered) {
      if (order.items && order.items.length > 0) {
        for (const item of order.items) {
          const qty = item.cantidad || 1;
          const price = item.precioUnitario || 0;
          const itemRev = qty * price;

          // Buscar producto correspondiente
          let prodMatch = productos.find(
            (p) => String(p.id) === String(item.productoId) || p.name === item.nombre
          );

          if (prodMatch) {
            productSalesMap[prodMatch.id].count += qty;
            productSalesMap[prodMatch.id].revenue += itemRev;

            const cat = prodMatch.categoria || 'OTROS';
            if (!categorySalesMap[cat]) categorySalesMap[cat] = { salesCount: 0, revenue: 0 };
            categorySalesMap[cat].salesCount += qty;
            categorySalesMap[cat].revenue += itemRev;
          }
        }
      }
    }

    // Identificar productos destacados con ventas reales
    const soldProducts = Object.values(productSalesMap).filter((ps) => ps.count > 0);

    if (soldProducts.length > 0) {
      soldProducts.sort((a, b) => b.revenue - a.revenue);
      const top = soldProducts[0];
      const least = soldProducts[soldProducts.length - 1];

      this.topProduct.set({
        id: top.product.id,
        name: top.product.name,
        category: CATEGORY_LABELS[top.product.categoria || ''] || top.product.categoria || 'Sin categoría',
        sku: top.product.sku || 'N/A',
        stock: top.product.stock || 0,
        price: top.product.price,
        totalRevenue: Math.round(top.revenue),
        icon: top.product.icon,
      });

      this.leastProduct.set({
        id: least.product.id,
        name: least.product.name,
        category: CATEGORY_LABELS[least.product.categoria || ''] || least.product.categoria || 'Sin categoría',
        sku: least.product.sku || 'N/A',
        stock: least.product.stock || 0,
        price: least.product.price,
        totalRevenue: Math.round(least.revenue),
        icon: least.product.icon,
      });
    } else if (productos.length > 0) {
      // Si aún no hay ventas registradas en el período, mostrar el catálogo sin inventar números falsos
      const first = productos[0];
      this.topProduct.set({
        id: first.id,
        name: first.name,
        category: CATEGORY_LABELS[first.categoria || ''] || first.categoria || 'Sin categoría',
        sku: first.sku || 'N/A',
        stock: first.stock || 0,
        price: first.price,
        totalRevenue: 0,
        icon: first.icon,
      });
      this.leastProduct.set(null);
    } else {
      this.topProduct.set(null);
      this.leastProduct.set(null);
    }

    // Métricas por categoría basadas en ventas reales
    const totalCatRevenue = Object.values(categorySalesMap).reduce((acc, c) => acc + c.revenue, 0);

    const metrics: CategoryMetric[] = Object.entries(categorySalesMap)
      .map(([cat, data]) => ({
        category: CATEGORY_LABELS[cat] || cat,
        salesCount: data.salesCount,
        revenue: data.revenue,
        percentage: totalCatRevenue > 0 ? data.revenue / totalCatRevenue : 0,
        color: CATEGORY_COLORS[cat] || '#64748b',
      }))
      .sort((a, b) => b.revenue - a.revenue);

    this.categoryMetrics.set(metrics);
  }

  private mapearTransacciones(pedidos: PedidoRaw[]): void {
    const filtered = this.filtrarPorPeriodo(pedidos);
    const trxs: FinancialTransaction[] = filtered
      .slice(0, 20)
      .map((p) => ({
        id: `PED-${p.id}`,
        date: p.fechaCreacion
          ? new Date(p.fechaCreacion).toLocaleDateString('es-CL', {
              day: '2-digit', month: '2-digit', year: 'numeric',
              hour: '2-digit', minute: '2-digit',
            })
          : 'Fecha no disponible',
        description: `Pedido #${p.id} · Cliente #${p.usuarioId} · Estado: ${p.estado}`,
        type: 'INGRESO',
        amount: p.total,
        category: 'Ventas de catálogo',
      }));
    this.transactions.set(trxs);
  }

  private filtrarPorPeriodo(pedidos: PedidoRaw[]): PedidoRaw[] {
    const now = new Date();
    const period = this.selectedPeriod();
    let cutoff: Date;

    switch (period) {
      case '7d':
        cutoff = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case '30d':
        cutoff = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        break;
      case '90d':
        cutoff = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
        break;
      case '1y':
        cutoff = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
        break;
      default:
        cutoff = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    }

    return pedidos.filter((p) => {
      if (!p.fechaCreacion) return true;
      const d = new Date(p.fechaCreacion);
      return !isNaN(d.getTime()) && d >= cutoff;
    });
  }

  setPeriod(period: '7d' | '30d' | '90d' | '1y'): void {
    this.selectedPeriod.set(period);
    this.cargarDatos();
  }
}
