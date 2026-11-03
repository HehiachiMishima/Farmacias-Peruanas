import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { valorTexto } from '../../../../../core/utils/evento.util';
import { AnaliticaInbound } from '../../data/analitica.model';
import { IncidenciaInboundFiltros } from '../../data/inbound.model';
import { InboundCatalogos } from '../../state/inbound-catalogos';
import { InboundIncidenciasApi } from '../../data/inbound-incidencias.api';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { CeldaCalor, Heatmap } from '../../../../../shared/ui/charts/heatmap';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { LineChart } from '../../../../../shared/ui/charts/line-chart';
import { RankingTiles } from '../../../../../shared/ui/charts/ranking-tiles';
import { StackedBars, agruparApiladas } from '../../../../../shared/ui/charts/stacked-bars';
import { Treemap } from '../../../../../shared/ui/charts/treemap';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import {
  EstadoFiltros,
  filtroCatalogo,
  filtroFecha,
} from '../../../../../shared/ui/panel-filtros/estado-filtros';
import { FiltrosActivos } from '../../../../../shared/ui/panel-filtros/filtros-activos';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { RangoFechas, RangoRapido } from '../../../../../shared/ui/panel-filtros/rango-rapido';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import {
  concentracionTop5,
  evolucionMensual,
  hallazgosInbound,
  mapaProveedores,
  rankingProveedores,
  rankingTipos,
  serieTendencia,
  skusConTexto,
  tarjetasInbound,
} from './dashboard.logica';

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
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EmptyState,
    Heatmap,
    Icon,
    Kpi,
    LineChart,
    RankingTiles,
    Treemap,
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
  readonly opcionesProveedor = computed(() =>
    this.proveedores().map((p) => ({ id: p.id, etiqueta: p.proveedor })),
  );
  readonly opcionesTipo = computed(() =>
    this.tipos().map((t) => ({ id: t.id, etiqueta: t.incidenciaInbound })),
  );

  readonly panelFiltros = new EstadoFiltros({
    proveedor: filtroCatalogo('Proveedor', 'truck', this.proveedores, (p) => p.proveedor),
    tipo: filtroCatalogo('Tipo', 'incidencias', this.tipos, (t) => t.incidenciaInbound),
    desde: filtroFecha('Desde'),
    hasta: filtroFecha('Hasta'),
  });
  readonly filtroProveedor = this.panelFiltros.valores.proveedor;
  readonly filtroTipo = this.panelFiltros.valores.tipo;
  readonly filtroDesde = this.panelFiltros.valores.desde;
  readonly filtroHasta = this.panelFiltros.valores.hasta;
  readonly filtrosAbierto = this.panelFiltros.abierto;
  /** Filtros del panel aplicados, como chips removibles. */
  readonly chips = this.panelFiltros.chips;
  readonly filtrosActivos = this.panelFiltros.activos;

  // ---------------- KPIs ----------------
  readonly kpis = computed(() => this.datos()?.kpis ?? null);
  readonly hayDatos = computed(() => (this.kpis()?.total ?? 0) > 0);

  readonly concentracionTop5 = computed(() =>
    concentracionTop5(this.datos()?.proveedores ?? [], this.kpis()?.total ?? 0),
  );

  readonly tarjetas = computed(() => tarjetasInbound(this.kpis(), this.concentracionTop5()));

  readonly hallazgos = computed(() => hallazgosInbound(this.datos()));

  // ---------------- Gráficos ----------------
  readonly mapaProveedores = computed(() => mapaProveedores(this.datos()?.proveedores ?? []));

  readonly rankingTipos = computed(() => rankingTipos(this.datos()?.tipos ?? []));

  readonly matriz = computed<CeldaCalor[]>(() => this.datos()?.proveedorTipo ?? []);

  /** Composición por tipo de los proveedores con más incidencias. */
  readonly apiladas = computed(() =>
    agruparApiladas(
      this.datos()?.proveedorTipo ?? [],
      (c) => c.fila,
      (c) => ({ nombre: c.columna, valor: c.valor }),
    ),
  );

  readonly tendencia = computed(() =>
    serieTendencia(this.datos()?.tendencia ?? [], 'Incidencias', 'var(--brand)', 'valor'),
  );

  readonly tendenciaUnidades = computed(() =>
    serieTendencia(this.datos()?.tendencia ?? [], 'Unidades', 'var(--warning)', 'secundario'),
  );

  /** Evolución mensual de los 5 proveedores críticos (una línea por proveedor). */
  readonly mensual = computed(() => evolucionMensual(this.datos()?.mensualTop ?? []));

  readonly ranking = computed(() =>
    rankingProveedores(this.datos()?.proveedores ?? [], this.kpis()?.total ?? 0),
  );

  readonly skus = computed(() => skusConTexto(this.datos()?.skus ?? []));

  readonly granularidad = computed(() =>
    this.datos()?.granularidad === 'dia' ? 'por día' : 'por mes',
  );

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
    this.panelFiltros.alternar();
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
    this.filtroDesde.set(valorTexto(evento));
    this.cargar();
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set(valorTexto(evento));
    this.cargar();
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.cargar();
  }

  quitarFiltro(clave: string): void {
    this.panelFiltros.quitar(clave);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.panelFiltros.limpiar();
    this.cargar();
  }
}
