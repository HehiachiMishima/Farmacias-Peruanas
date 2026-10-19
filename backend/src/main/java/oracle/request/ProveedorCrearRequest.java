package oracle.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ProveedorCrearRequest(
    @NotBlank @Size(max = 100) String proveedor
) {}
