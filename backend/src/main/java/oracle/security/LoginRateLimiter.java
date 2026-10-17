package oracle.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import oracle.exception.LimiteExcedidoException;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de login por IP (ventana deslizante en memoria).
 * Frena fuerza bruta sin acoplar a Redis; suficiente para un nodo.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 10;
    private static final long VENTANA_MS = 60_000L;
    private static final int MAX_CLAVES = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> intentos = new ConcurrentHashMap<>();

    /** Lanza 429 si la clave ya supero el limite en la ventana actual. */
    public void verificar(String clave) {
        Deque<Long> cola = intentos.get(clave);
        if (cola == null) {
            return;
        }
        synchronized (cola) {
            podar(cola);
            if (cola.size() >= MAX_INTENTOS) {
                throw new LimiteExcedidoException("Demasiados intentos de acceso. Intente de nuevo en un minuto.");
            }
        }
    }

    /** Registra un intento fallido. */
    public void registrarFallo(String clave) {
        Deque<Long> cola = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            podar(cola);
            cola.addLast(System.currentTimeMillis());
        }
        acotarTamano();
    }

    /**
     * Cota el numero de claves sin borrar TODOS los contadores (un clear() global
     * regalaria intentos nuevos al atacante). Primero descarta las colas ya
     * vencidas; si aun sobran, elimina las sobrantes de forma acotada.
     */
    private void acotarTamano() {
        if (intentos.size() <= MAX_CLAVES) {
            return;
        }
        long limite = System.currentTimeMillis() - VENTANA_MS;
        var it = intentos.entrySet().iterator();
        while (it.hasNext() && intentos.size() > MAX_CLAVES) {
            Deque<Long> cola = it.next().getValue();
            synchronized (cola) {
                while (!cola.isEmpty() && cola.peekFirst() < limite) {
                    cola.pollFirst();
                }
                if (cola.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    /** Limpia los intentos tras un login exitoso. */
    public void limpiar(String clave) {
        intentos.remove(clave);
    }

    private void podar(Deque<Long> cola) {
        long limite = System.currentTimeMillis() - VENTANA_MS;
        while (!cola.isEmpty() && cola.peekFirst() < limite) {
            cola.pollFirst();
        }
    }
}
