package oracle.common.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import oracle.common.exception.LimiteExcedidoException;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de login por IP (ventana deslizante en memoria).
 * Frena fuerza bruta sin acoplar a Redis; suficiente para un nodo.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 10;
    private static final int MAX_INTENTOS_POR_IP = 30;
    private static final long VENTANA_MS = 60_000L;
    private static final int MAX_CLAVES = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> intentos = new ConcurrentHashMap<>();

    /** Limite por defecto (por IP+usuario). */
    public void verificar(String clave) {
        verificar(clave, MAX_INTENTOS);
    }

    /** Lanza 429 si la clave ya alcanzo {@code maxIntentos} en la ventana actual. */
    public void verificar(String clave, int maxIntentos) {
        Deque<Long> cola = intentos.get(clave);
        if (cola == null) {
            return;
        }
        synchronized (cola) {
            podar(cola);
            if (cola.size() >= maxIntentos) {
                throw new LimiteExcedidoException("Demasiados intentos de acceso. Intente de nuevo en un minuto.");
            }
        }
    }

    /** Limite por IP (mas holgado que el de IP+usuario para no bloquear NAT compartidos). */
    public void verificarPorIp(String ip) {
        verificar(claveIp(ip), MAX_INTENTOS_POR_IP);
    }

    /** Clave de cubo por usuario (IP + usuario, para no bloquear a todos desde una IP). */
    public static String claveUsuario(String ip, String usuario) {
        return ip + "|" + usuario.trim().toLowerCase();
    }

    /** Clave de cubo por IP. */
    public static String claveIp(String ip) {
        return "ip:" + ip;
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
