package oracle.common.support;

import java.util.List;

/**
 * Fragmento WHERE parametrizado ({@code ?}) y sus argumentos, construido a
 * partir de un filtro. Solo contiene condiciones realmente informadas para que
 * MySQL pueda usar los indices compuestos (id_data_* + creado_en).
 */
public record CondicionSql(String where, List<Object> args) {

    public Object[] argumentos() {
        return args.toArray();
    }

    /** AND adicional sobre el WHERE existente (o WHERE si no habia condiciones). */
    public String y(String condicionExtra) {
        return where.isEmpty() ? "WHERE " + condicionExtra : where + " AND " + condicionExtra;
    }
}
