package oracle.common.support;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
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

    /** Filas por consulta al exportar en flujo: acota la memoria de la JVM. */
    public static final int TAMANO_LOTE = 1_000;

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

    /**
     * Export completo de un filtro como cuerpo en flujo: lee lotes ordenados de
     * {@value #TAMANO_LOTE} filas con {@code lector} y escribe cada fila ya
     * convertida, sin cargar el total en memoria.
     *
     * El {@code conversor} se obtiene al empezar a escribir (p. ej. con los
     * nombres de catalogo vigentes en ese momento). Si el export falla a mitad
     * (cliente que corta, BD) la respuesta ya esta en curso y no puede
     * convertirse en ApiError, asi que solo se registra en {@code log}.
     */
    public static <E> StreamingResponseBody enLotes(ObjectMapper json, String mensaje, Sort orden,
            Function<Pageable, Page<E>> lector, Supplier<Function<E, ?>> conversor,
            Logger log, String descripcion) {
        return salida -> {
            try {
                escribir(json, salida, mensaje, gen -> {
                    Function<E, ?> convertir = conversor.get();
                    int pagina = 0;
                    while (true) {
                        Page<E> lote = lector.apply(PageRequest.of(pagina, TAMANO_LOTE, orden));
                        for (E fila : lote.getContent()) {
                            json.writeValue(gen, convertir.apply(fila));
                        }
                        if (lote.getContent().size() < TAMANO_LOTE) {
                            break;
                        }
                        pagina++;
                    }
                });
            } catch (Exception e) {
                log.error("Exportación de {} interrumpida: {}", descripcion, e.getMessage(), e);
            }
        };
    }

    private ExportacionJson() {
    }
}
