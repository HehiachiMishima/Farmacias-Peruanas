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
    /** Rutas que hoy están en un chute distinto al original. */
    int rutasMovidas,
    /** Operarios por encima de los que fijó el usuario (suma de chutes). */
    int operariosAgregados,
    /** Operarios que el balanceo quitó a un chute para dárselos a otro. */
    int operariosMovidos,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    int cod) {}
