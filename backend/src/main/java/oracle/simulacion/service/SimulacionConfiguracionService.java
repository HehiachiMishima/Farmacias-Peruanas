package oracle.simulacion.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import oracle.simulacion.model.HistorialBalanceo;
import oracle.simulacion.model.ParametroChute;
import oracle.simulacion.model.ParametroCluster;
import oracle.simulacion.model.RutaProyeccion;
import oracle.simulacion.model.SimulacionConfig;
import oracle.simulacion.model.TipoRuta;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.CargaRutas;
import oracle.simulacion.repository.ChuteRepository;
import oracle.simulacion.repository.ClusterRepository;
import oracle.simulacion.repository.HistorialBalanceoRepository;
import oracle.simulacion.repository.MovimientoRutas;
import oracle.simulacion.repository.ParametroChuteRepository;
import oracle.simulacion.repository.ParametroClusterRepository;
import oracle.simulacion.repository.ResumenRutasFiltro;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionConfigRepository;
import oracle.simulacion.request.ChuteOperariosRequest;
import oracle.simulacion.request.ClusterLimiteRequest;
import oracle.simulacion.request.ConfiguracionActualizarRequest;
import oracle.simulacion.request.LadoChute;
import oracle.simulacion.request.Limites;
import oracle.simulacion.request.MoverRutaRequest;
import oracle.simulacion.response.ArchivoSimulacionResponse;
import oracle.simulacion.response.BalanceoResponse;
import oracle.simulacion.response.CargaSimulacionResponse;
import oracle.simulacion.response.ChuteComparativoResponse;
import oracle.simulacion.response.ChuteMezcladoResponse;
import oracle.simulacion.response.ChuteParametroResponse;
import oracle.simulacion.response.ClusterComparativoResponse;
import oracle.simulacion.response.ClusterParametroResponse;
import oracle.simulacion.response.ConfiguracionSimulacionResponse;
import oracle.simulacion.response.DashboardSimulacionResponse;
import oracle.simulacion.response.HistorialSimulacionResponse;
import oracle.simulacion.response.MovimientoChuteResponse;
import oracle.simulacion.response.ResumenRutasDashboardResponse;
import oracle.simulacion.response.RutaSimulacionResponse;
import oracle.simulacion.response.RutasDashboardResponse;
import oracle.simulacion.response.SimulacionKpisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Configuración de límites/operarios, rutas, balanceo y comparativos de
 * simulación.
 *
 * Toda escritura empieza bloqueando el registro (SELECT … FOR UPDATE), así
 * que dos usuarios sobre el mismo registro se serializan. Las lecturas
 * trabajan con agregados por (cluster, chute); solo el balanceo carga todas
 * las rutas, porque las mueve una a una.
 */
@Slf4j
@Service
public class SimulacionConfiguracionService {

  private static final BigDecimal VELOCIDAD_INICIAL = new BigDecimal("100.00");
  private static final int OPERARIOS_INICIALES = 1;
  private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
  private static final int MAX_ITERACIONES = 1000;
  private static final int MAX_TAMANO_PAGINA = 100;
  private static final int ESCALA = 4;
  /**
   * Clusters que no pueden compartir chute: CL1 y CL2 (número del nombre
   * {@link #CLUSTER_NUMERADO}). El resto de combinaciones sigue permitido.
   */
  private static final Set<Integer> CLUSTERS_INCOMPATIBLES = Set.of(1, 2);
  /** «CL1», «CL 1», «C1», «cl01» → 1. Otros prefijos (CP1, CB2…) no cuentan. */
  private static final Pattern CLUSTER_NUMERADO = Pattern.compile("CL?0*(\\d+)");
  private static final Pattern ESPACIOS = Pattern.compile("\\s+");
  /** Ordena CHUTE 2 antes que CHUTE 10 (comparación natural, no lexicográfica). */
  private static final Comparator<String> ORDEN_NATURAL =
      SimulacionConfiguracionService::compararNatural;

  private final ArchivoExcelRepository archivoRepo;
  private final SimulacionConfigRepository configRepo;
  private final ClusterRepository clusterRepo;
  private final ChuteRepository chuteRepo;
  private final ParametroClusterRepository parametroClusterRepo;
  private final ParametroChuteRepository parametroChuteRepo;
  private final RutaProyeccionRepository rutaRepo;
  private final HistorialBalanceoRepository historialRepo;
  private final Transacciones transacciones;

  public SimulacionConfiguracionService(
      ArchivoExcelRepository archivoRepo,
      SimulacionConfigRepository configRepo,
      ClusterRepository clusterRepo,
      ChuteRepository chuteRepo,
      ParametroClusterRepository parametroClusterRepo,
      ParametroChuteRepository parametroChuteRepo,
      RutaProyeccionRepository rutaRepo,
      HistorialBalanceoRepository historialRepo,
      Transacciones transacciones) {
    this.archivoRepo = archivoRepo;
    this.configRepo = configRepo;
    this.clusterRepo = clusterRepo;
    this.chuteRepo = chuteRepo;
    this.parametroClusterRepo = parametroClusterRepo;
    this.parametroChuteRepo = parametroChuteRepo;
    this.rutaRepo = rutaRepo;
    this.historialRepo = historialRepo;
    this.transacciones = transacciones;
  }

  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> obtener(Long archivoId) {
    return leer(
        "obtener id=" + archivoId,
        () -> aConfiguracion(contexto(archivoId, false)),
        "Configuración de simulación.");
  }

