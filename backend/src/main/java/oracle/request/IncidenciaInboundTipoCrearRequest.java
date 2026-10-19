package oracle.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record IncidenciaInboundTipoCrearRequest(
    @NotBlank @Size(max = 100) String incidenciaInbound
) {}
