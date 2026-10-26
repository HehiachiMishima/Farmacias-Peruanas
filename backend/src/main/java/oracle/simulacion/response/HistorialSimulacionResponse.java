package oracle.simulacion.response;

import java.time.LocalDateTime;

public record HistorialSimulacionResponse(
    Long id,
    String tipoAccion,
    Long rutaId,
    String ruta,
    String chuteOrigen,
    String chuteDestino,
    String chuteModificado,
    LocalDateTime creadoEn,
    Long creadoPor) {}
