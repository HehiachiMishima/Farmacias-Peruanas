package oracle.common.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SnapshotCacheTest {

    @Test
    void cargaUnaSolaVezHastaInvalidar() {
        AtomicInteger cargas = new AtomicInteger();
        SnapshotCache<Integer> cache = new SnapshotCache<>("test", cargas::incrementAndGet);

        assertEquals(1, cache.obtener());
        assertEquals(1, cache.obtener());
        assertEquals(1, cargas.get());

        cache.invalidar();
        assertEquals(2, cache.obtener());
        assertEquals(2, cargas.get());
    }

    @Test
    void refrescoFallidoConservaElSnapshotPrevio() {
        AtomicInteger cargas = new AtomicInteger();
        SnapshotCache<Integer> cache = new SnapshotCache<>("test", () -> {
            if (cargas.incrementAndGet() > 1) {
                throw new IllegalStateException("bd caida");
            }
            return 42;
        });

        assertEquals(42, cache.obtener());
        cache.refrescar();
        assertEquals(42, cache.obtener());
    }
}
