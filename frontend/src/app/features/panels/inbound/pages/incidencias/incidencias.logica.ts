import { fechaHoraNegocio } from '../../../../../core/utils/fecha.util';
import { IncidenciaInbound } from '../../data/inbound.model';

/** Lógica pura de la página de incidencias Inbound (exportación). */

export const CABECERAS_EXPORT = [
  'Orden de compra',
  'Proveedor',
  'Tipo',
  'SKU',
  'Unidades',
  'Creado',
  'Creado por',
  'Actualizado',
  'Actualizado por',
];

/** Fila del CSV de una incidencia; `actor` resuelve creado_por / actualizado_por. */
export function filaExportacion(
  incidencia: IncidenciaInbound,
  locale: string,
  actor: (id: number | null) => string,
): (string | number)[] {
  return [
    incidencia.ordenCompra,
    incidencia.proveedor ?? '—',
    incidencia.tipoIncidencia ?? '—',
    incidencia.skuProducto,
    incidencia.nUnidades,
    fechaHoraNegocio(incidencia.creadoEn, locale),
    actor(incidencia.creadoPor),
    incidencia.actualizadoEn ? fechaHoraNegocio(incidencia.actualizadoEn, locale) : '—',
    actor(incidencia.actualizadoPor),
  ];
}
