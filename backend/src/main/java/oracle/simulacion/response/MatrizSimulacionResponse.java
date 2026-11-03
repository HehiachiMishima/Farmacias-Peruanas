package oracle.simulacion.response;

import java.util.List;

/** Rutas del registro agrupadas por cluster y chute para el cuadro de configuración. */
public record MatrizSimulacionResponse(List<MatrizCeldaResponse> celdas) {}
