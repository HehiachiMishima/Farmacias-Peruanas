package oracle.inbound.response;
import java.util.List;
public record EstadisticaInboundResponse(
    long total,
    long totalUnidades,
    List<GrupoEstadisticaInbound> proveedores,
    List<GrupoEstadisticaInbound> tipos
) {}
