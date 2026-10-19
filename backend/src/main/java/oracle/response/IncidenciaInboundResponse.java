package oracle.response;
import java.time.LocalDateTime;
public record IncidenciaInboundResponse(
    Long id,
    String ordenCompra,
    Long idDataProveedor,
    String proveedor,
    Long idDataIncidenciaInbound,
    String tipoIncidencia,
    String skuProducto,
    Integer nUnidades,
    LocalDateTime creadoEn,
    LocalDateTime actualizadoEn,
    Long creadoPor,
    Long actualizadoPor
) {}
