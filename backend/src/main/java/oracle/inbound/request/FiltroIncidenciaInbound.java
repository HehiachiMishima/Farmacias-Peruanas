package oracle.inbound.request;
import java.time.LocalDate;
public record FiltroIncidenciaInbound(
    Long proveedor,
    Long tipo,
    LocalDate desde,
    LocalDate hasta,
    String texto
) {}
