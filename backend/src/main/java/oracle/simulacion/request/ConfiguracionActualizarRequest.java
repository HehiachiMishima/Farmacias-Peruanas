package oracle.simulacion.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ConfiguracionActualizarRequest(
    @NotNull @DecimalMin("0.01") @DecimalMax(Limites.MAX_VELOCIDAD) @Digits(integer = 8, fraction = 2)
        BigDecimal velocidadBase,
    @Valid @Size(max = Limites.MAX_PARAMETROS) List<ClusterLimiteRequest> clusters,
    @Valid @Size(max = Limites.MAX_PARAMETROS) List<ChuteOperariosRequest> chutes) {}
