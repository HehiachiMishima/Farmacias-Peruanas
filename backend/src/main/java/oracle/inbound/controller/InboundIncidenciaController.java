package oracle.inbound.controller;

import oracle.inbound.response.AnaliticaInboundResponse;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import oracle.inbound.request.FiltroIncidenciaInbound;
import oracle.inbound.request.IncidenciaInboundActualizarRequest;
import oracle.inbound.request.IncidenciaInboundCrearRequest;
import oracle.common.response.ApiResponse;
import oracle.inbound.response.EstadisticaInboundResponse;
import oracle.inbound.response.IncidenciaInboundResponse;
import oracle.common.response.PaginaResponse;
import oracle.inbound.response.ProveedorMensualEstadistica;
import oracle.inbound.service.IncidenciaInboundService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
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

  /**
   * Exporta en flujo (chunked) el JSON completo del filtro: el envoltorio es el
   * mismo que el de una respuesta normal, pero las filas se escriben por lotes
   * en vez de cargarse todas en memoria.
   */
  @GetMapping("/exportar")
  @PreAuthorize("hasAuthority('VER_INCIDENCIAS_INBOUND')")
  public ResponseEntity<StreamingResponseBody> exportar(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.exportarEnFlujo(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto)));
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

  /** Analitica del dashboard Inbound (en torno al proveedor) para el filtro. */
  @GetMapping("/analitica")
  @PreAuthorize("hasAuthority('VER_DASHBOARD_INBOUND')")
  public Mono<ApiResponse<AnaliticaInboundResponse>> analitica(
      @RequestParam(required = false) Long proveedor,
      @RequestParam(required = false) Long tipo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) String texto) {
    return service.analitica(new FiltroIncidenciaInbound(proveedor, tipo, desde, hasta, texto));
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
