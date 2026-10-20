package oracle.bitacora.response;

import java.time.LocalDateTime;
import oracle.bitacora.model.DataSistema;

/** Salida publica de sistema. */
public record SistemaResponse(
        Long id,
        String sistema,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        Long creadoPor,
        Long actualizadoPor) {

    public static SistemaResponse of(DataSistema s) {
        return new SistemaResponse(
                s.getId(), s.getSistema(), s.getCreadoEn(), s.getActualizadoEn(),
                s.getCreadoPor(), s.getActualizadoPor());
    }
}
