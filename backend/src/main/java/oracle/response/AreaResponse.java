package oracle.response;

import java.time.LocalDateTime;
import oracle.model.DataArea;

/** Salida publica de area. */
public record AreaResponse(
        Long id,
        String area,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        Long creadoPor,
        Long actualizadoPor) {

    public static AreaResponse of(DataArea a) {
        return new AreaResponse(
                a.getId(), a.getArea(), a.getCreadoEn(), a.getActualizadoEn(),
                a.getCreadoPor(), a.getActualizadoPor());
    }
}
