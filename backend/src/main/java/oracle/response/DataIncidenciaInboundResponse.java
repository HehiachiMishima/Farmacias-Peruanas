package oracle.response;
import java.time.LocalDateTime;
public record DataIncidenciaInboundResponse(
    Long id,
    String incidenciaInbound,
    LocalDateTime creadoEn,
    LocalDateTime actualizadoEn,
    Long creadoPor,
    Long actualizadoPor
) {}
