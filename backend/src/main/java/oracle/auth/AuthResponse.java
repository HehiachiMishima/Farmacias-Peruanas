package oracle.auth;

import oracle.admin.response.UsuarioResponse;

import java.time.Instant;
import java.util.List;

/** Resultado del login: token Bearer + identidad + permisos resueltos. */
public record AuthResponse(
        String token,
        String tipo,
        Instant expiraEn,
        UsuarioResponse usuario,
        List<String> permisos) {
}
