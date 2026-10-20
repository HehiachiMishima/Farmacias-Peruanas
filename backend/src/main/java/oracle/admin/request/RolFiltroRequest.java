package oracle.admin.request;

/** Filtro de listado de roles con LIKE. */
public record RolFiltroRequest(
        String nombre,
        String descripcion,
        Boolean activo) {
}
