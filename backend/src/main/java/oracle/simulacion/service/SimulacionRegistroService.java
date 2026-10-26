package oracle.simulacion.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.LimiteExcedidoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.response.ApiResponse;
import oracle.common.response.PaginaResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Filtros;
import oracle.common.support.Trazas;
import oracle.simulacion.model.ArchivoExcel;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.ResumenRutasArchivo;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionLotesRepository;
import oracle.simulacion.request.RegistroActualizarRequest;
import oracle.simulacion.response.ArchivoSimulacionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Registro de archivos Excel: alta con parseo, listado, edición y borrado. */
@Slf4j
@Service
public class SimulacionRegistroService {

  private static final int MAX_TAMANO_PAGINA = 100;
  private static final BigDecimal VELOCIDAD_INICIAL = new BigDecimal("100.00");
  private static final int OPERARIOS_INICIALES = 2;
  private static final String COLOR_INICIAL = "#1d63ed";
  /**
   * Cargas que se procesan a la vez por instancia. Leer y guardar un libro
   * grande usa CPU, memoria y una conexión durante segundos: con un tope, una
   * ráfaga de subidas responde 429 en lugar de agotar el pool para todos.
   */
  private static final int MAX_CARGAS_SIMULTANEAS = 4;

  private final ArchivoExcelRepository archivoRepo;
  private final RutaProyeccionRepository rutaRepo;
  private final SimulacionLotesRepository lotes;
  private final Transacciones transacciones;
  private final Semaphore cargas = new Semaphore(MAX_CARGAS_SIMULTANEAS);

  public SimulacionRegistroService(
      ArchivoExcelRepository archivoRepo,
      RutaProyeccionRepository rutaRepo,
      SimulacionLotesRepository lotes,
      Transacciones transacciones) {
    this.archivoRepo = archivoRepo;
    this.rutaRepo = rutaRepo;
    this.lotes = lotes;
    this.transacciones = transacciones;
  }

  public Mono<ApiResponse<PaginaResponse<ArchivoSimulacionResponse>>> listar(
      int pagina, int tamano, String texto, List<String> estados) {
    int p = Math.max(pagina, 1) - 1;
    int t = Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA);
    PageRequest pageable =
        PageRequest.of(
            p, t, Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
    String termino = Filtros.normalizar(texto);
    List<String> filtroEstados = estados == null ? List.of() : estados.stream().filter((e) -> e != null && !e.isBlank()).toList();
    Specification<ArchivoExcel> spec =
        (root, query, cb) -> {
          List<Predicate> condiciones = new ArrayList<>();
          if (!filtroEstados.isEmpty()) {
            condiciones.add(root.get("estado").in(filtroEstados));
          }
          if (termino != null) {
            String patron = "%" + termino.toLowerCase(Locale.ROOT) + "%";
            Predicate nombre = cb.like(cb.lower(root.get("nombreRegistro")), patron);
            Predicate archivo = cb.like(cb.lower(root.get("nombreArchivo")), patron);
            Predicate etiqueta = cb.like(cb.lower(root.get("etiqueta")), patron);
            condiciones.add(cb.or(nombre, archivo, etiqueta));
          }
          return cb.and(condiciones.toArray(Predicate[]::new));
        };
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.listar",
                    () ->
                        transacciones.consultar(
                            () -> {
                              Page<ArchivoExcel> page = archivoRepo.findAll(spec, pageable);
                              List<ArchivoSimulacionResponse> lista = aResponses(page.getContent());
                              return PaginaResponse.de(page, lista);
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registros listados.", datos));
  }

  public Mono<ApiResponse<List<ArchivoSimulacionResponse>>> opciones() {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.opciones",
                    () ->
                        transacciones.consultar(
                            () -> aResponses(archivoRepo.findTop200ByOrderByCreadoEnDescIdDesc()))))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registros disponibles.", datos));
  }

