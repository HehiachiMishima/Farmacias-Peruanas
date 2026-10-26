/** Modelos de los endpoints de analítica (dashboards). */

export interface PuntoSerie {
  periodo: string;
  valor: number;
  secundario: number;
}

export interface CeldaMatriz {
  fila: string;
  columna: string;
  valor: number;
}

export type Granularidad = 'dia' | 'mes';

// ---------- Bitácora ----------
export interface AnaliticaBitacora {
  kpis: {
    total: number;
    resueltas: number;
    segundosSolucion: number;
    mttrSegundos: number;
    sistemasAfectados: number;
    centrosAfectados: number;
    totalPeriodoAnterior: number | null;
  };
  sistemas: GrupoBitacora[];
  centros: GrupoBitacora[];
  areas: GrupoBitacora[];
  tipos: { tipo: string; sistema: string; total: number; mttrSegundos: number }[];
  centroSistema: CeldaMatriz[];
  tendencia: PuntoSerie[];
  granularidad: Granularidad;
  porHora: PuntoSerie[];
}

export interface GrupoBitacora {
  etiqueta: string;
  total: number;
  mttrSegundos: number;
}

// ---------- Inbound ----------
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

// ---------- Administración ----------
export interface AnaliticaAdmin {
  kpis: {
    usuarios: number;
    usuariosActivos: number;
    usuariosSinRol: number;
    roles: number;
    rolesActivos: number;
    permisos: number;
    permisosActivos: number;
    overridesGrant: number;
    overridesDeny: number;
  };
  usuariosPorRol: Conteo[];
  coberturaPaneles: Conteo[];
  altasPorMes: PuntoSerie[];
}

export interface Conteo {
  etiqueta: string;
  valor: number;
}
