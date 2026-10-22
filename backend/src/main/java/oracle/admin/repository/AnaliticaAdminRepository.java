package oracle.admin.repository;

import java.util.List;
import oracle.admin.response.AnaliticaAdminResponse.Conteo;
import oracle.admin.response.AnaliticaAdminResponse.Kpis;
import oracle.common.response.PuntoSerie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Agregados ejecutivos del RBAC resueltos en MySQL sobre tablas y vistas de resolucion. */
@Repository
public class AnaliticaAdminRepository {

    private static final RowMapper<Conteo> CONTEO = (rs, n) -> new Conteo(rs.getString("etiqueta"), rs.getLong("valor"));
    private static final int TOP_PERMISOS = 12;
    private static final int MESES_ALTAS = 12;

    private final JdbcTemplate jdbc;

    public AnaliticaAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Kpis kpis() {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM usuarios) AS usuarios,
                  (SELECT COUNT(*) FROM usuarios WHERE activo = TRUE) AS usuarios_activos,
                  (SELECT COUNT(*) FROM usuarios u
                     WHERE NOT EXISTS (SELECT 1 FROM usuarios_roles ur WHERE ur.usuario_id = u.id)) AS sin_rol,
                  (SELECT COUNT(*) FROM roles) AS roles,
                  (SELECT COUNT(*) FROM roles WHERE activo = TRUE) AS roles_activos,
                  (SELECT COUNT(*) FROM permisos) AS permisos,
                  (SELECT COUNT(*) FROM permisos WHERE activo = TRUE) AS permisos_activos,
                  (SELECT COUNT(*) FROM usuarios_permisos WHERE otorgado = TRUE) AS grants,
                  (SELECT COUNT(*) FROM usuarios_permisos WHERE otorgado = FALSE) AS denies
                """, (rs, n) -> new Kpis(rs.getLong("usuarios"), rs.getLong("usuarios_activos"),
                rs.getLong("sin_rol"), rs.getLong("roles"), rs.getLong("roles_activos"), rs.getLong("permisos"),
                rs.getLong("permisos_activos"), rs.getLong("grants"), rs.getLong("denies")));
    }

    public List<Conteo> usuariosPorRol() {
        return jdbc.query("""
                SELECT r.nombre AS etiqueta, COUNT(ur.usuario_id) AS valor
                FROM roles r
                LEFT JOIN usuarios_roles ur ON ur.rol_id = r.id
                GROUP BY r.id, r.nombre
                ORDER BY valor DESC, etiqueta ASC
                """, CONTEO);
    }

    /** Permisos con mas usuarios que los tienen efectivamente (deny-wins aplicado). */
    public List<Conteo> permisosMasOtorgados() {
        return jdbc.query("""
                SELECT p.nombre AS etiqueta, COUNT(*) AS valor
                FROM v_permisos_finales v
                JOIN permisos p ON p.id = v.permiso_id
                WHERE v.permitido = TRUE
                GROUP BY p.id, p.nombre
                ORDER BY valor DESC, etiqueta ASC
                LIMIT %d
                """.formatted(TOP_PERMISOS), CONTEO);
    }

    /** Usuarios ACTIVOS con acceso efectivo a cada panel. */
    public List<Conteo> coberturaPaneles() {
        return jdbc.query("""
                SELECT p.nombre AS etiqueta, COUNT(*) AS valor
                FROM v_permisos_finales v
                JOIN permisos p ON p.id = v.permiso_id
                JOIN usuarios u ON u.id = v.usuario_id AND u.activo = TRUE
                WHERE v.permitido = TRUE
                  AND p.nombre IN ('PANEL_ADMIN', 'PANEL_BITACORA', 'PANEL_INBOUND')
                GROUP BY p.nombre
                ORDER BY p.nombre
                """, CONTEO);
    }

    /** Altas de usuarios por mes (ultimos 12): valor = altas, secundario = altas aun activas. */
    public List<PuntoSerie> altasPorMes() {
        return jdbc.query("""
                SELECT DATE_FORMAT(creado_en, '%%Y-%%m') AS periodo, COUNT(*) AS valor,
                       COALESCE(SUM(activo = TRUE), 0) AS secundario
                FROM usuarios
                WHERE creado_en >= DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%%Y-%%m-01'), INTERVAL %d MONTH)
                GROUP BY periodo
                ORDER BY periodo
                """.formatted(MESES_ALTAS - 1),
                (rs, n) -> new PuntoSerie(rs.getString("periodo"), rs.getLong("valor"), rs.getLong("secundario")));
    }
}
