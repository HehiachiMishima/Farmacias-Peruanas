package oracle.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Punto unico donde las excepciones del backend se convierten en respuestas HTTP.
 * Toda excepcion que lancen los services cae aqui y sale como ApiError.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(NoAutorizadoException.class)
    public ResponseEntity<ApiError> noAutorizado(NoAutorizadoException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ApiError> limiteExcedido(LimiteExcedidoException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ApiError> noPermitido(OperacionNoPermitidaException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    /**
     * Denegacion de @PreAuthorize (AccessDeniedException / AuthorizationDeniedException).
     * Sin este handler la capturaria el catch-all y saldria como 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.FORBIDDEN, "No tiene el permiso requerido para esta operacion.", req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    /** Validaciones jakarta (@NotBlank, @Size...) declaradas en los Request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.conCampos(400, "Validacion fallida",
                        "Hay campos invalidos en la peticion.", req.getRequestURI(), campos));
    }

    // ------------------------------------------------------------------
    // Excepciones de infraestructura de Spring MVC. Sin estos handlers las
    // capturaria el catch-all y devolverian 500 en vez del codigo correcto.
    // ---------------------------------------------------------------------

    /** Cuerpo JSON ausente o mal formado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion es obligatorio y debe ser un JSON valido.", req);
    }

    /** Path variable / query param con tipo incorrecto (p. ej. id no numerico). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.BAD_REQUEST,
                "El parametro '" + ex.getName() + "' tiene un formato invalido.", req);
    }

    /** Falta un parametro obligatorio de la peticion. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> parametroFaltante(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.BAD_REQUEST,
                "Falta el parametro obligatorio '" + ex.getParameterName() + "'.", req);
    }

    /** Metodo HTTP no soportado por el recurso. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodoNoSoportado(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + ex.getMethod() + " no esta permitido en esta ruta.", req);
    }

    /** Content-Type no soportado. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> tipoContenidoNoSoportado(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "El Content-Type de la peticion no esta soportado. Use application/json.", req);
    }

    /** Ruta inexistente: ninguna coincidencia de handler ni recurso estatico. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return cuerpo(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.", req);
    }

    /**
     * Errores de la base de datos. Distingue:
     *  - SQLSTATE 45000: SIGNAL de un trigger de proteccion -> 403 con su mensaje.
     *  - error 1451 (FK RESTRICT al borrar/actualizar un padre en uso) -> 409 claro.
     *  - resto de violaciones de integridad (uq_*, FK) -> 409 generico.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> baseDeDatos(DataAccessException ex, HttpServletRequest req) {
        SQLException sql = causaSql(ex);
        if (sql != null && "45000".equals(sql.getSQLState())) {
            return cuerpo(HttpStatus.FORBIDDEN, sql.getMessage(), req);
        }
        if (sql != null && sql.getErrorCode() == 1451) {
            return cuerpo(HttpStatus.CONFLICT,
                    "El registro esta en uso por otros datos y no se puede eliminar.", req);
        }
        if (sql != null && sql.getErrorCode() == 1062) {
            return cuerpo(HttpStatus.CONFLICT, "Ya existe un registro con esos datos.", req);
        }
        if (ex instanceof DataIntegrityViolationException) {
            log.warn("Violacion de integridad: {}", ex.getMessage());
            return cuerpo(HttpStatus.CONFLICT,
                    "La operacion viola una restriccion de integridad del esquema.", req);
        }
        if (ex instanceof QueryTimeoutException) {
            return cuerpo(HttpStatus.SERVICE_UNAVAILABLE,
                    "La base de datos tardo demasiado en responder. Intente de nuevo.", req);
        }
        log.error("Error de acceso a datos", ex);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno inesperado.", req);
    }

    private static SQLException causaSql(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado", ex);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno inesperado.", req);
    }

    private ResponseEntity<ApiError> cuerpo(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.de(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI()));
    }
}
