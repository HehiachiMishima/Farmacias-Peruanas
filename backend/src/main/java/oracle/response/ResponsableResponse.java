package oracle.response;

import java.time.LocalDateTime;
import oracle.model.DataResponsable;

/** Salida publica de responsable de un area. */
public record ResponsableResponse(
        Long id,
        Long idDataArea,
        String responsable,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        Long creadoPor,
        Long actualizadoPor) {

    public static ResponsableResponse of(DataResponsable r) {
        return new ResponsableResponse(
                r.getId(), r.getIdDataArea(), r.getResponsable(), r.getCreadoEn(), r.getActualizadoEn(),
                r.getCreadoPor(), r.getActualizadoPor());
    }
}
