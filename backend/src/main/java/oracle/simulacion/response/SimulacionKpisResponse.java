package oracle.simulacion.response;

import java.math.BigDecimal;

/**
 * Indicadores del comparativo. tiempoOriginal y tiempoBalanceado son el
 * tiempo del cluster más lento (horas) antes y después de los ajustes.
 */
public record SimulacionKpisResponse(
    BigDecimal tiempoOriginal,
    BigDecimal tiempoBalanceado,
    BigDecimal tiempoAhorrado,
    int rutasMovidas,
    int operariosAgregados,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    int cod) {}
