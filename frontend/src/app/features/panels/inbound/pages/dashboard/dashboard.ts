import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { EstadisticaInbound, GrupoEstadisticaInbound, IncidenciaInboundFiltros } from '../../../../../core/models';
import { InboundControlApi } from '../../../../../core/services/inbound-control.api';
import { InboundIncidenciasApi } from '../../../../../core/services/inbound-incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';

const TOP = 5;
const PALETA = ['var(--brand)', 'var(--success)', 'var(--warning)', 'var(--danger)', '#a855f7'];
const COLOR_OTROS = 'var(--color-graphite)';

@Component({
  selector: 'app-inbound-dashboard',
  imports: [DecimalPipe, BarChart, EmptyState, Icon, PanelFiltros],
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

  readonly barrasProveedores = computed(() => this.aBarras(this.estadistica()?.proveedores ?? []));
  readonly barrasTipos = computed(() => this.aBarras(this.estadistica()?.tipos ?? []));

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
    this.incidenciasApi
      .estadisticas(this.filtros())
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (estadistica) => this.estadistica.set(estadistica),
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
