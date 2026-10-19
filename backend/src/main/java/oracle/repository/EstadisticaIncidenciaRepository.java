package oracle.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import oracle.request.FiltroIncidencia;
import oracle.response.GrupoEstadistica;
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

    public EstadisticaIncidenciaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** (total de incidencias, segundos acumulados de solucion) para el filtro. */
    public long[] totales(FiltroIncidencia filtro) {
        Condicion condicion = condicion(filtro);
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(TIME_TO_SEC(i.tiempo_solucion)), 0) AS segundos "
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
        Condicion condicion = condicion(filtro);
        String sql = "SELECT COALESCE(t." + columna + ", '—') AS etiqueta, "
                + "COUNT(*) AS count, "
                + "COALESCE(SUM(TIME_TO_SEC(i.tiempo_solucion)), 0) AS segundos "
                + "FROM incidencias i "
                + "LEFT JOIN " + tabla + " t ON t.id = " + fk + " "
                + condicion.where()
                + " GROUP BY t." + columna
                + " ORDER BY count DESC, etiqueta ASC";
        return jdbc.query(sql, GRUPO, condicion.args().toArray());
    }

    private Condicion condicion(FiltroIncidencia filtro) {
        List<String> condiciones = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (filtro != null) {
            if (filtro.centro() != null) {
                condiciones.add("i.id_data_centro = ?");
                args.add(filtro.centro());
            }
            if (filtro.sistema() != null) {
                condiciones.add("i.id_data_sistema = ?");
                args.add(filtro.sistema());
            }
            if (filtro.area() != null) {
                condiciones.add("i.id_data_area = ?");
                args.add(filtro.area());
            }
            if (filtro.responsable() != null) {
                condiciones.add("i.id_data_responsable = ?");
                args.add(filtro.responsable());
            }
            if (filtro.tipo() != null) {
                condiciones.add("i.id_data_incidencia = ?");
                args.add(filtro.tipo());
            }
            if (filtro.desde() != null) {
                condiciones.add("i.creado_en >= ?");
                args.add(Timestamp.valueOf(filtro.desde().atStartOfDay()));
            }
            if (filtro.hasta() != null) {
                condiciones.add("i.creado_en < ?");
                args.add(Timestamp.valueOf(filtro.hasta().plusDays(1).atStartOfDay()));
            }
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                condiciones.add("(LOWER(i.ticket) LIKE ? OR LOWER(i.descripcion) LIKE ?)");
                String patron = "%" + filtro.texto().trim().toLowerCase() + "%";
                args.add(patron);
                args.add(patron);
            }
        }
        String where = condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones);
        return new Condicion(where, args);
    }

    private record Condicion(String where, List<Object> args) {
    }
}
