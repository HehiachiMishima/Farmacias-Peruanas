import { Service, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { mensajeDeError } from '../errors/error.util';
import { Area, Centro, Sistema } from '../models';
import { AreasApi } from './areas.api';
import { CentrosApi } from './centros.api';
import { Notificaciones } from './notificaciones';
import { SistemasApi } from './sistemas.api';

/**
 * Caché de catálogos de Bitácora (centros, sistemas y áreas) compartida por
 * Incidencias y Dashboard: se piden una sola vez por sesión de la app en vez
 * de en cada visita a la página. El módulo Control llama a `invalidar()` tras
 * cada alta, edición o baja para que el resto de la app vea el cambio.
 */
@Service()
export class BitacoraCatalogos {
  private readonly centrosApi = inject(CentrosApi);
  private readonly sistemasApi = inject(SistemasApi);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);

  private readonly centrosSig = signal<Centro[]>([]);
  private readonly sistemasSig = signal<Sistema[]>([]);
  private readonly areasSig = signal<Area[]>([]);
  private estado: 'vacio' | 'cargando' | 'listo' = 'vacio';

  readonly centros = this.centrosSig.asReadonly();
  readonly sistemas = this.sistemasSig.asReadonly();
  readonly areas = this.areasSig.asReadonly();

  cargar(): void {
    if (this.estado !== 'vacio') {
      return;
    }
    this.estado = 'cargando';
    forkJoin({
      centros: this.centrosApi.listar(),
      sistemas: this.sistemasApi.listar(),
      areas: this.areasApi.listar(),
    }).subscribe({
      next: ({ centros, sistemas, areas }) => {
        this.centrosSig.set(centros);
        this.sistemasSig.set(sistemas);
        this.areasSig.set(areas);
        this.estado = 'listo';
      },
      error: (err: unknown) => {
        this.estado = 'vacio';
        this.notificaciones.error('No se pudieron cargar los catálogos', mensajeDeError(err));
      },
    });
  }

  /** Marca los catálogos como obsoletos; la próxima `cargar()` los vuelve a pedir. */
  invalidar(): void {
    if (this.estado === 'cargando') {
      return;
    }
    this.estado = 'vacio';
  }
}