  public Mono<ApiResponse<PaginaResponse<RutaSimulacionResponse>>> rutas(
      Long archivoId,
      int pagina,
      int tamano,
      String texto,
      List<Long> clusterIds,
      List<Long> chuteIds) {
    int p = Math.max(pagina, 1) - 1;
    int t = Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA);
    PageRequest pageable =
        PageRequest.of(p, t, Sort.by("codigoRuta").ascending().and(Sort.by("id").ascending()));
    String termino = Filtros.normalizar(texto);
    // Prefijo (no "contiene"): usa idx_rutas_archivo_codigo en lugar de recorrer
    // todas las rutas del archivo. La colación ignora mayúsculas y tildes.
    Specification<RutaProyeccion> spec =
        (root, query, cb) -> {
          List<Predicate> condiciones = new ArrayList<>();
          condiciones.add(cb.equal(root.get("archivoId"), archivoId));
          if (clusterIds != null && !clusterIds.isEmpty()) {
            condiciones.add(root.get("clusterId").in(clusterIds));
          }
          if (chuteIds != null && !chuteIds.isEmpty()) {
            condiciones.add(root.get("chuteId").in(chuteIds));
          }
          if (termino != null) {
            condiciones.add(cb.like(root.get("codigoRuta"), escaparLike(termino) + "%", '\\'));
          }
          return cb.and(condiciones.toArray(Predicate[]::new));
        };
    return leer(
        "rutas id=" + archivoId,
        () -> {
          Contexto ctx = contexto(archivoId, false);
          Page<RutaProyeccion> page = rutaRepo.findAll(spec, pageable);
          List<RutaSimulacionResponse> lista =
              page.getContent().stream().map((ruta) -> aRuta(ctx, ruta)).toList();
          return PaginaResponse.de(page, lista);
        },
        "Rutas del registro.");
  }

  /**
   * Un lado del detalle de rutas del dashboard: las rutas que los chutes
   * elegidos tenían al cargar el Excel (ANTES) o las que tienen hoy (DESPUES),
   * con los totales del conjunto filtrado (no solo de la página). Se ordenan
   * por cluster y código para que las rutas de cada cluster salgan agrupadas.
   */
  public Mono<ApiResponse<RutasDashboardResponse>> rutasDashboard(
      Long archivoId,
      int pagina,
      int tamano,
      String texto,
      TipoRuta tipoRuta,
      List<Long> chuteIds,
      LadoChute lado,
      Long clusterId,
      boolean soloCambios) {
    int p = Math.max(pagina, 1) - 1;
    int t = Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA);
    PageRequest pageable =
        PageRequest.of(p, t, Sort.by("clusterId", "codigoRuta", "id").ascending());
    String termino = Filtros.normalizar(texto);
    String patron = termino == null ? null : escaparLike(termino) + "%";
    boolean antes = lado == LadoChute.ANTES;
    Specification<RutaProyeccion> spec =
        (root, query, cb) -> {
          List<Predicate> condiciones = new ArrayList<>();
          condiciones.add(cb.equal(root.get("archivoId"), archivoId));
          if (patron != null) {
            condiciones.add(cb.like(root.get("codigoRuta"), patron, '\\'));
          }
          if (tipoRuta != null) {
            condiciones.add(cb.equal(root.get("tipo"), tipoRuta));
          }
          condiciones.add(root.get(antes ? "chuteOriginalId" : "chuteId").in(chuteIds));
          if (clusterId != null) {
            condiciones.add(cb.equal(root.get("clusterId"), clusterId));
          }
          if (soloCambios) {
            condiciones.add(cb.notEqual(root.get("chuteId"), root.get("chuteOriginalId")));
          }
          return cb.and(condiciones.toArray(Predicate[]::new));
        };
    return leer(
        "rutas dashboard id=" + archivoId,
        () -> {
          Contexto ctx = contexto(archivoId, false);
          Page<RutaProyeccion> page = rutaRepo.findAll(spec, pageable);
          List<RutaSimulacionResponse> lista =
              page.getContent().stream().map((ruta) -> aRuta(ctx, ruta)).toList();
          ResumenRutasFiltro fila =
              rutaRepo.resumenFiltrado(
                  archivoId,
                  patron,
                  tipoRuta == null ? null : tipoRuta.name(),
                  chuteIds,
                  antes,
                  clusterId,
                  soloCambios);
          ResumenRutasDashboardResponse resumen =
              new ResumenRutasDashboardResponse(
                  fila == null || fila.getRutas() == null ? 0 : fila.getRutas(),
                  fila == null || fila.getRutasB2c() == null ? 0 : fila.getRutasB2c(),
                  fila == null || fila.getRutasB2b() == null ? 0 : fila.getRutasB2b(),
                  fila == null || fila.getCambios() == null ? 0 : fila.getCambios(),
                  fila == null || fila.getBultos() == null ? BigDecimal.ZERO : fila.getBultos(),
                  fila == null || fila.getUnidades() == null
                      ? BigDecimal.ZERO
                      : fila.getUnidades(),
                  fila == null || fila.getLineas() == null ? BigDecimal.ZERO : fila.getLineas(),
                  fila == null || fila.getCod() == null ? 0 : fila.getCod().longValue());
          return new RutasDashboardResponse(resumen, PaginaResponse.de(page, lista));
        },
        "Rutas del dashboard.");
  }

  private static RutaSimulacionResponse aRuta(Contexto ctx, RutaProyeccion ruta) {
    return new RutaSimulacionResponse(
        ruta.getId(),
        ruta.getChuteId(),
        ctx.nombreChute(ruta.getChuteId()),
        ruta.getChuteOriginalId(),
        ctx.nombreChute(ruta.getChuteOriginalId()),
        ruta.getClusterId(),
        ctx.nombreCluster(ruta.getClusterId()),
        ruta.getCodigoRuta(),
        ruta.getTipo() == null ? null : ruta.getTipo().name(),
        ruta.getTotalBultos(),
        ruta.getTotalUnidades(),
        ruta.getTotalLineas(),
        ruta.getConteoCod());
  }

  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> guardar(
      Long archivoId, ConfiguracionActualizarRequest req) {
    Long actor = ActorActual.id();
    return escribir(
        "guardar id=" + archivoId,
        () -> {
          Contexto ctx = contexto(archivoId, true);
          SimulacionConfig config = ctx.config();
          if (config.getCreadoEn() == null) {
            Auditoria.alCrear(config, actor);
          } else {
            Auditoria.alActualizar(config, actor);
          }
          config.setVelocidadBase(req.velocidadBase());
          configRepo.save(config);

          if (req.clusters() != null) {
            for (ClusterLimiteRequest limite : req.clusters()) {
              ParametroCluster parametro = ctx.parametrosCluster().get(limite.clusterId());
              if (parametro == null) {
                throw new ConflictoException(
                    "El cluster " + limite.clusterId() + " no pertenece al registro.");
              }
              parametro.setLimiteHoras(limite.limiteHoras());
            }
          }
          if (req.chutes() != null) {
            for (ChuteOperariosRequest operarios : req.chutes()) {
              ParametroChute parametro = ctx.parametrosChute().get(operarios.chuteId());
              if (parametro == null) {
                throw new ConflictoException(
                    "El chute " + operarios.chuteId() + " no pertenece al registro.");
              }
              parametro.setOperarios(operarios.operarios());
            }
          }
          int asignados =
              ctx.parametrosChute().values().stream()
                  .mapToInt(
                      (p) ->
                          p.getOperarios() == null
                              ? OPERARIOS_INICIALES
                              : Math.max(1, p.getOperarios()))
                  .sum();
          Integer maximo =
              req.operariosMaximos() != null ? req.operariosMaximos() : config.getOperariosMaximos();
          if (maximo != null && asignados > maximo) {
            throw new ConflictoException(
                "Los operarios asignados ("
                    + asignados
                    + ") superan el máximo disponible ("
                    + maximo
                    + "). Ajusta los chutes o sube el máximo.");
          }
          if (req.operariosMaximos() != null) {
            config.setOperariosMaximos(req.operariosMaximos());
          }
          // El estado no cambia al guardar parámetros: si el registro ya estaba
          // balanceado, las rutas movidas siguen siendo válidas.
          tocar(ctx.archivo(), actor);
          return aConfiguracion(ctx);
        },
        "Parámetros guardados.");
  }

  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> mover(
      Long archivoId, Long rutaId, MoverRutaRequest req) {
    Long actor = ActorActual.id();
    return escribir(
        "mover archivo=" + archivoId + " ruta=" + rutaId,
        () -> {
          Contexto ctx = contexto(archivoId, true);
          RutaProyeccion ruta =
              rutaRepo
                  .findByIdAndArchivoId(rutaId, archivoId)
                  .orElseThrow(
                      () -> new RecursoNoEncontradoException("No existe la ruta " + rutaId + "."));
          if (!ctx.parametrosChute().containsKey(req.chuteId())) {
            throw new ConflictoException(
                "El chute " + req.chuteId() + " no pertenece al registro.");
          }
          if (ruta.getChuteId().equals(req.chuteId())) {
            throw new ConflictoException(
                "La ruta ya pertenece al chute " + ctx.nombreChute(req.chuteId()) + ".");
          }
          // CL1 y CL2 no pueden compartir chute: un movimiento manual tampoco
          // puede crear esa mezcla.
          Long conflicto =
              clusterEnConflicto(
                  rutaRepo.clustersEnChute(archivoId, req.chuteId()),
                  ruta.getClusterId(),
                  numerosCluster(ctx.clusters()));
          if (conflicto != null) {
            throw new ConflictoException(
                "El chute "
                    + ctx.nombreChute(req.chuteId())
                    + " ya tiene rutas del cluster "
                    + ctx.nombreCluster(conflicto)
                    + " y no pueden mezclarse con las del cluster "
                    + ctx.nombreCluster(ruta.getClusterId())
                    + ".");
          }
          historialRepo.save(
              movimiento(archivoId, ruta.getId(), ruta.getChuteId(), req.chuteId(), actor));
          ruta.setChuteId(req.chuteId());
          // BALANCEADO = los datos ya no son la proyección original (ver
          // archivos_excel.estado): un movimiento manual también lo cumple.
          ctx.archivo().setEstado(ArchivoExcel.ESTADO_BALANCEADO);
          tocar(ctx.archivo(), actor);
          return aConfiguracion(ctx);
        },
        "Ruta movida.");
  }

  public Mono<ApiResponse<BalanceoResponse>> balancear(Long archivoId) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.balancear id=" + archivoId,
                    () -> transacciones.ejecutar(() -> ejecutarBalanceo(archivoId, actor))))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok(datos.mensaje(), datos));
  }

  public Mono<ApiResponse<Void>> restablecer(Long archivoId) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.restablecer id=" + archivoId,
                    () ->
                        transacciones.<Void>ejecutar(
                            () -> {
                              deshacerHistorial(contexto(archivoId, true), actor);
                              return null;
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(ApiResponse.<Void>ok("Datos restablecidos.", null));
  }

  public Mono<ApiResponse<List<HistorialSimulacionResponse>>> historial(Long archivoId) {
    return leer(
        "historial id=" + archivoId,
        () -> {
          Contexto ctx = contexto(archivoId, false);
          List<HistorialBalanceo> logs = historialRepo.findTop500ByArchivoIdOrderByIdDesc(archivoId);
          Set<Long> rutaIds = new HashSet<>();
          logs.forEach(
              (log) -> {
                if (log.getRutaId() != null) {
                  rutaIds.add(log.getRutaId());
                }
              });
          Map<Long, String> codigos =
              rutaRepo.findAllById(rutaIds).stream()
                  .collect(Collectors.toMap(RutaProyeccion::getId, RutaProyeccion::getCodigoRuta));
          return logs.stream()
              .map(
                  (log) ->
                      new HistorialSimulacionResponse(
                          log.getId(),
                          log.getTipoAccion(),
                          log.getRutaId(),
                          log.getRutaId() == null ? null : codigos.get(log.getRutaId()),
                          ctx.nombreChute(log.getChuteOrigenId()),
                          ctx.nombreChute(log.getChuteDestinoId()),
                          ctx.nombreChute(log.getChuteModificadoId()),
                          log.getCreadoEn(),
                          log.getCreadoPor()))
              .toList();
        },
        "Historial de balanceo.");
  }

  public Mono<ApiResponse<DashboardSimulacionResponse>> dashboard(Long archivoId) {
    return leer(
        "dashboard id=" + archivoId,
        () -> calcularDashboard(contexto(archivoId, false)),
        "Dashboard de simulación.");
  }

  // ------------------------------------------------------------------
  // Balanceo
  // ------------------------------------------------------------------

  private BalanceoResponse ejecutarBalanceo(Long archivoId, Long actor) {
    Contexto ctx = contexto(archivoId, true);
    BigDecimal velocidad = ctx.config().getVelocidadBase();
    if (velocidad == null || velocidad.signum() <= 0) {
      throw new ConflictoException("La velocidad base debe ser mayor a cero.");
    }
    Map<Long, BigDecimal> limites = limites(ctx);
    if (limites.values().stream().noneMatch((limite) -> limite.signum() > 0)) {
      throw new ConflictoException(
          "Define al menos un límite de horas por cluster antes de balancear.");
    }
    Integer maxOperarios = ctx.config().getOperariosMaximos();

    Balanceador balanceador =
        new Balanceador(
            rutaRepo.findByArchivoIdOrderByIdAsc(archivoId),
            operarios(ctx),
            velocidad,
            limites,
            maxOperarios,
            numerosCluster(ctx.clusters()));
    List<HistorialBalanceo> logs = new ArrayList<>();
    // CL1 y CL2 no pueden compartir chute: antes de atender tiempos se separan
    // las mezclas que trae el Excel, aunque eso no mejore ningún tiempo.
    Traslado separada;
    while ((separada = balanceador.separarMezcla()) != null) {
      logs.add(
          movimiento(
              archivoId,
              separada.ruta().getId(),
              separada.origen(),
              separada.ruta().getChuteId(),
              actor));
    }
    Long chuteSaturado = null;
    boolean topeOperarios = false;
    int iteraciones = 0;
    while (iteraciones++ < MAX_ITERACIONES) {
      Long cluster = balanceador.peorCluster(limites);
      if (cluster == null) {
        break;
      }
      Long cuello = balanceador.chuteMasLento(cluster);
      RutaProyeccion movida = balanceador.moverMayorRuta(cluster, cuello);
      if (movida != null) {
        logs.add(movimiento(archivoId, movida.getId(), cuello, movida.getChuteId(), actor));
        continue;
      }
      if (balanceador.operarios(cuello) >= Limites.MAX_OPERARIOS) {
        chuteSaturado = cuello;
        break;
      }
      // Primero reasigna un operario con holgura; solo añade si no hay ninguno
      // disponible dentro del tope global del registro.
      Long donante = balanceador.moverOperario(cuello);
      if (donante != null) {
        logs.add(operarioMovido(archivoId, donante, cuello, actor));
        continue;
      }
      if (balanceador.hayCupo()) {
        balanceador.agregarOperario(cuello);
        logs.add(operario(archivoId, cuello, actor));
        continue;
      }
      topeOperarios = true;
      break;
    }

    balanceador
        .operariosModificados()
        .forEach(
            (chuteId, total) -> {
              ParametroChute parametro = ctx.parametrosChute().get(chuteId);
              if (parametro != null) {
                parametro.setOperarios(total);
              }
            });
    if (!logs.isEmpty()) {
      historialRepo.saveAll(logs);
      // Sin cambios el registro conserva su estado: sigue siendo la proyección original.
      ctx.archivo().setEstado(ArchivoExcel.ESTADO_BALANCEADO);
    }
    tocar(ctx.archivo(), actor);

    int movidas = (int) logs.stream().filter(HistorialBalanceo::esMovimiento).count();
    int movidos = (int) logs.stream().filter(HistorialBalanceo::esMovimientoOperario).count();
    int agregados = logs.size() - movidas - movidos;
    return new BalanceoResponse(
        movidas,
        agregados,
        movidos,
        mensajeBalanceo(
            movidas, agregados, movidos, ctx, chuteSaturado, topeOperarios, maxOperarios,
            iteraciones, balanceador.mezclasSinSalida()));
  }

  private String mensajeBalanceo(
      int movidas,
      int agregados,
      int movidos,
      Contexto ctx,
      Long chuteSaturado,
      boolean topeOperarios,
      Integer maxOperarios,
      int iteraciones,
      Set<Long> mezclasSinSalida) {
    StringBuilder texto = new StringBuilder();
    if (movidas == 0 && agregados == 0 && movidos == 0) {
      texto.append("Los clusters ya cumplían sus límites de tiempo.");
    } else {
      texto.append("Balanceo finalizado.");
      if (movidas > 0) {
        texto.append(" ").append(movidas).append(" ruta(s) movida(s).");
      }
      if (movidos > 0) {
        texto.append(" ").append(movidos).append(" operario(s) reasignado(s).");
      }
      if (agregados > 0) {
        texto.append(" ").append(agregados).append(" operario(s) añadido(s).");
      }
    }
    if (chuteSaturado != null) {
      texto
          .append(" ")
          .append(ctx.nombreChute(chuteSaturado))
          .append(" llegó al máximo de ")
          .append(Limites.MAX_OPERARIOS)
          .append(" operarios por chute: revisa los límites de horas.");
    } else if (topeOperarios) {
      texto
          .append(" Se alcanzó el máximo de ")
          .append(maxOperarios == null ? "operarios" : maxOperarios + " operario(s)")
          .append(" disponibles y ningún chute tiene holgura para ceder uno: sube el máximo o");
      texto.append(" revisa los límites de horas.");
    } else if (iteraciones > MAX_ITERACIONES) {
      texto.append(" Se alcanzó el máximo de ajustes por ejecución; vuelve a balancear.");
    }
    if (!mezclasSinSalida.isEmpty()) {
      texto
          .append(" No se pudo separar CL1 y CL2 en ")
          .append(
              mezclasSinSalida.stream()
                  .map(ctx::nombreChute)
                  .sorted(ORDEN_NATURAL)
                  .collect(Collectors.joining(", ")))
          .append(": no hay otro chute libre de ese cluster.");
    }
    return texto.toString();
  }

  /**
   * Estado del balanceo con tiempos incrementales: mover una ruta solo cambia
   * dos chutes, así que no se recalculan todas las rutas en cada iteración.
   */
  private static final class Balanceador {
    private final BigDecimal velocidad;
    private final Map<Long, Integer> operarios;
    private final Map<Long, Integer> operariosIniciales;
    private final Map<Long, BigDecimal> limites;
    /** Tope global de operarios del registro; {@code null} = sin tope global. */
    private final Integer maxOperarios;
    private final Map<Long, BigDecimal> bultosChute = new HashMap<>();
    /** Rutas de cada cluster por chute (cuántas). */
    private final Map<Long, Map<Long, Integer>> conteo = new HashMap<>();
    private final Map<Long, List<RutaProyeccion>> rutasCluster = new HashMap<>();
    /** Clusters que atiende cada chute, para medir el impacto de ceder un operario. */
    private final Map<Long, Set<Long>> clustersPorChute = new HashMap<>();
    /** Número de cada cluster (CL1 → 1) para validar la mezcla CL1/CL2. */
    private final Map<Long, Integer> numerosCluster;
    /** Chutes con CL1 y CL2 que no se pudieron separar por falta de destino. */
    private final Set<Long> mezclasSinSalida = new TreeSet<>();

    Balanceador(
        List<RutaProyeccion> rutas,
        Map<Long, Integer> operarios,
        BigDecimal velocidad,
        Map<Long, BigDecimal> limites,
        Integer maxOperarios,
        Map<Long, Integer> numerosCluster) {
      this.velocidad = velocidad;
      this.operarios = new HashMap<>(operarios);
      this.limites = limites;
      this.maxOperarios = maxOperarios != null && maxOperarios > 0 ? maxOperarios : null;
      this.numerosCluster = numerosCluster;
      for (RutaProyeccion ruta : rutas) {
        this.operarios.putIfAbsent(ruta.getChuteId(), OPERARIOS_INICIALES);
        bultosChute.merge(ruta.getChuteId(), ruta.getTotalBultos(), BigDecimal::add);
        conteo
            .computeIfAbsent(ruta.getClusterId(), (id) -> new HashMap<>())
            .merge(ruta.getChuteId(), 1, Integer::sum);
        rutasCluster.computeIfAbsent(ruta.getClusterId(), (id) -> new ArrayList<>()).add(ruta);
        clustersPorChute
            .computeIfAbsent(ruta.getChuteId(), (id) -> new HashSet<>())
            .add(ruta.getClusterId());
      }
      this.operariosIniciales = Map.copyOf(this.operarios);
    }

    BigDecimal tiempo(Long chute) {
      return horas(bultosChute.getOrDefault(chute, BigDecimal.ZERO), velocidad, operarios(chute));
    }

    BigDecimal tiempoCluster(Long cluster) {
      BigDecimal maximo = BigDecimal.ZERO;
      for (Long chute : conteo.getOrDefault(cluster, Map.of()).keySet()) {
        maximo = maximo.max(tiempo(chute));
      }
      return maximo;
    }

    /** Tiempo de cierre simulando un cambio de operarios sin mutar el estado. */
    private BigDecimal tiempoClusterCon(
        Long cluster, Long chuteA, int operariosA, Long chuteB, int operariosB) {
      BigDecimal maximo = BigDecimal.ZERO;
      for (Long chute : conteo.getOrDefault(cluster, Map.of()).keySet()) {
        int operarios = operarios(chute);
        if (chute.equals(chuteA)) {
          operarios = operariosA;
        }
        if (chute.equals(chuteB)) {
          operarios = operariosB;
        }
        maximo = maximo.max(horas(bultosChute.getOrDefault(chute, BigDecimal.ZERO), velocidad, operarios));
      }
      return maximo;
    }

    Long peorCluster(Map<Long, BigDecimal> limites) {
      Long peor = null;
      BigDecimal mayorDeficit = BigDecimal.ZERO;
      for (Long cluster : conteo.keySet()) {
        BigDecimal limite = limites.getOrDefault(cluster, BigDecimal.ZERO);
        if (limite.signum() <= 0) {
          continue;
        }
        BigDecimal deficit = tiempoCluster(cluster).subtract(limite);
        if (deficit.compareTo(TOLERANCIA) > 0 && deficit.compareTo(mayorDeficit) > 0) {
          mayorDeficit = deficit;
          peor = cluster;
        }
      }
      return peor;
    }

    Long chuteMasLento(Long cluster) {
      Long lento = null;
      BigDecimal mayor = null;
      for (Long chute : conteo.get(cluster).keySet()) {
        BigDecimal tiempo = tiempo(chute);
        if (mayor == null || tiempo.compareTo(mayor) > 0) {
          mayor = tiempo;
          lento = chute;
        }
      }
      return lento;
    }

    /**
     * Mueve la ruta más grande del cluster en el chute cuello al chute más
     * rápido, si con ello el destino queda por debajo del cuello. Devuelve la
     * ruta movida o null si no conviene mover.
     */
    RutaProyeccion moverMayorRuta(Long cluster, Long cuello) {
      RutaProyeccion ruta =
          rutasCluster.get(cluster).stream()
              .filter((r) -> r.getChuteId().equals(cuello))
              .max(Comparator.comparing(RutaProyeccion::getTotalBultos))
              .orElse(null);
      if (ruta == null) {
        return null;
      }
      Long rapido = null;
      BigDecimal menor = null;
      for (Long chute : operarios.keySet()) {
        if (chute.equals(cuello) || !admite(chute, cluster)) {
          continue;
        }
        BigDecimal tiempo = tiempoCon(chute, ruta);
        // El destino no puede dejar fuera de su límite a otro cluster que ya
        // usa ese chute: sería trasladar el problema.
        if (!respetaLimitesAjenos(chute, cluster, tiempo)) {
          continue;
        }
        if (menor == null || tiempo.compareTo(menor) < 0) {
          menor = tiempo;
          rapido = chute;
        }
      }
      if (rapido == null || menor.compareTo(tiempo(cuello)) >= 0) {
        return null;
      }
      trasladar(ruta, cluster, cuello, rapido);
      return ruta;
    }

    /**
     * Saca del primer chute que mezcla CL1 y CL2 una ruta del cluster con
     * menos rutas allí y la lleva al chute compatible más rápido. Devuelve el
     * traslado, o null cuando ya no quedan mezclas que se puedan separar.
     */
    Traslado separarMezcla() {
      List<Long> chutes = new ArrayList<>(clustersPorChute.keySet());
      chutes.sort(Comparator.naturalOrder());
      for (Long chute : chutes) {
        if (mezclasSinSalida.contains(chute)) {
          continue;
        }
        Long saliente = clusterSaliente(chute);
        if (saliente == null) {
          continue;
        }
        RutaProyeccion ruta =
            rutasCluster.get(saliente).stream()
                .filter((r) -> r.getChuteId().equals(chute))
                .max(Comparator.comparing(RutaProyeccion::getTotalBultos))
                .orElseThrow();
        Long destino = null;
        BigDecimal menor = null;
        for (Long candidato : operarios.keySet()) {
          if (candidato.equals(chute) || !admite(candidato, saliente)) {
            continue;
          }
          BigDecimal tiempo = tiempoCon(candidato, ruta);
          if (menor == null || tiempo.compareTo(menor) < 0) {
            menor = tiempo;
            destino = candidato;
          }
        }
        if (destino == null) {
          mezclasSinSalida.add(chute);
          continue;
        }
        trasladar(ruta, saliente, chute, destino);
        return new Traslado(ruta, chute);
      }
      return null;
    }

    Set<Long> mezclasSinSalida() {
      return mezclasSinSalida;
    }

    /**
     * Cluster que debe dejar un chute mezclado: entre los incompatibles
     * presentes, el que tiene menos rutas allí (menos movimientos). Null si el
     * chute no mezcla CL1 con CL2.
     */
    private Long clusterSaliente(Long chute) {
      Long saliente = null;
      int menos = Integer.MAX_VALUE;
      Set<Integer> numeros = new HashSet<>();
      for (Long cluster : clustersPorChute.get(chute)) {
        Integer numero = numerosCluster.get(cluster);
        if (numero == null || !CLUSTERS_INCOMPATIBLES.contains(numero)) {
          continue;
        }
        numeros.add(numero);
        int rutas = conteo.get(cluster).getOrDefault(chute, 0);
        if (rutas < menos || (rutas == menos && cluster < saliente)) {
          menos = rutas;
          saliente = cluster;
        }
      }
      return numeros.size() > 1 ? saliente : null;
    }

    /** CL1 y CL2 no pueden quedar juntos: el chute admite al cluster si no crea esa mezcla. */
    private boolean admite(Long chute, Long cluster) {
      return !mezclaRestringida(
          clustersPorChute.getOrDefault(chute, Set.of()), cluster, numerosCluster);
    }

    /** Tiempo del chute si recibiera la ruta. */
    private BigDecimal tiempoCon(Long chute, RutaProyeccion ruta) {
      return horas(
          bultosChute.getOrDefault(chute, BigDecimal.ZERO).add(ruta.getTotalBultos()),
          velocidad,
          operarios(chute));
    }

    private boolean respetaLimitesAjenos(Long chute, Long cluster, BigDecimal tiempo) {
      for (Long otro : clustersPorChute.getOrDefault(chute, Set.of())) {
        BigDecimal limite = limites.getOrDefault(otro, BigDecimal.ZERO);
        if (!otro.equals(cluster)
            && limite.signum() > 0
            && tiempo.compareTo(limite.add(TOLERANCIA)) > 0) {
          return false;
        }
      }
      return true;
    }

    private void trasladar(RutaProyeccion ruta, Long cluster, Long origen, Long destino) {
      bultosChute.merge(origen, ruta.getTotalBultos().negate(), BigDecimal::add);
      bultosChute.merge(destino, ruta.getTotalBultos(), BigDecimal::add);
      Map<Long, Integer> chutes = conteo.get(cluster);
      chutes.merge(origen, -1, Integer::sum);
      if (chutes.get(origen) == 0) {
        chutes.remove(origen);
        Set<Long> clustersOrigen = clustersPorChute.get(origen);
        if (clustersOrigen != null) {
          clustersOrigen.remove(cluster);
        }
      }
      chutes.merge(destino, 1, Integer::sum);
      clustersPorChute.computeIfAbsent(destino, (id) -> new HashSet<>()).add(cluster);
      ruta.setChuteId(destino);
    }

    int operarios(Long chute) {
      return operarios.getOrDefault(chute, OPERARIOS_INICIALES);
    }

    int totalOperarios() {
      return operarios.values().stream().mapToInt(Integer::intValue).sum();
    }

    boolean hayCupo() {
      return maxOperarios == null || totalOperarios() < maxOperarios;
    }

    void agregarOperario(Long chute) {
      operarios.merge(chute, 1, Integer::sum);
    }

    /**
     * Reasigna un operario desde el chute con más holgura al cuello de
     * botella. Solo cede quien puede hacerlo sin que su cluster empeore: si
     * tiene límite debe seguir cumpliéndolo, y en ningún caso puede quedar por
     * encima del cuello actual (sería trasladar el problema). Devuelve el
     * chute donante o null si no hay ninguno viable.
     */
    Long moverOperario(Long cuello) {
      int operariosCuello = operarios(cuello);
      if (operariosCuello >= Limites.MAX_OPERARIOS) {
        return null;
      }
      int operariosCuelloDespues = operariosCuello + 1;
      BigDecimal tiempoCuelloActual = tiempo(cuello);
      Long mejor = null;
      BigDecimal mejorHolgura = null;
      int mejorOperarios = 0;
      for (Long donante : operarios.keySet()) {
        int operariosDonante = operarios(donante);
        if (donante.equals(cuello) || operariosDonante <= 1) {
          continue;
        }
        int operariosDonanteDespues = operariosDonante - 1;
        Set<Long> clusters = clustersPorChute.getOrDefault(donante, Set.of());
        boolean viable = true;
        BigDecimal holgura = null;
        for (Long cluster : clusters) {
          BigDecimal tiempo =
              tiempoClusterCon(
                  cluster, donante, operariosDonanteDespues, cuello, operariosCuelloDespues);
          BigDecimal limite = limites.getOrDefault(cluster, BigDecimal.ZERO);
          if (limite.signum() > 0 && tiempo.compareTo(limite.add(TOLERANCIA)) > 0) {
            viable = false;
            break;
          }
          BigDecimal techo = limite.signum() > 0 ? limite : tiempoCuelloActual;
          if (tiempo.compareTo(techo.add(TOLERANCIA)) > 0) {
            viable = false;
            break;
          }
          BigDecimal disponible =
              limite.signum() > 0
                  ? limite.subtract(tiempo)
                  : BigDecimal.valueOf(1_000_000).subtract(tiempo);
          holgura = holgura == null ? disponible : holgura.min(disponible);
        }
        if (!viable) {
          continue;
        }
        // Chute sin rutas: ceder su operario no afecta a ningún cluster.
        BigDecimal disponible = holgura == null ? BigDecimal.valueOf(1_000_000) : holgura;
        if (mejorHolgura == null
            || disponible.compareTo(mejorHolgura) > 0
            || (disponible.compareTo(mejorHolgura) == 0 && operariosDonante > mejorOperarios)) {
          mejor = donante;
          mejorHolgura = disponible;
          mejorOperarios = operariosDonante;
        }
      }
      if (mejor == null) {
        return null;
      }
      operarios.merge(mejor, -1, Integer::sum);
      operarios.merge(cuello, 1, Integer::sum);
      return mejor;
    }

    Map<Long, Integer> operariosModificados() {
      Map<Long, Integer> cambios = new HashMap<>();
      operarios.forEach(
          (chute, total) -> {
            if (!total.equals(operariosIniciales.get(chute))) {
              cambios.put(chute, total);
            }
          });
      return cambios;
    }
  }

  private void deshacerHistorial(Contexto ctx, Long actor) {
    Long archivoId = ctx.archivo().getId();
    List<HistorialBalanceo> logs = historialRepo.findByArchivoIdOrderByIdAsc(archivoId);
    // El chute original quedó congelado al cargar el Excel, así que basta un
    // UPDATE masivo (sin depender de reconstruir el historial movimiento a movimiento).
    rutaRepo.restaurarChutes(archivoId);
    operariosNetos(logs)
        .forEach(
            (chuteId, neto) -> {
              ParametroChute parametro = ctx.parametrosChute().get(chuteId);
              if (parametro != null) {
                int actual =
                    parametro.getOperarios() == null
                        ? OPERARIOS_INICIALES
                        : parametro.getOperarios();
                parametro.setOperarios(Math.max(1, actual - neto));
              }
            });
    historialRepo.borrarPorArchivo(archivoId);
    ctx.archivo().setEstado(ArchivoExcel.ESTADO_PROYECCION);
    tocar(ctx.archivo(), actor);
  }

  // ------------------------------------------------------------------
  // Dashboard comparativo
  // ------------------------------------------------------------------

  /**
   * Comparativo original vs. actual sobre la carga agregada por (cluster,
   * chute). La carga original sale del chute congelado al cargar el Excel
   * ({@code chute_original_id}); los operarios originales, del historial.
   */
  private DashboardSimulacionResponse calcularDashboard(Contexto ctx) {
    Long archivoId = ctx.archivo().getId();
    BigDecimal velocidad = ctx.config().getVelocidadBase();
    if (velocidad == null || velocidad.signum() <= 0) {
      velocidad = VELOCIDAD_INICIAL;
    }
    List<HistorialBalanceo> logs = historialRepo.findByArchivoIdOrderByIdAsc(archivoId);
    Map<Long, Integer> netosPorChute = operariosNetos(logs);
    int rutasMovidas = (int) logs.stream().filter(HistorialBalanceo::esMovimiento).count();
    int operariosMovidos = (int) logs.stream().filter(HistorialBalanceo::esMovimientoOperario).count();
    int operariosAgregados =
        (int)
            logs.stream()
                .filter(
                    (log) -> HistorialBalanceo.ACCION_ANADIR_OPERARIO.equals(log.getTipoAccion()))
                .count();

    Map<Par, Estadistica> cargaActual = new HashMap<>();
    for (CargaRutas fila : rutaRepo.cargaPorArchivo(archivoId)) {
      cargaActual.put(new Par(fila.getClusterId(), fila.getChuteId()), estadistica(fila));
    }
    Map<Par, Estadistica> cargaOriginal = new HashMap<>();
    for (CargaRutas fila : rutaRepo.cargaOriginalPorArchivo(archivoId)) {
      if (fila.getChuteId() != null) {
        cargaOriginal.put(new Par(fila.getClusterId(), fila.getChuteId()), estadistica(fila));
      }
    }

    Map<Long, Integer> salieron = new HashMap<>();
    Map<Long, Integer> llegaron = new HashMap<>();
    List<MovimientoChuteResponse> movimientos = new ArrayList<>();
    for (MovimientoRutas movimiento : rutaRepo.movimientosPorArchivo(archivoId)) {
      int rutas = Math.toIntExact(movimiento.getRutas());
      salieron.merge(movimiento.getOrigen(), rutas, Integer::sum);
      llegaron.merge(movimiento.getDestino(), rutas, Integer::sum);
      movimientos.add(
          new MovimientoChuteResponse(movimiento.getOrigen(), movimiento.getDestino(), rutas));
    }

    Map<Long, Integer> operariosActuales = operarios(ctx);
    Map<Long, Integer> operariosOriginales = new HashMap<>();
    operariosActuales.forEach(
        (chuteId, total) ->
            operariosOriginales.put(
                chuteId, Math.max(1, total - netosPorChute.getOrDefault(chuteId, 0))));

    Metricas actual = Metricas.de(cargaActual, operariosActuales, velocidad);
    Metricas original = Metricas.de(cargaOriginal, operariosOriginales, velocidad);
    BigDecimal tiempoOriginal = original.tiempoMaximo();
    BigDecimal tiempoBalanceado = actual.tiempoMaximo();
    Map<Long, BigDecimal> limites = limites(ctx);

    Set<Long> clusterIds = new LinkedHashSet<>(actual.porCluster().keySet());
    clusterIds.addAll(original.porCluster().keySet());
    List<ClusterComparativoResponse> clusters =
        clusterIds.stream()
            .sorted(Comparator.comparing(ctx::nombreCluster, ORDEN_NATURAL))
            .map(
                (clusterId) -> {
                  Estadistica antes = original.porCluster().getOrDefault(clusterId, Estadistica.VACIA);
                  Estadistica despues = actual.porCluster().getOrDefault(clusterId, Estadistica.VACIA);
                  BigDecimal limite = limites.getOrDefault(clusterId, BigDecimal.ZERO);
                  BigDecimal tiempo = actual.tiempoCluster(clusterId);
                  return new ClusterComparativoResponse(
                      clusterId,
                      ctx.nombreCluster(clusterId),
                      original.tiempoCluster(clusterId),
                      tiempo,
                      limite,
                      limite.signum() > 0 && tiempo.compareTo(limite.add(TOLERANCIA)) > 0,
                      antes.bultos(),
                      despues.bultos(),
                      antes.lineas(),
                      despues.lineas(),
                      antes.codEntero(),
                      despues.codEntero());
                })
            .toList();

    Map<Long, Set<String>> clustersPorChute = new HashMap<>();
    for (Map<Par, Estadistica> carga : List.of(cargaActual, cargaOriginal)) {
      carga.forEach(
          (par, valor) -> {
            if (valor.rutas() > 0) {
              clustersPorChute
                  .computeIfAbsent(par.chuteId(), (id) -> new TreeSet<>(ORDEN_NATURAL))
                  .add(ctx.nombreCluster(par.clusterId()));
            }
          });
    }
    Map<Long, Set<Long>> clustersOriginales = clustersPorChute(cargaOriginal);
    Map<Long, Set<Long>> clustersActuales = clustersPorChute(cargaActual);
    Map<Long, Integer> numeros = numerosCluster(ctx.clusters());

    Set<Long> chuteIds = new LinkedHashSet<>(actual.porChute().keySet());
    chuteIds.addAll(original.porChute().keySet());
    List<ChuteComparativoResponse> chutes =
        chuteIds.stream()
            .sorted(Comparator.comparing(ctx::nombreChute, ORDEN_NATURAL))
            .map(
                (chuteId) -> {
                  Estadistica antes = original.porChute().getOrDefault(chuteId, Estadistica.VACIA);
                  Estadistica despues = actual.porChute().getOrDefault(chuteId, Estadistica.VACIA);
                  BigDecimal tiempoAntes = original.tiempoChute(chuteId);
                  BigDecimal tiempoDespues = actual.tiempoChute(chuteId);
                  String estado;
                  if (tiempoDespues.compareTo(tiempoAntes.subtract(TOLERANCIA)) < 0) {
                    estado = "MEJORO";
                  } else if (tiempoDespues.compareTo(tiempoAntes.add(TOLERANCIA)) > 0) {
                    estado = "AUMENTO";
                  } else {
                    estado = "IGUAL";
                  }
                  return new ChuteComparativoResponse(
                      chuteId,
                      ctx.nombreChute(chuteId),
                      operariosOriginales.getOrDefault(chuteId, OPERARIOS_INICIALES),
                      operariosActuales.getOrDefault(chuteId, OPERARIOS_INICIALES),
                      Math.toIntExact(antes.rutas()),
                      Math.toIntExact(despues.rutas()),
                      salieron.getOrDefault(chuteId, 0),
                      llegaron.getOrDefault(chuteId, 0),
                      tiempoAntes,
                      tiempoDespues,
                      estado,
                      antes.bultos(),
                      despues.bultos(),
                      antes.unidades(),
                      despues.unidades(),
                      antes.lineas(),
                      despues.lineas(),
                      antes.codEntero(),
                      despues.codEntero(),
                      List.copyOf(clustersPorChute.getOrDefault(chuteId, Set.of())),
                      nombresCluster(clustersOriginales.get(chuteId), ctx),
                      nombresCluster(clustersActuales.get(chuteId), ctx),
                      mezclaIncompatible(clustersOriginales.get(chuteId), numeros),
                      mezclaIncompatible(clustersActuales.get(chuteId), numeros));
                })
            .toList();

    Estadistica total =
        actual.porCluster().values().stream().reduce(Estadistica.VACIA, Estadistica::sumar);
    SimulacionKpisResponse kpis =
        new SimulacionKpisResponse(
            tiempoOriginal,
            tiempoBalanceado,
            tiempoOriginal.subtract(tiempoBalanceado).max(BigDecimal.ZERO),
            rutasMovidas,
            operariosAgregados,
            operariosMovidos,
            total.bultos(),
            total.unidades(),
            total.lineas(),
            total.codEntero());
    List<Par> ocupados =
        cargaActual.entrySet().stream()
            .filter((entrada) -> entrada.getValue().rutas() > 0)
            .map(Map.Entry::getKey)
            .toList();
    return new DashboardSimulacionResponse(
        kpis, clusters, chutes, mezclas(ocupados, ctx), movimientos);
  }

  /**
   * Chutes que hoy mezclan CL1 y CL2 (pares cluster/chute con rutas). La carga
   * ya se aceptó con esos datos, así que se informa como advertencia: el
   * balanceo los separa primero y un movimiento manual tampoco puede crearlos.
   */
  private List<ChuteMezcladoResponse> mezclas(Collection<Par> ocupados, Contexto ctx) {
    Map<Long, Integer> numeros = numerosCluster(ctx.clusters());
    Map<Long, Set<Long>> clustersPorChute = new LinkedHashMap<>();
    ocupados.forEach(
        (par) ->
            clustersPorChute
                .computeIfAbsent(par.chuteId(), (id) -> new LinkedHashSet<>())
                .add(par.clusterId()));
    return clustersPorChute.entrySet().stream()
        .filter((entrada) -> mezclaIncompatible(entrada.getValue(), numeros))
        .sorted(
            Comparator.comparing(
                (Map.Entry<Long, Set<Long>> entrada) -> ctx.nombreChute(entrada.getKey()),
                ORDEN_NATURAL))
        .map(
            (entrada) ->
                new ChuteMezcladoResponse(
                    entrada.getKey(),
                    ctx.nombreChute(entrada.getKey()),
                    entrada.getValue().stream()
                        .map(ctx::nombreCluster)
                        .sorted(ORDEN_NATURAL)
                        .toList()))
        .toList();
  }

  /** Clusters con rutas en cada chute para una distribución de carga. */
  private static Map<Long, Set<Long>> clustersPorChute(Map<Par, Estadistica> carga) {
    Map<Long, Set<Long>> resultado = new HashMap<>();
    carga.forEach(
        (par, valor) -> {
          if (valor.rutas() > 0) {
            resultado.computeIfAbsent(par.chuteId(), (id) -> new HashSet<>()).add(par.clusterId());
          }
        });
    return resultado;
  }

  private static List<String> nombresCluster(Set<Long> clusters, Contexto ctx) {
    return clusters == null
        ? List.of()
        : clusters.stream().map(ctx::nombreCluster).sorted(ORDEN_NATURAL).toList();
  }

  /** True si entre los clusters hay más de uno de {@link #CLUSTERS_INCOMPATIBLES} (CL1 con CL2). */
  private static boolean mezclaIncompatible(Collection<Long> clusters, Map<Long, Integer> numeros) {
    if (clusters == null) {
      return false;
    }
    return clusters.stream()
            .map(numeros::get)
            .filter((numero) -> numero != null && CLUSTERS_INCOMPATIBLES.contains(numero))
            .distinct()
            .count()
        > 1;
  }

  /**
   * Número del cluster según {@link #CLUSTER_NUMERADO} («CL1», «CL 2», «C1»).
   * Null si el nombre no sigue ese formato (p. ej. «CP1»).
   */
  static Integer numeroCluster(String nombre) {
    if (nombre == null) {
      return null;
    }
    Matcher coincidencia =
        CLUSTER_NUMERADO.matcher(ESPACIOS.matcher(nombre).replaceAll("").toUpperCase(Locale.ROOT));
    return coincidencia.matches() ? aNumero(coincidencia.group(1)) : null;
  }

  private static Integer aNumero(String digitos) {
    try {
      return Integer.valueOf(digitos);
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private static Map<Long, Integer> numerosCluster(Map<Long, String> nombres) {
    Map<Long, Integer> numeros = new HashMap<>();
    nombres.forEach(
        (id, nombre) -> {
          Integer numero = numeroCluster(nombre);
          if (numero != null) {
            numeros.put(id, numero);
          }
        });
    return numeros;
  }

  /**
   * Cluster ya presente en el chute que impide agregar el cluster indicado:
   * solo CL1 y CL2 no pueden compartir chute. Null si la mezcla es válida.
   */
  private static Long clusterEnConflicto(
      Collection<Long> presentes, Long cluster, Map<Long, Integer> numeros) {
    Integer numero = numeros.get(cluster);
    if (numero == null || !CLUSTERS_INCOMPATIBLES.contains(numero)) {
      return null;
    }
    for (Long presente : presentes) {
      Integer otro = numeros.get(presente);
      if (otro != null && !otro.equals(numero) && CLUSTERS_INCOMPATIBLES.contains(otro)) {
        return presente;
      }
    }
    return null;
  }

  private static boolean mezclaRestringida(
      Collection<Long> presentes, Long cluster, Map<Long, Integer> numeros) {
    return clusterEnConflicto(presentes, cluster, numeros) != null;
  }

  private record Par(Long clusterId, Long chuteId) {}

  /** Ruta que el balanceo cambió de chute y el chute del que salió. */
  private record Traslado(RutaProyeccion ruta, Long origen) {}

  private record Estadistica(
      long rutas, BigDecimal bultos, BigDecimal unidades, BigDecimal lineas, long cod) {
    static final Estadistica VACIA =
        new Estadistica(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

    Estadistica sumar(Estadistica otra) {
      return new Estadistica(
          rutas + otra.rutas,
          bultos.add(otra.bultos),
          unidades.add(otra.unidades),
          lineas.add(otra.lineas),
          cod + otra.cod);
    }

    int codEntero() {
      return (int) Math.min(Integer.MAX_VALUE, cod);
    }
  }

  /** Totales y tiempos por chute y cluster para una distribución de carga. */
  private record Metricas(
      Map<Long, Estadistica> porChute,
      Map<Long, Estadistica> porCluster,
      Map<Long, BigDecimal> tiemposChute,
      Map<Long, BigDecimal> tiemposCluster) {

    static Metricas de(
        Map<Par, Estadistica> carga, Map<Long, Integer> operarios, BigDecimal velocidad) {
      Map<Long, Estadistica> porChute = new LinkedHashMap<>();
      Map<Long, Estadistica> porCluster = new LinkedHashMap<>();
      carga.forEach(
          (par, valor) -> {
            if (valor.rutas() > 0) {
              porChute.merge(par.chuteId(), valor, Estadistica::sumar);
              porCluster.merge(par.clusterId(), valor, Estadistica::sumar);
            }
          });
      Map<Long, BigDecimal> tiemposChute = new HashMap<>();
      porChute.forEach(
          (chute, valor) ->
              tiemposChute.put(
                  chute,
                  horas(valor.bultos(), velocidad, operarios.getOrDefault(chute, OPERARIOS_INICIALES))));
      Map<Long, BigDecimal> tiemposCluster = new HashMap<>();
      carga.forEach(
          (par, valor) -> {
            if (valor.rutas() > 0) {
              tiemposCluster.merge(
                  par.clusterId(),
                  tiemposChute.getOrDefault(par.chuteId(), BigDecimal.ZERO),
                  BigDecimal::max);
            }
          });
      return new Metricas(porChute, porCluster, tiemposChute, tiemposCluster);
    }

    BigDecimal tiempoChute(Long chute) {
      return tiemposChute.getOrDefault(chute, BigDecimal.ZERO);
    }

    BigDecimal tiempoCluster(Long cluster) {
      return tiemposCluster.getOrDefault(cluster, BigDecimal.ZERO);
    }

    BigDecimal tiempoMaximo() {
      return tiemposCluster.values().stream().reduce(BigDecimal.ZERO, BigDecimal::max);
    }
  }

  // ------------------------------------------------------------------
  // Contexto y respuestas
  // ------------------------------------------------------------------

  /** Registro, velocidad, parámetros y nombres de catálogo (sin rutas). */
  private record Contexto(
      ArchivoExcel archivo,
      SimulacionConfig config,
      Map<Long, ParametroCluster> parametrosCluster,
      Map<Long, ParametroChute> parametrosChute,
      Map<Long, String> clusters,
      Map<Long, String> chutes) {

    String nombreCluster(Long id) {
      return clusters.getOrDefault(id, "Cluster " + id);
    }

    String nombreChute(Long id) {
      return id == null ? null : chutes.getOrDefault(id, "Chute " + id);
    }
  }

  /**
   * Carga el contexto del registro. Con bloquear = true el registro queda
   * bloqueado (FOR UPDATE) hasta el fin de la transacción.
   */
  private Contexto contexto(Long archivoId, boolean bloquear) {
    ArchivoExcel archivo =
        (bloquear ? archivoRepo.bloquear(archivoId) : archivoRepo.findById(archivoId))
            .orElseThrow(
                () ->
                    new RecursoNoEncontradoException(
                        "No existe el registro con id " + archivoId + "."));
    SimulacionConfig config =
        configRepo
            .findById(archivoId)
            .orElseGet(
                () -> {
                  SimulacionConfig inicial = new SimulacionConfig();
                  inicial.setArchivoId(archivoId);
                  inicial.setVelocidadBase(VELOCIDAD_INICIAL);
                  return inicial;
                });
    Map<Long, ParametroCluster> parametrosCluster =
        parametroClusterRepo.findByIdArchivoId(archivoId).stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getClusterId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
    Map<Long, ParametroChute> parametrosChute =
        parametroChuteRepo.findByIdArchivoId(archivoId).stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getChuteId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
    Map<Long, String> clusters =
        clusterRepo.findAllById(parametrosCluster.keySet()).stream()
            .collect(Collectors.toMap(Cluster::getId, Cluster::getNombre));
    Map<Long, String> chutes =
        chuteRepo.findAllById(parametrosChute.keySet()).stream()
            .collect(Collectors.toMap(Chute::getId, Chute::getNombre));
    return new Contexto(archivo, config, parametrosCluster, parametrosChute, clusters, chutes);
  }

  private ConfiguracionSimulacionResponse aConfiguracion(Contexto ctx) {
    List<CargaRutas> carga = rutaRepo.cargaPorArchivo(ctx.archivo().getId());
    Map<Long, BigDecimal> limites = limites(ctx);
    Map<Long, Integer> operarios = operarios(ctx);
    List<ClusterParametroResponse> clusters =
        ctx.parametrosCluster().keySet().stream()
            .map(
                (id) ->
                    new ClusterParametroResponse(
                        id, ctx.nombreCluster(id), limites.getOrDefault(id, BigDecimal.ZERO)))
            .sorted(Comparator.comparing(ClusterParametroResponse::nombre, ORDEN_NATURAL))
            .toList();
    List<ChuteParametroResponse> chutes =
        ctx.parametrosChute().keySet().stream()
            .map((id) -> new ChuteParametroResponse(id, ctx.nombreChute(id), operarios.get(id)))
            .sorted(Comparator.comparing(ChuteParametroResponse::nombre, ORDEN_NATURAL))
            .toList();
    List<CargaSimulacionResponse> cargaResponse =
        carga.stream()
            .map(
                (fila) ->
                    new CargaSimulacionResponse(
                        fila.getClusterId(),
                        fila.getChuteId(),
                        fila.getRutas(),
                        fila.getBultos(),
                        fila.getUnidades(),
                        fila.getLineas(),
                        cod(fila.getCod())))
            .toList();
    return new ConfiguracionSimulacionResponse(
        aArchivo(ctx.archivo(), carga),
        ctx.config().getVelocidadBase(),
        operariosMaximos(ctx, operarios),
        clusters,
        chutes,
        cargaResponse,
        mezclas(
            carga.stream()
                .filter((fila) -> fila.getRutas() > 0)
                .map((fila) -> new Par(fila.getClusterId(), fila.getChuteId()))
                .toList(),
            ctx));
  }

  private ArchivoSimulacionResponse aArchivo(ArchivoExcel archivo, List<CargaRutas> carga) {
    long rutas = 0;
    BigDecimal bultos = BigDecimal.ZERO;
    BigDecimal unidades = BigDecimal.ZERO;
    BigDecimal lineas = BigDecimal.ZERO;
    BigDecimal cod = BigDecimal.ZERO;
    Set<Long> chutes = new HashSet<>();
    for (CargaRutas fila : carga) {
      rutas += fila.getRutas();
      bultos = bultos.add(fila.getBultos());
      unidades = unidades.add(fila.getUnidades());
      lineas = lineas.add(fila.getLineas());
      cod = cod.add(fila.getCod());
      chutes.add(fila.getChuteId());
    }
    return new ArchivoSimulacionResponse(
        archivo.getId(),
        archivo.getNombreRegistro(),
        archivo.getNombreArchivo(),
        archivo.getEtiqueta(),
        archivo.getColorHex(),
        archivo.getEstado(),
        rutas,
        chutes.size(),
        bultos,
        unidades,
        lineas,
        cod,
        archivo.getCreadoEn(),
        archivo.getActualizadoEn(),
        archivo.getCreadoPor(),
        archivo.getActualizadoPor());
  }

  private static Map<Long, BigDecimal> limites(Contexto ctx) {
    Map<Long, BigDecimal> limites = new HashMap<>();
    ctx.parametrosCluster()
        .forEach(
            (id, p) -> limites.put(id, p.getLimiteHoras() == null ? BigDecimal.ZERO : p.getLimiteHoras()));
    return limites;
  }

  private static Map<Long, Integer> operarios(Contexto ctx) {
    Map<Long, Integer> operarios = new HashMap<>();
    ctx.parametrosChute()
        .forEach(
            (id, p) ->
                operarios.put(
                    id, Math.max(1, p.getOperarios() == null ? OPERARIOS_INICIALES : p.getOperarios())));
    return operarios;
  }

  /**
   * Cambio neto de operarios por chute que produjo el historial. Un operario
   * añadido suma 1; uno reasignado resta 1 al origen y suma 1 al destino.
   */
  private static Map<Long, Integer> operariosNetos(List<HistorialBalanceo> logs) {
    Map<Long, Integer> netos = new HashMap<>();
    for (HistorialBalanceo log : logs) {
      if (log.esMovimientoOperario()) {
        if (log.getChuteOrigenId() != null) {
          netos.merge(log.getChuteOrigenId(), -1, Integer::sum);
        }
        if (log.getChuteDestinoId() != null) {
          netos.merge(log.getChuteDestinoId(), 1, Integer::sum);
        }
      } else if (HistorialBalanceo.ACCION_ANADIR_OPERARIO.equals(log.getTipoAccion())
          && log.getChuteModificadoId() != null) {
        netos.merge(log.getChuteModificadoId(), 1, Integer::sum);
      }
    }
    return netos;
  }

  /** Tope global del registro; si nunca se definió, la suma asignada actual. */
  private static int operariosMaximos(Contexto ctx, Map<Long, Integer> operarios) {
    Integer maximo = ctx.config().getOperariosMaximos();
    if (maximo != null && maximo > 0) {
      return maximo;
    }
    return operarios.values().stream().mapToInt(Integer::intValue).sum();
  }

  /** La consulta agrega COD como decimal; los contadores del balanceo son enteros. */
  private static long cod(BigDecimal valor) {
    return valor == null ? 0 : valor.longValue();
  }

  /** Convierte una fila agregada de rutas en los totales que usa el dashboard. */
  private static Estadistica estadistica(CargaRutas fila) {
    return new Estadistica(
        fila.getRutas(), fila.getBultos(), fila.getUnidades(), fila.getLineas(), cod(fila.getCod()));
  }

  /** Horas = bultos / (velocidad × operarios). */
  private static BigDecimal horas(BigDecimal bultos, BigDecimal velocidad, int operarios) {
    return bultos.divide(
        velocidad.multiply(BigDecimal.valueOf(Math.max(1, operarios))), ESCALA, RoundingMode.HALF_UP);
  }

  private void tocar(ArchivoExcel archivo, Long actor) {
    Auditoria.alActualizar(archivo, actor);
    archivoRepo.save(archivo);
  }

  private <T> Mono<ApiResponse<T>> leer(String operacion, Supplier<T> trabajo, String mensaje) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService." + operacion,
                    () -> transacciones.consultar(trabajo)))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok(mensaje, datos));
  }

  private <T> Mono<ApiResponse<T>> escribir(
      String operacion, Supplier<T> trabajo, String mensaje) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService." + operacion,
                    () -> transacciones.ejecutar(trabajo)))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok(mensaje, datos));
  }

  private static HistorialBalanceo movimiento(
      Long archivoId, Long rutaId, Long origen, Long destino, Long actor) {
    HistorialBalanceo log = nuevoLog(archivoId, HistorialBalanceo.ACCION_MOVER_RUTA, actor);
    log.setRutaId(rutaId);
    log.setChuteOrigenId(origen);
    log.setChuteDestinoId(destino);
    return log;
  }

  private static HistorialBalanceo operario(Long archivoId, Long chuteId, Long actor) {
    HistorialBalanceo log = nuevoLog(archivoId, HistorialBalanceo.ACCION_ANADIR_OPERARIO, actor);
    log.setChuteModificadoId(chuteId);
    return log;
  }

  private static HistorialBalanceo operarioMovido(
      Long archivoId, Long origen, Long destino, Long actor) {
    HistorialBalanceo log = nuevoLog(archivoId, HistorialBalanceo.ACCION_MOVER_OPERARIO, actor);
    log.setChuteOrigenId(origen);
    log.setChuteDestinoId(destino);
    return log;
  }

  private static HistorialBalanceo nuevoLog(Long archivoId, String accion, Long actor) {
    HistorialBalanceo log = new HistorialBalanceo();
    log.setArchivoId(archivoId);
    log.setTipoAccion(accion);
    log.setCreadoPor(actor);
    log.setCreadoEn(Instant.now());
    return log;
  }

  private static String escaparLike(String texto) {
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static int compararNatural(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      char ca = a.charAt(i);
      char cb = b.charAt(j);
      if (Character.isDigit(ca) && Character.isDigit(cb)) {
        int inicioA = i;
        int inicioB = j;
        while (i < a.length() && Character.isDigit(a.charAt(i))) {
          i++;
        }
        while (j < b.length() && Character.isDigit(b.charAt(j))) {
          j++;
        }
        String numeroA = a.substring(inicioA, i).replaceFirst("^0+(?!$)", "");
        String numeroB = b.substring(inicioB, j).replaceFirst("^0+(?!$)", "");
        int comparacion = Integer.compare(numeroA.length(), numeroB.length());
        if (comparacion != 0) {
          return comparacion;
        }
        comparacion = numeroA.compareTo(numeroB);
        if (comparacion != 0) {
          return comparacion;
        }
      } else {
        int comparacion = Character.compare(Character.toUpperCase(ca), Character.toUpperCase(cb));
        if (comparacion != 0) {
          return comparacion;
        }
        i++;
        j++;
      }
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }
}
