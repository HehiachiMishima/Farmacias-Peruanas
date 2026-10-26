package oracle.simulacion.response;

import java.math.BigDecimal;

/** Rutas y totales que un cluster tiene asignados a un chute. */
public record CargaSimulacionResponse(
    Long clusterId,
    Long chuteId,
    long rutas,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    long cod) {}
