package oracle.common.support;

import java.io.OutputStream;
import java.time.LocalDateTime;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe un export en flujo JSON con exactamente el mismo envoltorio que
 * {@code ApiResponse&lt;List&lt;T&gt;&gt;} (exito, mensaje, datos, timestamp),
 * de modo que el contrato con el frontend no cambia.
 *
 * La lista de datos nunca se materializa: los elementos se escriben uno a uno
 * dentro del array {@code datos}, por lo que el pico de memoria de la JVM
 * depende del tamaño del lote, no del total de filas.
 */
public final class ExportacionJson {

    @FunctionalInterface
    public interface Elementos {
        /** Escribe los elementos de {@code datos}; cada llamada emite un objeto del array. */
        void escribir(JsonGenerator gen);
    }

    public static void escribir(ObjectMapper json, OutputStream salida, String mensaje, Elementos datos) {
        try (JsonGenerator gen = json.createGenerator(salida)) {
            gen.writeStartObject();
            gen.writeBooleanProperty("exito", true);
            gen.writeStringProperty("mensaje", mensaje);
            gen.writeArrayPropertyStart("datos");
            datos.escribir(gen);
            gen.writeEndArray();
            gen.writeName("timestamp");
            // Mismo serializador que usa el conversor HTTP para no romper el formato.
            gen.writeRawValue(json.writeValueAsString(LocalDateTime.now()));
            gen.writeEndObject();
        }
    }

    private ExportacionJson() {
    }
}
