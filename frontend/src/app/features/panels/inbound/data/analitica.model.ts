import { CeldaMatriz, Granularidad, PuntoSerie } from '../../../../core/models';

/** Analítica del dashboard Inbound. */
export interface AnaliticaInbound {
  kpis: {
    total: number;
    unidades: number;
    proveedores: number;
    ordenesCompra: number;
    skus: number;
    tipos: number;
    totalPeriodoAnterior: number | null;
    unidadesPeriodoAnterior: number | null;
  };
  proveedores: ProveedorAnalitica[];
  tipos: { tipo: string; total: number; unidades: number; proveedores: number }[];
  proveedorTipo: CeldaMatriz[];
  skus: { sku: string; total: number; unidades: number; proveedores: number }[];
  tendencia: PuntoSerie[];
  granularidad: Granularidad;
  mensualTop: {
    idProveedor: number;
    proveedor: string;
    total: number;
    meses: { mes: string; incidencias: number }[];
  }[];
}

export interface ProveedorAnalitica {
  id: number;
  proveedor: string;
  total: number;
  unidades: number;
  ordenesCompra: number;
  skus: number;
  tipoDominante: string;
  totalTipoDominante: number;
}
