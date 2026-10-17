package oracle.services;

import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Helper de trazabilidad: envuelve una operacion de service, registra su
 * ejecucion y la duracion en milisegundos, y la propaga si falla.
 */
final class Trazas {

    static <T> T medir(Logger log, String operacion, Supplier<T> bloque) {
        long inicio = System.nanoTime();
        try {
            T resultado = bloque.get();
            log.info("{} -> OK ({} ms)", operacion, milisegundos(inicio));
            return resultado;
        } catch (RuntimeException ex) {
            log.warn("{} -> ERROR ({} ms): {}", operacion, milisegundos(inicio), ex.getMessage());
            throw ex;
        }
    }

    private static long milisegundos(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000L;
    }

    private Trazas() {
    }
}
