package oracle.common.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite de solicitudes por clave (p. ej. usuario + tipo de operacion) con
 * ventana deslizante en memoria. Suficiente para un nodo; con varios, cada uno
 * cuenta lo suyo.
 */
@Component
public class LimiteSolicitudes {

    private static final int MAX_CLAVES = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> solicitudes = new ConcurrentHashMap<>();

    /**
     * Registra una solicitud si cabe en la ventana. Devuelve 0 si se admitio o,
     * si no, los milisegundos hasta que se libere un lugar.
     */
    public long consumir(String clave, int maximo, long ventanaMs) {
        long ahora = System.currentTimeMillis();
        Deque<Long> cola = solicitudes.computeIfAbsent(clave, k -> new ArrayDeque<>());
        long espera;
        synchronized (cola) {
            while (!cola.isEmpty() && cola.peekFirst() <= ahora - ventanaMs) {
                cola.pollFirst();
            }
            if (cola.size() >= maximo) {
                espera = cola.peekFirst() + ventanaMs - ahora;
            } else {
                cola.addLast(ahora);
                espera = 0;
            }
        }
        if (solicitudes.size() > MAX_CLAVES) {
            podarVencidas(ahora, ventanaMs);
        }
        return espera;
    }

    /** Descarta las claves sin solicitudes en la ventana (acota la memoria). */
    private void podarVencidas(long ahora, long ventanaMs) {
        solicitudes.entrySet().removeIf(entrada -> {
            Deque<Long> cola = entrada.getValue();
            synchronized (cola) {
                return cola.isEmpty() || cola.peekLast() <= ahora - ventanaMs;
            }
        });
    }
}
