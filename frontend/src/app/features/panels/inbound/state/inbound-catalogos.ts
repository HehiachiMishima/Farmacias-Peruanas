import { Service, inject } from '@angular/core';
import { CacheCatalogos } from '../../../../core/utils/cache-catalogos';
import { InboundControlApi } from '../data/inbound-control.api';
import { Proveedor, TipoIncidenciaInbound } from '../data/inbound.model';

/**
 * Caché de catálogos Inbound (proveedores y tipos de incidencia) que reutilizan
 * los filtros y formularios del panel. El módulo Control llama a `invalidar()`
 * tras cada alta, edición o baja.
 */
@Service()
export class InboundCatalogos extends CacheCatalogos<{
  proveedores: Proveedor[];
  tipos: TipoIncidenciaInbound[];
}> {
  private readonly api = inject(InboundControlApi);

  readonly proveedores = this.lista('proveedores');
  readonly tipos = this.lista('tipos');

  protected fuentes() {
    return { proveedores: this.api.listarProveedores(), tipos: this.api.listarTipos() };
  }

  protected avisoError(clave: 'proveedores' | 'tipos'): [string, string] {
    const catalogo = clave === 'proveedores' ? 'los proveedores' : 'los tipos de incidencia';
    return ['Catálogos', `No se pudieron cargar ${catalogo}.`];
  }
}
