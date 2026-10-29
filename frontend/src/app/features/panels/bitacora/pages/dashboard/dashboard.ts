import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { valorNumero, valorTexto } from '../../../../../core/utils/evento.util';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { OpcionesDependientes } from '../../../../../core/utils/opciones-dependientes';
import { AnaliticaBitacora } from '../../data/analitica.model';
import { IncidenciaFiltros, Responsable } from '../../data/bitacora.model';
import { AreasApi } from '../../data/areas.api';
import { BitacoraCatalogos } from '../../state/bitacora-catalogos';
import { IncidenciasApi } from '../../data/incidencias.api';
import { BulletChart, ItemBullet } from '../../../../../shared/ui/charts/bullet-chart';
import { ColumnChart, Columna } from '../../../../../shared/ui/charts/column-chart';
import { duracion, entero, porcentaje, variacion } from '../../../../../shared/ui/charts/formato';
import { CeldaCalor, Heatmap } from '../../../../../shared/ui/charts/heatmap';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { LineChart, SerieLinea } from '../../../../../shared/ui/charts/line-chart';
import { ItemRanking, RankingTiles } from '../../../../../shared/ui/charts/ranking-tiles';
import { RingChart, SegmentoAnillo } from '../../../../../shared/ui/charts/ring-chart';
import { GrupoApilado, StackedBars } from '../../../../../shared/ui/charts/stacked-bars';
import { ItemTreemap, Treemap } from '../../../../../shared/ui/charts/treemap';
import { ItemWaffle, WaffleChart } from '../../../../../shared/ui/charts/waffle-chart';
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

/**
 * Dashboard Bitácora: sistemas y tipos de incidencia.
 *
 * Responde: ¿qué sistemas fallan más y cuánto tardamos en resolverlos?, ¿qué
 * tipos se repiten y en qué sistema?, ¿qué combinación centro × sistema
 * concentra los casos?, ¿cómo evoluciona el volumen y a qué hora ocurre?
 */
