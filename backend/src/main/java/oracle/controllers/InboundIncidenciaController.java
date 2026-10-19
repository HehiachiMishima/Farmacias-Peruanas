package oracle.controllers;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import oracle.request.FiltroIncidenciaInbound;
import oracle.request.IncidenciaInboundActualizarRequest;
import oracle.request.IncidenciaInboundCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.EstadisticaInboundResponse;
import oracle.response.IncidenciaInboundResponse;
import oracle.response.PaginaResponse;
import oracle.response.ProveedorMensualEstadistica;
import oracle.services.IncidenciaInboundService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/inbound/incidencias")
public class InboundIncidenciaController {
  private final IncidenciaInboundService service;

  public InboundIncidenciaController(IncidenciaInboundService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('VER_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<PaginaResponse<IncidenciaInboundResponse>>> listar(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto,
      @RequestParam(defaultValue = "1") int pagina,
      @RequestParam(defaultValue = "10") int tamano) {
    return service.listar(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto), pagina, tamano);
  }

  @GetMapping("/exportar")
  @PreAuthorize("hasAuthority('VER_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<List<IncidenciaInboundResponse>>> exportar(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto) {
    return service.listarParaExportar(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto));
  }

  @GetMapping("/estadisticas")
  @PreAuthorize("hasAuthority('VER_DASHBOARD_INBOUND')")
  public Mono<ApiResponse<EstadisticaInboundResponse>> estadisticas(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto) {
    return service.estadisticas(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto));
  }

  @GetMapping("/estadisticas/proveedores-mensual")
  @PreAuthorize("hasAuthority('VER_DASHBOARD_INBOUND')")
  public Mono<ApiResponse<List<ProveedorMensualEstadistica>>> estadisticaProveedoresMensual(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto) {
    return service.estadisticaProveedoresMensual(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('VER_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<IncidenciaInboundResponse>> obtener(@PathVariable Long id) {
    return service.obtenerPorId(id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('CREAR_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<IncidenciaInboundResponse>> crear(@Valid @RequestBody IncidenciaInboundCrearRequest req) {
    return service.crear(req);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('MODIFICAR_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<IncidenciaInboundResponse>> actualizar(@PathVariable Long id,
      @Valid @RequestBody IncidenciaInboundActualizarRequest req) {
    return service.actualizar(id, req);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('ELIMINAR_INCIDENCIAS_INBOUND')")
  public Mono<ApiResponse<Void>> eliminar(@PathVariable Long id) {
    return service.eliminar(id);
  }
}
