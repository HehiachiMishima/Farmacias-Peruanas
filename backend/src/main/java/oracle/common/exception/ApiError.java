package oracle.common.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cuerpo de error estandar para toda la API.
 * Lo construye el GlobalExceptionHandler y lo devuelve en el body de la respuesta HTTP.
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> campos) {

    public static ApiError de(int status, String error, String mensaje, String ruta) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, ruta, null);
    }

    public static ApiError conCampos(int status, String error, String mensaje, String ruta, Map<String, String> campos) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, ruta, campos);
    }
}
