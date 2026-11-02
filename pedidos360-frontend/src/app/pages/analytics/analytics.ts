import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, of } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { CatalogService } from '../../core/services/catalog.service';
import { AuthService } from '../../core/services/auth.service';
import { PedidoService, CuponItem, ConfiguracionComercialItem } from '../../core/services/pedido.service';
import { API_CONFIG } from '../../core/config/api.config';
import { Product } from '../../core/models/catalog.model';

export interface CategoryMetric {
  category: string;
  salesCount: number;
  revenue: number;
  percentage: number;
  color: string;
}

export interface TimeBlockSales {
  slot: string;
  timeRange: string;
  revenue: number;
  ordersCount: number;
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
  imports: [RouterLink, Icon, DecimalPipe, PercentPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analytics.html',
  styleUrl: './analytics.css',
})
export class Analytics implements OnInit {
  private readonly http = inject(HttpClient);
  readonly catalog = inject(CatalogService);
  readonly auth = inject(AuthService);
  readonly pedidoSvc = inject(PedidoService);
  private readonly route = inject(ActivatedRoute);

  // Selector de Sección Principal (Exclusivo Admin)
  readonly activeTab = signal<'metricas' | 'cupones'>('metricas');

  // Estado del Motor Comercial y Cupones (PostgreSQL)
  readonly cupones = signal<CuponItem[]>([]);
  readonly configuraciones = signal<ConfiguracionComercialItem[]>([]);
  readonly loadingComercial = signal(false);
  readonly mensajeComercial = signal<string | null>(null);
  readonly errorComercial = signal<string | null>(null);

  // Formulario nuevo cupón
  readonly nuevoCodigo = signal('');
  readonly nuevoTipo = signal<'PORCENTAJE' | 'MONTO_FIJO'>('PORCENTAJE');
  readonly nuevoValor = signal<number>(10);
  readonly nuevoMontoMinimo = signal<number>(0);
  readonly guardandoCupon = signal(false);

  // Parámetros de Reglas Comerciales editables
  readonly umbralEnvioGratis = signal<number>(20000);
  readonly costoEnvioBase = signal<number>(2990);
  readonly tarifaServicioBase = signal<number>(990);
  readonly guardandoConfig = signal(false);

