export interface Proveedor {
  id: number;
  proveedor: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface TipoIncidenciaInbound {
  id: number;
  incidenciaInbound: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface IncidenciaInbound {
  id: number;
  ordenCompra: string;
  idDataProveedor: number;
  proveedor: string | null;
  idDataIncidenciaInbound: number;
  tipoIncidencia: string | null;
  skuProducto: string;
  nUnidades: number;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface ProveedorCrearRequest {
  proveedor: string;
}

export interface ProveedorActualizarRequest {
  proveedor?: string;
}

export interface TipoIncidenciaInboundCrearRequest {
  incidenciaInbound: string;
}

export interface TipoIncidenciaInboundActualizarRequest {
  incidenciaInbound?: string;
}

export interface IncidenciaInboundCrearRequest {
  ordenCompra: string;
  idDataProveedor: number;
  idDataIncidenciaInbound: number;
  skuProducto: string;
  nUnidades: number;
}

export type IncidenciaInboundActualizarRequest = IncidenciaInboundCrearRequest;

export interface IncidenciaInboundFiltros {
  proveedor?: number | null;
  tipo?: number | null;
  desde?: string;
  hasta?: string;
  texto?: string;
}

export interface GrupoEstadisticaInbound {
  etiqueta: string;
  count: number;
}

export interface EstadisticaInbound {
  total: number;
  totalUnidades: number;
  proveedores: GrupoEstadisticaInbound[];
  tipos: GrupoEstadisticaInbound[];
}

export interface PaginaInbound<T> {
  contenido: T[];
  total: number;
  pagina: number;
  tamano: number;
  totalPaginas: number;
}
