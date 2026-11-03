import { PuntoSerie } from '../../../../../core/models';
import { escaparHtml } from '../../../../../core/utils/texto.util';
import {
  SERIES,
  compacto,
  entero,
  porcentaje,
  variacion,
} from '../../../../../shared/ui/charts/formato';
import { TarjetaKpi } from '../../../../../shared/ui/charts/kpi';
import { SerieLinea } from '../../../../../shared/ui/charts/line-chart';
import { ItemRanking } from '../../../../../shared/ui/charts/ranking-tiles';
import { ItemTreemap } from '../../../../../shared/ui/charts/treemap';
import { AnaliticaInbound, ProveedorAnalitica } from '../../data/analitica.model';

/**
 * Lógica pura del dashboard Inbound: convierte la respuesta de analítica en
 * los datos que pintan tarjetas, hallazgos y gráficos. Sin estado ni
 * inyección: el componente solo la envuelve en `computed`.
 */

type Kpis = AnaliticaInbound['kpis'];

export interface Hallazgo {
  icono: string;
  color: string;
  kicker: string;
  /** HTML con los nombres ya escapados. */
  html: string;
}

/** Fracción de las incidencias que concentran los 5 primeros proveedores. */
export function concentracionTop5(
  proveedores: readonly ProveedorAnalitica[],
  total: number,
): number {
  return total ? proveedores.slice(0, 5).reduce((t, p) => t + p.total, 0) / total : 0;
}

export function tarjetasInbound(k: Kpis | null, concentracion: number): TarjetaKpi[] {
  return [
    {
      etiqueta: 'Incidencias',
      valor: k ? entero(k.total) : '—',
      icono: 'incidencias',
      color: 'var(--brand)',
      detalle: 'Registradas en el periodo',
      variacion: k ? variacion(k.total, k.totalPeriodoAnterior) : null,
    },
    {
      etiqueta: 'Unidades afectadas',
      valor: k ? compacto(k.unidades) : '—',
      icono: 'paquete',
      color: 'var(--warning)',
      detalle: k && k.total ? `${entero(Math.round(k.unidades / k.total))} u por incidencia` : '',
      variacion: k ? variacion(k.unidades, k.unidadesPeriodoAnterior) : null,
    },
    {
      etiqueta: 'Proveedores con incidencias',
      valor: k ? entero(k.proveedores) : '—',
      icono: 'truck',
      color: 'var(--accent)',
      detalle: k ? `${entero(k.tipos)} tipos distintos` : '',
      variacion: null,
    },
    {
      etiqueta: 'Concentración top 5',
      valor: k ? porcentaje(concentracion) : '—',
      icono: 'tendencia',
      color: 'var(--violet)',
      detalle: 'De las incidencias en 5 proveedores',
      variacion: null,
    },
    {
      etiqueta: 'Órdenes de compra',
      valor: k ? entero(k.ordenesCompra) : '—',
      icono: 'layers',
      color: 'var(--cyan)',
      detalle: 'OC distintas con incidencia',
      variacion: null,
    },
    {
      etiqueta: 'SKUs afectados',
      valor: k ? entero(k.skus) : '—',
      icono: 'key',
      color: 'var(--success)',
      detalle: 'Productos distintos',
      variacion: null,
    },
  ];
}

