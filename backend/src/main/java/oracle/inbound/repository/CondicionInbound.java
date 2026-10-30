package oracle.inbound.repository;

import java.util.ArrayList;
import java.util.List;
import oracle.common.config.ZonaNegocio;
import oracle.common.support.CondicionSql;
import oracle.inbound.request.FiltroIncidenciaInbound;

/** WHERE dinamico de la tabla {@code incidencias_inbound} (alias {@code i}) a partir del filtro. */
final class CondicionInbound {

    /** Los dias del filtro son dias del negocio; se comparan como instantes UTC. */
    static CondicionSql de(FiltroIncidenciaInbound filtro, ZonaNegocio zona) {
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
                args.add(zona.inicioDelDiaSql(filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add("i.creado_en < ?");
                args.add(zona.inicioDelDiaSql(filtro.hasta().plusDays(1)));
            }
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                condiciones.add("(LOWER(i.orden_compra) LIKE ? OR LOWER(i.sku_producto) LIKE ?)");
                String patron = "%" + filtro.texto().trim().toLowerCase() + "%";
                args.add(patron);
                args.add(patron);
            }
        }
        String where = condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones);
        return new CondicionSql(where, args);
    }

    private CondicionInbound() {
    }
}
