package oracle.simulacion.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ArchivoSimulacionResponse(
    Long id,
    String nombreRegistro,
    String nombreArchivo,
    String etiqueta,
    String colorHex,
    String estado,
    long rutas,
    long chutes,
    BigDecimal bultos,
    BigDecimal unidades,
    BigDecimal lineas,
    BigDecimal cod,
    Instant creadoEn,
    Instant actualizadoEn,
    Long creadoPor,
    Long actualizadoPor) {}
