package oracle.simulacion.service;

import java.math.BigDecimal;
import oracle.simulacion.model.ArchivoExcel;
import oracle.simulacion.repository.ResumenRutasArchivo;
import oracle.simulacion.response.ArchivoSimulacionResponse;

/** Mapeo de entidades del panel a respuestas, compartido por los servicios. */
final class SimulacionRespuestas {

  private SimulacionRespuestas() {}

  /** Registro con sus totales de rutas; sin resumen (archivo sin rutas) todo va en cero. */
  static ArchivoSimulacionResponse archivo(ArchivoExcel archivo, ResumenRutasArchivo resumen) {
    return new ArchivoSimulacionResponse(
        archivo.getId(),
        archivo.getNombreRegistro(),
        archivo.getNombreArchivo(),
        archivo.getEtiqueta(),
        archivo.getColorHex(),
        archivo.getEstado(),
        resumen == null || resumen.getRutas() == null ? 0 : resumen.getRutas(),
        resumen == null || resumen.getChutes() == null ? 0 : resumen.getChutes(),
        cero(resumen == null ? null : resumen.getBultos()),
        cero(resumen == null ? null : resumen.getUnidades()),
        cero(resumen == null ? null : resumen.getLineas()),
        archivo.getCreadoEn(),
        archivo.getActualizadoEn(),
        archivo.getCreadoPor(),
        archivo.getActualizadoPor());
  }

  private static BigDecimal cero(BigDecimal valor) {
    return valor == null ? BigDecimal.ZERO : valor;
  }
}
