package oracle.auth;

import oracle.common.support.ActorActual;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import oracle.common.response.ApiResponse;
import oracle.common.security.LoginRateLimiter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Acceso: login (publico) y consulta de la sesion vigente. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public Mono<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest peticion,
            HttpServletRequest http) {
        // Doble cubo: por IP (frena la rotacion de usuarios) y por IP+usuario
        // (evita que un atacante bloquee el login de todos desde una misma IP).
        String ip = http.getRemoteAddr();
        String claveUsuario = LoginRateLimiter.claveUsuario(ip, peticion.usuario());
        String claveIp = LoginRateLimiter.claveIp(ip);
        rateLimiter.verificar(claveUsuario);
        rateLimiter.verificarPorIp(ip);
        return authService.login(peticion, claveUsuario, claveIp);
    }

    /** Sesion vigente (usuario + permisos actuales) del portador del token. */
    @GetMapping("/sesion")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<SesionResponse>> sesion() {
        return authService.sesionActual(ActorActual.id());
    }

    /** Cambio de la propia contrasena: exige la actual como confirmacion. */
    @PutMapping("/contrasena")
    @PreAuthorize("isAuthenticated()")
    public Mono<ApiResponse<Void>> cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest peticion) {
        return authService.cambiarContrasena(ActorActual.id(), peticion);
    }
}
