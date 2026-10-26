import { ArchivoSimulacion } from '../../../core/models';

/** Valores iniciales del backend al registrar un archivo. */
export const VELOCIDAD_INICIAL = 100;
export const OPERARIOS_INICIALES = 2;

export interface ColorRegistro {
  nombre: string;
  hex: string;
}

export const PALETA_REGISTROS: ColorRegistro[] = [
  { nombre: 'Azul', hex: '#1d63ed' },
  { nombre: 'Rojo', hex: '#e11d2e' },
  { nombre: 'Verde', hex: '#059669' },
  { nombre: 'Ámbar', hex: '#d97706' },
  { nombre: 'Violeta', hex: '#7c3aed' },
  { nombre: 'Cian', hex: '#0891b2' },
  { nombre: 'Rosa', hex: '#ec4899' },
  { nombre: 'Pizarra', hex: '#64748b' },
];

export const PALETA_CLUSTERS = [
  '#1d63ed',
  '#e11d2e',
  '#059669',
  '#d97706',
  '#7c3aed',
  '#0891b2',
  '#ec4899',
  '#64748b',
  '#84cc16',
  '#f97316',
];

export function colorCluster(indice: number): string {
  return PALETA_CLUSTERS[indice % PALETA_CLUSTERS.length];
}

/** Horas que tarda un chute: bultos / (velocidad × operarios); operarios mínimo 1. */
export function horas(bultos: number, velocidad: number, operarios: number): number {
  return velocidad > 0 ? bultos / (velocidad * Math.max(1, operarios)) : 0;
}

/** Orden natural sin mayúsculas: CHUTE 2 antes que CHUTE 10. */
export function compararNombre(a: string, b: string): number {
  return a.localeCompare(b, 'es', { numeric: true, sensitivity: 'base' });
}

/** Texto del registro en los selectores: «Nombre · etiqueta» (la etiqueta es opcional). */
export function etiquetaRegistro(registro: ArchivoSimulacion): string {
  return registro.etiqueta ? `${registro.nombreRegistro} · ${registro.etiqueta}` : registro.nombreRegistro;
}

/** Convierte horas decimales a un texto compacto: 3.5 → «3h 30m». */
export function formatearHoras(horas: number | null | undefined): string {
  if (!horas || horas <= 0) {
    return '0m';
  }
  const minutosTotales = Math.round(horas * 60);
  const h = Math.floor(minutosTotales / 60);
  const m = minutosTotales % 60;
  if (h === 0) {
    return `${m}m`;
  }
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

export function formatearNumero(valor: number | null | undefined, decimales = 0): string {
  return new Intl.NumberFormat('es-PE', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
  }).format(valor ?? 0);
}

export function colorRegistro(hex: string | null | undefined): string {
  return hex && /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : PALETA_REGISTROS[0].hex;
}
