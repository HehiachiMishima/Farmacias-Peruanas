package oracle.simulacion.response;

import java.util.List;

/**
 * Operarios de un chute (actuales y los que fijó el usuario) y los clusters
 * que hoy tienen rutas en él, para validar destinos sin juntar CL1 y CL2.
 */
public record ChuteParametroResponse(
    Long id, String nombre, Integer operarios, Integer operariosOriginal, List<Long> clusterIds) {}
