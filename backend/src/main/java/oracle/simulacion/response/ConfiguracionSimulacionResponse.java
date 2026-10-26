package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

public record ConfiguracionSimulacionResponse(
    ArchivoSimulacionResponse archivo,
    BigDecimal velocidadBase,
    List<ClusterParametroResponse> clusters,
    List<ChuteParametroResponse> chutes,
    List<RutaSimulacionResponse> rutas) {}