  public Mono<ApiResponse<ArchivoSimulacionResponse>> subir(
      MultipartFile archivo, String nombreRegistro, String etiqueta, String colorHex) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () -> {
              if (!cargas.tryAcquire()) {
                throw new LimiteExcedidoException(
                    "Hay varias cargas en proceso. Intenta de nuevo en unos segundos.");
              }
              try {
                return Trazas.medir(
                    log,
                    "SimulacionRegistroService.subir",
                    () -> registrar(archivo, nombreRegistro, etiqueta, colorHex, actor));
              } finally {
                cargas.release();
              }
            })
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registro creado.", datos));
  }

  public Mono<ApiResponse<ArchivoSimulacionResponse>> actualizar(
      Long id, RegistroActualizarRequest req) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.actualizar id=" + id,
                    () ->
                        transacciones.ejecutar(
                            () -> {
                              ArchivoExcel archivo = buscar(id);
                              archivo.setNombreRegistro(req.nombreRegistro().trim());
                              archivo.setEtiqueta(normalizarEtiqueta(req.etiqueta()));
                              archivo.setColorHex(normalizarColor(req.colorHex()));
                              Auditoria.alActualizar(archivo, actor);
                              return aResponses(List.of(archivoRepo.save(archivo))).get(0);
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registro actualizado.", datos));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.eliminar id=" + id,
                    () ->
                        transacciones.<Void>ejecutar(
                            () -> {
                              // Bloquea el registro: no se borra a mitad de un balanceo.
                              archivoRepo.delete(
                                  archivoRepo
                                      .bloquear(id)
                                      .orElseThrow(() -> noExiste(id)));
                              return null;
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(ApiResponse.ok("Registro eliminado.", null));
  }

  /** Lee el archivo fuera de la transacción y guarda todo en una sola. */
  private ArchivoSimulacionResponse registrar(
      MultipartFile archivo, String nombreRegistro, String etiqueta, String colorHex, Long actor) {
    if (archivo == null || archivo.isEmpty()) {
      throw new ConflictoException("Selecciona un archivo para registrar.");
    }
    String nombreOriginal = archivo.getOriginalFilename();
    if (nombreOriginal == null || nombreOriginal.isBlank()) {
      throw new ConflictoException("El archivo no tiene nombre.");
    }
    String color = normalizarColor(colorHex);
    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);
    ArchivoExcel guardado =
        transacciones.ejecutar(
            () -> persistir(nombreOriginal, nombreRegistro, etiqueta, color, analisis, actor));
    return aResponses(List.of(guardado)).get(0);
  }

  private ArchivoExcel persistir(
      String nombreOriginal,
      String nombreRegistro,
      String etiqueta,
      String color,
      SimulacionExcel.Analisis analisis,
      Long actor) {
    ArchivoExcel entidad = new ArchivoExcel();
    entidad.setNombreArchivo(limitar(nombreOriginal, 255));
    entidad.setNombreRegistro(limitar(nombreRegistro(nombreRegistro, nombreOriginal), 150));
    entidad.setEtiqueta(normalizarEtiqueta(etiqueta));
    entidad.setColorHex(color);
    entidad.setEstado(ArchivoExcel.ESTADO_PROYECCION);
    Auditoria.alCrear(entidad, actor);
    ArchivoExcel guardado = archivoRepo.saveAndFlush(entidad);
    Long archivoId = guardado.getId();

    List<SimulacionExcel.FilaRuta> rutas = analisis.rutas();
    Map<String, Long> clusters =
        lotes.asegurarClusters(rutas.stream().map(SimulacionExcel.FilaRuta::cluster).toList());
    Map<String, Long> chutes =
        lotes.asegurarChutes(rutas.stream().map(SimulacionExcel.FilaRuta::chute).toList());

    lotes.insertarConfig(archivoId, VELOCIDAD_INICIAL, actor);
    lotes.insertarParametrosCluster(archivoId, clusters.values());
    lotes.insertarParametrosChute(archivoId, chutes.values(), OPERARIOS_INICIALES);
    lotes.insertarRutas(archivoId, rutas, clusters, chutes);
    return guardado;
  }

  private ArchivoExcel buscar(Long id) {
    return archivoRepo.findById(id).orElseThrow(() -> noExiste(id));
  }

  private static RecursoNoEncontradoException noExiste(Long id) {
    return new RecursoNoEncontradoException("No existe el registro con id " + id + ".");
  }

  /** Respuestas con los totales de rutas de cada registro (una consulta agregada). */
  private List<ArchivoSimulacionResponse> aResponses(List<ArchivoExcel> archivos) {
    if (archivos.isEmpty()) {
      return List.of();
    }
    Map<Long, ResumenRutasArchivo> resumenes =
        rutaRepo.resumenPorArchivos(archivos.stream().map(ArchivoExcel::getId).toList()).stream()
            .collect(Collectors.toMap(ResumenRutasArchivo::getArchivoId, (fila) -> fila));
    return archivos.stream()
        .map((archivo) -> SimulacionRespuestas.archivo(archivo, resumenes.get(archivo.getId())))
        .toList();
  }

  private String nombreRegistro(String nombreRegistro, String nombreArchivo) {
    if (nombreRegistro != null && !nombreRegistro.isBlank()) {
      return nombreRegistro.trim();
    }
    String sinExtension = nombreArchivo.replaceFirst("(?i)\\.(xlsx|xls|csv)$", "");
    return sinExtension.isBlank() ? "Registro sin nombre" : sinExtension;
  }

  /** La etiqueta es opcional: si llega vacía se guarda NULL, no un texto ficticio. */
  private String normalizarEtiqueta(String etiqueta) {
    return etiqueta == null || etiqueta.isBlank() ? null : limitar(etiqueta.trim(), 50);
  }

  private String normalizarColor(String color) {
    if (color == null || color.isBlank()) {
      return COLOR_INICIAL;
    }
    String limpio = color.trim();
    if (!limpio.matches("^#[0-9a-fA-F]{6}$")) {
      throw new ConflictoException("El color debe tener formato #RRGGBB.");
    }
    return limpio.toLowerCase(Locale.ROOT);
  }

  private String limitar(String texto, int maximo) {
    return texto.length() <= maximo ? texto : texto.substring(0, maximo);
  }
}
