package oracle.inbound.request;
import jakarta.validation.constraints.Size;
public record IncidenciaInboundTipoActualizarRequest(
    @Size(max = 100) String incidenciaInbound
) {}
