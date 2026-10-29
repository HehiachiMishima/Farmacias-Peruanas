import { Observable } from 'rxjs';
import { Pagina } from '../../../../../../core/models';
import { InboundControlApi } from '../../../data/inbound-control.api';
import { Proveedor, TipoIncidenciaInbound } from '../../../data/inbound.model';

/**
 * Catálogo de un solo campo («nombre») que gestiona la página genérica de
 * Control Inbound: textos de la pantalla y peticiones al backend.
 */
export interface DefCatalogo<T extends CatalogoItem = CatalogoItem> {
  /** Sustantivo para los textos: «proveedor» / «proveedores» / «Proveedor». */
  singular: string;
  plural: string;
  titulo: string;
  icono: string;
  placeholder: string;
  nombre: (item: T) => string;
  listar: (
    api: InboundControlApi,
    pagina: number,
    tamano: number,
    texto: string,
  ) => Observable<Pagina<T>>;
  crear: (api: InboundControlApi, nombre: string) => Observable<T>;
  actualizar: (api: InboundControlApi, id: number, nombre: string) => Observable<T>;
  eliminar: (api: InboundControlApi, id: number) => Observable<void>;
}

/** Campos comunes de los elementos de catálogo (columnas de auditoría). */
export interface CatalogoItem {
  id: number;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export const CATALOGO_PROVEEDORES: DefCatalogo<Proveedor> = {
  singular: 'proveedor',
  plural: 'proveedores',
  titulo: 'Proveedor',
  icono: 'responsable',
  placeholder: 'Proveedor',
  nombre: (proveedor) => proveedor.proveedor,
  listar: (api, pagina, tamano, texto) => api.listarProveedoresPaginado(pagina, tamano, texto),
  crear: (api, nombre) => api.crearProveedor({ proveedor: nombre }),
  actualizar: (api, id, nombre) => api.actualizarProveedor(id, { proveedor: nombre }),
  eliminar: (api, id) => api.eliminarProveedor(id),
};

export const CATALOGO_TIPOS: DefCatalogo<TipoIncidenciaInbound> = {
  singular: 'tipo',
  plural: 'tipos',
  titulo: 'Tipo',
  icono: 'incidencias',
  placeholder: 'Tipo de incidencia',
  nombre: (tipo) => tipo.incidenciaInbound,
  listar: (api, pagina, tamano, texto) => api.listarTiposPaginado(pagina, tamano, texto),
  crear: (api, nombre) => api.crearTipo({ incidenciaInbound: nombre }),
  actualizar: (api, id, nombre) => api.actualizarTipo(id, { incidenciaInbound: nombre }),
  eliminar: (api, id) => api.eliminarTipo(id),
};
