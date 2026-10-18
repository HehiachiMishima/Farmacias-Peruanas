import { Component, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { Area, Centro, Incidencia, Responsable, Sistema } from '../../../../../core/models';
import { AreasApi } from '../../../../../core/services/areas.api';
import { CentrosApi } from '../../../../../core/services/centros.api';
import { IncidenciasApi } from '../../../../../core/services/incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SistemasApi } from '../../../../../core/services/sistemas.api';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';

interface Grupo {
  etiqueta: string;
  count: number;
  segundos: number;
}

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
  readonly incidenciasLista = signal<Incidencia[]>([]);

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

  readonly filtradas = computed(() => {
    const centro = this.filtroCentro();
    const sistema = this.filtroSistema();
    const area = this.filtroArea();
    const responsable = this.filtroResponsable();
    const desde = this.filtroDesde();
    const hasta = this.filtroHasta();

    return this.incidenciasLista().filter((incidencia) => {
      if (centro !== null && incidencia.idDataCentro !== centro) {
        return false;
      }
      if (sistema !== null && incidencia.idDataSistema !== sistema) {
        return false;
      }
      if (area !== null && incidencia.idDataArea !== area) {
        return false;
      }
      if (responsable !== null && incidencia.idDataResponsable !== responsable) {
        return false;
      }
      const fecha = incidencia.creadoEn.slice(0, 10);
      if (desde && fecha < desde) {
        return false;
      }
      if (hasta && fecha > hasta) {
        return false;
      }
      return true;
    });
  });

  readonly totalIncidencias = computed(() => this.filtradas().length);

  readonly segundosTotales = computed(() =>
    this.filtradas().reduce((total, incidencia) => total + this.aSegundos(incidencia.tiempoSolucion), 0),
  );

  readonly totalTiempo = computed(() => this.formatear(this.segundosTotales()));

  readonly barrasSistemas = computed(() => this.aBarras(this.agrupar((i) => i.sistema)));
  readonly barrasTipos = computed(() => this.aBarras(this.agrupar((i) => i.tipoIncidencia)));
  readonly barrasCentros = computed(() => this.aBarras(this.agrupar((i) => i.centro)));
  readonly barrasAreas = computed(() => this.aBarras(this.agrupar((i) => i.area)));

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
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.incidenciasLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  alternarFiltros(): void {
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  setFiltroCentro(evento: Event): void {
    this.filtroCentro.set(this.valorNumero(evento));
  }

  setFiltroSistema(evento: Event): void {
    this.filtroSistema.set(this.valorNumero(evento));
  }

  setFiltroArea(evento: Event): void {
    const areaId = this.valorNumero(evento);
    this.filtroArea.set(areaId);
    this.filtroResponsable.set(null);
    this.responsablesFiltro.set([]);
    if (areaId !== null) {
      this.cargarResponsablesFiltro(areaId);
    }
  }

  setFiltroResponsable(evento: Event): void {
    this.filtroResponsable.set(this.valorNumero(evento));
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set((evento.target as HTMLInputElement).value);
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set((evento.target as HTMLInputElement).value);
  }

  limpiarFiltros(): void {
    this.filtroCentro.set(null);
    this.filtroSistema.set(null);
    this.filtroArea.set(null);
    this.filtroResponsable.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.responsablesFiltro.set([]);
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

  private agrupar(selector: (incidencia: Incidencia) => string | null): Grupo[] {
    const mapa = new Map<string, Grupo>();
    for (const incidencia of this.filtradas()) {
      const etiqueta = selector(incidencia) ?? '—';
      const grupo = mapa.get(etiqueta) ?? { etiqueta, count: 0, segundos: 0 };
      grupo.count += 1;
      grupo.segundos += this.aSegundos(incidencia.tiempoSolucion);
      mapa.set(etiqueta, grupo);
    }
    return [...mapa.values()].sort((a, b) => b.count - a.count);
  }

  private aBarras(grupos: Grupo[]): BarraDato[] {
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

  private aSegundos(valor: string | null): number {
    if (!valor) {
      return 0;
    }
    const [horas, minutos, segundos] = valor.split(':').map((parte) => Number(parte));
    return (horas || 0) * 3600 + (minutos || 0) * 60 + (segundos || 0);
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
