package oracle.admin.request;

/**
 * Filtro de listado de usuarios con LIKE.
 * Todos los campos son opcionales y se combinan con AND.
 */
public record UsuarioFiltroRequest(
        String usuario,
        String nombre,
        String apellido,
        String email,
        Boolean activo) {
}
