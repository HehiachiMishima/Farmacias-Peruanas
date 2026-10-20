package oracle.admin.response;

import java.time.LocalDateTime;
import oracle.admin.model.Permiso;

/** Salida publica de permiso. */
public record PermisoResponse(
        Long id,
        String nombre,
        String descripcion,
        Boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        Long creadoPor,
        Long actualizadoPor) {

    public static PermisoResponse of(Permiso p) {
        return new PermisoResponse(
                p.getId(), p.getNombre(), p.getDescripcion(), p.getActivo(), p.getCreadoEn(), p.getActualizadoEn(),
                p.getCreadoPor(), p.getActualizadoPor());
    }
}
