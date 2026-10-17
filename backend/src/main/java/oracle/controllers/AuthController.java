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
        // Clave compuesta IP + usuario: aun si el proxy colapsa la IP (mismo
        // valor para todos), el cubo queda acotado por usuario, de modo que un
        // atacante no puede bloquear el login de todo el mundo a la vez.
        String clave = http.getRemoteAddr() + "|" + peticion.usuario().trim().toLowerCase();
        rateLimiter.verificar(clave);
        return authService.login(peticion, clave);
    }
}
