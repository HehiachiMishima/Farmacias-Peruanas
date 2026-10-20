package oracle.inbound.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import oracle.inbound.request.FiltroIncidenciaInbound;
import oracle.inbound.response.GrupoEstadisticaInbound;
import oracle.inbound.response.MesIncidenciaProveedor;
import oracle.inbound.response.ProveedorMensualEstadistica;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Agregados de incidencias inbound resueltos en MySQL (COUNT / SUM + GROUP BY)
 * para el dashboard. Se usa JdbcTemplate construyendo el WHERE de forma
 * dinamica: solo se añaden las condiciones realmente informadas, de modo que
 * las consultas filtradas puedan apoyarse en los indices compuestos
 * (id_data_* + creado_en) en lugar de recorrer la tabla completa.
 *
 * Las columnas de agrupacion y las tablas de catalogo son constantes internas
 * (nunca provienen del usuario), por lo que no hay riesgo de inyeccion SQL.
 */
@Repository
public class EstadisticaInboundRepository {

    private static final RowMapper<GrupoEstadisticaInbound> GRUPO = (rs, fila) ->
            new GrupoEstadisticaInbound(rs.getString("etiqueta"), rs.getLong("count"));

    private final JdbcTemplate jdbc;

    public EstadisticaInboundRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** (total de incidencias, unidades acumuladas) para el filtro. */
    public long[] totales(FiltroIncidenciaInbound filtro) {
        Condicion condicion = condicion(filtro);
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(i.n_unidades), 0) AS unidades "
                + "FROM incidencias_inbound i " + condicion.where();
        return jdbc.queryForObject(sql, (rs, fila) -> new long[] {rs.getLong("total"), rs.getLong("unidades")},
                condicion.args().toArray());
    }

    public List<GrupoEstadisticaInbound> porProveedor(FiltroIncidenciaInbound filtro) {
        return agrupar(filtro, "data_proveedor", "proveedor", "i.id_data_proveedor");
    }

    public List<GrupoEstadisticaInbound> porTipo(FiltroIncidenciaInbound filtro) {
        return agrupar(filtro, "data_incidencia_inbound", "incidencia_inbound", "i.id_data_incidencia_inbound");
    }

    private List<GrupoEstadisticaInbound> agrupar(FiltroIncidenciaInbound filtro, String tabla, String columna, String fk) {
        Condicion condicion = condicion(filtro);
        String sql = "SELECT COALESCE(t." + columna + ", '—') AS etiqueta, "
                + "COUNT(*) AS count "
                + "FROM incidencias_inbound i "
                + "LEFT JOIN " + tabla + " t ON t.id = " + fk + " "
                + condicion.where()
                + " GROUP BY t." + columna
                + " ORDER BY count DESC, etiqueta ASC";
        return jdbc.query(sql, GRUPO, condicion.args().toArray());
    }

    public List<ProveedorMensualEstadistica> porProveedorMensual(FiltroIncidenciaInbound filtro) {
        Condicion condicion = condicion(filtro);
        String sql = "SELECT i.id_data_proveedor AS id_proveedor, "
                + "COALESCE(t.proveedor, '—') AS proveedor, "
                + "DATE_FORMAT(i.creado_en, '%Y-%m') AS mes, "
                + "COUNT(*) AS count "
                + "FROM incidencias_inbound i "
                + "LEFT JOIN data_proveedor t ON t.id = i.id_data_proveedor "
                + condicion.where()
                + " GROUP BY i.id_data_proveedor, t.proveedor, mes "
                + "ORDER BY i.id_data_proveedor ASC, mes ASC";

        record Fila(long idProveedor, String proveedor, String mes, long count) {}
        List<Fila> filas = jdbc.query(sql, (rs, fila) -> new Fila(
                rs.getLong("id_proveedor"),
                rs.getString("proveedor"),
                rs.getString("mes"),
                rs.getLong("count")),
                condicion.args().toArray());

        record Acumulado(long idProveedor, String proveedor, List<MesIncidenciaProveedor> meses) {}
        List<Acumulado> acumulados = new ArrayList<>();
        long idActual = Long.MIN_VALUE;
        for (Fila f : filas) {
            if (f.idProveedor() != idActual) {
                idActual = f.idProveedor();
                acumulados.add(new Acumulado(f.idProveedor(), f.proveedor(), new ArrayList<>()));
            }
            acumulados.get(acumulados.size() - 1).meses().add(new MesIncidenciaProveedor(f.mes(), f.count()));
        }

        List<ProveedorMensualEstadistica> resultado = new ArrayList<>();
        for (Acumulado a : acumulados) {
            long total = a.meses().stream().mapToLong(MesIncidenciaProveedor::incidencias).sum();
            resultado.add(new ProveedorMensualEstadistica(a.idProveedor(), a.proveedor(), total, a.meses()));
        }
        resultado.sort((a, b) -> Long.compare(b.total(), a.total()));
        return resultado;
    }

    private Condicion condicion(FiltroIncidenciaInbound filtro) {
        List<String> condiciones = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (filtro != null) {
            if (filtro.proveedor() != null) {
                condiciones.add("i.id_data_proveedor = ?");
                args.add(filtro.proveedor());
            }
            if (filtro.tipo() != null) {
                condiciones.add("i.id_data_incidencia_inbound = ?");
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
                condiciones.add("(LOWER(i.orden_compra) LIKE ? OR LOWER(i.sku_producto) LIKE ?)");
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
