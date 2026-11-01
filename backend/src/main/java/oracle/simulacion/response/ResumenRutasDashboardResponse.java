package oracle.simulacion.response;

import java.math.BigDecimal;

/** Totales de las rutas que cumplen los filtros del modal «Rutas por chute». */
public record ResumenRutasDashboardResponse(
    long rutas,
    long rutasB2c,
    long rutasB2b,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    long cod) {}
