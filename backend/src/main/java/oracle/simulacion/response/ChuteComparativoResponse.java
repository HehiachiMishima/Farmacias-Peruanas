package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

public record ChuteComparativoResponse(
    Long id,
    String chute,
    int operariosOriginal,
    int operariosBalanceado,
    int rutasOriginal,
    int rutasBalanceado,
    /** Rutas que el balanceo sacó del chute y las que trajo de otros. */
    int rutasSalieron,
    int rutasLlegaron,
    BigDecimal tiempoOriginal,
    BigDecimal tiempoBalanceado,
    String estado,
    BigDecimal bultosOriginal,
    BigDecimal bultosBalanceado,
    BigDecimal unidadesOriginal,
    BigDecimal unidadesBalanceado,
    BigDecimal lineasOriginal,
    BigDecimal lineasBalanceado,
    int codOriginal,
    int codBalanceado,
    /** Clusters que el chute atiende antes o después del balanceo (para filtrar por cluster). */
    List<String> clusters,
    /** Clusters del chute al cargar el Excel y tras el balanceo, por separado. */
    List<String> clustersOriginal,
    List<String> clustersBalanceado,
    /** El chute junta CL1 y CL2 (no permitido) al cargar el Excel / tras el balanceo. */
    boolean mezclaOriginal,
    boolean mezclaBalanceada) {}
