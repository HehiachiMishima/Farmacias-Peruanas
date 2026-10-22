package oracle.bitacora.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import oracle.bitacora.request.FiltroIncidencia;
import oracle.common.support.CondicionSql;

/** WHERE dinamico de la tabla {@code incidencias} (alias {@code i}) a partir del filtro. */
final class CondicionIncidencia {

    static CondicionSql de(FiltroIncidencia filtro) {
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
        return new CondicionSql(where, args);
    }

    private CondicionIncidencia() {
    }
}
