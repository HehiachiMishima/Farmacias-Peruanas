package oracle.admin.repository;

import java.util.List;
import oracle.admin.response.AnaliticaAdminResponse.Conteo;
import oracle.admin.response.AnaliticaAdminResponse.Kpis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Agregados ejecutivos del RBAC resueltos en MySQL sobre tablas y vistas de resolucion. */
@Repository
public class AnaliticaAdminRepository {

    private static final RowMapper<Conteo> CONTEO = (rs, n) -> new Conteo(rs.getString("etiqueta"), rs.getLong("valor"));

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
                  (SELECT COUNT(*) FROM usuarios_permisos WHERE otorgado = TRUE) AS grants,
                  (SELECT COUNT(*) FROM usuarios_permisos WHERE otorgado = FALSE) AS denies
                """, (rs, n) -> new Kpis(rs.getLong("usuarios"), rs.getLong("usuarios_activos"),
                rs.getLong("sin_rol"), rs.getLong("grants"), rs.getLong("denies")));
    }

    /** Usuarios ACTIVOS con acceso efectivo a cada panel. */
    public List<Conteo> coberturaPaneles() {
        return jdbc.query("""
                SELECT p.nombre AS etiqueta, COUNT(*) AS valor
                FROM v_permisos_finales v
                JOIN permisos p ON p.id = v.permiso_id
                JOIN usuarios u ON u.id = v.usuario_id AND u.activo = TRUE
                WHERE v.permitido = TRUE
                  AND p.nombre IN ('PANEL_ADMIN', 'PANEL_BITACORA', 'PANEL_INBOUND',
                                   'PANEL_SIMULACION')
                GROUP BY p.nombre
                ORDER BY p.nombre
                """, CONTEO);
    }

}
