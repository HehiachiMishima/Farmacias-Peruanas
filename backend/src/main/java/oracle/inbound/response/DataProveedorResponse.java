package oracle.inbound.response;
import java.time.LocalDateTime;
public record DataProveedorResponse(
    Long id,
    String proveedor,
    LocalDateTime creadoEn,
    LocalDateTime actualizadoEn,
    Long creadoPor,
    Long actualizadoPor
) {}
