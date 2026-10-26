package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parámetros del registro y su carga agregada por (cluster, chute). Las rutas
 * se consultan aparte y paginadas (GET …/rutas): un registro puede tener
 * decenas de miles.
 */
public record ConfiguracionSimulacionResponse(
    ArchivoSimulacionResponse archivo,
    BigDecimal velocidadBase,
    List<ClusterParametroResponse> clusters,
    List<ChuteParametroResponse> chutes,
    List<CargaSimulacionResponse> carga) {}
