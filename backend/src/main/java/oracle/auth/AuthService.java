package oracle.auth;

import oracle.common.security.AutorizacionCache;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Trazas;
import oracle.common.config.Transacciones;

import java.util.List;
import java.util.Optional;
import oracle.common.exception.NoAutorizadoException;
import oracle.common.exception.OperacionNoPermitidaException;
import oracle.common.security.JwtService;
import oracle.admin.model.Usuario;
import oracle.admin.repository.ResolucionRepository;
import oracle.admin.repository.UsuarioRepository;
import oracle.common.response.ApiResponse;
import oracle.admin.response.UsuarioResponse;
import oracle.common.security.LoginRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Autenticacion: valida credenciales, respeta el kill switch y emite JWT. */
@Slf4j
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ResolucionRepository resolucionRepository;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final AutorizacionCache autorizacionCache;
    private final Transacciones transacciones;

    /**
     * Hash descartable contra el que se compara cuando el usuario no existe,
     * para que el coste de BCrypt no delate la existencia de la cuenta.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioRepository usuarioRepository,
            ResolucionRepository resolucionRepository,
            JwtService jwtService,
            LoginRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            AutorizacionCache autorizacionCache,
            Transacciones transacciones) {
        this.autorizacionCache = autorizacionCache;
        this.usuarioRepository = usuarioRepository;
        this.resolucionRepository = resolucionRepository;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.transacciones = transacciones;
        this.hashFicticio = passwordEncoder.encode("oracle-hash-ficticio");
    }

    public Mono<ApiResponse<AuthResponse>> login(LoginRequest peticion, String claveUsuario, String claveIp) {
        return Mono.fromCallable(() -> Trazas.medir(log, "AuthService.login usuario=" + peticion.usuario(),
                        () -> autenticar(peticion)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(respuesta -> {
                    rateLimiter.limpiar(claveUsuario);
                    rateLimiter.limpiar(claveIp);
                    return ApiResponse.ok("Autenticacion exitosa.", respuesta);
                })
                .onErrorResume(NoAutorizadoException.class, ex -> {
                    rateLimiter.registrarFallo(claveUsuario);
                    rateLimiter.registrarFallo(claveIp);
                    return Mono.error(ex);
                });
    }

    /**
     * Sesion vigente del usuario autenticado. Usa la misma cache de autoridades
     * que el filtro JWT, asi que no agrega consultas en el caso comun y refleja
     * al instante los cambios de rol/permiso (que invalidan esa cache).
     */
    public Mono<ApiResponse<SesionResponse>> sesionActual(Long usuarioId) {
        return Mono.fromCallable(() -> {
                    Usuario usuario = usuarioRepository.findById(usuarioId)
                            .orElseThrow(() -> new NoAutorizadoException("La sesion ya no es valida."));
                    if (!Boolean.TRUE.equals(usuario.getActivo())) {
                        throw new NoAutorizadoException("La cuenta esta desactivada.");
                    }
                    List<String> permisos = autorizacionCache.cargar(usuarioId).autoridades().stream()
                            .map(a -> a.getAuthority())
                            .toList();
                    return ApiResponse.ok("Sesion vigente.", new SesionResponse(UsuarioResponse.of(usuario), permisos));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Cambio de la propia contrasena. Exige la contrasena actual como
     * confirmacion de identidad, valida que la nueva sea distinta y guarda el
     * nuevo hash. No invalida los JWT vigentes (el token no caduca al cambiar
     * la clave), pero si la cuenta esta desactivada se rechaza.
     */
    public Mono<ApiResponse<Void>> cambiarContrasena(Long usuarioId, CambiarContrasenaRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log,
                        "AuthService.cambiarContrasena usuarioId=" + usuarioId,
                        () -> transacciones.ejecutar(() -> {
                            Usuario usuario = usuarioRepository.findById(usuarioId)
                                    .orElseThrow(() -> new NoAutorizadoException("La sesion ya no es valida."));
                            if (!Boolean.TRUE.equals(usuario.getActivo())) {
                                throw new NoAutorizadoException("La cuenta esta desactivada.");
                            }
                            if (!passwordEncoder.matches(peticion.contrasenaActual(), usuario.getContrasena())) {
                                throw new NoAutorizadoException("La contrasena actual no es correcta.");
                            }
                            if (passwordEncoder.matches(peticion.contrasenaNueva(), usuario.getContrasena())) {
                                throw new OperacionNoPermitidaException(
                                        "La contrasena nueva debe ser distinta a la actual.");
                            }
                            usuario.setContrasena(passwordEncoder.encode(peticion.contrasenaNueva()));
                            Auditoria.alActualizar(usuario, actor);
                            usuarioRepository.save(usuario);
                            autorizacionCache.invalidar(usuarioId);
                            return null;
                        })))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Contrasena actualizada.", null)));
    }

    private AuthResponse autenticar(LoginRequest peticion) {
        String credencial = peticion.usuario().trim();
        Optional<Usuario> encontrado = usuarioRepository.findByUsuarioOrEmail(credencial, credencial);

        // Se ejecuta BCrypt SIEMPRE (contra un hash ficticio si el usuario no
        // existe) para no filtrar por tiempo la existencia de una cuenta.
        String hash = encontrado.map(Usuario::getContrasena).orElse(hashFicticio);
        boolean coincide = passwordEncoder.matches(peticion.contrasena(), hash);
        if (encontrado.isEmpty() || !coincide) {
            throw new NoAutorizadoException("Usuario o contrasena incorrectos.");
        }

        Usuario usuario = encontrado.get();
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new NoAutorizadoException("La cuenta esta desactivada.");
        }

        List<String> permisos = resolucionRepository.nombresPermitidos(usuario.getId());
        JwtService.TokenGenerado token = jwtService.generar(usuario);

        return new AuthResponse(token.valor(), "Bearer", token.expiraEn(), UsuarioResponse.of(usuario), permisos);
    }
}
