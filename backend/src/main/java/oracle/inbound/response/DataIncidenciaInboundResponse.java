package oracle.inbound.response;
import java.time.Instant;
public record DataIncidenciaInboundResponse(
    Long id,
    String incidenciaInbound,
    Instant creadoEn,
    Instant actualizadoEn,
    Long creadoPor,
    Long actualizadoPor
) {}
