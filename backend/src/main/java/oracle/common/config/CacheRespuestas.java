package oracle.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cache en memoria (Caffeine) para respuestas de solo lectura costosas, como
 * los agregados del dashboard (varias agregaciones full-table por request).
 *
 * No cachea datos por usuario: la clave la construye quien consulta. Se
 * invalida por completo cuando cambian las incidencias (invalidarTodo), de modo
 * que el dashboard nunca queda desactualizado mas alla del TTL.
 */
@Component
public class CacheRespuestas {

    private final boolean habilitado;
    private final Cache<String, Object> cache;

    public CacheRespuestas(
            @Value("${app.stats-cache.enabled:true}") boolean habilitado,
            @Value("${app.stats-cache.ttl-ms:60000}") long ttlMs,
            @Value("${app.stats-cache.max-size:500}") long maxSize) {
        this.habilitado = habilitado;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .build();
    }

    /** Devuelve el valor cacheado o lo calcula con {@code calculo} y lo guarda. */
    @SuppressWarnings("unchecked")
    public <T> T obtener(String clave, Supplier<T> calculo) {
        if (!habilitado) {
            return calculo.get();
        }
        return (T) cache.get(clave, k -> calculo.get());
    }

    public void invalidarTodo() {
        cache.invalidateAll();
    }
}
