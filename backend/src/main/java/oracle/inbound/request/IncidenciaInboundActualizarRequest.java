package oracle.inbound.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
public record IncidenciaInboundActualizarRequest(
    @NotBlank @Size(max = 30) String ordenCompra,
    @NotNull Long idDataProveedor,
    @NotNull Long idDataIncidenciaInbound,
    @NotBlank @Size(max = 30) String skuProducto,
    @NotNull @Positive Integer nUnidades
) {}
