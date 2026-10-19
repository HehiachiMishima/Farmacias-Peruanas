package oracle.controllers;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import oracle.request.FiltroIncidencia;
import oracle.request.IncidenciaActualizarRequest;
import oracle.request.IncidenciaCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.EstadisticaResponse;
import oracle.response.IncidenciaResponse;
import oracle.response.PaginaResponse;
import oracle.services.IncidenciaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** CRUD de incidencias (bitácora), con listado paginado y filtrado en el servidor. */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;

    public IncidenciaController(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VER_INCIDENCIAS')")
    public Mono<ApiResponse<PaginaResponse<IncidenciaResponse>>> listar(
            @RequestParam(required = false) Long centro,
            @RequestParam(required = false) Long sistema,
            @RequestParam(required = false) Long area,
            @RequestParam(required = false) Long responsable,
            @RequestParam(required = false) Long tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return incidenciaService.listar(
                new FiltroIncidencia(centro, sistema, area, responsable, tipo, desde, hasta, texto),
                pagina, tamano);
    }

    @GetMapping("/exportar")
    @PreAuthorize("hasAuthority('VER_INCIDENCIAS')")
    public Mono<ApiResponse<List<IncidenciaResponse>>> exportar(
            @RequestParam(required = false) Long centro,
            @RequestParam(required = false) Long sistema,
            @RequestParam(required = false) Long area,
            @RequestParam(required = false) Long responsable,
            @RequestParam(required = false) Long tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String texto) {
        return incidenciaService.listarParaExportar(
                new FiltroIncidencia(centro, sistema, area, responsable, tipo, desde, hasta, texto));
    }

    @GetMapping("/estadisticas")
    @PreAuthorize("hasAuthority('VER_DASHBOARD')")
    public Mono<ApiResponse<EstadisticaResponse>> estadisticas(
            @RequestParam(required = false) Long centro,
            @RequestParam(required = false) Long sistema,
            @RequestParam(required = false) Long area,
            @RequestParam(required = false) Long responsable,
            @RequestParam(required = false) Long tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String texto) {
        return incidenciaService.estadisticas(
                new FiltroIncidencia(centro, sistema, area, responsable, tipo, desde, hasta, texto));
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
