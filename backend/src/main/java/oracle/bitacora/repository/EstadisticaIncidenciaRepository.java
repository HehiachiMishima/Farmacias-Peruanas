package oracle.bitacora.repository;

import oracle.common.config.ZonaNegocio;
import oracle.common.support.CondicionSql;
import java.util.List;
import oracle.bitacora.request.FiltroIncidencia;
import oracle.bitacora.response.GrupoEstadistica;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Agregados de incidencias resueltos en MySQL (COUNT / SUM + GROUP BY) para el
 * dashboard. Se usa JdbcTemplate construyendo el WHERE de forma dinamica: solo
 * se añaden las condiciones realmente informadas, de modo que las consultas
 * filtradas puedan apoyarse en los indices compuestos (id_data_* + creado_en)
 * en lugar de recorrer la tabla completa.
 *
 * Las columnas de agrupacion y las tablas de catalogo son constantes internas
 * (nunca provienen del usuario), por lo que no hay riesgo de inyeccion SQL.
 */
@Repository
public class EstadisticaIncidenciaRepository {

    private static final RowMapper<GrupoEstadistica> GRUPO = (rs, fila) ->
            new GrupoEstadistica(rs.getString("etiqueta"), rs.getLong("count"), rs.getLong("segundos"));

    private final JdbcTemplate jdbc;
    private final ZonaNegocio zona;

    public EstadisticaIncidenciaRepository(JdbcTemplate jdbc, ZonaNegocio zona) {
        this.jdbc = jdbc;
        this.zona = zona;
    }

    /** (total de incidencias, segundos acumulados de solucion) para el filtro. */
    public long[] totales(FiltroIncidencia filtro) {
        CondicionSql condicion = condicion(filtro);
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(i.tiempo_solucion_seg), 0) AS segundos "
                + "FROM incidencias i " + condicion.where();
        return jdbc.queryForObject(sql, (rs, fila) -> new long[] {rs.getLong("total"), rs.getLong("segundos")},
                condicion.args().toArray());
    }

    public List<GrupoEstadistica> porSistema(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_sistema", "sistema", "i.id_data_sistema");
    }

    public List<GrupoEstadistica> porTipo(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_incidencia", "incidencia", "i.id_data_incidencia");
    }

    public List<GrupoEstadistica> porCentro(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_centro", "centro", "i.id_data_centro");
    }

    public List<GrupoEstadistica> porArea(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_area", "area", "i.id_data_area");
    }

    private List<GrupoEstadistica> agrupar(FiltroIncidencia filtro, String tabla, String columna, String fk) {
        CondicionSql condicion = condicion(filtro);
        String sql = "SELECT COALESCE(t." + columna + ", '—') AS etiqueta, "
                + "COUNT(*) AS count, "
                + "COALESCE(SUM(i.tiempo_solucion_seg), 0) AS segundos "
                + "FROM incidencias i "
                + "LEFT JOIN " + tabla + " t ON t.id = " + fk + " "
                + condicion.where()
                + " GROUP BY t." + columna
                + " ORDER BY count DESC, etiqueta ASC";
        return jdbc.query(sql, GRUPO, condicion.args().toArray());
    }

    private CondicionSql condicion(FiltroIncidencia filtro) {
        return CondicionIncidencia.de(filtro, zona);
    }
}
