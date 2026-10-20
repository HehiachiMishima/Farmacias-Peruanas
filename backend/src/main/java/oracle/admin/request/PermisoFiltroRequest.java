package oracle.admin.request;

/** Filtro de listado de permisos con LIKE. */
public record PermisoFiltroRequest(
        String nombre,
        String descripcion,
        Boolean activo) {
}
