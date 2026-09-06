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

interface PedidoRaw {
  id: number;
  usuarioId: number;
  total: number;
  estado: string;
  fechaCreacion: string;
}

const SNAPSHOT_KEY = 'analitica_last_snapshot';
const SNAPSHOT_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000; // 7 días

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

  // KPIs - calculados de datos reales
  readonly totalRevenue = signal(0);
  readonly totalExpenses = signal(0);
  readonly totalPedidos = signal(0);
  readonly ticketPromedio = signal(0);

  readonly netProfit = computed(() => this.totalRevenue() - this.totalExpenses());
  readonly profitMargin = computed(() =>
    this.totalRevenue() > 0 ? this.netProfit() / this.totalRevenue() : 0
  );

  // Producto destacado y menos destacado
  readonly topProduct = signal<{
    id: string; name: string; category: string; sku: string;
    stock: number; price: number; totalRevenue: number; icon: string;
  } | null>(null);

  readonly leastProduct = signal<{
    id: string; name: string; category: string; sku: string;
    stock: number; price: number; totalRevenue: number; icon: string;
  } | null>(null);

  // Distribución por categorías basada en productos reales
  readonly categoryMetrics = signal<CategoryMetric[]>([]);

  // Transacciones = pedidos reales mapeados
  readonly transactions = signal<FinancialTransaction[]>([]);

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.isLoading.set(true);

    forkJoin({
      pedidos: this.http
        .get<PedidoRaw[]>(`${API_CONFIG.pedidos}/pedidos`)
        .pipe(catchError(() => of([] as PedidoRaw[]))),
      productos: this.http
        .get<any>(`${API_CONFIG.productos}/productos`)
        .pipe(catchError(() => of({ success: false, data: [] }))),
    }).subscribe(({ pedidos, productos }) => {
      this.isLoading.set(false);

      const allPedidos: PedidoRaw[] = pedidos || [];
      const allProductos: Product[] = (productos?.data || []).map((dto: any) => ({
        id: String(dto.id),
        sku: dto.sku,
        name: dto.nombre,
        brand: dto.categoria,
        price: dto.precio,
        icon: dto.categoria?.substring(0, 3) || 'PRD',
        rating: 0,
        deliveryMinutes: 0,
        categoria: dto.categoria,
        descripcion: dto.descripcion,
        stock: dto.stock,
      }));

      this.calcularKPIs(allPedidos, allProductos);
      this.calcularCategorias(allProductos);
      this.mapearTransacciones(allPedidos);
      this.guardarSnapshotSiCorresponde(allPedidos, allProductos);
    });
  }

  private calcularKPIs(pedidos: PedidoRaw[], productos: Product[]): void {
    const filtered = this.filtrarPorPeriodo(pedidos);
    const entregados = filtered.filter((p) => p.estado?.toUpperCase() === 'ENTREGADO');

    const totalIngresos = filtered.reduce((acc, p) => acc + (p.total || 0), 0);
    // Estimamos egresos como 56% del ingreso (costo de mercadería + operaciones)
    const totalEgresos = totalIngresos * 0.56;

    this.totalRevenue.set(Math.round(totalIngresos));
    this.totalExpenses.set(Math.round(totalEgresos));
    this.totalPedidos.set(filtered.length);
    this.ticketPromedio.set(
      filtered.length > 0 ? Math.round(totalIngresos / filtered.length) : 0
    );

    // Producto con más/menos stock como proxy de ventas
    if (productos.length > 0) {
      const sorted = [...productos].sort((a, b) => (b.stock || 0) - (a.stock || 0));
      const top = sorted[0];
      const least = sorted[sorted.length - 1];

      this.topProduct.set({
        id: top.id,
        name: top.name,
        category: CATEGORY_LABELS[top.categoria || ''] || top.categoria || 'Sin categoría',
        sku: top.sku || 'N/A',
        stock: top.stock || 0,
        price: top.price,
        totalRevenue: Math.round((top.stock || 0) * top.price * 0.3),
        icon: top.icon,
      });

      this.leastProduct.set({
        id: least.id,
        name: least.name,
        category: CATEGORY_LABELS[least.categoria || ''] || least.categoria || 'Sin categoría',
        sku: least.sku || 'N/A',
        stock: least.stock || 0,
        price: least.price,
        totalRevenue: Math.round((least.stock || 0) * least.price * 0.3),
        icon: least.icon,
      });
    }
  }

  private calcularCategorias(productos: Product[]): void {
    // Agrupar por categoría usando stock y precio como proxy de ventas
    const grouped: Record<string, { salesCount: number; revenue: number }> = {};
    let totalRevenue = 0;

    for (const p of productos) {
      const cat = p.categoria || 'OTROS';
      if (!grouped[cat]) grouped[cat] = { salesCount: 0, revenue: 0 };
      // Stock "vendido" estimado = 30% del stock actual
      const ventasEstimadas = Math.round((p.stock || 0) * 0.3);
      const revenueEstimado = ventasEstimadas * p.price;
      grouped[cat].salesCount += ventasEstimadas;
      grouped[cat].revenue += revenueEstimado;
      totalRevenue += revenueEstimado;
    }

    const metrics: CategoryMetric[] = Object.entries(grouped)
      .map(([cat, data]) => ({
        category: CATEGORY_LABELS[cat] || cat,
        salesCount: data.salesCount,
        revenue: data.revenue,
        percentage: totalRevenue > 0 ? data.revenue / totalRevenue : 0,
        color: CATEGORY_COLORS[cat] || '#64748b',
      }))
      .sort((a, b) => b.revenue - a.revenue);

    this.categoryMetrics.set(metrics);
  }

  private mapearTransacciones(pedidos: PedidoRaw[]): void {
    const filtered = this.filtrarPorPeriodo(pedidos);
    const trxs: FinancialTransaction[] = filtered
      .slice(0, 20) // últimas 20
      .map((p) => ({
        id: `PED-${p.id}`,
        date: p.fechaCreacion
          ? new Date(p.fechaCreacion).toLocaleDateString('es-CL', {
              day: '2-digit', month: '2-digit', year: 'numeric',
              hour: '2-digit', minute: '2-digit',
            })
          : 'Fecha no disponible',
        description: `Pedido #${p.id} · Usuario #${p.usuarioId} · Estado: ${p.estado}`,
        type: 'INGRESO',
        amount: p.total,
        category: 'Ventas',
      }));
    this.transactions.set(trxs);
  }

  private filtrarPorPeriodo(pedidos: PedidoRaw[]): PedidoRaw[] {
    const now = new Date();
    const period = this.selectedPeriod();
    let cutoff: Date;
    if (period === '7d') cutoff = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    else if (period === '30d') cutoff = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    else if (period === '90d') cutoff = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
    else cutoff = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);

    return pedidos.filter((p) => {
      if (!p.fechaCreacion) return true;
      return new Date(p.fechaCreacion) >= cutoff;
    });
  }

  private guardarSnapshotSiCorresponde(pedidos: PedidoRaw[], productos: Product[]): void {
    try {
      const lastRaw = localStorage.getItem(SNAPSHOT_KEY);
      const lastTime = lastRaw ? parseInt(lastRaw, 10) : 0;
      const now = Date.now();

      if (now - lastTime < SNAPSHOT_INTERVAL_MS) return; // menos de 7 días

      const totalIngresos = pedidos.reduce((acc, p) => acc + (p.total || 0), 0);
      const descripcion = `Snapshot semanal: ${pedidos.length} pedidos · $${Math.round(totalIngresos).toLocaleString('es-CL')} ingresos · ${productos.length} productos en catálogo`;

      this.http
        .post(`${API_CONFIG.analitica}/analitica`, {
          tipoEvento: 'SNAPSHOT_SEMANAL',
          descripcion,
        })
        .pipe(catchError(() => of(null)))
        .subscribe((res) => {
          if (res) {
            localStorage.setItem(SNAPSHOT_KEY, String(now));
            console.info('[Analytics] Snapshot semanal guardado en DB:', descripcion);
          }
        });
    } catch {
      // localStorage no disponible
    }
  }

  setPeriod(period: '7d' | '30d' | '90d' | '1y'): void {
    this.selectedPeriod.set(period);
    this.cargarDatos(); // recalcular con el nuevo filtro
  }
}
