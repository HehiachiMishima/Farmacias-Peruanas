package oracle.simulacion.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ConfiguracionActualizarRequest(
    @NotNull @DecimalMin("0.01") BigDecimal velocidadBase,
    @Valid List<ClusterLimiteRequest> clusters,
    @Valid List<ChuteOperariosRequest> chutes) {}
