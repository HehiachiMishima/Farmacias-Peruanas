package oracle.bitacora.response;

import java.util.List;

/**
 * Agregados del dashboard calculados en el servidor (SQL GROUP BY) para no
 * tener que descargar toda la tabla de incidencias en el navegador.
 */
public record EstadisticaResponse(
        long total,
        long segundosTotales,
        List<GrupoEstadistica> sistemas,
        List<GrupoEstadistica> tipos,
        List<GrupoEstadistica> centros,
        List<GrupoEstadistica> areas) {
}
