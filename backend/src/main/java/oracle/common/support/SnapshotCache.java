package oracle.common.support;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snapshot inmutable en memoria con recarga perezosa.
 *
 * Las lecturas son lock-free (AtomicReference). Tras un CRUD se llama a
 * {@link #invalidar()} y la siguiente lectura recarga una sola vez (double
 * check), de modo que nunca se sirven nombres desactualizados y no hay
 * thundering herd. Si una recarga programada falla se conserva el anterior.
 */
public final class SnapshotCache<T> {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);

    private final String nombre;
    private final Supplier<T> cargador;
    private final AtomicReference<T> ref = new AtomicReference<>();

    public SnapshotCache(String nombre, Supplier<T> cargador) {
        this.nombre = nombre;
        this.cargador = cargador;
    }

    public T obtener() {
        T actual = ref.get();
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            actual = ref.get();
            if (actual == null) {
                actual = cargador.get();
                ref.set(actual);
            }
            return actual;
        }
    }

    /** Descarta el snapshot: la siguiente lectura lo recarga. */
    public void invalidar() {
        ref.set(null);
    }

    /** Recarga en segundo plano conservando el snapshot previo si falla. */
    public void refrescar() {
        try {
            ref.set(cargador.get());
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar la cache '{}'; se conserva el snapshot previo.", nombre, ex);
        }
    }
}
