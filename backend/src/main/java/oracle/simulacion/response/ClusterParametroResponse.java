package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Límite de un cluster, su avance (PENDIENTE, RUTAS o LISTO) y los clusters
 * con los que no puede compartir chute.
 */
public record ClusterParametroResponse(
    Long id, String nombre, BigDecimal limiteHoras, List<Long> incompatibles, String estado) {}
