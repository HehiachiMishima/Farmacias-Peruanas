package oracle.repository;

import java.util.List;
import oracle.response.PermisoEfectivoResponse;
import oracle.response.PermisoFinalResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Consultas de resolucion de permisos sobre las vistas de MySQL.
 * Al ser vistas (no entidades), se leen con JdbcTemplate y se mapean
 * directamente a las respuestas, evitando el problema de identidad de una
 * entidad JPA cuando la vista devuelve filas con la misma clave.
 */
@Repository
public class ResolucionRepository {

    private static final RowMapper<PermisoEfectivoResponse> EFECTIVO = (rs, fila) ->
            new PermisoEfectivoResponse(
                    rs.getLong("usuario_id"),
                    rs.getLong("permiso_id"),
                    rs.getBoolean("otorgado"),
                    rs.getString("origen"));

    private static final RowMapper<PermisoFinalResponse> FINAL = (rs, fila) ->
            new PermisoFinalResponse(
                    rs.getLong("usuario_id"),
                    rs.getLong("permiso_id"),
                    rs.getBoolean("permitido"));

    private final JdbcTemplate jdbc;

    public ResolucionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PermisoEfectivoResponse> efectivos(Long usuarioId) {
        return jdbc.query("""
                SELECT usuario_id, permiso_id, otorgado, origen
                FROM v_permisos_efectivos
                WHERE usuario_id = ?
                ORDER BY permiso_id, origen
                """, EFECTIVO, usuarioId);
    }

    public List<PermisoFinalResponse> finales(Long usuarioId) {
        return jdbc.query("""
                SELECT usuario_id, permiso_id, permitido
                FROM v_permisos_finales
                WHERE usuario_id = ?
                ORDER BY permiso_id
                """, FINAL, usuarioId);
    }

    public List<PermisoFinalResponse> permitidos(Long usuarioId) {
        return jdbc.query("""
                SELECT usuario_id, permiso_id, permitido
                FROM v_permisos_finales
                WHERE usuario_id = ? AND permitido = TRUE
                ORDER BY permiso_id
                """, FINAL, usuarioId);
    }

    public boolean tienePermitido(Long usuarioId, Long permisoId) {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM v_permisos_finales
                WHERE usuario_id = ? AND permiso_id = ? AND permitido = TRUE
                """, Integer.class, usuarioId, permisoId);
        return total != null && total > 0;
    }

    public List<String> nombresPermitidos(Long usuarioId) {
        return jdbc.queryForList("""
                SELECT p.nombre
                FROM v_permisos_finales v
                JOIN permisos p ON p.id = v.permiso_id
                WHERE v.usuario_id = ? AND v.permitido = TRUE
                ORDER BY p.nombre
                """, String.class, usuarioId);
    }
}
