package oracle.inbound.request;
import java.time.LocalDate;
public record FiltroIncidenciaInbound(
    Long proveedor,
    Long tipo,
    LocalDate desde,
    LocalDate hasta,
    String texto
) {
  public boolean vacio() {
    return proveedor == null && tipo == null && desde == null && hasta == null
        && (texto == null || texto.isBlank());
  }
}
