package oracle.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de la propia contrasena: exige la actual y la nueva (dos veces). */
public record CambiarContrasenaRequest(
        @NotBlank(message = "La contrasena actual es obligatoria.")
        String contrasenaActual,

        @NotBlank(message = "La contrasena nueva es obligatoria.")
        @Size(min = 6, max = 100, message = "La contrasena debe tener entre 6 y 100 caracteres.")
        String contrasenaNueva) {
}
