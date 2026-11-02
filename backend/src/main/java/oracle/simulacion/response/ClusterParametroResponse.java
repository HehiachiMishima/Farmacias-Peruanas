package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

/** Límite de un cluster y los clusters con los que no puede compartir chute. */
public record ClusterParametroResponse(
    Long id, String nombre, BigDecimal limiteHoras, List<Long> incompatibles) {}
