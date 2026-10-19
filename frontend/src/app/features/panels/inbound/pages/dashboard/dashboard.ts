import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { EstadisticaInbound, GrupoEstadisticaInbound, IncidenciaInboundFiltros, ProveedorMensualEstadistica } from '../../../../../core/models';
import { InboundControlApi } from '../../../../../core/services/inbound-control.api';
import { InboundIncidenciasApi } from '../../../../../core/services/inbound-incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { ColumnasProveedores, DatoCol } from '../../../../../shared/ui/charts/cols-proveedores';
import { DatoPareto, ParetoProveedores } from '../../../../../shared/ui/charts/pareto-proveedores';
import { RankingProveedores } from '../../../../../shared/ui/charts/ranking-proveedores';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';

const TOP = 10;
const TOP_COLS = 15;
const PALETA = ['var(--brand)', 'var(--success)', 'var(--warning)', 'var(--danger)', '#a855f7'];
const COLOR_OTROS = 'var(--color-graphite)';

@Component({
  selector: 'app-inbound-dashboard',
  imports: [
    DecimalPipe,
    BarChart,
    ColumnasProveedores,
    RankingProveedores,
    ParetoProveedores,
    EmptyState,
    Icon,
    PanelFiltros,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class InboundDashboard {
  private readonly incidenciasApi = inject(InboundIncidenciasApi);
  private readonly controlApi = inject(InboundControlApi);
  private readonly notificaciones = inject(Notificaciones);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly estadistica = signal<EstadisticaInbound | null>(null);
  readonly proveedoresMensual = signal<ProveedorMensualEstadistica[]>([]);

  readonly proveedores = signal<{ id: number; proveedor: string }[]>([]);
  readonly tipos = signal<{ id: number; incidenciaInbound: string }[]>([]);

  readonly filtroProveedor = signal<number | null>(null);
  readonly filtroTipo = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly filtrosAbierto = signal(false);

  readonly filtrosActivos = computed(() => {
    let total = 0;
    if (this.filtroProveedor() !== null) total++;
    if (this.filtroTipo() !== null) total++;
    if (this.filtroDesde()) total++;
    if (this.filtroHasta()) total++;
    return total;
  });

  readonly totalIncidencias = computed(() => this.estadistica()?.total ?? 0);
  readonly totalUnidades = computed(() => this.estadistica()?.totalUnidades ?? 0);

  readonly topProveedores = computed(() => this.proveedoresMensual().slice(0, TOP));
  readonly barrasTipos = computed(() => this.aBarras(this.estadistica()?.tipos ?? []));

  readonly paretoDatos = computed<DatoPareto[]>(() =>
    this.proveedoresMensual().map((p) => ({ etiqueta: p.proveedor, valor: p.total })),
  );

  readonly mensajeTop10 = computed(() => {
    const lista = this.proveedoresMensual();
    const suma = lista.reduce((t, p) => t + p.total, 0);
    if (suma === 0) return 'Sin incidencias para los filtros aplicados.';
    const pct = (lista.slice(0, TOP).reduce((t, p) => t + p.total, 0) / suma) * 100;
    return `Ranking de los ${Math.min(TOP, lista.length)} proveedores con más incidencias · concentran el ${pct.toFixed(1)}% del total · incluye su evolución mes a mes`;
  });

  readonly totalProveedores = computed(() => this.proveedoresMensual().length);

  readonly pctTop10 = computed(() => {
    const lista = this.proveedoresMensual();
    const suma = lista.reduce((t, p) => t + p.total, 0);
    if (suma === 0) return 0;
    return (lista.slice(0, TOP).reduce((t, p) => t + p.total, 0) / suma) * 100;
  });

  readonly pctResto = computed(() => 100 - this.pctTop10());

  readonly colsTop = computed<DatoCol[]>(() =>
    this.proveedoresMensual()
      .slice(0, TOP_COLS)
      .map((p) => ({ etiqueta: p.proveedor, valor: p.total })),
  );

  readonly colsResto = computed<DatoCol[]>(() =>
    this.proveedoresMensual()
      .slice(TOP_COLS)
      .map((p) => ({ etiqueta: p.proveedor, valor: p.total })),
  );

  readonly mensajeColsTop = computed(() => {
    const lista = this.proveedoresMensual();
    const suma = lista.reduce((t, p) => t + p.total, 0);
    if (suma === 0) return 'Sin incidencias para los filtros aplicados.';
    const n = Math.min(TOP_COLS, lista.length);
    const pct = (lista.slice(0, TOP_COLS).reduce((t, p) => t + p.total, 0) / suma) * 100;
    return `Total de incidencias por proveedor · los ${n} con más incidencias concentran el ${pct.toFixed(1)}% del total`;
  });

  readonly mensajeColsResto = computed(() => {
    const restantes = this.colsResto().length;
    if (restantes === 0) return 'No hay más proveedores con incidencias.';
    return `Los otros ${restantes} proveedores · total de incidencias de cada uno · desliza para recorrerlos →`;
  });

  constructor() {
    this.cargarCatalogos();
    this.cargar();
  }

  cargarCatalogos(): void {
    forkJoin({
      proveedores: this.controlApi.listarProveedores(),
      tipos: this.controlApi.listarTipos(),
    }).subscribe({
      next: ({ proveedores, tipos }) => {
        this.proveedores.set(proveedores);
        this.tipos.set(tipos);
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudieron cargar los catálogos', mensajeDeError(err)),
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      estadistica: this.incidenciasApi.estadisticas(this.filtros()),
      proveedoresMensual: this.incidenciasApi.estadisticaProveedoresMensual(this.filtros()),
    })
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: ({ estadistica, proveedoresMensual }) => {
          this.estadistica.set(estadistica);
          this.proveedoresMensual.set(proveedoresMensual);
        },
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
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

  setFiltroProveedor(evento: Event): void {
    this.filtroProveedor.set(this.valorNumero(evento));
    this.cargar();
  }

  setFiltroTipo(evento: Event): void {
    this.filtroTipo.set(this.valorNumero(evento));
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

  limpiarFiltros(): void {
    this.filtroProveedor.set(null);
    this.filtroTipo.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.cargar();
  }

  private aBarras(grupos: GrupoEstadisticaInbound[]): BarraDato[] {
    const barras: BarraDato[] = grupos.slice(0, TOP).map((grupo, indice) => ({
      etiqueta: grupo.etiqueta,
      valor: grupo.count,
      color: PALETA[indice % PALETA.length],
      detalle: this.formatearUnidades(grupo),
    }));

    const resto = grupos.slice(TOP);
    if (resto.length > 0) {
      barras.push({
        etiqueta: 'Otros',
        valor: resto.reduce((total, grupo) => total + grupo.count, 0),
        color: COLOR_OTROS,
      });
    }

    return barras;
  }

  private formatearUnidades(grupo: GrupoEstadisticaInbound): string {
    return `${grupo.count} ${grupo.count === 1 ? 'incidencia' : 'incidencias'}`;
  }

  private valorNumero(evento: Event): number | null {
    const valor = (evento.target as HTMLSelectElement).value;
    return valor ? Number(valor) : null;
  }
}
