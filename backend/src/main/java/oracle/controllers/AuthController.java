package oracle.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import oracle.request.LoginRequest;
import oracle.response.ApiResponse;
import oracle.response.AuthResponse;
import oracle.security.LoginRateLimiter;
import oracle.services.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Acceso: unico endpoint publico de la API. */
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
}
