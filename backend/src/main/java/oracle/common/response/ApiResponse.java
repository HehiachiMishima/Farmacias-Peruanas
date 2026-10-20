package oracle.common.response;

import java.time.LocalDateTime;

/**
 * Envoltorio estandar de toda respuesta de la API.
 * exito=true => datos con payload; exito=false => mensaje de error.
 */
public record ApiResponse<T>(
        boolean exito,
        String mensaje,
        T datos,
        LocalDateTime timestamp) {

    public static <ApiResponseT> ApiResponse<ApiResponseT> ok(String mensaje, ApiResponseT datos) {
        return new ApiResponse<>(true, mensaje, datos, LocalDateTime.now());
    }
}
