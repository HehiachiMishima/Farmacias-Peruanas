package oracle.simulacion.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    LocalDateTime creadoEn,
    LocalDateTime actualizadoEn,
    Long creadoPor,
    Long actualizadoPor) {}
