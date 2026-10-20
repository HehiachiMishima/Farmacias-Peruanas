package oracle.bitacora.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Alta de incidencia. La fecha de creación se ingresa manual (solo fecha);
 * el backend la combina con la hora de inicio para registrar creado_en.
 */
public record IncidenciaCrearRequest(
        @NotNull(message = "El centro es obligatorio.") Long idDataCentro,
        @NotNull(message = "El sistema es obligatorio.") Long idDataSistema,
        @NotNull(message = "El tipo de incidencia es obligatorio.") Long idDataIncidencia,
        @NotBlank(message = "El ticket es obligatorio.")
        @Size(max = 59, message = "El ticket no debe superar 59 caracteres.") String ticket,
        @NotNull(message = "El área es obligatoria.") Long idDataArea,
        @NotNull(message = "El responsable es obligatorio.") Long idDataResponsable,
        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 255, message = "La descripción no debe superar 255 caracteres.") String descripcion,
        @NotNull(message = "La hora de inicio es obligatoria.") LocalTime horaInicio,
        LocalTime horaFin,
        @NotNull(message = "La fecha de creación es obligatoria.") LocalDate fechaCreacion) {
}