/** Hallazgos en texto: proveedor crítico, incidencia más repetida y SKU reincidente. */
export function hallazgosInbound(d: AnaliticaInbound | null): Hallazgo[] {
  if (!d || d.kpis.total === 0) {
    return [];
  }
  const total = d.kpis.total;
  const hallazgos: Hallazgo[] = [];
  const lider = d.proveedores[0];
  if (lider) {
    hallazgos.push({
      icono: 'alertTriangle',
      color: 'var(--brand)',
      kicker: 'Proveedor crítico',
      html: `<strong>${escaparHtml(lider.proveedor)}</strong> concentra el <strong>${porcentaje(lider.total / total)}</strong> de las incidencias; su problema más frecuente es <strong>${escaparHtml(lider.tipoDominante)}</strong> (${porcentaje(lider.total ? lider.totalTipoDominante / lider.total : 0)} de sus casos).`,
    });
  }
  const tipo = d.tipos[0];
  if (tipo) {
    hallazgos.push({
      icono: 'incidencias',
      color: 'var(--warning)',
      kicker: 'Incidencia más repetida',
      html: `<strong>${escaparHtml(tipo.tipo)}</strong> suma <strong>${entero(tipo.total)}</strong> casos (${porcentaje(tipo.total / total)}) y aparece en <strong>${entero(tipo.proveedores)}</strong> proveedores.`,
    });
  }
  const sku = d.skus[0];
  if (sku && sku.total > 1) {
    hallazgos.push({
      icono: 'paquete',
      color: 'var(--accent)',
      kicker: 'SKU reincidente',
      html: `El SKU <strong>${escaparHtml(sku.sku)}</strong> tuvo <strong>${entero(sku.total)}</strong> incidencias (${entero(sku.unidades)} u) con <strong>${entero(sku.proveedores)}</strong> proveedor(es).`,
    });
  }
  return hallazgos;
}

/** Proveedores por volumen (treemap): área = incidencias. */
export function mapaProveedores(proveedores: readonly ProveedorAnalitica[]): ItemTreemap[] {
  return proveedores.map((p) => ({
    etiqueta: p.proveedor,
    valor: p.total,
    detalle: `${entero(p.unidades)} u · principal: ${p.tipoDominante}`,
  }));
}

/** Tipos más repetidos, con cuántos proveedores los presentan. */
export function rankingTipos(tipos: AnaliticaInbound['tipos']): ItemRanking[] {
  return tipos.map((t) => ({
    etiqueta: t.tipo,
    valor: t.total,
    chip: `${entero(t.proveedores)} prov.`,
    detalle: `${compacto(t.unidades)} unidades`,
  }));
}

/** Una serie de la tendencia: incidencias (`valor`) o unidades (`secundario`). */
export function serieTendencia(
  serie: readonly PuntoSerie[],
  nombre: string,
  color: string,
  campo: 'valor' | 'secundario',
): { etiquetas: string[]; series: SerieLinea[] } {
  return {
    etiquetas: serie.map((p) => p.periodo),
    series: [{ nombre, color, valores: serie.map((p) => p[campo]) }],
  };
}

/** Evolución mensual de los proveedores críticos (una línea por proveedor). */
export function evolucionMensual(top: AnaliticaInbound['mensualTop']): {
  etiquetas: string[];
  series: SerieLinea[];
} {
  const meses = [...new Set(top.flatMap((p) => p.meses.map((m) => m.mes)))].sort();
  const series: SerieLinea[] = top.map((p, i) => {
    const porMes = new Map(p.meses.map((m) => [m.mes, m.incidencias]));
    return {
      nombre: p.proveedor,
      color: SERIES[i % SERIES.length],
      valores: meses.map((m) => porMes.get(m) ?? 0),
    };
  });
  return { etiquetas: meses, series };
}

/** Tabla de los 15 proveedores con más incidencias, con sus textos ya formateados. */
export function rankingProveedores(proveedores: readonly ProveedorAnalitica[], total: number) {
  const tope = proveedores[0]?.total || 1;
  return proveedores.slice(0, 15).map((p) => ({
    ...p,
    pct: porcentaje(total ? p.total / total : 0),
    barra: (p.total / tope) * 100,
    dominantePct: porcentaje(p.total ? p.totalTipoDominante / p.total : 0),
    unidadesPorIncidencia: p.total ? entero(Math.round(p.unidades / p.total)) : '—',
    totalTexto: entero(p.total),
    unidadesTexto: entero(p.unidades),
  }));
}

/** SKUs reincidentes con sus totales formateados. */
export function skusConTexto(skus: AnaliticaInbound['skus']) {
  return skus.map((s) => ({
    ...s,
    totalTexto: entero(s.total),
    unidadesTexto: entero(s.unidades),
  }));
}
