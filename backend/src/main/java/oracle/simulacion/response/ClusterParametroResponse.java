package oracle.simulacion.response;

import java.math.BigDecimal;

public record ClusterParametroResponse(Long id, String nombre, BigDecimal limiteHoras) {}
