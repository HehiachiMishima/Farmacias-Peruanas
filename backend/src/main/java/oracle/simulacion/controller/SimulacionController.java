package oracle.simulacion.controller;

import jakarta.validation.Valid;
import java.util.List;
import oracle.common.response.ApiResponse;
import oracle.common.response.PaginaResponse;
import oracle.simulacion.request.ConfiguracionActualizarRequest;
import oracle.simulacion.request.MoverRutaRequest;
import oracle.simulacion.request.RegistroActualizarRequest;
import oracle.simulacion.response.ArchivoSimulacionResponse;
import oracle.simulacion.response.BalanceoResponse;
import oracle.simulacion.response.ConfiguracionSimulacionResponse;
import oracle.simulacion.response.DashboardSimulacionResponse;
import oracle.simulacion.response.HistorialSimulacionResponse;
import oracle.simulacion.response.RutaSimulacionResponse;
import oracle.simulacion.service.SimulacionConfiguracionService;
import oracle.simulacion.service.SimulacionRegistroService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

/** Registro, configuración y dashboard del panel Simulación. */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

  private final SimulacionRegistroService registroService;
  private final SimulacionConfiguracionService configuracionService;

  public SimulacionController(
      SimulacionRegistroService registroService,
      SimulacionConfiguracionService configuracionService) {
    this.registroService = registroService;
    this.configuracionService = configuracionService;
  }

  @GetMapping("/registros")
  @PreAuthorize("hasAuthority('VER_REGISTRO_SIMULACION')")
  public Mono<ApiResponse<PaginaResponse<ArchivoSimulacionResponse>>> listar(
      @RequestParam(defaultValue = "1") int pagina,
      @RequestParam(defaultValue = "10") int tamano,
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) List<String> estados) {
    return registroService.listar(pagina, tamano, texto, estados);
  }

  @GetMapping("/registros/opciones")
  @PreAuthorize(
      "hasAnyAuthority('VER_REGISTRO_SIMULACION','VER_CONFIGURACION_SIMULACION','VER_DASHBOARD_SIMULACION')")
  public Mono<ApiResponse<List<ArchivoSimulacionResponse>>> opciones() {
    return registroService.opciones();
  }

  @PostMapping(value = "/registros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('CREAR_REGISTRO_SIMULACION')")
  public Mono<ApiResponse<ArchivoSimulacionResponse>> subir(
      @RequestPart("archivo") MultipartFile archivo,
      @RequestParam(required = false) String nombreRegistro,
      @RequestParam(required = false) String etiqueta,
      @RequestParam(required = false) String colorHex) {
    return registroService.subir(archivo, nombreRegistro, etiqueta, colorHex);
  }

  @PutMapping("/registros/{id}")
  @PreAuthorize("hasAuthority('MODIFICAR_REGISTRO_SIMULACION')")
  public Mono<ApiResponse<ArchivoSimulacionResponse>> actualizar(
      @PathVariable Long id, @Valid @RequestBody RegistroActualizarRequest req) {
    return registroService.actualizar(id, req);
  }

  @DeleteMapping("/registros/{id}")
  @PreAuthorize("hasAuthority('ELIMINAR_REGISTRO_SIMULACION')")
  public Mono<ApiResponse<Void>> eliminar(@PathVariable Long id) {
    return registroService.eliminar(id);
  }

  @GetMapping("/registros/{id}/configuracion")
  @PreAuthorize("hasAuthority('VER_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> configuracion(@PathVariable Long id) {
    return configuracionService.obtener(id);
  }

  @PutMapping("/registros/{id}/configuracion")
  @PreAuthorize("hasAuthority('MODIFICAR_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> guardarConfiguracion(
      @PathVariable Long id, @Valid @RequestBody ConfiguracionActualizarRequest req) {
    return configuracionService.guardar(id, req);
  }

  @GetMapping("/registros/{id}/rutas")
  @PreAuthorize("hasAuthority('VER_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<PaginaResponse<RutaSimulacionResponse>>> rutas(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") int pagina,
      @RequestParam(defaultValue = "25") int tamano,
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) List<Long> clusterIds,
      @RequestParam(required = false) List<Long> chuteIds) {
    return configuracionService.rutas(id, pagina, tamano, texto, clusterIds, chuteIds);
  }

  @PutMapping("/registros/{id}/rutas/{rutaId}/mover")
  @PreAuthorize("hasAuthority('MODIFICAR_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> moverRuta(
      @PathVariable Long id,
      @PathVariable Long rutaId,
      @Valid @RequestBody MoverRutaRequest req) {
    return configuracionService.mover(id, rutaId, req);
  }

  @PostMapping("/registros/{id}/balancear")
  @PreAuthorize("hasAuthority('MODIFICAR_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<BalanceoResponse>> balancear(@PathVariable Long id) {
    return configuracionService.balancear(id);
  }

  @PostMapping("/registros/{id}/restablecer")
  @PreAuthorize("hasAuthority('MODIFICAR_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<Void>> restablecer(@PathVariable Long id) {
    return configuracionService.restablecer(id);
  }

  @GetMapping("/registros/{id}/historial")
  @PreAuthorize("hasAuthority('VER_CONFIGURACION_SIMULACION')")
  public Mono<ApiResponse<List<HistorialSimulacionResponse>>> historial(@PathVariable Long id) {
    return configuracionService.historial(id);
  }

  @GetMapping("/registros/{id}/dashboard")
  @PreAuthorize("hasAuthority('VER_DASHBOARD_SIMULACION')")
  public Mono<ApiResponse<DashboardSimulacionResponse>> dashboard(@PathVariable Long id) {
    return configuracionService.dashboard(id);
  }
}
