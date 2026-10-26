package oracle.simulacion.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ClusterLimiteRequest(
    @NotNull Long clusterId, @NotNull @DecimalMin("0.00") BigDecimal limiteHoras) {}
