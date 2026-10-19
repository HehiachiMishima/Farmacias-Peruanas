package oracle.request;
import jakarta.validation.constraints.Size;
public record ProveedorActualizarRequest(
    @Size(max = 100) String proveedor
) {}
