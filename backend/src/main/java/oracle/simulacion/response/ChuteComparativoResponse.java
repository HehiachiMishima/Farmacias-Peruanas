package oracle.simulacion.response;

import java.math.BigDecimal;
import java.util.List;

public record ChuteComparativoResponse(
    String chute,
    int operariosOriginal,
    int operariosBalanceado,
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
    List<String> clusters) {}
