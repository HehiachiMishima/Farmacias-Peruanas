package oracle.simulacion.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.response.ApiResponse;
import oracle.common.response.PaginaResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Filtros;
import oracle.common.support.Trazas;
import oracle.simulacion.model.ArchivoExcel;
import oracle.simulacion.model.Chute;
import oracle.simulacion.model.Cluster;
import oracle.simulacion.model.ParametroChute;
import oracle.simulacion.model.ParametroChuteId;
import oracle.simulacion.model.ParametroCluster;
import oracle.simulacion.model.ParametroClusterId;
import oracle.simulacion.model.RutaProyeccion;
import oracle.simulacion.model.SimulacionConfig;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.ChuteRepository;
import oracle.simulacion.repository.ClusterRepository;
import oracle.simulacion.repository.ParametroChuteRepository;
import oracle.simulacion.repository.ParametroClusterRepository;
import oracle.simulacion.repository.ResumenGlobal;
import oracle.simulacion.repository.ResumenRutasArchivo;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionConfigRepository;
import oracle.simulacion.request.RegistroActualizarRequest;
import oracle.simulacion.response.ArchivoSimulacionResponse;
import oracle.simulacion.response.ResumenSimulacionResponse;
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
  private static final int MAX_OPCIONES = 200;
  private static final BigDecimal VELOCIDAD_INICIAL = new BigDecimal("100.00");
  private static final int OPERARIOS_INICIALES = 2;
  private static final String COLOR_INICIAL = "#1d63ed";

  private final ArchivoExcelRepository archivoRepo;
  private final SimulacionConfigRepository configRepo;
  private final ClusterRepository clusterRepo;
  private final ChuteRepository chuteRepo;
  private final ParametroClusterRepository parametroClusterRepo;
  private final ParametroChuteRepository parametroChuteRepo;
  private final RutaProyeccionRepository rutaRepo;
  private final Transacciones transacciones;

  public SimulacionRegistroService(
      ArchivoExcelRepository archivoRepo,
      SimulacionConfigRepository configRepo,
      ClusterRepository clusterRepo,
      ChuteRepository chuteRepo,
      ParametroClusterRepository parametroClusterRepo,
      ParametroChuteRepository parametroChuteRepo,
      RutaProyeccionRepository rutaRepo,
      Transacciones transacciones) {
    this.archivoRepo = archivoRepo;
    this.configRepo = configRepo;
    this.clusterRepo = clusterRepo;
    this.chuteRepo = chuteRepo;
    this.parametroClusterRepo = parametroClusterRepo;
    this.parametroChuteRepo = parametroChuteRepo;
    this.rutaRepo = rutaRepo;
    this.transacciones = transacciones;
  }

  public Mono<ApiResponse<PaginaResponse<ArchivoSimulacionResponse>>> listar(
      int pagina, int tamano, String texto) {
    int p = Math.max(pagina, 1) - 1;
    int t = Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA);
    PageRequest pageable =
        PageRequest.of(
            p, t, Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
    String termino = Filtros.normalizar(texto);
    Specification<ArchivoExcel> spec =
        (root, query, cb) -> {
          if (termino == null) {
            return cb.conjunction();
          }
          String patron = "%" + termino.toLowerCase(Locale.ROOT) + "%";
          Predicate nombre = cb.like(cb.lower(root.get("nombreRegistro")), patron);
          Predicate archivo = cb.like(cb.lower(root.get("nombreArchivo")), patron);
          Predicate etiqueta = cb.like(cb.lower(root.get("etiqueta")), patron);
          return cb.or(nombre, archivo, etiqueta);
        };
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.listar",
                    () -> {
                      Page<ArchivoExcel> page = archivoRepo.findAll(spec, pageable);
                      Map<Long, ResumenRutasArchivo> resumenes = resumenes(page.getContent());
                      List<ArchivoSimulacionResponse> lista =
                          page.getContent().stream()
                              .map((archivo) -> aResponse(archivo, resumenes.get(archivo.getId())))
                              .toList();
                      return PaginaResponse.de(page, lista);
                    }))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registros listados.", datos));
  }

  public Mono<ApiResponse<List<ArchivoSimulacionResponse>>> opciones() {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.opciones",
                    () -> {
                      List<ArchivoExcel> todos =
                          archivoRepo.findAllByOrderByCreadoEnDescIdDesc().stream()
                              .limit(MAX_OPCIONES)
                              .toList();
                      Map<Long, ResumenRutasArchivo> resumenes = resumenes(todos);
                      return todos.stream()
                          .map((archivo) -> aResponse(archivo, resumenes.get(archivo.getId())))
                          .toList();
                    }))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registros disponibles.", datos));
  }

  public Mono<ApiResponse<ResumenSimulacionResponse>> resumen() {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.resumen",
                    () -> {
                      ResumenGlobal global = rutaRepo.resumenGlobal();
                      long rutas = rutaRepo.count();
                      long chutes =
                          global == null || global.getChutes() == null ? 0 : global.getChutes();
                      BigDecimal bultos =
                          global == null || global.getBultos() == null
                              ? BigDecimal.ZERO
                              : global.getBultos();
                      BigDecimal unidades =
                          global == null || global.getUnidades() == null
                              ? BigDecimal.ZERO
                              : global.getUnidades();
                      BigDecimal lineas =
                          global == null || global.getLineas() == null
                              ? BigDecimal.ZERO
                              : global.getLineas();
                      return new ResumenSimulacionResponse(
                          archivoRepo.count(),
                          rutas,
                          chutes,
                          archivoRepo.contarEtiquetas(),
                          bultos,
                          unidades,
                          lineas);
                    }))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Resumen de simulación.", datos));
  }

  public Mono<ApiResponse<ArchivoSimulacionResponse>> obtener(Long id) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.obtener id=" + id,
                    () -> {
                      ArchivoExcel archivo = buscar(id);
                      Map<Long, ResumenRutasArchivo> resumenes = resumenes(List.of(archivo));
                      return aResponse(archivo, resumenes.get(archivo.getId()));
                    }))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registro encontrado.", datos));
  }

  public Mono<ApiResponse<ArchivoSimulacionResponse>> subir(
      MultipartFile archivo, String nombreRegistro, String etiqueta, String colorHex) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionRegistroService.subir",
                    () -> {
                      if (archivo == null || archivo.isEmpty()) {
                        throw new ConflictoException("Selecciona un archivo para registrar.");
                      }
                      String nombreOriginal = archivo.getOriginalFilename();
                      if (nombreOriginal == null || nombreOriginal.isBlank()) {
                        throw new ConflictoException("El archivo no tiene nombre.");
                      }
                      SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);
                      ArchivoExcel guardado =
                          transacciones.ejecutar(
                              () ->
                                  persistir(
                                      archivo,
                                      nombreOriginal,
                                      nombreRegistro,
                                      etiqueta,
                                      colorHex,
                                      analisis,
                                      actor));
                      return aResponse(guardado, resumenDe(analisis));
                    }))
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
                              ArchivoExcel guardado = archivoRepo.save(archivo);
                              return aResponse(guardado, resumenes(List.of(guardado)).get(id));
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Registro actualizado.", datos));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(
            () ->
                transacciones.ejecutar(
                    () -> {
                      if (!archivoRepo.existsById(id)) {
                        throw new RecursoNoEncontradoException("No existe el registro con id " + id + ".");
                      }
                      archivoRepo.deleteById(id);
                      return null;
                    }))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(ApiResponse.ok("Registro eliminado.", null));
  }

  private ArchivoExcel persistir(
      MultipartFile archivo,
      String nombreOriginal,
      String nombreRegistro,
      String etiqueta,
      String colorHex,
      SimulacionExcel.Analisis analisis,
      Long actor) {
    ArchivoExcel entidad = new ArchivoExcel();
    entidad.setNombreArchivo(limitar(nombreOriginal, 255));
    entidad.setNombreRegistro(limitar(nombreRegistro(nombreRegistro, nombreOriginal), 150));
    entidad.setEtiqueta(normalizarEtiqueta(etiqueta));
    entidad.setColorHex(normalizarColor(colorHex));
    entidad.setEstado(ArchivoExcel.ESTADO_PROYECCION);
    Auditoria.alCrear(entidad, actor);
    ArchivoExcel guardado = archivoRepo.save(entidad);

    SimulacionConfig config = new SimulacionConfig();
    config.setArchivoId(guardado.getId());
    config.setVelocidadBase(VELOCIDAD_INICIAL);
    Auditoria.alCrear(config, actor);
    configRepo.save(config);

    Map<String, Cluster> clusters = new HashMap<>();
    Map<String, Chute> chutes = new HashMap<>();
    for (SimulacionExcel.FilaRuta fila : analisis.rutas()) {
      String claveCluster = fila.cluster().toUpperCase(Locale.ROOT);
      String claveChute = fila.chute().toUpperCase(Locale.ROOT);
      clusters.computeIfAbsent(claveCluster, (clave) -> obtenerOCrearCluster(fila.cluster()));
      chutes.computeIfAbsent(claveChute, (clave) -> obtenerOCrearChute(fila.chute()));
    }

    Map<Long, Cluster> clustersUnicos = new HashMap<>();
    clusters.values().forEach((cluster) -> clustersUnicos.put(cluster.getId(), cluster));
    Map<Long, Chute> chutesUnicos = new HashMap<>();
    chutes.values().forEach((chute) -> chutesUnicos.put(chute.getId(), chute));

    List<ParametroCluster> parametrosClusters = new ArrayList<>();
    for (Cluster cluster : clustersUnicos.values()) {
      ParametroCluster parametro = new ParametroCluster();
      parametro.setId(new ParametroClusterId(guardado.getId(), cluster.getId()));
      parametro.setLimiteHoras(BigDecimal.ZERO);
      parametrosClusters.add(parametro);
    }
    parametroClusterRepo.saveAll(parametrosClusters);

    List<ParametroChute> parametrosChutes = new ArrayList<>();
    for (Chute chute : chutesUnicos.values()) {
      ParametroChute parametro = new ParametroChute();
      parametro.setId(new ParametroChuteId(guardado.getId(), chute.getId()));
      parametro.setOperarios(OPERARIOS_INICIALES);
      parametrosChutes.add(parametro);
    }
    parametroChuteRepo.saveAll(parametrosChutes);

    List<RutaProyeccion> rutas = new ArrayList<>(analisis.rutas().size());
    for (SimulacionExcel.FilaRuta fila : analisis.rutas()) {
      RutaProyeccion ruta = new RutaProyeccion();
      ruta.setArchivoId(guardado.getId());
      ruta.setClusterId(clusters.get(fila.cluster().toUpperCase(Locale.ROOT)).getId());
      ruta.setChuteId(chutes.get(fila.chute().toUpperCase(Locale.ROOT)).getId());
      ruta.setCodigoRuta(fila.codigoRuta());
      ruta.setTotalBultos(fila.bultos());
      ruta.setTotalUnidades(fila.unidades());
      ruta.setTotalLineas(fila.lineas());
      ruta.setConteoCod(fila.conteoCod());
      rutas.add(ruta);
    }
    rutaRepo.saveAll(rutas);
    return guardado;
  }

  private Cluster obtenerOCrearCluster(String nombre) {
    return clusterRepo
        .findByNombreIgnoreCase(nombre)
        .orElseGet(
            () -> {
              Cluster cluster = new Cluster();
              cluster.setNombre(nombre);
              cluster.setActivo(Boolean.TRUE);
              return clusterRepo.save(cluster);
            });
  }

  private Chute obtenerOCrearChute(String nombre) {
    return chuteRepo
        .findByNombreIgnoreCase(nombre)
        .orElseGet(
            () -> {
              Chute chute = new Chute();
              chute.setNombre(nombre);
              chute.setActivo(Boolean.TRUE);
              return chuteRepo.save(chute);
            });
  }

  private ArchivoExcel buscar(Long id) {
    return archivoRepo
        .findById(id)
        .orElseThrow(
            () -> new RecursoNoEncontradoException("No existe el registro con id " + id + "."));
  }

  private Map<Long, ResumenRutasArchivo> resumenes(List<ArchivoExcel> archivos) {
    if (archivos.isEmpty()) {
      return Map.of();
    }
    List<Long> ids = archivos.stream().map(ArchivoExcel::getId).toList();
    return rutaRepo.resumenPorArchivos(ids).stream()
        .collect(Collectors.toMap(ResumenRutasArchivo::getArchivoId, (fila) -> fila));
  }

  private ArchivoSimulacionResponse aResponse(ArchivoExcel archivo, ResumenRutasArchivo resumen) {
    return new ArchivoSimulacionResponse(
        archivo.getId(),
        archivo.getNombreRegistro(),
        archivo.getNombreArchivo(),
        archivo.getEtiqueta(),
        archivo.getColorHex(),
        archivo.getEstado(),
        resumen == null || resumen.getRutas() == null ? 0 : resumen.getRutas(),
        resumen == null || resumen.getChutes() == null ? 0 : resumen.getChutes(),
        resumen == null || resumen.getBultos() == null ? BigDecimal.ZERO : resumen.getBultos(),
        resumen == null || resumen.getUnidades() == null ? BigDecimal.ZERO : resumen.getUnidades(),
        resumen == null || resumen.getLineas() == null ? BigDecimal.ZERO : resumen.getLineas(),
        archivo.getCreadoEn(),
        archivo.getActualizadoEn(),
        archivo.getCreadoPor(),
        archivo.getActualizadoPor());
  }

  private ResumenRutasArchivo resumenDe(SimulacionExcel.Analisis analisis) {
    BigDecimal unidades =
        analisis.rutas().stream()
            .map(SimulacionExcel.FilaRuta::unidades)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal lineas =
        analisis.rutas().stream()
            .map(SimulacionExcel.FilaRuta::lineas)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    long chutes = analisis.rutas().stream().map(SimulacionExcel.FilaRuta::chute).distinct().count();
    return new ResumenRutasArchivo() {
      @Override
      public Long getArchivoId() {
        return 0L;
      }

      @Override
      public Long getRutas() {
        return (long) analisis.rutas().size();
      }

      @Override
      public Long getChutes() {
        return chutes;
      }

      @Override
      public BigDecimal getBultos() {
        return analisis.totalBultos();
      }

      @Override
      public BigDecimal getUnidades() {
        return unidades;
      }

      @Override
      public BigDecimal getLineas() {
        return lineas;
      }
    };
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
