import { Component, computed, inject, signal } from '@angular/core';
import { AnaliticaInbound, IncidenciaInboundFiltros } from '../../../../../core/models';
import { InboundCatalogos } from '../../../../../core/services/inbound-catalogos';
import { InboundIncidenciasApi } from '../../../../../core/services/inbound-incidencias.api';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { SERIES, SERIE_OTROS, compacto, entero, porcentaje, variacion } from '../../../../../shared/ui/charts/formato';
import { CeldaCalor, Heatmap } from '../../../../../shared/ui/charts/heatmap';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { LineChart, SerieLinea } from '../../../../../shared/ui/charts/line-chart';
import { ItemPareto, ParetoChart } from '../../../../../shared/ui/charts/pareto-chart';
import { GrupoApilado, StackedBars } from '../../../../../shared/ui/charts/stacked-bars';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { ChipFiltro, FiltrosActivos, fechaChip } from '../../../../../shared/ui/panel-filtros/filtros-activos';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { RangoFechas, RangoRapido } from '../../../../../shared/ui/panel-filtros/rango-rapido';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';

const TOP_TIPOS = 8;

/**
 * Dashboard Inbound: control de proveedores.
 *
 * Responde, para el filtro aplicado: ¿quién genera más incidencias (Pareto)?,
 * ¿qué tipos se repiten y en qué proveedores (matriz y apiladas)?, ¿cómo
 * evolucionan los proveedores críticos (tendencia mensual)? y ¿qué SKUs son
 * reincidentes? Todo sale de un único endpoint de analítica (SQL + caché).
 */
