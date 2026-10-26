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

/** Convierte horas decimales a un texto compacto: 3.5 → «3h 30m». */
export function formatearHoras(horas: number | null | undefined): string {
  if (!horas || horas <= 0) {
    return '0m';
  }
  const minutosTotales = Math.round(horas * 60);
  const h = Math.floor(minutosTotales / 60);
  const m = minutosTotales % 60;
  return `${h > 0 ? `${h}h ` : ''}${m}m`.trim();
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
