package oracle.controllers;

import jakarta.validation.Valid;
import java.util.List;
import oracle.request.IncidenciaInboundTipoActualizarRequest;
import oracle.request.IncidenciaInboundTipoCrearRequest;
import oracle.request.ProveedorActualizarRequest;
import oracle.request.ProveedorCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.DataIncidenciaInboundResponse;
import oracle.response.DataProveedorResponse;
import oracle.response.PaginaResponse;
import oracle.services.IncidenciaInboundTipoService;
import oracle.services.ProveedorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/inbound/control")
public class InboundControlController {
  private final ProveedorService proveedorService;
  private final IncidenciaInboundTipoService tipoService;

  public InboundControlController(ProveedorService proveedorService, IncidenciaInboundTipoService tipoService) {
    this.proveedorService = proveedorService;
    this.tipoService = tipoService;
  }

  @GetMapping("/proveedores")
  @PreAuthorize("hasAuthority('VER_CONTROL_INBOUND')")
  public Mono<ApiResponse<List<DataProveedorResponse>>> listarProveedoresTodos() {
    return proveedorService.listarTodos();
  }

  @GetMapping("/proveedores/paginado")
  @PreAuthorize("hasAuthority('VER_CONTROL_INBOUND')")
  public Mono<ApiResponse<PaginaResponse<DataProveedorResponse>>> listarProveedoresPaginado(
      @RequestParam(defaultValue = "1") int pagina, @RequestParam(defaultValue = "20") int tamano,
      @RequestParam(required = false) String texto) {
    return proveedorService.listarPaginado(pagina, tamano, texto);
  }

  @PostMapping("/proveedores")
  @PreAuthorize("hasAuthority('CREAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<DataProveedorResponse>> crearProveedor(@Valid @RequestBody ProveedorCrearRequest req) {
    return proveedorService.crear(req);
  }

  @PutMapping("/proveedores/{id}")
  @PreAuthorize("hasAuthority('MODIFICAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<DataProveedorResponse>> actualizarProveedor(@PathVariable Long id,
      @Valid @RequestBody ProveedorActualizarRequest req) {
    return proveedorService.actualizar(id, req);
  }

  @DeleteMapping("/proveedores/{id}")
  @PreAuthorize("hasAuthority('ELIMINAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<Void>> eliminarProveedor(@PathVariable Long id) {
    return proveedorService.eliminar(id);
  }

  @GetMapping("/incidencias")
  @PreAuthorize("hasAuthority('VER_CONTROL_INBOUND')")
  public Mono<ApiResponse<List<DataIncidenciaInboundResponse>>> listarTiposTodos() {
    return tipoService.listarTodos();
  }

  @GetMapping("/incidencias/paginado")
  @PreAuthorize("hasAuthority('VER_CONTROL_INBOUND')")
  public Mono<ApiResponse<PaginaResponse<DataIncidenciaInboundResponse>>> listarTiposPaginado(
      @RequestParam(defaultValue = "1") int pagina, @RequestParam(defaultValue = "20") int tamano,
      @RequestParam(required = false) String texto) {
    return tipoService.listarPaginado(pagina, tamano, texto);
  }

  @PostMapping("/incidencias")
  @PreAuthorize("hasAuthority('CREAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<DataIncidenciaInboundResponse>> crearTipo(@Valid @RequestBody IncidenciaInboundTipoCrearRequest req) {
    return tipoService.crear(req);
  }

  @PutMapping("/incidencias/{id}")
  @PreAuthorize("hasAuthority('MODIFICAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<DataIncidenciaInboundResponse>> actualizarTipo(@PathVariable Long id,
      @Valid @RequestBody IncidenciaInboundTipoActualizarRequest req) {
    return tipoService.actualizar(id, req);
  }

  @DeleteMapping("/incidencias/{id}")
  @PreAuthorize("hasAuthority('ELIMINAR_CONTROL_INBOUND')")
  public Mono<ApiResponse<Void>> eliminarTipo(@PathVariable Long id) {
    return tipoService.eliminar(id);
  }
}
