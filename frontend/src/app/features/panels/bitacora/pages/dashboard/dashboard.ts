import { Component, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import {
  Area,
  Centro,
  EstadisticaIncidencia,
  GrupoEstadistica,
  IncidenciaFiltros,
  Responsable,
  Sistema,
} from '../../../../../core/models';
import { AreasApi } from '../../../../../core/services/areas.api';
import { CentrosApi } from '../../../../../core/services/centros.api';
import { IncidenciasApi } from '../../../../../core/services/incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SistemasApi } from '../../../../../core/services/sistemas.api';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';

const TOP = 5;
const PALETA = ['var(--brand)', 'var(--success)', 'var(--warning)', 'var(--danger)', '#a855f7'];
const COLOR_OTROS = 'var(--color-graphite)';

@Component({
  selector: 'app-bitacora-dashboard',
  imports: [BarChart, EmptyState, Icon, PanelFiltros],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardBitacora {
  private readonly incidenciasApi = inject(IncidenciasApi);
  private readonly centrosApi = inject(CentrosApi);
  private readonly sistemasApi = inject(SistemasApi);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly estadistica = signal<EstadisticaIncidencia | null>(null);

  readonly centros = signal<Centro[]>([]);
  readonly sistemas = signal<Sistema[]>([]);
  readonly areas = signal<Area[]>([]);

  readonly filtroCentro = signal<number | null>(null);
  readonly filtroSistema = signal<number | null>(null);
  readonly filtroArea = signal<number | null>(null);
  readonly filtroResponsable = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly responsablesFiltro = signal<Responsable[]>([]);
  readonly cargandoResponsablesFiltro = signal(false);
  readonly filtrosAbierto = signal(false);

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

  readonly totalIncidencias = computed(() => this.estadistica()?.total ?? 0);

  readonly totalTiempo = computed(() => this.formatear(this.estadistica()?.segundosTotales ?? 0));

  readonly barrasSistemas = computed(() => this.aBarras(this.estadistica()?.sistemas ?? []));
  readonly barrasTipos = computed(() => this.aBarras(this.estadistica()?.tipos ?? []));
  readonly barrasCentros = computed(() => this.aBarras(this.estadistica()?.centros ?? []));
  readonly barrasAreas = computed(() => this.aBarras(this.estadistica()?.areas ?? []));

  constructor() {
    this.cargarCatalogos();
    this.cargar();
  }

  cargarCatalogos(): void {
    forkJoin({
      centros: this.centrosApi.listar(),
      sistemas: this.sistemasApi.listar(),
      areas: this.areasApi.listar(),
    }).subscribe({
      next: ({ centros, sistemas, areas }) => {
        this.centros.set(centros);
        this.sistemas.set(sistemas);
        this.areas.set(areas);
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudieron cargar los catálogos', mensajeDeError(err)),
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.incidenciasApi
      .estadisticas(this.filtros())
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (estadistica) => this.estadistica.set(estadistica),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
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

  private aBarras(grupos: GrupoEstadistica[]): BarraDato[] {
    const barras: BarraDato[] = grupos.slice(0, TOP).map((grupo, indice) => ({
      etiqueta: grupo.etiqueta,
      valor: grupo.count,
      color: PALETA[indice % PALETA.length],
      detalle: this.formatear(grupo.segundos),
    }));

    const resto = grupos.slice(TOP);
    if (resto.length > 0) {
      barras.push({
        etiqueta: 'Otros',
        valor: resto.reduce((total, grupo) => total + grupo.count, 0),
        color: COLOR_OTROS,
        detalle: this.formatear(resto.reduce((total, grupo) => total + grupo.segundos, 0)),
      });
    }

    return barras;
  }

  private formatear(segundos: number): string {
    const total = Math.floor(segundos);
    const horas = Math.floor(total / 3600);
    const minutos = Math.floor((total % 3600) / 60);
    return `${`${horas}`.padStart(2, '0')}:${`${minutos}`.padStart(2, '0')}`;
  }

  private valorNumero(evento: Event): number | null {
    const valor = (evento.target as HTMLSelectElement).value;
    return valor ? Number(valor) : null;
  }
}
