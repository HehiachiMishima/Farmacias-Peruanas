package oracle.inbound.response;
import java.time.Instant;
public record DataProveedorResponse(
    Long id,
    String proveedor,
    Instant creadoEn,
    Instant actualizadoEn,
    Long creadoPor,
    Long actualizadoPor
) {}
