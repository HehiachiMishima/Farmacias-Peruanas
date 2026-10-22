package oracle.bitacora.response;

import java.util.List;
import oracle.common.response.CeldaMatriz;
import oracle.common.response.PuntoSerie;

/**
 * Analitica del panel Bitacora calculada en SQL para el filtro recibido.
 *
 * @param granularidad "dia" o "mes": unidad de la serie {@code tendencia}.
 */
public record AnaliticaBitacoraResponse(
        Kpis kpis,
        List<Grupo> sistemas,
        List<Grupo> centros,
        List<Grupo> areas,
        List<Tipo> tipos,
        List<CeldaMatriz> centroSistema,
        List<PuntoSerie> tendencia,
        String granularidad,
        List<PuntoSerie> porHora) {

    /**
     * @param totalPeriodoAnterior total del periodo inmediatamente anterior de igual
     *        duracion; null si el filtro no tiene rango de fechas completo.
     */
    public record Kpis(
            long total,
            long abiertas,
            long resueltas,
            long segundosSolucion,
            long mttrSegundos,
            long sistemasAfectados,
            long centrosAfectados,
            Long totalPeriodoAnterior) {
    }

    /** Agregado por una dimension: volumen, abiertas y tiempo medio de solucion. */
    public record Grupo(String etiqueta, long total, long abiertas, long mttrSegundos) {
    }

    /** Tipo de incidencia con su sistema (el tipo pertenece a un sistema). */
    public record Tipo(String tipo, String sistema, long total, long mttrSegundos) {
    }
}
