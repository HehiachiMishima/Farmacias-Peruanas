export interface ArchivoSimulacion {
  id: number;
  nombreRegistro: string;
  nombreArchivo: string;
  etiqueta: string | null;
  colorHex: string | null;
  estado: 'PROYECCION' | 'BALANCEADO' | string;
  rutas: number;
  chutes: number;
  bultos: number;
  unidades: number;
  lineas: number;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
}

export interface PaginaSimulacion<T> {
  contenido: T[];
  total: number;
  pagina: number;
  tamano: number;
  totalPaginas: number;
}

export interface ResumenSimulacion {
  registros: number;
  rutas: number;
  chutes: number;
  etiquetas: number;
  bultos: number;
  unidades: number;
  lineas: number;
}

export interface ClusterParametroSimulacion {
  id: number;
  nombre: string;
  limiteHoras: number;
}

export interface ChuteParametroSimulacion {
  id: number;
  nombre: string;
  operarios: number;
}

export interface RutaSimulacion {
  id: number;
  chuteId: number;
  chute: string;
  clusterId: number;
  cluster: string;
  codigoRuta: string;
  totalBultos: number;
  totalUnidades: number;
  totalLineas: number;
  conteoCod: number;
}

export interface ConfiguracionSimulacion {
  archivo: ArchivoSimulacion;
  velocidadBase: number;
  clusters: ClusterParametroSimulacion[];
  chutes: ChuteParametroSimulacion[];
  rutas: RutaSimulacion[];
}

export interface RegistroSimulacionActualizarRequest {
  nombreRegistro: string;
  etiqueta: string;
  colorHex: string;
}

export interface ClusterLimiteRequest {
  clusterId: number;
  limiteHoras: number;
}

export interface ChuteOperariosRequest {
  chuteId: number;
  operarios: number;
}

export interface ConfiguracionSimulacionRequest {
  velocidadBase: number;
  clusters: ClusterLimiteRequest[];
  chutes: ChuteOperariosRequest[];
}

export interface HistorialSimulacion {
  id: number;
  tipoAccion: 'MOVER_RUTA' | 'AÑADIR_OPERARIO' | string;
  rutaId: number | null;
  ruta: string | null;
  chuteOrigen: string | null;
  chuteDestino: string | null;
  chuteModificado: string | null;
  creadoEn: string;
  creadoPor: number | null;
}

export interface BalanceoSimulacion {
  rutasMovidas: number;
  operariosAgregados: number;
  mensaje: string;
}

export interface SimulacionKpis {
  tiempoAhorrado: number;
  rutasMovidas: number;
  operariosAgregados: number;
  bultos: number;
  unidades: number;
  lineas: number;
  cod: number;
}

export interface ClusterComparativoSimulacion {
  cluster: string;
  tiempoOriginal: number;
  tiempoBalanceado: number;
  limiteHoras: number;
  excede: boolean;
  bultosOriginal: number;
  bultosBalanceado: number;
  lineasOriginal: number;
  lineasBalanceado: number;
  codOriginal: number;
  codBalanceado: number;
}

export interface ChuteComparativoSimulacion {
  chute: string;
  operariosOriginal: number;
  operariosBalanceado: number;
  tiempoOriginal: number;
  tiempoBalanceado: number;
  estado: 'MEJORO' | 'AUMENTO' | 'IGUAL' | string;
  bultosOriginal: number;
  bultosBalanceado: number;
  unidadesOriginal: number;
  unidadesBalanceado: number;
  lineasOriginal: number;
  lineasBalanceado: number;
  codOriginal: number;
  codBalanceado: number;
}

export interface DashboardSimulacion {
  kpis: SimulacionKpis;
  clusters: ClusterComparativoSimulacion[];
  chutes: ChuteComparativoSimulacion[];
}
