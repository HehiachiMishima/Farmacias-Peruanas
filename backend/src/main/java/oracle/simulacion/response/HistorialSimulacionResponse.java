package oracle.simulacion.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un cambio del historial con los datos para leerlo sin consultar nada más:
 * la ruta (cluster, tipo, bultos), los chutes y cuántos operarios quedaron.
 */
public record HistorialSimulacionResponse(
    Long id,
    String tipoAccion,
    /** BALANCEO, SEPARACION (CL1 y CL2) o MANUAL. */
    String motivo,
    Long rutaId,
    String ruta,
    String cluster,
    String tipo,
    BigDecimal bultos,
    String chuteOrigen,
    String chuteDestino,
    String chuteModificado,
    /** Operarios después de la acción en el chute de origen y en el de destino o modificado. */
    Integer operariosOrigen,
    Integer operariosDestino,
    Instant creadoEn,
    Long creadoPor) {}
