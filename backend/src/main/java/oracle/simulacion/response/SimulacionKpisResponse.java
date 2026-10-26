package oracle.simulacion.response;

import java.math.BigDecimal;

public record SimulacionKpisResponse(
    BigDecimal tiempoAhorrado,
    int rutasMovidas,
    int operariosAgregados,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    int cod) {}
