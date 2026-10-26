package oracle.simulacion.response;

import java.math.BigDecimal;

public record ResumenSimulacionResponse(
    long registros,
    long rutas,
    long chutes,
    long etiquetas,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas) {}
