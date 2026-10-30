package oracle.bitacora.repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import oracle.bitacora.request.FiltroIncidencia;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Grupo;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Kpis;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Tipo;
import oracle.common.response.CeldaMatriz;
import oracle.common.config.ZonaNegocio;
import oracle.common.support.CondicionSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Agregados analiticos de {@code incidencias} resueltos en MySQL (GROUP BY).
 * Todas las consultas comparten el WHERE de {@link CondicionIncidencia}, de modo
 * que respetan exactamente los filtros del listado y aprovechan sus indices.
 * Las columnas y tablas de agrupacion son constantes internas (sin inyeccion).
 */
@Repository
public class AnaliticaIncidenciaRepository {

    private final JdbcTemplate jdbc;
    private final ZonaNegocio zona;

    public AnaliticaIncidenciaRepository(JdbcTemplate jdbc, ZonaNegocio zona) {
        this.jdbc = jdbc;
        this.zona = zona;
    }

    public Kpis kpis(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro, zona);
        String sql = "SELECT COUNT(*) AS total, "
                + "COUNT(i.tiempo_solucion_seg) AS resueltas, "
                + "COALESCE(SUM(i.tiempo_solucion_seg), 0) AS segundos, "
                + "COALESCE(ROUND(AVG(i.tiempo_solucion_seg)), 0) AS mttr, "
                + "COUNT(DISTINCT i.id_data_sistema) AS sistemas, "
                + "COUNT(DISTINCT i.id_data_centro) AS centros "
                + "FROM incidencias i " + c.where();
        Kpis actual = jdbc.queryForObject(sql, (rs, n) -> new Kpis(
                rs.getLong("total"), rs.getLong("resueltas"),
                rs.getLong("segundos"), rs.getLong("mttr"), rs.getLong("sistemas"),
                rs.getLong("centros"), null), c.argumentos());
        FiltroIncidencia anterior = periodoAnterior(filtro);
        if (actual == null || anterior == null) {
            return actual;
        }
        CondicionSql ca = CondicionIncidencia.de(anterior, zona);
        Long previo = jdbc.queryForObject("SELECT COUNT(*) FROM incidencias i " + ca.where(), Long.class,
                ca.argumentos());
        return new Kpis(actual.total(), actual.resueltas(), actual.segundosSolucion(),
                actual.mttrSegundos(), actual.sistemasAfectados(), actual.centrosAfectados(), previo);
    }

    public List<Grupo> porSistema(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_sistema", "sistema", "i.id_data_sistema");
    }

    public List<Grupo> porCentro(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_centro", "centro", "i.id_data_centro");
    }

    public List<Grupo> porArea(FiltroIncidencia filtro) {
        return agrupar(filtro, "data_area", "area", "i.id_data_area");
    }

    public List<Tipo> porTipo(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro, zona);
        String sql = "SELECT COALESCE(t.incidencia, '—') AS tipo, COALESCE(s.sistema, '—') AS sistema, "
                + "COUNT(*) AS total, COALESCE(ROUND(AVG(i.tiempo_solucion_seg)), 0) AS mttr "
                + "FROM incidencias i "
                + "LEFT JOIN data_incidencia t ON t.id = i.id_data_incidencia "
                + "LEFT JOIN data_sistema s ON s.id = i.id_data_sistema "
                + c.where()
                + " GROUP BY i.id_data_incidencia, t.incidencia, s.sistema"
                + " ORDER BY total DESC, tipo ASC";
        return jdbc.query(sql, (rs, n) -> new Tipo(rs.getString("tipo"), rs.getString("sistema"),
                rs.getLong("total"), rs.getLong("mttr")), c.argumentos());
    }

    public List<CeldaMatriz> centroPorSistema(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro, zona);
        String sql = "SELECT COALESCE(ce.centro, '—') AS fila, COALESCE(s.sistema, '—') AS columna, "
                + "COUNT(*) AS valor "
                + "FROM incidencias i "
                + "LEFT JOIN data_centro ce ON ce.id = i.id_data_centro "
                + "LEFT JOIN data_sistema s ON s.id = i.id_data_sistema "
                + c.where()
                + " GROUP BY i.id_data_centro, ce.centro, i.id_data_sistema, s.sistema";
        return jdbc.query(sql, (rs, n) -> new CeldaMatriz(rs.getString("fila"), rs.getString("columna"),
                rs.getLong("valor")), c.argumentos());
    }

    private List<Grupo> agrupar(FiltroIncidencia filtro, String tabla, String columna, String fk) {
        CondicionSql c = CondicionIncidencia.de(filtro, zona);
        String sql = "SELECT COALESCE(t." + columna + ", '—') AS etiqueta, COUNT(*) AS total, "
                + "COALESCE(ROUND(AVG(i.tiempo_solucion_seg)), 0) AS mttr "
                + "FROM incidencias i "
                + "LEFT JOIN " + tabla + " t ON t.id = " + fk + " "
                + c.where()
                + " GROUP BY " + fk + ", t." + columna
                + " ORDER BY total DESC, etiqueta ASC";
        return jdbc.query(sql, (rs, n) -> new Grupo(rs.getString("etiqueta"), rs.getLong("total"),
                rs.getLong("mttr")), c.argumentos());
    }

    /** Mismo filtro desplazado al periodo anterior de igual duracion (requiere desde y hasta). */
    private static FiltroIncidencia periodoAnterior(FiltroIncidencia f) {
        if (f == null || f.desde() == null || f.hasta() == null || f.hasta().isBefore(f.desde())) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(f.desde(), f.hasta()) + 1;
        LocalDate hasta = f.desde().minusDays(1);
        LocalDate desde = hasta.minusDays(dias - 1);
        return new FiltroIncidencia(f.centro(), f.sistema(), f.area(), f.responsable(), f.tipo(), desde, hasta,
                f.texto());
    }
}
