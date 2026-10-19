package oracle.request;

import java.time.LocalDate;

/**
 * Filtros de busqueda del listado de incidencias. Todos los campos son
 * opcionales; un campo nulo no aplica la condicion correspondiente, de modo
 * que las consultas sin filtrar sigan aprovechando los indices por defecto.
 *
 * Las condiciones se construyen en el mismo orden que los indices compuestos
 * (id_data_* + creado_en) para que MySQL pueda usarlos.
 */
public record FiltroIncidencia(
        Long centro,
        Long sistema,
        Long area,
        Long responsable,
        Long tipo,
        LocalDate desde,
        LocalDate hasta,
        String texto) {

    public boolean vacio() {
        return centro == null
                && sistema == null
                && area == null
                && responsable == null
                && tipo == null
                && desde == null
                && hasta == null
                && (texto == null || texto.isBlank());
    }
}
