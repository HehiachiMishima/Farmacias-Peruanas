package oracle.admin.response;

/** Fila de v_permisos_finales: permitido = MIN(otorgado) => deny-overrides aplicado. */
public record PermisoFinalResponse(Long usuarioId, Long permisoId, Boolean permitido) {
}
