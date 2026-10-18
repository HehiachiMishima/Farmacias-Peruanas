package oracle.controllers;

import jakarta.validation.Valid;
import java.util.List;
import oracle.request.IncidenciaActualizarRequest;
import oracle.request.IncidenciaCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.IncidenciaResponse;
import oracle.services.IncidenciaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** CRUD de incidencias (bitácora). */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;

    public IncidenciaController(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VER_INCIDENCIAS')")
    public Mono<ApiResponse<List<IncidenciaResponse>>> listar() {
        return incidenciaService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VER_INCIDENCIAS')")
    public Mono<ApiResponse<IncidenciaResponse>> obtener(@PathVariable Long id) {
        return incidenciaService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREAR_INCIDENCIAS')")
    public Mono<ApiResponse<IncidenciaResponse>> crear(@Valid @RequestBody IncidenciaCrearRequest peticion) {
        return incidenciaService.crear(peticion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MODIFICAR_INCIDENCIAS')")
    public Mono<ApiResponse<IncidenciaResponse>> actualizar(@PathVariable Long id,
            @Valid @RequestBody IncidenciaActualizarRequest peticion) {
        return incidenciaService.actualizar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ELIMINAR_INCIDENCIAS')")
    public Mono<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        return incidenciaService.eliminar(id);
    }
}
