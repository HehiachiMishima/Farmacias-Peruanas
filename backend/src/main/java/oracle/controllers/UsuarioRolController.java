package oracle.controllers;

import jakarta.validation.Valid;
import oracle.request.UsuarioRolAsignarRequest;
import oracle.response.ApiResponse;
import oracle.response.RolResponse;
import oracle.response.UsuarioResponse;
import oracle.response.UsuarioRolResponse;
import oracle.services.UsuarioRolService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/** Asignacion de roles a usuarios y consulta inversa. */
@RestController
@RequestMapping("/api")
public class UsuarioRolController {

    private final UsuarioRolService usuarioRolService;

    public UsuarioRolController(UsuarioRolService usuarioRolService) {
        this.usuarioRolService = usuarioRolService;
    }

    @GetMapping("/usuarios/{usuarioId}/roles")
    @PreAuthorize("hasAuthority('VER_USUARIOS') and hasAuthority('VER_ROLES')")
    public Mono<ApiResponse<List<RolResponse>>> rolesDeUsuario(@PathVariable Long usuarioId) {
        return usuarioRolService.listarPorUsuario(usuarioId);
    }

    @PostMapping("/usuarios/{usuarioId}/roles")
    @PreAuthorize("hasAuthority('ASIGNAR_ROLES')")
    public Mono<ApiResponse<UsuarioRolResponse>> asignar(@PathVariable Long usuarioId,
            @Valid @RequestBody UsuarioRolAsignarRequest peticion) {
        return usuarioRolService.asignar(usuarioId, peticion);
    }

    @DeleteMapping("/usuarios/{usuarioId}/roles/{rolId}")
    @PreAuthorize("hasAuthority('ASIGNAR_ROLES')")
    public Mono<ApiResponse<Void>> quitar(@PathVariable Long usuarioId, @PathVariable Long rolId) {
        return usuarioRolService.quitar(usuarioId, rolId);
    }

    @GetMapping("/roles/{rolId}/usuarios")
    @PreAuthorize("hasAuthority('VER_ROLES') and hasAuthority('VER_USUARIOS')")
    public Mono<ApiResponse<List<UsuarioResponse>>> usuariosDeRol(@PathVariable Long rolId) {
        return usuarioRolService.listarPorRol(rolId);
    }
}
