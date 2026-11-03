package oracle.simulacion.response;

/**
 * Resultado de lanzar un cluster: qué paso se ejecutó, en qué estado quedó
 * el cluster y qué cambió.
 */
public record BalanceoResponse(
    Long clusterId,
    String cluster,
    /** RUTAS u OPERARIOS. */
    String fase,
    /** Estado del cluster tras el paso (RUTAS o LISTO). */
    String estado,
    int rutasMovidas,
    /** Operarios asignados en la ola tras el paso 2. */
    int operariosAsignados,
    String mensaje) {}
