package oracle.simulacion.response;

import java.util.List;

/** Lanzamientos del registro y los cambios; cada cambio del balanceo apunta a su lanzamiento. */
public record HistorialRegistroResponse(
    List<LanzamientoSimulacionResponse> lanzamientos, List<HistorialSimulacionResponse> cambios) {}