  readonly selectedPeriod = signal<'24h' | '7d' | '30d' | '90d' | '1y'>('30d');
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  // Venta del Día (Últimas 24 Horas)
  readonly revenue24h = signal(0);
  readonly orders24h = signal(0);
  readonly ticketPromedio24h = signal(0);
  readonly salesByTimeBlock = signal<TimeBlockSales[]>([]);

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
    const tabParam = this.route.snapshot.queryParams['tab'];
    if (tabParam === 'cupones') {
      this.activeTab.set('cupones');
    }
    this.cargarDatos();
    if (this.auth.isAdmin()) {
      this.cargarComercial();
    }
  }

  cargarComercial(): void {
    this.loadingComercial.set(true);
    forkJoin({
      cupones: this.pedidoSvc.obtenerTodosCupones().pipe(catchError(() => of([]))),
      configs: this.pedidoSvc.obtenerConfiguraciones().pipe(catchError(() => of([]))),
    }).subscribe(({ cupones, configs }) => {
      this.loadingComercial.set(false);
      this.cupones.set(cupones || []);
      this.configuraciones.set(configs || []);

      for (const c of configs || []) {
        const k = (c.clave || '').toLowerCase();
        if (k === 'umbral_envio_gratis') this.umbralEnvioGratis.set(Number(c.valor));
        if (k === 'costo_envio_base') this.costoEnvioBase.set(Number(c.valor));
        if (k === 'tarifa_servicio' || k === 'tarifa_servicio_base') this.tarifaServicioBase.set(Number(c.valor));
      }
    });
  }

  toggleCupon(id: number): void {
    this.pedidoSvc.toggleCupon(id).subscribe({
      next: (actualizado) => {
        this.cupones.update((list) =>
          list.map((c) => (c.id === id ? { ...c, activo: !c.activo } : c))
        );
        this.mensajeComercial.set(
          `Cupón #${id} ${actualizado.activo ? 'ACTIVADO' : 'DESACTIVADO'} con éxito en PostgreSQL.`
        );
        setTimeout(() => this.mensajeComercial.set(null), 3500);
      },
      error: (err) => {
        console.error('Error al cambiar estado del cupón:', err);
        this.errorComercial.set('No se pudo modificar el estado del cupón en PostgreSQL.');
        setTimeout(() => this.errorComercial.set(null), 4000);
      },
    });
  }

  crearCupon(): void {
    const cod = this.nuevoCodigo().trim().toUpperCase();
    if (!cod) {
      this.errorComercial.set('Debes ingresar un código para el cupón (ej: VERANO2026).');
      return;
    }
    if (this.nuevoValor() <= 0) {
      this.errorComercial.set('El valor del descuento debe ser mayor a 0.');
      return;
    }

    this.guardandoCupon.set(true);
    this.errorComercial.set(null);
    this.pedidoSvc
      .crearCupon({
        codigo: cod,
        tipoDescuento: this.nuevoTipo(),
        valor: this.nuevoValor(),
        montoMinimo: this.nuevoMontoMinimo(),
        activo: true,
      })
      .subscribe({
        next: (creado) => {
          this.guardandoCupon.set(false);
          this.cupones.update((list) => [creado, ...list]);
          this.nuevoCodigo.set('');
          this.nuevoValor.set(10);
          this.nuevoMontoMinimo.set(0);
          this.mensajeComercial.set(`¡Cupón "${creado.codigo}" creado y activado en PostgreSQL!`);
          setTimeout(() => this.mensajeComercial.set(null), 4000);
        },
        error: (err) => {
          this.guardandoCupon.set(false);
          console.error('Error al crear cupón:', err);
          this.errorComercial.set('No se pudo crear el cupón. Verifica que no exista un cupón con el mismo código.');
          setTimeout(() => this.errorComercial.set(null), 4000);
        },
      });
  }

  guardarConfiguracion(clave: string, valor: number): void {
    this.guardandoConfig.set(true);
    this.errorComercial.set(null);
    this.pedidoSvc.actualizarConfiguracion(clave, valor).subscribe({
      next: () => {
        this.guardandoConfig.set(false);
        this.mensajeComercial.set(
          `Regla comercial "${clave}" actualizada a $${valor.toLocaleString('es-CL')} en PostgreSQL.`
        );
        setTimeout(() => this.mensajeComercial.set(null), 3500);
      },
      error: (err) => {
        this.guardandoConfig.set(false);
        console.error('Error al actualizar regla comercial:', err);
        this.errorComercial.set('Error al guardar la regla comercial en la base de datos.');
        setTimeout(() => this.errorComercial.set(null), 4000);
      },
    });
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
        rating: dto.rating ?? 0,
        deliveryMinutes: dto.deliveryMinutes ?? 35,
        categoria: dto.categoria,
        descripcion: dto.descripcion,
        stock: dto.stock,
      }));

      this.calcularMetricasReales(allPedidos, allProductos);
      this.mapearTransacciones(allPedidos);
    });
  }

  private calcularMetricasReales(pedidos: PedidoRaw[], productos: Product[]): void {
    // Cálculo específico de Venta del Día (Últimas 24 Horas)
    const now = new Date();
    const cutoff24h = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const pedidos24h = pedidos.filter((p) => {
      if (!p.fechaCreacion) return true;
      const d = new Date(p.fechaCreacion);
      return !isNaN(d.getTime()) && d >= cutoff24h;
    });

    const sum24h = pedidos24h.reduce((acc, p) => acc + (p.total || 0), 0);
    this.revenue24h.set(Math.round(sum24h));
    this.orders24h.set(pedidos24h.length);
    this.ticketPromedio24h.set(
      pedidos24h.length > 0 ? Math.round(sum24h / pedidos24h.length) : 0
    );

    // Distribución por franjas horarias de las 24 horas
    const blocks: Record<string, { label: string; range: string; rev: number; count: number; color: string }> = {
      morning: { label: 'Mañana', range: '08:00 - 13:00', rev: 0, count: 0, color: '#3b82f6' },
      afternoon: { label: 'Tarde', range: '13:00 - 18:00', rev: 0, count: 0, color: '#10b981' },
      evening: { label: 'Noche', range: '18:00 - 23:00', rev: 0, count: 0, color: '#f59e0b' },
      night: { label: 'Madrugada', range: '23:00 - 08:00', rev: 0, count: 0, color: '#8b5cf6' },
    };

    for (const p of pedidos24h) {
      const d = p.fechaCreacion ? new Date(p.fechaCreacion) : new Date();
      const hour = d.getHours();
      let key = 'night';
      if (hour >= 8 && hour < 13) key = 'morning';
      else if (hour >= 13 && hour < 18) key = 'afternoon';
      else if (hour >= 18 && hour < 23) key = 'evening';

      blocks[key].rev += p.total || 0;
      blocks[key].count += 1;
    }

    const timeBlockList: TimeBlockSales[] = Object.values(blocks).map((b) => ({
      slot: b.label,
      timeRange: b.range,
      revenue: Math.round(b.rev),
      ordersCount: b.count,
      percentage: sum24h > 0 ? b.rev / sum24h : 0,
      color: b.color,
    }));
    this.salesByTimeBlock.set(timeBlockList);

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
      case '24h':
        cutoff = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        break;
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

  getPeriodLabel(): string {
    switch (this.selectedPeriod()) {
      case '24h': return '24 Horas (Día)';
      case '7d': return '7 Días';
      case '30d': return '30 Días';
      case '90d': return '90 Días';
      case '1y': return '1 Año';
      default: return '30 Días';
    }
  }

  setPeriod(period: '24h' | '7d' | '30d' | '90d' | '1y'): void {
    this.selectedPeriod.set(period);
    this.cargarDatos();
  }
}