@Component({
  selector: 'app-inbound-dashboard',
  imports: [
    BarChart,
    EmptyState,
    Heatmap,
    Icon,
    Kpi,
    LineChart,
    ParetoChart,
    StackedBars,
    PanelFiltros,
    FiltrosActivos,
    RangoRapido,
    SelectBuscable,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class InboundDashboard {
  private readonly incidenciasApi = inject(InboundIncidenciasApi);
  private readonly catalogos = inject(InboundCatalogos);

  private readonly recurso = new RecursoRemoto<AnaliticaInbound>(() =>
    this.incidenciasApi.analitica(this.filtros()),
  );
  readonly cargando = this.recurso.cargando;
  readonly error = this.recurso.error;
  readonly datos = this.recurso.valor;

  readonly proveedores = this.catalogos.proveedores;
  readonly tipos = this.catalogos.tipos;
  readonly opcionesProveedor = computed(() => this.proveedores().map((p) => ({ id: p.id, etiqueta: p.proveedor })));
  readonly opcionesTipo = computed(() => this.tipos().map((t) => ({ id: t.id, etiqueta: t.incidenciaInbound })));

  readonly filtroProveedor = signal<number | null>(null);
  readonly filtroTipo = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly filtrosAbierto = signal(false);

  readonly filtrosActivos = computed(() => this.chips().length);

  readonly chips = computed<ChipFiltro[]>(() => {
    const chips: ChipFiltro[] = [];
    const proveedor = this.filtroProveedor();
    if (proveedor !== null) {
      const nombre = this.proveedores().find((p) => p.id === proveedor)?.proveedor ?? `#${proveedor}`;
      chips.push({ clave: 'proveedor', etiqueta: 'Proveedor', valor: nombre, icono: 'truck' });
    }
    const tipo = this.filtroTipo();
    if (tipo !== null) {
      const nombre = this.tipos().find((t) => t.id === tipo)?.incidenciaInbound ?? `#${tipo}`;
      chips.push({ clave: 'tipo', etiqueta: 'Tipo', valor: nombre, icono: 'incidencias' });
    }
    if (this.filtroDesde()) {
      chips.push({ clave: 'desde', etiqueta: 'Desde', valor: fechaChip(this.filtroDesde()), icono: 'calendario' });
    }
    if (this.filtroHasta()) {
      chips.push({ clave: 'hasta', etiqueta: 'Hasta', valor: fechaChip(this.filtroHasta()), icono: 'calendario' });
    }
    return chips;
  });

  // ---------------- KPIs ----------------
  readonly kpis = computed(() => this.datos()?.kpis ?? null);
  readonly hayDatos = computed(() => (this.kpis()?.total ?? 0) > 0);

  readonly concentracionTop5 = computed(() => {
    const lista = this.datos()?.proveedores ?? [];
    const total = this.kpis()?.total ?? 0;
    return total ? lista.slice(0, 5).reduce((t, p) => t + p.total, 0) / total : 0;
  });

  readonly tarjetas = computed(() => {
    const k = this.kpis();
    return [
      {
        etiqueta: 'Incidencias',
        valor: k ? entero(k.total) : '—',
        icono: 'incidencias',
        color: 'var(--brand)',
        detalle: 'Registradas en el periodo',
        variacion: k ? variacion(k.total, k.totalPeriodoAnterior) : null,
      },
      {
        etiqueta: 'Unidades afectadas',
        valor: k ? compacto(k.unidades) : '—',
        icono: 'paquete',
        color: 'var(--warning)',
        detalle: k && k.total ? `${entero(Math.round(k.unidades / k.total))} u por incidencia` : '',
        variacion: k ? variacion(k.unidades, k.unidadesPeriodoAnterior) : null,
      },
      {
        etiqueta: 'Proveedores con incidencias',
        valor: k ? entero(k.proveedores) : '—',
        icono: 'truck',
        color: 'var(--accent)',
        detalle: k ? `${entero(k.tipos)} tipos distintos` : '',
        variacion: null,
      },
      {
        etiqueta: 'Concentración top 5',
        valor: k ? porcentaje(this.concentracionTop5()) : '—',
        icono: 'tendencia',
        color: 'var(--violet)',
        detalle: 'De las incidencias en 5 proveedores',
        variacion: null,
      },
      {
        etiqueta: 'Órdenes de compra',
        valor: k ? entero(k.ordenesCompra) : '—',
        icono: 'layers',
        color: 'var(--cyan)',
        detalle: 'OC distintas con incidencia',
        variacion: null,
      },
      {
        etiqueta: 'SKUs afectados',
        valor: k ? entero(k.skus) : '—',
        icono: 'key',
        color: 'var(--success)',
        detalle: 'Productos distintos',
        variacion: null,
      },
    ];
  });

  // ---------------- Hallazgos (texto) ----------------
  readonly hallazgos = computed(() => {
    const d = this.datos();
    if (!d || d.kpis.total === 0) {
      return [];
    }
    const total = d.kpis.total;
    const hallazgos: { icono: string; color: string; kicker: string; html: string }[] = [];
    const lider = d.proveedores[0];
    if (lider) {
      hallazgos.push({
        icono: 'alertTriangle',
        color: 'var(--brand)',
        kicker: 'Proveedor crítico',
        html: `<strong>${escapar(lider.proveedor)}</strong> concentra el <strong>${porcentaje(lider.total / total)}</strong> de las incidencias; su problema más frecuente es <strong>${escapar(lider.tipoDominante)}</strong> (${porcentaje(lider.total ? lider.totalTipoDominante / lider.total : 0)} de sus casos).`,
      });
    }
    const tipo = d.tipos[0];
    if (tipo) {
      hallazgos.push({
        icono: 'incidencias',
        color: 'var(--warning)',
        kicker: 'Incidencia más repetida',
        html: `<strong>${escapar(tipo.tipo)}</strong> suma <strong>${entero(tipo.total)}</strong> casos (${porcentaje(tipo.total / total)}) y aparece en <strong>${entero(tipo.proveedores)}</strong> proveedores.`,
      });
    }
    const sku = d.skus[0];
    if (sku && sku.total > 1) {
      hallazgos.push({
        icono: 'paquete',
        color: 'var(--accent)',
        kicker: 'SKU reincidente',
        html: `El SKU <strong>${escapar(sku.sku)}</strong> tuvo <strong>${entero(sku.total)}</strong> incidencias (${entero(sku.unidades)} u) con <strong>${entero(sku.proveedores)}</strong> proveedor(es).`,
      });
    }
    return hallazgos;
  });

  // ---------------- Gráficos ----------------
  readonly pareto = computed<ItemPareto[]>(() =>
    (this.datos()?.proveedores ?? []).map((p) => ({
      etiqueta: p.proveedor,
      valor: p.total,
      detalle: `${entero(p.unidades)} u · principal: ${p.tipoDominante}`,
    })),
  );

  readonly barrasTipos = computed<BarraDato[]>(() => {
    const tipos = this.datos()?.tipos ?? [];
    const barras = tipos.slice(0, TOP_TIPOS).map((t, i) => ({
      etiqueta: t.tipo,
      valor: t.total,
      color: SERIES[i % SERIES.length],
      detalle: `${entero(t.proveedores)} prov. · ${compacto(t.unidades)} u`,
    }));
    const resto = tipos.slice(TOP_TIPOS);
    if (resto.length) {
      barras.push({
        etiqueta: `Otros (${resto.length})`,
        valor: resto.reduce((t, x) => t + x.total, 0),
        color: SERIE_OTROS,
        detalle: '',
      });
    }
    return barras;
  });

  readonly matriz = computed<CeldaCalor[]>(() => this.datos()?.proveedorTipo ?? []);

  /** Composición por tipo de los proveedores con más incidencias. */
  readonly apiladas = computed<GrupoApilado[]>(() => {
    const grupos = new Map<string, { nombre: string; valor: number }[]>();
    for (const c of this.datos()?.proveedorTipo ?? []) {
      const lista = grupos.get(c.fila) ?? [];
      lista.push({ nombre: c.columna, valor: c.valor });
      grupos.set(c.fila, lista);
    }
    return [...grupos.entries()].map(([etiqueta, segmentos]) => ({ etiqueta, segmentos }));
  });

  readonly tendencia = computed(() => {
    const serie = this.datos()?.tendencia ?? [];
    return {
      etiquetas: serie.map((p) => p.periodo),
      series: [{ nombre: 'Incidencias', color: 'var(--brand)', valores: serie.map((p) => p.valor) }] as SerieLinea[],
    };
  });

  readonly tendenciaUnidades = computed(() => {
    const serie = this.datos()?.tendencia ?? [];
    return {
      etiquetas: serie.map((p) => p.periodo),
      series: [{ nombre: 'Unidades', color: 'var(--warning)', valores: serie.map((p) => p.secundario) }] as SerieLinea[],
    };
  });

  /** Evolución mensual de los 5 proveedores críticos (una línea por proveedor). */
  readonly mensual = computed(() => {
    const top = this.datos()?.mensualTop ?? [];
    const meses = [...new Set(top.flatMap((p) => p.meses.map((m) => m.mes)))].sort();
    const series: SerieLinea[] = top.map((p, i) => {
      const porMes = new Map(p.meses.map((m) => [m.mes, m.incidencias]));
      return { nombre: p.proveedor, color: SERIES[i % SERIES.length], valores: meses.map((m) => porMes.get(m) ?? 0) };
    });
    return { etiquetas: meses, series };
  });

  readonly ranking = computed(() => {
    const total = this.kpis()?.total ?? 0;
    const lista = this.datos()?.proveedores ?? [];
    const tope = lista[0]?.total || 1;
    return lista.slice(0, 15).map((p) => ({
      ...p,
      pct: porcentaje(total ? p.total / total : 0),
      barra: (p.total / tope) * 100,
      dominantePct: porcentaje(p.total ? p.totalTipoDominante / p.total : 0),
      unidadesPorIncidencia: p.total ? entero(Math.round(p.unidades / p.total)) : '—',
      totalTexto: entero(p.total),
      unidadesTexto: entero(p.unidades),
    }));
  });

  readonly skus = computed(() =>
    (this.datos()?.skus ?? []).map((s) => ({ ...s, totalTexto: entero(s.total), unidadesTexto: entero(s.unidades) })),
  );

  readonly granularidad = computed(() => (this.datos()?.granularidad === 'dia' ? 'por día' : 'por mes'));

  constructor() {
    this.catalogos.cargar();
    this.cargar();
  }

  cargar(): void {
    this.recurso.recargar();
  }

  private filtros(): IncidenciaInboundFiltros {
    return {
      proveedor: this.filtroProveedor(),
      tipo: this.filtroTipo(),
      desde: this.filtroDesde(),
      hasta: this.filtroHasta(),
    };
  }

  alternarFiltros(): void {
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  setFiltroProveedor(id: number | null): void {
    this.filtroProveedor.set(id);
    this.cargar();
  }

  setFiltroTipo(id: number | null): void {
    this.filtroTipo.set(id);
    this.cargar();
  }

  /** Drill-down desde el ranking: filtra el dashboard por ese proveedor. */
  enfocarProveedor(id: number): void {
    this.setFiltroProveedor(this.filtroProveedor() === id ? null : id);
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set((evento.target as HTMLInputElement).value);
    this.cargar();
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set((evento.target as HTMLInputElement).value);
    this.cargar();
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.cargar();
  }

  quitarFiltro(clave: string): void {
    if (clave === 'proveedor') this.filtroProveedor.set(null);
    if (clave === 'tipo') this.filtroTipo.set(null);
    if (clave === 'desde') this.filtroDesde.set('');
    if (clave === 'hasta') this.filtroHasta.set('');
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroProveedor.set(null);
    this.filtroTipo.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.cargar();
  }
}

/** Los nombres vienen de la BD: se escapan antes de insertarlos como HTML en los hallazgos. */
function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
