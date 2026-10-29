package oracle.inbound.response;
import java.time.LocalDateTime;
import oracle.inbound.model.IncidenciaInbound;
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
) {

  /** Respuesta de una incidencia con los nombres de catalogo ya resueltos. */
  public static IncidenciaInboundResponse of(IncidenciaInbound i, String proveedor, String tipoIncidencia) {
    return new IncidenciaInboundResponse(
        i.getId(),
        i.getOrdenCompra(),
        i.getIdDataProveedor(),
        proveedor,
        i.getIdDataIncidenciaInbound(),
        tipoIncidencia,
        i.getSkuProducto(),
        i.getNUnidades(),
        i.getCreadoEn(),
        i.getActualizadoEn(),
        i.getCreadoPor(),
        i.getActualizadoPor());
  }
}
