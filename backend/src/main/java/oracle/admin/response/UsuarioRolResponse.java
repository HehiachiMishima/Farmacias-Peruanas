package oracle.admin.response;

import java.time.LocalDateTime;
import oracle.admin.model.UsuarioRol;

/** Salida de la asignacion usuario-rol. */
public record UsuarioRolResponse(
        Long usuarioId,
        Long rolId,
        String usuario,
        String rolNombre,
        LocalDateTime creadoEn) {

    public static UsuarioRolResponse of(UsuarioRol ur) {
        return new UsuarioRolResponse(
                ur.getUsuario().getId(),
                ur.getRol().getId(),
                ur.getUsuario().getUsuario(),
                ur.getRol().getNombre(),
                ur.getCreadoEn());
    }
}
