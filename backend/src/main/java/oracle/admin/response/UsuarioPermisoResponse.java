package oracle.admin.response;

import java.time.LocalDateTime;
import oracle.admin.model.UsuarioPermiso;

/** Salida de un override individual (grant/deny). */
public record UsuarioPermisoResponse(
        Long usuarioId,
        String usuario,
        Long permisoId,
        String permisoNombre,
        Boolean otorgado,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    public static UsuarioPermisoResponse of(UsuarioPermiso up) {
        return new UsuarioPermisoResponse(
                up.getUsuario().getId(),
                up.getUsuario().getUsuario(),
                up.getPermiso().getId(),
                up.getPermiso().getNombre(),
                up.getOtorgado(),
                up.getCreadoEn(),
                up.getActualizadoEn());
    }
}
