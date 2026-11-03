package oracle.malla.repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import oracle.malla.model.MallaDatos;
import oracle.malla.model.MallaDatos.Capacidad;
import oracle.malla.model.MallaDatos.Centro;
import oracle.malla.model.MallaDatos.Dotacion;
import oracle.malla.model.MallaDatos.Flujo;
import oracle.malla.model.MallaDatos.Ola;
import oracle.malla.model.MallaDatos.Turno;
import oracle.malla.model.MallaDatos.Zona;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectura de la malla capturada. Se usa JdbcTemplate (y no entidades JPA)
 * porque la evaluación necesita una foto completa y de sólo lectura de las
 * ocho tablas; mapearlas a entidades agregaría ruido sin aportar nada.
 */
@Repository
public class MallaDatosRepository {

    private final JdbcTemplate jdbc;

    public MallaDatosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MallaDatos cargar() {
        return new MallaDatos(
                centros(),
                turnos(),
                flujos(),
                olas(),
                zonas(),
                capacidades(),
                dotaciones());
    }

    private List<Centro> centros() {
        return jdbc.query("""
                SELECT id, origen_id, codigo, nombre
                FROM malla_centro
                WHERE activo = TRUE
                ORDER BY id
                """, (rs, fila) -> new Centro(
                rs.getLong("id"),
                rs.getString("origen_id"),
                rs.getString("codigo"),
                rs.getString("nombre")));
    }

    private List<Turno> turnos() {
        return jdbc.query("""
                SELECT id, centro_id, codigo, nombre, hora_inicio, hora_fin, hora_refrigerio, confirmado
                FROM malla_turno
                ORDER BY centro_id, id
                """, (rs, fila) -> new Turno(
                rs.getLong("id"),
                rs.getLong("centro_id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                hora(rs.getTime("hora_inicio")),
                hora(rs.getTime("hora_fin")),
                hora(rs.getTime("hora_refrigerio")),
                rs.getBoolean("confirmado")));
    }

    private List<Flujo> flujos() {
        return jdbc.query("""
                SELECT id, centro_id, proceso, orden, responsable, hora_inicio, hora_fin, actividad, confirmado
                FROM malla_flujo
                ORDER BY centro_id, proceso, orden
                """, (rs, fila) -> new Flujo(
                rs.getLong("id"),
                rs.getLong("centro_id"),
                rs.getString("proceso"),
                rs.getInt("orden"),
                rs.getString("responsable"),
                hora(rs.getTime("hora_inicio")),
                hora(rs.getTime("hora_fin")),
                rs.getString("actividad"),
                rs.getBoolean("confirmado")));
    }

    private List<Ola> olas() {
        return jdbc.query("""
                SELECT id, centro_id, cluster, nombre, caida_pedidos, lanzamiento, cierre_picking,
                       despacho, confirmado
                FROM malla_ola
                ORDER BY centro_id, cluster, nombre
                """, (rs, fila) -> new Ola(
                rs.getLong("id"),
                rs.getLong("centro_id"),
                rs.getString("cluster"),
                rs.getString("nombre"),
                hora(rs.getTime("caida_pedidos")),
                hora(rs.getTime("lanzamiento")),
                hora(rs.getTime("cierre_picking")),
                hora(rs.getTime("despacho")),
                rs.getBoolean("confirmado")));
    }

    private List<Zona> zonas() {
        return jdbc.query("""
                SELECT id, centro_id, nombre, lph_real, lph_diseno
                FROM malla_zona
                ORDER BY centro_id, nombre
                """, (rs, fila) -> new Zona(
                rs.getLong("id"),
                rs.getLong("centro_id"),
                rs.getString("nombre"),
                entero(rs.getObject("lph_real")),
                entero(rs.getObject("lph_diseno"))));
    }

    private List<Capacidad> capacidades() {
        return jdbc.query("""
                SELECT centro_id, unidades_dia, lineas_dia, bultos_recepcion_dia, proveedores_dia
                FROM malla_capacidad
                ORDER BY centro_id
                """, (rs, fila) -> new Capacidad(
                rs.getLong("centro_id"),
                entero(rs.getObject("unidades_dia")),
                entero(rs.getObject("lineas_dia")),
                entero(rs.getObject("bultos_recepcion_dia")),
                entero(rs.getObject("proveedores_dia"))));
    }

    private List<Dotacion> dotaciones() {
        return jdbc.query("""
                SELECT id, centro_id, turno_id, proceso, personas
                FROM malla_dotacion
                ORDER BY centro_id, proceso, turno_id
                """, (rs, fila) -> new Dotacion(
                rs.getLong("id"),
                rs.getLong("centro_id"),
                rs.getLong("turno_id"),
                rs.getString("proceso"),
                entero(rs.getObject("personas"))));
    }

    private static LocalTime hora(Time valor) {
        return valor == null ? null : valor.toLocalTime();
    }

    private static Integer entero(Object valor) {
        return valor == null ? null : ((Number) valor).intValue();
    }
}
