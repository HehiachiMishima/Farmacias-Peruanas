import { Injectable, inject, signal } from '@angular/core';
import { Proveedor, TipoIncidenciaInbound } from '../models';
import { InboundControlApi } from './inbound-control.api';
import { Notificaciones } from './notificaciones';

/**
 * Caché de catálogos Inbound (proveedores y tipos de incidencia).
 * Se cargan una sola vez por sesión de la app y se reutilizan en todos los
 * módulos (filtros, formularios). `refrescar()` fuerza la recarga después de
 * un CRUD en el módulo Control.
 */
@Injectable({ providedIn: 'root' })
export class InboundCatalogos {
  private readonly api = inject(InboundControlApi);
  private readonly notificaciones = inject(Notificaciones);

  private readonly proveedoresSig = signal<Proveedor[]>([]);
  private readonly tiposSig = signal<TipoIncidenciaInbound[]>([]);
  private cargado = false;

  readonly proveedores = this.proveedoresSig.asReadonly();
  readonly tipos = this.tiposSig.asReadonly();

  cargar(): void {
    if (this.cargado) return;
    this.cargado = true;
    this.api.listarProveedores().subscribe({
      next: (lista) => this.proveedoresSig.set(lista),
      error: () => {
        this.cargado = false;
        this.notificaciones.error('Catálogos', 'No se pudieron cargar los proveedores.');
      },
    });
    this.api.listarTipos().subscribe({
      next: (lista) => this.tiposSig.set(lista),
      error: () => {
        this.cargado = false;
        this.notificaciones.error('Catálogos', 'No se pudieron cargar los tipos de incidencia.');
      },
    });
  }

  refrescar(): void {
    this.cargado = false;
    this.cargar();
  }
}
