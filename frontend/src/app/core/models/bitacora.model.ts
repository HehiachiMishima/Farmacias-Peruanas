export interface Centro {
  id: number;
  centro: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface Sistema {
  id: number;
  sistema: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface Area {
  id: number;
  area: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface Responsable {
  id: number;
  idDataArea: number;
  responsable: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface TipoIncidencia {
  id: number;
  idDataSistema: number;
  incidencia: string;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface CentroCrearRequest {
  centro: string;
}

export interface CentroActualizarRequest {
  centro?: string;
}

export interface SistemaCrearRequest {
  sistema: string;
}

export interface SistemaActualizarRequest {
  sistema?: string;
}

export interface AreaCrearRequest {
  area: string;
}

export interface AreaActualizarRequest {
  area?: string;
}

export interface ResponsableCrearRequest {
  responsable: string;
}

export interface ResponsableActualizarRequest {
  responsable?: string;
}

export interface TipoIncidenciaCrearRequest {
  incidencia: string;
}

export interface TipoIncidenciaActualizarRequest {
  incidencia?: string;
}
