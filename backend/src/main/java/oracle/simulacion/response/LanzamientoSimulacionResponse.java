package oracle.simulacion.response;

import java.time.Instant;

/** Un lanzamiento de un cluster con el resultado que vio el usuario. */
public record LanzamientoSimulacionResponse(
    Long id,
    Long clusterId,
    String cluster,
    /** RUTAS u OPERARIOS. */
    String fase,
    String mensaje,
    Instant creadoEn,
    Long creadoPor) {}