@Component({
  selector: 'app-bitacora-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BulletChart,
    ColumnChart,
    EmptyState,
    Heatmap,
    Icon,
    Kpi,
    LineChart,
    RankingTiles,
    RingChart,
    Treemap,
    WaffleChart,
    StackedBars,
    PanelFiltros,
    FiltrosActivos,
    RangoRapido,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardBitacora {
  private readonly incidenciasApi = inject(IncidenciasApi);
  private readonly catalogos = inject(BitacoraCatalogos);
  private readonly areasApi = inject(AreasApi);

  private readonly recurso = new RecursoRemoto<AnaliticaBitacora>(() =>
    this.incidenciasApi.analitica(this.filtros()),
  );
  readonly cargando = this.recurso.cargando;
  readonly error = this.recurso.error;
  readonly datos = this.recurso.valor;

  readonly centros = this.catalogos.centros;
  readonly sistemas = this.catalogos.sistemas;
  readonly areas = this.catalogos.areas;

  private readonly responsablesDelFiltro = new OpcionesDependientes(
    (areaId) => this.areasApi.responsablesDe(areaId),
    'No se pudieron cargar los responsables',
  );
  readonly responsablesFiltro = this.responsablesDelFiltro.opciones;
  readonly cargandoResponsablesFiltro = this.responsablesDelFiltro.cargando;

  readonly panelFiltros = new EstadoFiltros({
    centro: filtroCatalogo('Centro', 'centro', this.centros, (c) => c.centro),
    sistema: filtroCatalogo('Sistema', 'sistema', this.sistemas, (s) => s.sistema),
    area: filtroCatalogo('Área', 'area', this.areas, (a) => a.area),
    responsable: filtroCatalogo(
      'Responsable',
      'responsable',
      this.responsablesFiltro,
      (r) => r.responsable,
    ),
    desde: filtroFecha('Desde'),
    hasta: filtroFecha('Hasta'),
  });
  readonly filtroCentro = this.panelFiltros.valores.centro;
  readonly filtroSistema = this.panelFiltros.valores.sistema;
  readonly filtroArea = this.panelFiltros.valores.area;
  readonly filtroResponsable = this.panelFiltros.valores.responsable;
  readonly filtroDesde = this.panelFiltros.valores.desde;
  readonly filtroHasta = this.panelFiltros.valores.hasta;
  readonly filtrosAbierto = this.panelFiltros.abierto;
  /** Filtros del panel aplicados, como chips removibles. */
  readonly chips = this.panelFiltros.chips;
  readonly filtrosActivos = this.panelFiltros.activos;

  // ---------------- KPIs ----------------
  readonly kpis = computed(() => this.datos()?.kpis ?? null);
  readonly hayDatos = computed(() => (this.kpis()?.total ?? 0) > 0);

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
        etiqueta: 'Tiempo medio de solución',
        valor: k ? duracion(k.mttrSegundos) : '—',
        icono: 'reloj',
        color: 'var(--accent)',
        detalle: k ? `MTTR de ${entero(k.resueltas)} resueltas` : '',
        variacion: null,
      },
      {
        etiqueta: 'Horas invertidas',
        valor: k ? entero(Math.round(k.segundosSolucion / 3600)) + ' h' : '—',
        icono: 'activity',
        color: 'var(--violet)',
        detalle: 'Suma de tiempos de solución',
        variacion: null,
      },
      {
        etiqueta: 'Sistemas afectados',
        valor: k ? entero(k.sistemasAfectados) : '—',
        icono: 'sistema',
        color: 'var(--cyan)',
        detalle: 'Con al menos una incidencia',
        variacion: null,
      },
      {
        etiqueta: 'Centros afectados',
        valor: k ? entero(k.centrosAfectados) : '—',
        icono: 'centro',
        color: 'var(--success)',
        detalle: 'Con al menos una incidencia',
        variacion: null,
      },
    ];
  });

  // ---------------- Gráficos ----------------
  /** Sistemas por volumen (treemap): área = casos, con su MTTR como detalle. */
  readonly mapaSistemas = computed<ItemTreemap[]>(() =>
    (this.datos()?.sistemas ?? []).map((g) => ({
      etiqueta: g.etiqueta,
      valor: g.total,
      detalle: `MTTR ${duracion(g.mttrSegundos)}`,
    })),
  );

  /** Tipos más repetidos, con el sistema al que pertenecen. */
  readonly rankingTipos = computed<ItemRanking[]>(() =>
    (this.datos()?.tipos ?? []).map((t) => ({
      etiqueta: t.tipo,
      valor: t.total,
      chip: t.sistema,
      detalle: `MTTR ${duracion(t.mttrSegundos)}`,
    })),
  );

  readonly matriz = computed<CeldaCalor[]>(() => this.datos()?.centroSistema ?? []);

  /** Tipos de incidencia dentro de cada sistema. */
  readonly apiladas = computed<GrupoApilado[]>(() => {
    const grupos = new Map<string, { nombre: string; valor: number }[]>();
    for (const t of this.datos()?.tipos ?? []) {
      const lista = grupos.get(t.sistema) ?? [];
      lista.push({ nombre: t.tipo, valor: t.total });
      grupos.set(t.sistema, lista);
    }
    return [...grupos.entries()].map(([etiqueta, segmentos]) => ({ etiqueta, segmentos }));
  });

  readonly anilloCentros = computed<SegmentoAnillo[]>(() =>
    (this.datos()?.centros ?? []).map((g) => ({
      etiqueta: g.etiqueta,
      valor: g.total,
      detalle: `MTTR ${duracion(g.mttrSegundos)}`,
    })),
  );

  readonly waffleAreas = computed<ItemWaffle[]>(() =>
    (this.datos()?.areas ?? []).map((g) => ({
      etiqueta: g.etiqueta,
      valor: g.total,
      detalle: `MTTR ${duracion(g.mttrSegundos)}`,
    })),
  );

  /** ¿Dónde tardamos más? MTTR por sistema contra el MTTR general. */
  readonly bulletMttr = computed<ItemBullet[]>(() =>
    (this.datos()?.sistemas ?? [])
      .filter((g) => g.mttrSegundos > 0)
      .map((g) => ({
        etiqueta: g.etiqueta,
        valor: g.mttrSegundos,
        texto: duracion(g.mttrSegundos),
        detalle: `${entero(g.total)} casos`,
      })),
  );

  readonly mttrGeneral = computed(() => this.kpis()?.mttrSegundos ?? 0);
  readonly mttrGeneralTexto = computed(() => duracion(this.mttrGeneral()));

  readonly tendencia = computed(() => {
    const serie = this.datos()?.tendencia ?? [];
    return {
      etiquetas: serie.map((p) => p.periodo),
      series: [
        { nombre: 'Incidencias', color: 'var(--accent)', valores: serie.map((p) => p.valor) },
      ] as SerieLinea[],
    };
  });

  readonly porHora = computed<Columna[]>(() => {
    const mapa = new Map((this.datos()?.porHora ?? []).map((p) => [p.periodo, p.valor]));
    return Array.from({ length: 24 }, (_, h) => {
      const clave = `${h}`.padStart(2, '0');
      return { etiqueta: clave, valor: mapa.get(clave) ?? 0 };
    });
  });

  readonly granularidad = computed(() =>
    this.datos()?.granularidad === 'dia' ? 'por día' : 'por mes',
  );

  constructor() {
    this.cargarCatalogos();
    this.cargar();
  }

  cargarCatalogos(): void {
    this.catalogos.cargar();
  }

  cargar(): void {
    this.recurso.recargar();
  }

  private filtros(): IncidenciaFiltros {
    return {
      centro: this.filtroCentro(),
      sistema: this.filtroSistema(),
      area: this.filtroArea(),
      responsable: this.filtroResponsable(),
      desde: this.filtroDesde(),
      hasta: this.filtroHasta(),
    };
  }

  alternarFiltros(): void {
    this.panelFiltros.alternar();
  }

  setFiltroCentro(evento: Event): void {
    this.filtroCentro.set(valorNumero(evento));
    this.cargar();
  }

  setFiltroSistema(evento: Event): void {
    this.filtroSistema.set(valorNumero(evento));
    this.cargar();
  }

  setFiltroArea(evento: Event): void {
    const areaId = valorNumero(evento);
    this.filtroArea.set(areaId);
    this.filtroResponsable.set(null);
    this.responsablesDelFiltro.elegir(areaId);
    this.cargar();
  }

  setFiltroResponsable(evento: Event): void {
    this.filtroResponsable.set(valorNumero(evento));
    this.cargar();
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set(valorTexto(evento));
    this.cargar();
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set(valorTexto(evento));
    this.cargar();
  }

  quitarFiltro(clave: string): void {
    this.panelFiltros.quitar(clave);
    if (clave === 'area') {
      this.filtroResponsable.set(null);
      this.responsablesDelFiltro.elegir(null);
    }
    this.cargar();
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.panelFiltros.limpiar();
    this.responsablesDelFiltro.elegir(null);
    this.cargar();
  }
}
