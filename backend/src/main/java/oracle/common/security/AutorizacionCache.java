package oracle.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import oracle.admin.model.Usuario;
import oracle.admin.repository.ResolucionRepository;
import oracle.admin.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Cachea (Caffeine, TTL corto) las autoridades de un usuario para no ejecutar
 * las 2 consultas SQL (existencia/activo + vista v_permisos_finales) en CADA
 * peticion autenticada.
 *
 * Trade-off de seguridad: un cambio de rol/permiso o una desactivacion se
 * refleja al instante porque los servicios que los modifican llaman a
 * invalidar()/invalidarTodo(); el TTL es solo la red de seguridad. Se puede
 * desactivar con app.security.permission-cache.enabled=false (sin cache, se
 * resuelve por peticion como antes).
 */
@Component
public class AutorizacionCache {

    /** Autorizacion resuelta de un usuario. */
    public record Autorizacion(boolean activo, String usuario, List<SimpleGrantedAuthority> autoridades) {
    }

    private final UsuarioRepository usuarioRepository;
    private final ResolucionRepository resolucionRepository;
    private final boolean habilitado;
    private final Cache<Long, Autorizacion> cache;

    public AutorizacionCache(
            UsuarioRepository usuarioRepository,
            ResolucionRepository resolucionRepository,
            @Value("${app.security.permission-cache.enabled:true}") boolean habilitado,
            @Value("${app.security.permission-cache.ttl-ms:30000}") long ttlMs,
            @Value("${app.security.permission-cache.max-size:10000}") long maxSize) {
        this.usuarioRepository = usuarioRepository;
        this.resolucionRepository = resolucionRepository;
        this.habilitado = habilitado;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .build();
    }

    /** Resuelve la autorizacion del usuario (cacheada si esta habilitada). */
    public Autorizacion cargar(Long usuarioId) {
        if (!habilitado) {
            return consultar(usuarioId);
        }
        return cache.get(usuarioId, this::consultar);
    }

    /** Invalida la entrada de un usuario (tras cambiar sus roles/permisos/estado). */
    public void invalidar(Long usuarioId) {
        if (usuarioId != null) {
            cache.invalidate(usuarioId);
        }
    }

    /** Invalida todas las entradas (cambios de rol o de permisos globales). */
    public void invalidarTodo() {
        cache.invalidateAll();
    }

    private Autorizacion consultar(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuario inexistente."));
        boolean activo = Boolean.TRUE.equals(usuario.getActivo());
        List<SimpleGrantedAuthority> autoridades = resolucionRepository.nombresPermitidos(usuarioId).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new Autorizacion(activo, usuario.getUsuario(), autoridades);
    }
}
