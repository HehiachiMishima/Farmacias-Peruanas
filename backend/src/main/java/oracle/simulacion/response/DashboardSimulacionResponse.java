package oracle.simulacion.response;

import java.util.List;

/**
 * Comparativo antes vs. después del registro completo (vista final) o de un
 * solo cluster (vista de su lanzamiento).
 */
public record DashboardSimulacionResponse(
    SimulacionKpisResponse kpis,
    List<ClusterComparativoResponse> clusters,
    List<ChuteComparativoResponse> chutes,
    /** Chutes que hoy mezclan CL1 y CL2 (advertencia, no bloquea la carga). */
    List<ChuteMezcladoResponse> mezclas,
    /** Rutas movidas por (chute original, chute actual): flujo de cualquier grupo de chutes. */
    List<MovimientoChuteResponse> movimientos,
    /** Clusters en el orden de las olas con su avance: arman el selector de vistas. */
    List<OlaSimulacionResponse> olas,
    /** Paso de operarios entre olas seguidas con operarios asignados (solo en la vista final). */
    List<FlujoOperariosResponse> flujoOperarios) {}
