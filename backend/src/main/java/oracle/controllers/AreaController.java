package oracle.controllers;

import jakarta.validation.Valid;
import oracle.request.AreaActualizarRequest;
import oracle.request.AreaCrearRequest;
import oracle.request.ResponsableActualizarRequest;
import oracle.request.ResponsableCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.AreaResponse;
import oracle.response.ResponsableResponse;
import oracle.services.AreaService;
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

import java.util.List;

/** CRUD de areas + responsables (hijos 1:N). */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VER_CONTROL')")
    public Mono<ApiResponse<List<AreaResponse>>> listar() {
        return areaService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VER_CONTROL')")
    public Mono<ApiResponse<AreaResponse>> obtener(@PathVariable Long id) {
        return areaService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREAR_CONTROL')")
    public Mono<ApiResponse<AreaResponse>> crear(@Valid @RequestBody AreaCrearRequest peticion) {
        return areaService.crear(peticion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MODIFICAR_CONTROL')")
    public Mono<ApiResponse<AreaResponse>> actualizar(@PathVariable Long id,
            @Valid @RequestBody AreaActualizarRequest peticion) {
        return areaService.actualizar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ELIMINAR_CONTROL')")
    public Mono<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        return areaService.eliminar(id);
    }

    @GetMapping("/{id}/responsables")
    @PreAuthorize("hasAuthority('VER_CONTROL')")
    public Mono<ApiResponse<List<ResponsableResponse>>> responsablesDe(@PathVariable Long id) {
        return areaService.responsablesDe(id);
    }

    @PostMapping("/{id}/responsables")
    @PreAuthorize("hasAuthority('CREAR_CONTROL')")
    public Mono<ApiResponse<ResponsableResponse>> crearResponsable(@PathVariable Long id,
            @Valid @RequestBody ResponsableCrearRequest peticion) {
        return areaService.crearResponsable(id, peticion);
    }

    @PutMapping("/{areaId}/responsables/{responsableId}")
    @PreAuthorize("hasAuthority('MODIFICAR_CONTROL')")
    public Mono<ApiResponse<ResponsableResponse>> actualizarResponsable(@PathVariable Long areaId,
            @PathVariable Long responsableId, @Valid @RequestBody ResponsableActualizarRequest peticion) {
        return areaService.actualizarResponsable(areaId, responsableId, peticion);
    }

    @DeleteMapping("/{areaId}/responsables/{responsableId}")
    @PreAuthorize("hasAuthority('ELIMINAR_CONTROL')")
    public Mono<ApiResponse<Void>> eliminarResponsable(@PathVariable Long areaId, @PathVariable Long responsableId) {
        return areaService.eliminarResponsable(areaId, responsableId);
    }
}
