package oracle.services;

import java.util.List;
import java.util.Optional;
import oracle.exception.NoAutorizadoException;
import oracle.jwt.JwtService;
import oracle.model.Usuario;
import oracle.repository.ResolucionRepository;
import oracle.repository.UsuarioRepository;
import oracle.request.LoginRequest;
import oracle.response.ApiResponse;
import oracle.response.AuthResponse;
import oracle.response.UsuarioResponse;
import oracle.security.LoginRateLimiter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Hash descartable contra el que se compara cuando el usuario no existe,
     * para que el coste de BCrypt no delate la existencia de la cuenta.
     */
    private final String hashFicticio = passwordEncoder.encode("oracle-hash-ficticio");

    public AuthService(
            UsuarioRepository usuarioRepository,
            ResolucionRepository resolucionRepository,
            JwtService jwtService,
            LoginRateLimiter rateLimiter) {
        this.usuarioRepository = usuarioRepository;
        this.resolucionRepository = resolucionRepository;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    public Mono<ApiResponse<AuthResponse>> login(LoginRequest peticion, String clave) {
        return Mono.fromCallable(() -> Trazas.medir(log, "AuthService.login usuario=" + peticion.usuario(),
                        () -> autenticar(peticion)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(respuesta -> {
                    rateLimiter.limpiar(clave);
                    return ApiResponse.ok("Autenticacion exitosa.", respuesta);
                })
                .onErrorResume(NoAutorizadoException.class, ex -> {
                    rateLimiter.registrarFallo(clave);
                    return Mono.error(ex);
                });
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
