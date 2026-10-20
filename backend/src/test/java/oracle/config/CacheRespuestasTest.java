package oracle.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CacheRespuestasTest {

    private CacheRespuestas cache(boolean habilitado) {
        return new CacheRespuestas(habilitado, 60_000L, 100L);
    }

    @Test
    void calculaUnaVezYReutiliza() {
        AtomicInteger contador = new AtomicInteger();
        CacheRespuestas cache = cache(true);

        String primero = cache.obtener("k", () -> "v" + contador.incrementAndGet());
        String segundo = cache.obtener("k", () -> "v" + contador.incrementAndGet());

        assertThat(primero).isEqualTo("v1");
        assertThat(segundo).isEqualTo("v1");
        assertThat(contador.get()).isEqualTo(1);
    }

    @Test
    void invalidarTodoLimpiaLaCache() {
        AtomicInteger contador = new AtomicInteger();
        CacheRespuestas cache = cache(true);

        cache.obtener("k", () -> contador.incrementAndGet());
        cache.invalidarTodo();
        cache.obtener("k", () -> contador.incrementAndGet());

        assertThat(contador.get()).isEqualTo(2);
    }

    @Test
    void deshabilitadaCalculaSiempre() {
        AtomicInteger contador = new AtomicInteger();
        CacheRespuestas cache = cache(false);

        cache.obtener("k", () -> contador.incrementAndGet());
        cache.obtener("k", () -> contador.incrementAndGet());

        assertThat(contador.get()).isEqualTo(2);
    }
}
