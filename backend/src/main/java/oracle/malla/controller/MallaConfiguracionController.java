package oracle.malla.controller;

import jakarta.validation.Valid;
import java.util.List;
import oracle.common.response.ApiResponse;
import oracle.malla.request.MallaReglaActualizarRequest;
import oracle.malla.response.EvaluacionMallaResponse;
import oracle.malla.response.ReglaMallaResponse;
import oracle.malla.service.MallaConfiguracionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Módulo Configuración de la Malla Operativa: catálogo de reglas, evaluación
 * vigente y ajuste de parámetros. Reutiliza los permisos del panel
 * ({@code VER_MALLA} y {@code MODIFICAR_MALLA}); no crea permisos nuevos.
 */
@RestController
@RequestMapping("/api/malla/configuracion")
public class MallaConfiguracionController {

    private final MallaConfiguracionService service;

    public MallaConfiguracionController(MallaConfiguracionService service) {
        this.service = service;
    }

    @GetMapping("/reglas")
    @PreAuthorize("hasAuthority('VER_MALLA')")
    public Mono<ApiResponse<List<ReglaMallaResponse>>> reglas() {
        return service.listarReglas();
    }

    @GetMapping("/evaluacion")
    @PreAuthorize("hasAuthority('VER_MALLA')")
    public Mono<ApiResponse<EvaluacionMallaResponse>> evaluacion() {
        return service.evaluar();
    }

    @PutMapping("/reglas/{id}")
    @PreAuthorize("hasAuthority('MODIFICAR_MALLA')")
    public Mono<ApiResponse<ReglaMallaResponse>> actualizar(@PathVariable Long id,
            @Valid @RequestBody MallaReglaActualizarRequest req) {
        return service.actualizar(id, req);
    }
}
