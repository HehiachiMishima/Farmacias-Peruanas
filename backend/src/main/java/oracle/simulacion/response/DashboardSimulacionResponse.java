package oracle.simulacion.response;

import java.util.List;

public record DashboardSimulacionResponse(
    SimulacionKpisResponse kpis,
    List<ClusterComparativoResponse> clusters,
    List<ChuteComparativoResponse> chutes) {}
