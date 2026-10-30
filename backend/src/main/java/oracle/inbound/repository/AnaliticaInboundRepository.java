package oracle.inbound.repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import oracle.common.response.CeldaMatriz;
import oracle.common.response.PuntoSerie;
import oracle.common.config.ZonaNegocio;
import oracle.common.support.CondicionSql;
import oracle.inbound.request.FiltroIncidenciaInbound;
import oracle.inbound.response.AnaliticaInboundResponse.Kpis;
import oracle.inbound.response.AnaliticaInboundResponse.Proveedor;
import oracle.inbound.response.AnaliticaInboundResponse.Sku;
import oracle.inbound.response.AnaliticaInboundResponse.Tipo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Agregados analiticos de {@code incidencias_inbound} (GROUP BY en MySQL) con el
 * mismo WHERE que el listado ({@link CondicionInbound}).
 */
@Repository
public class AnaliticaInboundRepository {

    private static final long MAX_DIAS_SERIE_DIARIA = 92;
    private static final int TOP_SKUS = 10;

    private final JdbcTemplate jdbc;
    private final ZonaNegocio zona;

    public AnaliticaInboundRepository(JdbcTemplate jdbc, ZonaNegocio zona) {
        this.jdbc = jdbc;
        this.zona = zona;
    }

    public Kpis kpis(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String sql = "SELECT COUNT(*) AS total, COALESCE(SUM(i.n_unidades), 0) AS unidades, "
                + "COUNT(DISTINCT i.id_data_proveedor) AS proveedores, "
                + "COUNT(DISTINCT i.orden_compra) AS ocs, "
                + "COUNT(DISTINCT i.sku_producto) AS skus, "
                + "COUNT(DISTINCT i.id_data_incidencia_inbound) AS tipos "
                + "FROM incidencias_inbound i " + c.where();
        Kpis actual = jdbc.queryForObject(sql, (rs, n) -> new Kpis(rs.getLong("total"), rs.getLong("unidades"),
                rs.getLong("proveedores"), rs.getLong("ocs"), rs.getLong("skus"), rs.getLong("tipos"), null, null),
                c.argumentos());
        FiltroIncidenciaInbound anterior = periodoAnterior(filtro);
        if (actual == null || anterior == null) {
            return actual;
        }
        CondicionSql ca = CondicionInbound.de(anterior, zona);
        long[] previo = jdbc.queryForObject(
                "SELECT COUNT(*) AS total, COALESCE(SUM(i.n_unidades), 0) AS unidades FROM incidencias_inbound i "
                        + ca.where(),
                (rs, n) -> new long[] {rs.getLong("total"), rs.getLong("unidades")}, ca.argumentos());
        return new Kpis(actual.total(), actual.unidades(), actual.proveedores(), actual.ordenesCompra(),
                actual.skus(), actual.tipos(), previo[0], previo[1]);
    }

    /**
     * Ranking completo de proveedores con su tipo dominante (el tipo con mas
     * incidencias de cada proveedor, resuelto con ROW_NUMBER en una sola pasada).
     */
    public List<Proveedor> proveedores(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String sql = "WITH base AS ("
                + "  SELECT i.id_data_proveedor AS pid, i.id_data_incidencia_inbound AS tid,"
                + "         i.orden_compra, i.sku_producto, i.n_unidades"
                + "  FROM incidencias_inbound i " + c.where()
                + "), agg AS ("
                + "  SELECT pid, COUNT(*) AS total, COALESCE(SUM(n_unidades), 0) AS unidades,"
                + "         COUNT(DISTINCT orden_compra) AS ocs, COUNT(DISTINCT sku_producto) AS skus"
                + "  FROM base GROUP BY pid"
                + "), dom AS ("
                + "  SELECT pid, tid, cnt FROM ("
                + "    SELECT pid, tid, COUNT(*) AS cnt,"
                + "           ROW_NUMBER() OVER (PARTITION BY pid ORDER BY COUNT(*) DESC, tid) AS rn"
                + "    FROM base GROUP BY pid, tid"
                + "  ) x WHERE rn = 1"
                + ") "
                + "SELECT a.pid, COALESCE(p.proveedor, '—') AS proveedor, a.total, a.unidades, a.ocs, a.skus,"
                + "       COALESCE(t.incidencia_inbound, '—') AS tipo_dominante, COALESCE(d.cnt, 0) AS total_dominante "
                + "FROM agg a "
                + "LEFT JOIN dom d ON d.pid = a.pid "
                + "LEFT JOIN data_proveedor p ON p.id = a.pid "
                + "LEFT JOIN data_incidencia_inbound t ON t.id = d.tid "
                + "ORDER BY a.total DESC, proveedor ASC";
        return jdbc.query(sql, (rs, n) -> new Proveedor(rs.getLong("pid"), rs.getString("proveedor"),
                rs.getLong("total"), rs.getLong("unidades"), rs.getLong("ocs"), rs.getLong("skus"),
                rs.getString("tipo_dominante"), rs.getLong("total_dominante")), c.argumentos());
    }

