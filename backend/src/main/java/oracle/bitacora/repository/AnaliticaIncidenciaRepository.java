package oracle.bitacora.repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import oracle.bitacora.request.FiltroIncidencia;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Grupo;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Kpis;
import oracle.bitacora.response.AnaliticaBitacoraResponse.Tipo;
import oracle.common.response.CeldaMatriz;
import oracle.common.response.PuntoSerie;
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

    /** Por encima de este rango (dias) la tendencia se agrupa por mes. */
    private static final long MAX_DIAS_SERIE_DIARIA = 92;

    private final JdbcTemplate jdbc;

    public AnaliticaIncidenciaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Kpis kpis(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro);
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(i.hora_fin IS NULL), 0) AS abiertas, "
                + "COUNT(i.tiempo_solucion) AS resueltas, "
                + "COALESCE(SUM(TIME_TO_SEC(i.tiempo_solucion)), 0) AS segundos, "
                + "COALESCE(ROUND(AVG(TIME_TO_SEC(i.tiempo_solucion))), 0) AS mttr, "
                + "COUNT(DISTINCT i.id_data_sistema) AS sistemas, "
                + "COUNT(DISTINCT i.id_data_centro) AS centros "
                + "FROM incidencias i " + c.where();
        Kpis actual = jdbc.queryForObject(sql, (rs, n) -> new Kpis(
                rs.getLong("total"), rs.getLong("abiertas"), rs.getLong("resueltas"),
                rs.getLong("segundos"), rs.getLong("mttr"), rs.getLong("sistemas"),
                rs.getLong("centros"), null), c.argumentos());
        FiltroIncidencia anterior = periodoAnterior(filtro);
        if (actual == null || anterior == null) {
            return actual;
        }
        CondicionSql ca = CondicionIncidencia.de(anterior);
        Long previo = jdbc.queryForObject("SELECT COUNT(*) FROM incidencias i " + ca.where(), Long.class,
                ca.argumentos());
        return new Kpis(actual.total(), actual.abiertas(), actual.resueltas(), actual.segundosSolucion(),
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
        CondicionSql c = CondicionIncidencia.de(filtro);
        String sql = "SELECT COALESCE(t.incidencia, '—') AS tipo, COALESCE(s.sistema, '—') AS sistema, "
                + "COUNT(*) AS total, COALESCE(ROUND(AVG(TIME_TO_SEC(i.tiempo_solucion))), 0) AS mttr "
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
        CondicionSql c = CondicionIncidencia.de(filtro);
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

    /** Serie temporal: valor = incidencias, secundario = abiertas. */
    public List<PuntoSerie> tendencia(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro);
        String formato = granularidad(filtro).equals("dia") ? "%Y-%m-%d" : "%Y-%m";
        String sql = "SELECT DATE_FORMAT(i.creado_en, '" + formato + "') AS periodo, COUNT(*) AS valor, "
                + "COALESCE(SUM(i.hora_fin IS NULL), 0) AS secundario "
                + "FROM incidencias i " + c.where()
                + " GROUP BY periodo ORDER BY periodo";
        return jdbc.query(sql, (rs, n) -> new PuntoSerie(rs.getString("periodo"), rs.getLong("valor"),
                rs.getLong("secundario")), c.argumentos());
    }

    /** Incidencias por hora de inicio (0-23): valor = incidencias, secundario = abiertas. */
    public List<PuntoSerie> porHora(FiltroIncidencia filtro) {
        CondicionSql c = CondicionIncidencia.de(filtro);
        String sql = "SELECT HOUR(i.hora_inicio) AS hora, COUNT(*) AS valor, "
                + "COALESCE(SUM(i.hora_fin IS NULL), 0) AS secundario "
                + "FROM incidencias i " + c.where()
                + " GROUP BY hora ORDER BY hora";
        return jdbc.query(sql, (rs, n) -> new PuntoSerie(String.format("%02d", rs.getInt("hora")),
                rs.getLong("valor"), rs.getLong("secundario")), c.argumentos());
    }

    /** "dia" si el rango pedido es corto; "mes" en otro caso (o sin rango). */
    public String granularidad(FiltroIncidencia filtro) {
        if (filtro != null && filtro.desde() != null && filtro.hasta() != null
                && ChronoUnit.DAYS.between(filtro.desde(), filtro.hasta()) <= MAX_DIAS_SERIE_DIARIA) {
            return "dia";
        }
        return "mes";
    }

    private List<Grupo> agrupar(FiltroIncidencia filtro, String tabla, String columna, String fk) {
        CondicionSql c = CondicionIncidencia.de(filtro);
        String sql = "SELECT COALESCE(t." + columna + ", '—') AS etiqueta, COUNT(*) AS total, "
                + "COALESCE(SUM(i.hora_fin IS NULL), 0) AS abiertas, "
                + "COALESCE(ROUND(AVG(TIME_TO_SEC(i.tiempo_solucion))), 0) AS mttr "
                + "FROM incidencias i "
                + "LEFT JOIN " + tabla + " t ON t.id = " + fk + " "
                + c.where()
                + " GROUP BY " + fk + ", t." + columna
                + " ORDER BY total DESC, etiqueta ASC";
        return jdbc.query(sql, (rs, n) -> new Grupo(rs.getString("etiqueta"), rs.getLong("total"),
                rs.getLong("abiertas"), rs.getLong("mttr")), c.argumentos());
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
