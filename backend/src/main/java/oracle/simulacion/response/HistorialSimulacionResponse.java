package oracle.simulacion.response;

import java.time.Instant;

public record HistorialSimulacionResponse(
    Long id,
    String tipoAccion,
    Long rutaId,
    String ruta,
    String chuteOrigen,
    String chuteDestino,
    String chuteModificado,
    Instant creadoEn,
    Long creadoPor) {}
