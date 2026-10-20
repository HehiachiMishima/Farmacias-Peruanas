package oracle.inbound.response;
import java.util.List;
public record ProveedorMensualEstadistica(
    long idProveedor,
    String proveedor,
    long total,
    List<MesIncidenciaProveedor> meses
) {}
