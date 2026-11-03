package oracle.simulacion.response;

import java.util.List;

/** Rutas de un cluster en un chute (una celda del cuadro de configuración). */
public record MatrizCeldaResponse(Long clusterId, Long chuteId, List<MatrizRutaResponse> rutas) {}
