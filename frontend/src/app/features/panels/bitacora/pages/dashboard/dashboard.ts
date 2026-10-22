import { Component, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { AnaliticaBitacora, GrupoBitacora, IncidenciaFiltros, Responsable } from '../../../../../core/models';
import { AreasApi } from '../../../../../core/services/areas.api';
import { BitacoraCatalogos } from '../../../../../core/services/bitacora-catalogos';
import { IncidenciasApi } from '../../../../../core/services/incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { ColumnChart, Columna } from '../../../../../shared/ui/charts/column-chart';
import { SERIES, SERIE_OTROS, duracion, entero, porcentaje, variacion } from '../../../../../shared/ui/charts/formato';
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

const TOP = 8;

/**
 * Dashboard Bitácora: sistemas y tipos de incidencia.
 *
 * Responde: ¿qué sistemas fallan más y cuánto tardamos en resolverlos?, ¿qué
 * tipos se repiten y en qué sistema?, ¿qué combinación centro × sistema
 * concentra los casos?, ¿cómo evoluciona el volumen y a qué hora ocurre?
 */
@Component({
  selector: 'app-bitacora-dashboard',
  imports: [
    BarChart,
    ColumnChart,
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
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardBitacora {
  private readonly incidenciasApi = inject(IncidenciasApi);
  private readonly catalogos = inject(BitacoraCatalogos);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);

  private readonly recurso = new RecursoRemoto<AnaliticaBitacora>(() =>
    this.incidenciasApi.analitica(this.filtros()),
  );
  readonly cargando = this.recurso.cargando;
  readonly error = this.recurso.error;
  readonly datos = this.recurso.valor;

  readonly centros = this.catalogos.centros;
  readonly sistemas = this.catalogos.sistemas;
  readonly areas = this.catalogos.areas;

  readonly filtroCentro = signal<number | null>(null);
  readonly filtroSistema = signal<number | null>(null);
  readonly filtroArea = signal<number | null>(null);
  readonly filtroResponsable = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly responsablesFiltro = signal<Responsable[]>([]);
  readonly cargandoResponsablesFiltro = signal(false);
  readonly filtrosAbierto = signal(false);

  /** Filtros del panel aplicados, como chips removibles. */
  readonly chips = computed<ChipFiltro[]>(() => {
    const chips: ChipFiltro[] = [];
    const nombre = <T extends { id: number }>(lista: T[], id: number, campo: keyof T): string =>
      String(lista.find((x) => x.id === id)?.[campo] ?? `#${id}`);
    const centro = this.filtroCentro();
    if (centro !== null) chips.push({ clave: 'centro', etiqueta: 'Centro', valor: nombre(this.centros(), centro, 'centro'), icono: 'centro' });
    const sistema = this.filtroSistema();
    if (sistema !== null) chips.push({ clave: 'sistema', etiqueta: 'Sistema', valor: nombre(this.sistemas(), sistema, 'sistema'), icono: 'sistema' });
    const area = this.filtroArea();
    if (area !== null) chips.push({ clave: 'area', etiqueta: 'Área', valor: nombre(this.areas(), area, 'area'), icono: 'area' });
    const responsable = this.filtroResponsable();
    if (responsable !== null) chips.push({ clave: 'responsable', etiqueta: 'Responsable', valor: nombre(this.responsablesFiltro(), responsable, 'responsable'), icono: 'responsable' });
    if (this.filtroDesde()) chips.push({ clave: 'desde', etiqueta: 'Desde', valor: fechaChip(this.filtroDesde()), icono: 'calendario' });
    if (this.filtroHasta()) chips.push({ clave: 'hasta', etiqueta: 'Hasta', valor: fechaChip(this.filtroHasta()), icono: 'calendario' });
    return chips;
  });

  readonly filtrosActivos = computed(() => {
    let total = 0;
    if (this.filtroCentro() !== null) total++;
    if (this.filtroSistema() !== null) total++;
    if (this.filtroArea() !== null) total++;
    if (this.filtroResponsable() !== null) total++;
    if (this.filtroDesde()) total++;
    if (this.filtroHasta()) total++;
    return total;
  });

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

  // ---------------- Hallazgos ----------------
  readonly hallazgos = computed(() => {
    const d = this.datos();
    if (!d || d.kpis.total === 0) {
      return [];
    }
    const total = d.kpis.total;
    const salida: { icono: string; color: string; kicker: string; html: string }[] = [];
    const sistema = d.sistemas[0];
    if (sistema) {
      salida.push({
        icono: 'sistema',
        color: 'var(--brand)',
        kicker: 'Sistema más crítico',
        html: `<strong>${escapar(sistema.etiqueta)}</strong> acumula <strong>${entero(sistema.total)}</strong> incidencias (${porcentaje(sistema.total / total)}), con un tiempo medio de solución de <strong>${duracion(sistema.mttrSegundos)}</strong>.`,
      });
    }
    const cruce = [...d.centroSistema].sort((a, b) => b.valor - a.valor)[0];
    if (cruce) {
      salida.push({
        icono: 'centro',
        color: 'var(--accent)',
        kicker: 'Punto caliente',
        html: `<strong>${escapar(cruce.fila)}</strong> con <strong>${escapar(cruce.columna)}</strong> es la combinación centro-sistema con más casos: <strong>${entero(cruce.valor)}</strong>.`,
      });
    }
    const tipo = d.tipos[0];
    if (tipo) {
      salida.push({
        icono: 'incidencias',
        color: 'var(--warning)',
        kicker: 'Incidencia más repetida',
        html: `<strong>${escapar(tipo.tipo)}</strong> (${escapar(tipo.sistema)}) se repitió <strong>${entero(tipo.total)}</strong> veces.`,
      });
    }
    const hora = [...d.porHora].sort((a, b) => b.valor - a.valor)[0];
    if (hora) {
      salida.push({
        icono: 'reloj',
        color: 'var(--violet)',
        kicker: 'Hora pico',
        html: `La mayor cantidad de incidencias inicia a las <strong>${hora.periodo}:00</strong> (${entero(hora.valor)} casos).`,
      });
    }
    return salida;
  });

  // ---------------- Gráficos ----------------
  readonly paretoSistemas = computed<ItemPareto[]>(() =>
    (this.datos()?.sistemas ?? []).map((g) => ({
      etiqueta: g.etiqueta,
      valor: g.total,
      detalle: `MTTR ${duracion(g.mttrSegundos)}`,
    })),
  );

  readonly barrasTipos = computed<BarraDato[]>(() => {
    const tipos = this.datos()?.tipos ?? [];
    const barras: BarraDato[] = tipos.slice(0, TOP).map((t, i) => ({
      etiqueta: `${t.tipo} · ${t.sistema}`,
      valor: t.total,
      color: SERIES[i % SERIES.length],
      detalle: `MTTR ${duracion(t.mttrSegundos)}`,
    }));
    const resto = tipos.slice(TOP);
    if (resto.length) {
      barras.push({ etiqueta: `Otros (${resto.length})`, valor: resto.reduce((t, x) => t + x.total, 0), color: SERIE_OTROS });
    }
    return barras;
  });

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

  readonly barrasCentros = computed<BarraDato[]>(() => this.aBarras(this.datos()?.centros ?? [], 'var(--accent)'));
  readonly barrasAreas = computed<BarraDato[]>(() => this.aBarras(this.datos()?.areas ?? [], 'var(--violet)'));

  /** ¿Dónde tardamos más? MTTR por sistema (minutos), solo sistemas con incidencias resueltas. */
  readonly barrasMttr = computed<BarraDato[]>(() =>
    [...(this.datos()?.sistemas ?? [])]
      .filter((g) => g.mttrSegundos > 0)
      .sort((a, b) => b.mttrSegundos - a.mttrSegundos)
      .slice(0, TOP)
      .map((g, i) => ({
        etiqueta: g.etiqueta,
        valor: Math.round(g.mttrSegundos / 60),
        color: i === 0 ? 'var(--brand)' : 'var(--warning)',
        detalle: `${duracion(g.mttrSegundos)} · ${entero(g.total)} casos`,
      })),
  );

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

  readonly granularidad = computed(() => (this.datos()?.granularidad === 'dia' ? 'por día' : 'por mes'));

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
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  setFiltroCentro(evento: Event): void {
    this.filtroCentro.set(this.valorNumero(evento));
    this.cargar();
  }

  setFiltroSistema(evento: Event): void {
    this.filtroSistema.set(this.valorNumero(evento));
    this.cargar();
  }

  setFiltroArea(evento: Event): void {
    const areaId = this.valorNumero(evento);
    this.filtroArea.set(areaId);
    this.filtroResponsable.set(null);
    this.responsablesFiltro.set([]);
    if (areaId !== null) {
      this.cargarResponsablesFiltro(areaId);
    }
    this.cargar();
  }

  setFiltroResponsable(evento: Event): void {
    this.filtroResponsable.set(this.valorNumero(evento));
    this.cargar();
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set((evento.target as HTMLInputElement).value);
    this.cargar();
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set((evento.target as HTMLInputElement).value);
    this.cargar();
  }

  quitarFiltro(clave: string): void {
    if (clave === 'centro') this.filtroCentro.set(null);
    if (clave === 'sistema') this.filtroSistema.set(null);
    if (clave === 'area') {
      this.filtroArea.set(null);
      this.filtroResponsable.set(null);
      this.responsablesFiltro.set([]);
    }
    if (clave === 'responsable') this.filtroResponsable.set(null);
    if (clave === 'desde') this.filtroDesde.set('');
    if (clave === 'hasta') this.filtroHasta.set('');
    this.cargar();
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroCentro.set(null);
    this.filtroSistema.set(null);
    this.filtroArea.set(null);
    this.filtroResponsable.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.responsablesFiltro.set([]);
    this.cargar();
  }

  private cargarResponsablesFiltro(areaId: number): void {
    this.cargandoResponsablesFiltro.set(true);
    this.areasApi
      .responsablesDe(areaId)
      .pipe(finalize(() => this.cargandoResponsablesFiltro.set(false)))
      .subscribe({
        next: (lista) => this.responsablesFiltro.set(lista),
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los responsables', mensajeDeError(err)),
      });
  }

  private aBarras(grupos: GrupoBitacora[], color: string): BarraDato[] {
    const barras: BarraDato[] = grupos.slice(0, TOP).map((g) => ({
      etiqueta: g.etiqueta,
      valor: g.total,
      color,
      detalle: `MTTR ${duracion(g.mttrSegundos)}`,
    }));
    const resto = grupos.slice(TOP);
    if (resto.length) {
      barras.push({ etiqueta: `Otros (${resto.length})`, valor: resto.reduce((t, g) => t + g.total, 0), color: SERIE_OTROS });
    }
    return barras;
  }

  private valorNumero(evento: Event): number | null {
    const valor = (evento.target as HTMLSelectElement).value;
    return valor ? Number(valor) : null;
  }
}

/** Los nombres vienen de la BD: se escapan antes de insertarlos como HTML en los hallazgos. */
function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
