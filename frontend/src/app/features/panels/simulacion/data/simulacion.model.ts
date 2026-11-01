import { Pagina } from '../../../../core/models';

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
  /** Total de COD contados en las rutas del archivo. */
  cod: number;
  creadoEn: string;
  actualizadoEn: string | null;
  creadoPor: number | null;
  actualizadoPor: number | null;
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
  /** Chute que tenía la ruta al cargar el Excel (no lo cambia el balanceo). */
  chuteOriginalId: number;
  chuteOriginal: string;
  clusterId: number;
  cluster: string;
  codigoRuta: string;
  /** Tipo de operación de la ruta: B2C o B2B. */
  tipo: 'B2C' | 'B2B' | string;
  totalBultos: number;
  totalUnidades: number;
  totalLineas: number;
  conteoCod: number;
}

/** Rutas y totales de un cluster asignados a un chute. */
export interface CargaSimulacion {
  clusterId: number;
  chuteId: number;
  rutas: number;
  bultos: number;
  unidades: number;
  lineas: number;
  cod: number;
}

/** Parámetros y carga agregada; las rutas se piden aparte, paginadas. */
export interface ConfiguracionSimulacion {
  archivo: ArchivoSimulacion;
  velocidadBase: number;
  /** Tope global de operarios disponibles para todo el registro. */
  operariosMaximos: number;
  clusters: ClusterParametroSimulacion[];
  chutes: ChuteParametroSimulacion[];
  carga: CargaSimulacion[];
  /** Chutes que hoy mezclan CL1 y CL2: el balanceo los separa. */
  mezclas: ChuteMezcladoSimulacion[];
}

export interface RutasSimulacionFiltros {
  pagina: number;
  tamano: number;
  texto: string;
  clusterIds: number[];
  chuteIds: number[];
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
  operariosMaximos: number;
  clusters: ClusterLimiteRequest[];
  chutes: ChuteOperariosRequest[];
}

export interface HistorialSimulacion {
  id: number;
  tipoAccion: 'MOVER_RUTA' | 'AÑADIR_OPERARIO' | 'MOVER_OPERARIO' | string;
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
  /** Operarios que pasaron de un chute con holgura a un cuello de botella. */
  operariosMovidos: number;
  mensaje: string;
}

export interface SimulacionKpis {
  /** Tiempo del cluster más lento (h) antes y después de los ajustes. */
  tiempoOriginal: number;
  tiempoBalanceado: number;
  tiempoAhorrado: number;
  rutasMovidas: number;
  operariosAgregados: number;
  operariosMovidos: number;
  bultos: number;
  unidades: number;
  lineas: number;
  cod: number;
}

export interface ClusterComparativoSimulacion {
  id: number;
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
  id: number;
  chute: string;
  operariosOriginal: number;
  operariosBalanceado: number;
  /** Cantidad de rutas asignadas al chute antes y después del balanceo. */
  rutasOriginal: number;
  rutasBalanceado: number;
  /** Rutas que el balanceo sacó del chute y las que trajo de otros. */
  rutasSalieron: number;
  rutasLlegaron: number;
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
  /** Clusters que el chute atiende antes o después del balanceo. */
  clusters: string[];
  /** Clusters del chute al cargar el Excel y tras el balanceo, por separado. */
  clustersOriginal: string[];
  clustersBalanceado: string[];
  /** El chute junta CL1 y CL2 (no permitido) al cargar el Excel / tras el balanceo. */
  mezclaOriginal: boolean;
  mezclaBalanceada: boolean;
}

/** Rutas que pasaron de un chute a otro (por chute original y actual). */
export interface MovimientoChuteSimulacion {
  origenId: number;
  destinoId: number;
  rutas: number;
}

export interface DashboardSimulacion {
  kpis: SimulacionKpis;
  clusters: ClusterComparativoSimulacion[];
  chutes: ChuteComparativoSimulacion[];
  /** Chutes que hoy mezclan CL1 y CL2: el balanceo los separa. */
  mezclas: ChuteMezcladoSimulacion[];
  /** Movimientos de rutas entre chutes: flujo de cualquier grupo de chutes. */
  movimientos: MovimientoChuteSimulacion[];
}

export interface ChuteMezcladoSimulacion {
  id: number;
  chute: string;
  clusters: string[];
}

/** Totales de un lado del chute (antes o después) con los filtros del modal. */
export interface ResumenRutasSimulacion {
  rutas: number;
  rutasB2c: number;
  rutasB2b: number;
  /** Rutas que cambiaron de chute: salieron (antes) o llegaron (después). */
  cambios: number;
  bultos: number;
  unidades: number;
  lineas: number;
  cod: number;
}

export interface RutasDashboardFiltros {
  pagina: number;
  tamano: number;
  texto: string;
  tipo: '' | 'B2C' | 'B2B';
  /** Chutes elegidos (1 a 50). */
  chuteIds: number[];
  /** ANTES: rutas que los chutes tenían al cargar el Excel; DESPUES: las que tienen hoy. */
  lado: LadoChute;
  clusterId: number | null;
  /** Solo las rutas que cambiaron de chute. */
  soloCambios: boolean;
}

export type LadoChute = 'ANTES' | 'DESPUES';

/** Detalle ejecutivo del modal: totales del conjunto filtrado y la página pedida. */
export interface RutasDashboardSimulacion {
  resumen: ResumenRutasSimulacion;
  rutas: Pagina<RutaSimulacion>;
}
