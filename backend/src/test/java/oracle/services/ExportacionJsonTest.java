package oracle.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import oracle.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * El export en flujo debe producir byte a byte el mismo JSON que el envoltorio
 * ApiResponse normal, o el frontend rompería al descargar.
 */
class ExportacionJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("el export en flujo serializa el mismo envoltorio que ApiResponse<List<T>>")
    void igualQueElConversorNormal() {
        List<Ejemplo> filas = List.of(new Ejemplo(1L, "Centro 1"), new Ejemplo(2L, "Centro 2"));
        String real = exportar("OK", filas);
        String esperado = json.writeValueAsString(ApiResponse.ok("OK", filas));

        assertThat(sinTimestamp(real)).isEqualTo(sinTimestamp(esperado));
        assertThat(real).contains("\"exito\":true").contains("\"datos\":").contains("\"timestamp\":");
    }

    @Test
    @DisplayName("con cero filas el JSON sigue siendo un ApiResponse con datos vacíos")
    void listaVaciaIgualQueElConversorNormal() {
        String real = exportar("Incidencias exportadas.", List.of());
        String esperado = json.writeValueAsString(ApiResponse.ok("Incidencias exportadas.", List.of()));

        assertThat(sinTimestamp(real)).isEqualTo(sinTimestamp(esperado));
        assertThat(real).contains("\"datos\":[]");
    }

    private String exportar(String mensaje, List<Ejemplo> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ExportacionJson.escribir(json, salida, mensaje, gen -> {
            for (Ejemplo fila : filas) {
                json.writeValue(gen, fila);
            }
        });
        return salida.toString(StandardCharsets.UTF_8);
    }

    /** El timestamp se genera en el momento; se neutraliza para comparar el resto. */
    private static String sinTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"X\"");
    }

    private record Ejemplo(Long id, String nombre) {
    }
}
