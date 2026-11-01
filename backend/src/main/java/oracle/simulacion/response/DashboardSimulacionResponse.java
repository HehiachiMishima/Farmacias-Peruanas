package oracle.simulacion.response;

import java.util.List;

public record DashboardSimulacionResponse(
    SimulacionKpisResponse kpis,
    List<ClusterComparativoResponse> clusters,
    List<ChuteComparativoResponse> chutes,
    /** Chutes que hoy mezclan CL1 y CL2 (advertencia, no bloquea la carga). */
    List<ChuteMezcladoResponse> mezclas) {}
