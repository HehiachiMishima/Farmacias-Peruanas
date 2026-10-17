package oracle.controllers;

import jakarta.validation.Valid;
import oracle.request.UsuarioPermisoOverrideRequest;
import oracle.response.ApiResponse;
import oracle.response.PermisoResponse;
import oracle.response.UsuarioPermisoResponse;
import oracle.services.UsuarioPermisoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/** Overrides grant/deny por usuario (la capa de mayor prioridad del RBAC). */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/permisos")
public class UsuarioPermisoController {

    private final UsuarioPermisoService usuarioPermisoService;

    public UsuarioPermisoController(UsuarioPermisoService usuarioPermisoService) {
        this.usuarioPermisoService = usuarioPermisoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VER_USUARIOS') and hasAuthority('VER_PERMISOS')")
    public Mono<ApiResponse<List<UsuarioPermisoResponse>>> listar(@PathVariable Long usuarioId) {
        return usuarioPermisoService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/denegados")
    @PreAuthorize("hasAuthority('VER_USUARIOS') and hasAuthority('VER_PERMISOS')")
    public Mono<ApiResponse<List<UsuarioPermisoResponse>>> denegados(@PathVariable Long usuarioId) {
        return usuarioPermisoService.listarDenegados(usuarioId);
    }

    @GetMapping("/con-override")
    @PreAuthorize("hasAuthority('VER_USUARIOS') and hasAuthority('VER_PERMISOS')")
    public Mono<ApiResponse<List<PermisoResponse>>> conOverride(@PathVariable Long usuarioId) {
        return usuarioPermisoService.permisosConOverride(usuarioId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ASIGNAR_PERMISOS')")
    public Mono<ApiResponse<UsuarioPermisoResponse>> otorgar(@PathVariable Long usuarioId,
            @Valid @RequestBody UsuarioPermisoOverrideRequest peticion) {
        return usuarioPermisoService.otorgar(usuarioId, peticion);
    }

    @DeleteMapping("/{permisoId}")
    @PreAuthorize("hasAuthority('ASIGNAR_PERMISOS')")
    public Mono<ApiResponse<Void>> eliminar(@PathVariable Long usuarioId, @PathVariable Long permisoId) {
        return usuarioPermisoService.eliminar(usuarioId, permisoId);
    }
}
