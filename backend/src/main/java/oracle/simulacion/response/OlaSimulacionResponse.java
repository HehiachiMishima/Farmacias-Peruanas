package oracle.simulacion.response;

/** Un cluster en el orden de las olas y su avance: PENDIENTE, RUTAS o LISTO. */
public record OlaSimulacionResponse(Long clusterId, String cluster, String estado) {}