    public List<Tipo> tipos(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String sql = "SELECT COALESCE(t.incidencia_inbound, '—') AS tipo, COUNT(*) AS total, "
                + "COALESCE(SUM(i.n_unidades), 0) AS unidades, COUNT(DISTINCT i.id_data_proveedor) AS proveedores "
                + "FROM incidencias_inbound i "
                + "LEFT JOIN data_incidencia_inbound t ON t.id = i.id_data_incidencia_inbound "
                + c.where()
                + " GROUP BY i.id_data_incidencia_inbound, t.incidencia_inbound"
                + " ORDER BY total DESC, tipo ASC";
        return jdbc.query(sql, (rs, n) -> new Tipo(rs.getString("tipo"), rs.getLong("total"),
                rs.getLong("unidades"), rs.getLong("proveedores")), c.argumentos());
    }

    public List<CeldaMatriz> proveedorPorTipo(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String sql = "SELECT COALESCE(p.proveedor, '—') AS fila, COALESCE(t.incidencia_inbound, '—') AS columna, "
                + "COUNT(*) AS valor "
                + "FROM incidencias_inbound i "
                + "LEFT JOIN data_proveedor p ON p.id = i.id_data_proveedor "
                + "LEFT JOIN data_incidencia_inbound t ON t.id = i.id_data_incidencia_inbound "
                + c.where()
                + " GROUP BY i.id_data_proveedor, p.proveedor, i.id_data_incidencia_inbound, t.incidencia_inbound";
        return jdbc.query(sql, (rs, n) -> new CeldaMatriz(rs.getString("fila"), rs.getString("columna"),
                rs.getLong("valor")), c.argumentos());
    }

    public List<Sku> skusRecurrentes(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String sql = "SELECT i.sku_producto AS sku, COUNT(*) AS total, COALESCE(SUM(i.n_unidades), 0) AS unidades, "
                + "COUNT(DISTINCT i.id_data_proveedor) AS proveedores "
                + "FROM incidencias_inbound i " + c.where()
                + " GROUP BY i.sku_producto ORDER BY total DESC, unidades DESC LIMIT " + TOP_SKUS;
        return jdbc.query(sql, (rs, n) -> new Sku(rs.getString("sku"), rs.getLong("total"),
                rs.getLong("unidades"), rs.getLong("proveedores")), c.argumentos());
    }

    /** Serie temporal: valor = incidencias, secundario = unidades. */
    public List<PuntoSerie> tendencia(FiltroIncidenciaInbound filtro) {
        CondicionSql c = CondicionInbound.de(filtro, zona);
        String formato = granularidad(filtro).equals("dia") ? "%Y-%m-%d" : "%Y-%m";
        String sql = "SELECT DATE_FORMAT(i.creado_en + INTERVAL " + zona.desfaseSegundos() + " SECOND, '" + formato + "') AS periodo, COUNT(*) AS valor, "
                + "COALESCE(SUM(i.n_unidades), 0) AS secundario "
                + "FROM incidencias_inbound i " + c.where()
                + " GROUP BY periodo ORDER BY periodo";
        return jdbc.query(sql, (rs, n) -> new PuntoSerie(rs.getString("periodo"), rs.getLong("valor"),
                rs.getLong("secundario")), c.argumentos());
    }

    public String granularidad(FiltroIncidenciaInbound filtro) {
        if (filtro != null && filtro.desde() != null && filtro.hasta() != null
                && ChronoUnit.DAYS.between(filtro.desde(), filtro.hasta()) <= MAX_DIAS_SERIE_DIARIA) {
            return "dia";
        }
        return "mes";
    }

    private static FiltroIncidenciaInbound periodoAnterior(FiltroIncidenciaInbound f) {
        if (f == null || f.desde() == null || f.hasta() == null || f.hasta().isBefore(f.desde())) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(f.desde(), f.hasta()) + 1;
        LocalDate hasta = f.desde().minusDays(1);
        LocalDate desde = hasta.minusDays(dias - 1);
        return new FiltroIncidenciaInbound(f.proveedor(), f.tipo(), desde, hasta, f.texto());
    }
}
