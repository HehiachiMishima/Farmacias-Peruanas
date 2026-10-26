package oracle.simulacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import oracle.common.config.Transacciones;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.common.response.ApiResponse;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Trazas;
import oracle.simulacion.model.ArchivoExcel;
import oracle.simulacion.model.Chute;
import oracle.simulacion.model.Cluster;
import oracle.simulacion.model.HistorialBalanceo;
import oracle.simulacion.model.ParametroChute;
import oracle.simulacion.model.ParametroCluster;
import oracle.simulacion.model.RutaProyeccion;
import oracle.simulacion.model.SimulacionConfig;
import oracle.simulacion.repository.ArchivoExcelRepository;
import oracle.simulacion.repository.ChuteRepository;
import oracle.simulacion.repository.ClusterRepository;
import oracle.simulacion.repository.HistorialBalanceoRepository;
import oracle.simulacion.repository.ParametroChuteRepository;
import oracle.simulacion.repository.ParametroClusterRepository;
import oracle.simulacion.repository.RutaProyeccionRepository;
import oracle.simulacion.repository.SimulacionConfigRepository;
import oracle.simulacion.request.ChuteOperariosRequest;
import oracle.simulacion.request.ClusterLimiteRequest;
import oracle.simulacion.request.ConfiguracionActualizarRequest;
import oracle.simulacion.request.MoverRutaRequest;
import oracle.simulacion.response.ArchivoSimulacionResponse;
import oracle.simulacion.response.BalanceoResponse;
import oracle.simulacion.response.ChuteComparativoResponse;
import oracle.simulacion.response.ChuteParametroResponse;
import oracle.simulacion.response.ClusterComparativoResponse;
import oracle.simulacion.response.ClusterParametroResponse;
import oracle.simulacion.response.ConfiguracionSimulacionResponse;
import oracle.simulacion.response.DashboardSimulacionResponse;
import oracle.simulacion.response.HistorialSimulacionResponse;
import oracle.simulacion.response.RutaSimulacionResponse;
import oracle.simulacion.response.SimulacionKpisResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Configuración de límites/operarios, balanceo y comparativos de simulación. */
@Slf4j
@Service
public class SimulacionConfiguracionService {

  private static final BigDecimal VELOCIDAD_INICIAL = new BigDecimal("100.00");
  private static final int OPERARIOS_INICIALES = 2;
  private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
  private static final int MAX_ITERACIONES = 1000;
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
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.obtener id=" + archivoId,
                    () -> transacciones.consultar(() -> aConfiguracion(cargarContexto(archivoId)))))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Configuración de simulación.", datos));
  }

  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> guardar(
      Long archivoId, ConfiguracionActualizarRequest req) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.guardar id=" + archivoId,
                    () ->
                        transacciones.ejecutar(
                            () -> {
                              boolean configExistente = configRepo.existsById(archivoId);
                              Contexto ctx = cargarContexto(archivoId);
                              SimulacionConfig config = ctx.config();
                              config.setVelocidadBase(req.velocidadBase());
                              if (configExistente) {
                                Auditoria.alActualizar(config, actor);
                              } else {
                                Auditoria.alCrear(config, actor);
                              }
                              configRepo.save(config);

                              if (req.clusters() != null) {
                                for (ClusterLimiteRequest limite : req.clusters()) {
                                  ParametroCluster parametro =
                                      ctx.parametrosCluster().stream()
                                          .filter(
                                              (p) ->
                                                  p.getId()
                                                      .getClusterId()
                                                      .equals(limite.clusterId()))
                                          .findFirst()
                                          .orElseThrow(
                                              () ->
                                                  new ConflictoException(
                                                      "El cluster "
                                                          + limite.clusterId()
                                                          + " no pertenece al registro."));
                                  parametro.setLimiteHoras(limite.limiteHoras());
                                  parametroClusterRepo.save(parametro);
                                }
                              }

                              if (req.chutes() != null) {
                                for (ChuteOperariosRequest operarios : req.chutes()) {
                                  ParametroChute parametro =
                                      ctx.parametrosChute().stream()
                                          .filter(
                                              (p) ->
                                                  p.getId().getChuteId().equals(operarios.chuteId()))
                                          .findFirst()
                                          .orElseThrow(
                                              () ->
                                                  new ConflictoException(
                                                      "El chute "
                                                          + operarios.chuteId()
                                                          + " no pertenece al registro."));
                                  parametro.setOperarios(operarios.operarios());
                                  parametroChuteRepo.save(parametro);
                                }
                              }

                              // El estado no cambia al guardar parámetros: si el registro ya
                              // estaba balanceado, las rutas movidas siguen siendo válidas y
                              // el dashboard debe continuar disponible.
                              Auditoria.alActualizar(ctx.archivo(), actor);
                              archivoRepo.save(ctx.archivo());
                              return aConfiguracion(cargarContexto(archivoId));
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Parámetros guardados.", datos));
  }

  public Mono<ApiResponse<ConfiguracionSimulacionResponse>> mover(
      Long archivoId, Long rutaId, MoverRutaRequest req) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.mover archivo=" + archivoId + " ruta=" + rutaId,
                    () ->
                        transacciones.ejecutar(
                            () -> {
                              Contexto ctx = cargarContexto(archivoId);
                              RutaProyeccion ruta =
                                  ctx.rutas().stream()
                                      .filter((r) -> r.getId().equals(rutaId))
                                      .findFirst()
                                      .orElseThrow(
                                          () ->
                                              new RecursoNoEncontradoException(
                                                  "No existe la ruta " + rutaId + "."));
                              Chute destino =
                                  ctx.chutesPorId().get(req.chuteId());
                              if (destino == null) {
                                throw new ConflictoException(
                                    "El chute " + req.chuteId() + " no pertenece al registro.");
                              }
                              if (ruta.getChuteId().equals(req.chuteId())) {
                                throw new ConflictoException(
                                    "La ruta ya pertenece al chute " + destino.getNombre() + ".");
                              }
                              Long origen = ruta.getChuteId();
                              ruta.setChuteId(req.chuteId());
                              rutaRepo.save(ruta);
                              registrarMovimiento(
                                  archivoId,
                                  ruta.getId(),
                                  origen,
                                  req.chuteId(),
                                  actor);
                              // Mover una ruta no invalida el comparativo previo: el
                              // historial permite reconstruir la proyección original.
                              Auditoria.alActualizar(ctx.archivo(), actor);
                              archivoRepo.save(ctx.archivo());
                              return aConfiguracion(cargarContexto(archivoId));
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Ruta movida.", datos));
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
                              Contexto ctx = cargarContexto(archivoId);
                              List<HistorialBalanceo> logs =
                                  historialRepo.findByArchivoIdOrderByIdAsc(archivoId);
                              Map<Long, RutaProyeccion> rutasPorId =
                                  ctx.rutas().stream()
                                      .collect(
                                          Collectors.toMap(
                                              RutaProyeccion::getId, Function.identity()));
                              Map<Long, ParametroChute> parametrosPorChute =
                                  ctx.parametrosChute().stream()
                                      .collect(
                                          Collectors.toMap(
                                              (p) -> p.getId().getChuteId(), Function.identity()));
                              Set<RutaProyeccion> rutasTocadas = new HashSet<>();
                              Set<ParametroChute> parametrosTocados = new HashSet<>();

                              for (int i = logs.size() - 1; i >= 0; i--) {
                                HistorialBalanceo log = logs.get(i);
                                if (HistorialBalanceo.ACCION_MOVER_RUTA.equals(log.getTipoAccion())) {
                                  RutaProyeccion ruta = rutasPorId.get(log.getRutaId());
                                  if (ruta != null && log.getChuteOrigenId() != null) {
                                    ruta.setChuteId(log.getChuteOrigenId());
                                    rutasTocadas.add(ruta);
                                  }
                                } else if (HistorialBalanceo.ACCION_ANADIR_OPERARIO.equals(
                                    log.getTipoAccion())) {
                                  ParametroChute parametro =
                                      parametrosPorChute.get(log.getChuteModificadoId());
                                  if (parametro != null && parametro.getOperarios() > 1) {
                                    parametro.setOperarios(parametro.getOperarios() - 1);
                                    parametrosTocados.add(parametro);
                                  }
                                }
                              }
                              if (!rutasTocadas.isEmpty()) {
                                rutaRepo.saveAll(rutasTocadas);
                              }
                              if (!parametrosTocados.isEmpty()) {
                                parametroChuteRepo.saveAll(parametrosTocados);
                              }
                              historialRepo.deleteByArchivoId(archivoId);
                              ctx.archivo().setEstado(ArchivoExcel.ESTADO_PROYECCION);
                              Auditoria.alActualizar(ctx.archivo(), actor);
                              archivoRepo.save(ctx.archivo());
                              return null;
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(ApiResponse.<Void>ok("Datos restablecidos.", null));
  }

  public Mono<ApiResponse<List<HistorialSimulacionResponse>>> historial(Long archivoId) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.historial id=" + archivoId,
                    () ->
                        transacciones.consultar(
                            () -> {
                              Contexto ctx = cargarContexto(archivoId);
                              return historialRepo.findByArchivoIdOrderByIdAsc(archivoId).stream()
                                  .sorted(
                                      Comparator.comparing(HistorialBalanceo::getId).reversed())
                                  .map(
                                      (log) ->
                                          new HistorialSimulacionResponse(
                                              log.getId(),
                                              log.getTipoAccion(),
                                              log.getRutaId(),
                                              nombreRuta(ctx, log.getRutaId()),
                                              nombreChute(ctx, log.getChuteOrigenId()),
                                              nombreChute(ctx, log.getChuteDestinoId()),
                                              nombreChute(ctx, log.getChuteModificadoId()),
                                              log.getCreadoEn(),
                                              log.getCreadoPor()))
                                  .toList();
                            })))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Historial de balanceo.", datos));
  }

  public Mono<ApiResponse<DashboardSimulacionResponse>> dashboard(Long archivoId) {
    return Mono.fromCallable(
            () ->
                Trazas.medir(
                    log,
                    "SimulacionConfiguracionService.dashboard id=" + archivoId,
                    () -> transacciones.consultar(() -> calcularDashboard(archivoId))))
        .subscribeOn(Schedulers.boundedElastic())
        .map((datos) -> ApiResponse.ok("Dashboard de simulación.", datos));
  }

  // ------------------------------------------------------------------
  // Balanceo
  // ------------------------------------------------------------------

  private BalanceoResponse ejecutarBalanceo(Long archivoId, Long actor) {
    Contexto ctx = cargarContexto(archivoId);
    BigDecimal velocidad = ctx.config().getVelocidadBase();
    if (velocidad == null || velocidad.signum() <= 0) {
      throw new ConflictoException("La velocidad base debe ser mayor a cero.");
    }

    Map<Long, BigDecimal> limites =
        ctx.parametrosCluster().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getClusterId(),
                    (p) -> p.getLimiteHoras() == null ? BigDecimal.ZERO : p.getLimiteHoras()));
    if (limites.values().stream().noneMatch((limite) -> limite.signum() > 0)) {
      throw new ConflictoException(
          "Define al menos un límite de horas por cluster antes de balancear.");
    }

    Map<Long, Integer> operarios =
        ctx.parametrosChute().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getChuteId(),
                    (p) -> p.getOperarios() == null ? OPERARIOS_INICIALES : p.getOperarios()));
    for (RutaProyeccion ruta : ctx.rutas()) {
      operarios.putIfAbsent(ruta.getChuteId(), OPERARIOS_INICIALES);
    }

    Map<Long, BigDecimal> tiemposChute = calcularTiemposChute(ctx.rutas(), operarios, velocidad);
    Map<Long, BigDecimal> tiemposCluster = calcularTiemposCluster(ctx.rutas(), tiemposChute);

    List<HistorialBalanceo> logs = new ArrayList<>();
    Set<RutaProyeccion> rutasTocadas = new HashSet<>();
    Set<Long> chutesConOperariosTocados = new HashSet<>();
    int movidas = 0;
    int operariosAgregados = 0;
    int iteraciones = 0;

    while (iteraciones < MAX_ITERACIONES) {
      iteraciones++;

      Long peorCluster = null;
      BigDecimal mayorDeficit = BigDecimal.ZERO;
      for (Map.Entry<Long, BigDecimal> entry : tiemposCluster.entrySet()) {
        BigDecimal limite = limites.getOrDefault(entry.getKey(), BigDecimal.ZERO);
        if (limite.signum() <= 0) {
          continue;
        }
        BigDecimal deficit = entry.getValue().subtract(limite);
        if (deficit.compareTo(TOLERANCIA) > 0 && deficit.compareTo(mayorDeficit) > 0) {
          mayorDeficit = deficit;
          peorCluster = entry.getKey();
        }
      }
      if (peorCluster == null) {
        break;
      }

      final Long clusterObjetivo = peorCluster;
      List<RutaProyeccion> rutasCluster =
          ctx.rutas().stream()
              .filter((ruta) -> ruta.getClusterId().equals(clusterObjetivo))
              .toList();
      Long chuteCuello = null;
      BigDecimal mayorTiempo = BigDecimal.ZERO;
      for (RutaProyeccion ruta : rutasCluster) {
        BigDecimal tiempo = tiemposChute.getOrDefault(ruta.getChuteId(), BigDecimal.ZERO);
        if (tiempo.compareTo(mayorTiempo) > 0) {
          mayorTiempo = tiempo;
          chuteCuello = ruta.getChuteId();
        }
      }
      if (chuteCuello == null) {
        break;
      }

      final Long cuello = chuteCuello;
      RutaProyeccion rutaAMover =
          rutasCluster.stream()
              .filter((ruta) -> ruta.getChuteId().equals(cuello))
              .max(Comparator.comparing(RutaProyeccion::getTotalBultos))
              .orElse(null);

      boolean movida = false;
      if (rutaAMover != null) {
        Long chuteRapido = null;
        BigDecimal menorTiempo = null;
        for (Map.Entry<Long, Integer> entry : operarios.entrySet()) {
          if (entry.getKey().equals(cuello)) {
            continue;
          }
          BigDecimal tiempo = tiemposChute.getOrDefault(entry.getKey(), BigDecimal.ZERO);
          if (menorTiempo == null || tiempo.compareTo(menorTiempo) < 0) {
            menorTiempo = tiempo;
            chuteRapido = entry.getKey();
          }
        }
        if (chuteRapido != null && menorTiempo != null) {
          int operariosDestino = operarios.getOrDefault(chuteRapido, OPERARIOS_INICIALES);
          BigDecimal tiempoDestino =
              menorTiempo.add(
                  rutaAMover
                      .getTotalBultos()
                      .divide(
                          velocidad.multiply(BigDecimal.valueOf(operariosDestino)),
                          4,
                          RoundingMode.HALF_UP));
          if (tiempoDestino.compareTo(mayorTiempo) < 0) {
            Long origen = rutaAMover.getChuteId();
            rutaAMover.setChuteId(chuteRapido);
            rutasTocadas.add(rutaAMover);
            movidas++;
            logs.add(
                logMovimiento(
                    archivoId, rutaAMover.getId(), origen, chuteRapido, actor));
            tiemposChute = calcularTiemposChute(ctx.rutas(), operarios, velocidad);
            tiemposCluster = calcularTiemposCluster(ctx.rutas(), tiemposChute);
            movida = true;
          }
        }
      }

      if (!movida) {
        operarios.merge(cuello, 1, Integer::sum);
        chutesConOperariosTocados.add(cuello);
        operariosAgregados++;
        logs.add(logOperario(archivoId, cuello, actor));
        tiemposChute = calcularTiemposChute(ctx.rutas(), operarios, velocidad);
        tiemposCluster = calcularTiemposCluster(ctx.rutas(), tiemposChute);
      }
    }

    if (!rutasTocadas.isEmpty()) {
      rutaRepo.saveAll(rutasTocadas);
    }
    if (!chutesConOperariosTocados.isEmpty()) {
      List<ParametroChute> parametros =
          ctx.parametrosChute().stream()
              .filter((p) -> chutesConOperariosTocados.contains(p.getId().getChuteId()))
              .toList();
      for (ParametroChute parametro : parametros) {
        parametro.setOperarios(
            operarios.getOrDefault(parametro.getId().getChuteId(), OPERARIOS_INICIALES));
      }
      parametroChuteRepo.saveAll(parametros);
    }
    if (!logs.isEmpty()) {
      historialRepo.saveAll(logs);
    }
    ctx.archivo().setEstado(ArchivoExcel.ESTADO_BALANCEADO);
    Auditoria.alActualizar(ctx.archivo(), actor);
    archivoRepo.save(ctx.archivo());

    String mensaje;
    if (movidas == 0 && operariosAgregados == 0) {
      mensaje = "Los clusters ya cumplían sus límites o no se definieron límites de tiempo.";
    } else {
      StringBuilder texto = new StringBuilder("Balanceo finalizado.");
      if (movidas > 0) {
        texto.append(" ").append(movidas).append(" ruta(s) movida(s).");
      }
      if (operariosAgregados > 0) {
        texto.append(" ").append(operariosAgregados).append(" operario(s) añadido(s).");
      }
      mensaje = texto.toString();
    }
    return new BalanceoResponse(movidas, operariosAgregados, mensaje);
  }

  private Map<Long, BigDecimal> calcularTiemposChute(
      List<RutaProyeccion> rutas, Map<Long, Integer> operarios, BigDecimal velocidad) {
    Map<Long, BigDecimal> bultos = new HashMap<>();
    for (RutaProyeccion ruta : rutas) {
      bultos.merge(ruta.getChuteId(), ruta.getTotalBultos(), BigDecimal::add);
    }
    Map<Long, BigDecimal> tiempos = new HashMap<>();
    for (Map.Entry<Long, BigDecimal> entry : bultos.entrySet()) {
      int ops = operarios.getOrDefault(entry.getKey(), OPERARIOS_INICIALES);
      tiempos.put(
          entry.getKey(),
          entry
              .getValue()
              .divide(velocidad.multiply(BigDecimal.valueOf(ops)), 4, RoundingMode.HALF_UP));
    }
    return tiempos;
  }

  private Map<Long, BigDecimal> calcularTiemposCluster(
      List<RutaProyeccion> rutas, Map<Long, BigDecimal> tiemposChute) {
    Map<Long, BigDecimal> tiempos = new HashMap<>();
    for (RutaProyeccion ruta : rutas) {
      BigDecimal tiempo = tiemposChute.getOrDefault(ruta.getChuteId(), BigDecimal.ZERO);
      tiempos.merge(ruta.getClusterId(), tiempo, (actual, nuevo) -> actual.max(nuevo));
    }
    return tiempos;
  }

  // ------------------------------------------------------------------
  // Dashboard comparativo
  // ------------------------------------------------------------------

  private DashboardSimulacionResponse calcularDashboard(Long archivoId) {
    Contexto ctx = cargarContexto(archivoId);
    BigDecimal velocidad = ctx.config().getVelocidadBase();
    if (velocidad == null || velocidad.signum() <= 0) {
      velocidad = VELOCIDAD_INICIAL;
    }
    List<HistorialBalanceo> logs = historialRepo.findByArchivoIdOrderByIdAsc(archivoId);

    Map<Long, Long> chuteOriginalPorRuta = new HashMap<>();
    Map<Long, Integer> operariosAgregadosPorChute = new HashMap<>();
    int rutasMovidas = 0;
    int operariosAgregados = 0;
    for (HistorialBalanceo log : logs) {
      if (HistorialBalanceo.ACCION_MOVER_RUTA.equals(log.getTipoAccion())) {
        rutasMovidas++;
        if (log.getRutaId() != null && log.getChuteOrigenId() != null) {
          chuteOriginalPorRuta.putIfAbsent(log.getRutaId(), log.getChuteOrigenId());
        }
      } else if (HistorialBalanceo.ACCION_ANADIR_OPERARIO.equals(log.getTipoAccion())
          && log.getChuteModificadoId() != null) {
        operariosAgregados++;
        operariosAgregadosPorChute.merge(log.getChuteModificadoId(), 1, Integer::sum);
      }
    }

    Map<Long, Integer> operariosActuales =
        ctx.parametrosChute().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getChuteId(),
                    (p) -> Math.max(1, p.getOperarios() == null ? OPERARIOS_INICIALES : p.getOperarios())));
    Map<Long, Integer> operariosOriginales = new HashMap<>();
    operariosActuales.forEach(
        (chuteId, operarios) ->
            operariosOriginales.put(
                chuteId,
                Math.max(1, operarios - operariosAgregadosPorChute.getOrDefault(chuteId, 0))));

    Map<Long, Long> chuteActualPorRuta =
        ctx.rutas().stream()
            .collect(Collectors.toMap(RutaProyeccion::getId, RutaProyeccion::getChuteId));
    Map<Long, Long> chuteOriginalPorRutaFinal = new HashMap<>(chuteActualPorRuta);
    chuteOriginalPorRutaFinal.putAll(chuteOriginalPorRuta);

    Metricas actuales = calcularMetricas(ctx.rutas(), chuteActualPorRuta, operariosActuales, velocidad);
    Metricas originales =
        calcularMetricas(ctx.rutas(), chuteOriginalPorRutaFinal, operariosOriginales, velocidad);

    BigDecimal maxOriginal = originales.tiemposCluster().values().stream()
        .max(BigDecimal::compareTo)
        .orElse(BigDecimal.ZERO);
    BigDecimal maxActual = actuales.tiemposCluster().values().stream()
        .max(BigDecimal::compareTo)
        .orElse(BigDecimal.ZERO);
    BigDecimal tiempoAhorrado = maxOriginal.subtract(maxActual).max(BigDecimal.ZERO);

    Map<Long, BigDecimal> limites =
        ctx.parametrosCluster().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getClusterId(),
                    (p) -> p.getLimiteHoras() == null ? BigDecimal.ZERO : p.getLimiteHoras()));

    Set<Long> clusterIds = new LinkedHashSet<>();
    clusterIds.addAll(actuales.estadisticasCluster().keySet());
    clusterIds.addAll(originales.estadisticasCluster().keySet());
    List<ClusterComparativoResponse> clusters =
        clusterIds.stream()                      .sorted(
                          Comparator.comparing(
                              (Long id) -> nombreCluster(ctx, id), ORDEN_NATURAL))
                      .map(
                          (clusterId) -> {
                  Estadistica original =
                      originales.estadisticasCluster().getOrDefault(clusterId, Estadistica.VACIA);
                  Estadistica balanceado =
                      actuales.estadisticasCluster().getOrDefault(clusterId, Estadistica.VACIA);
                  BigDecimal limite = limites.getOrDefault(clusterId, BigDecimal.ZERO);
                  BigDecimal tiempoBalanceado =
                      actuales.tiemposCluster().getOrDefault(clusterId, BigDecimal.ZERO);
                  boolean excede =
                      limite.signum() > 0
                          && tiempoBalanceado.compareTo(limite.add(TOLERANCIA)) > 0;
                  return new ClusterComparativoResponse(
                      nombreCluster(ctx, clusterId),
                      originales.tiemposCluster().getOrDefault(clusterId, BigDecimal.ZERO),
                      tiempoBalanceado,
                      limite,
                      excede,
                      original.bultos(),
                      balanceado.bultos(),
                      original.lineas(),
                      balanceado.lineas(),
                      original.cod(),
                      balanceado.cod());
                })
            .toList();

    Set<Long> chuteIds = new LinkedHashSet<>();
    chuteIds.addAll(actuales.estadisticasChute().keySet());
    chuteIds.addAll(originales.estadisticasChute().keySet());
    List<ChuteComparativoResponse> chutes =
        chuteIds.stream()
            .sorted(Comparator.comparing((Long id) -> nombreChute(ctx, id), ORDEN_NATURAL))
            .map(
                (chuteId) -> {
                  Estadistica original =
                      originales.estadisticasChute().getOrDefault(chuteId, Estadistica.VACIA);
                  Estadistica balanceado =
                      actuales.estadisticasChute().getOrDefault(chuteId, Estadistica.VACIA);
                  BigDecimal tiempoOriginal =
                      originales.tiemposChute().getOrDefault(chuteId, BigDecimal.ZERO);
                  BigDecimal tiempoBalanceado =
                      actuales.tiemposChute().getOrDefault(chuteId, BigDecimal.ZERO);
                  String estado;
                  if (tiempoBalanceado.compareTo(tiempoOriginal.subtract(TOLERANCIA)) < 0) {
                    estado = "MEJORO";
                  } else if (tiempoBalanceado.compareTo(tiempoOriginal.add(TOLERANCIA)) > 0) {
                    estado = "AUMENTO";
                  } else {
                    estado = "IGUAL";
                  }
                  return new ChuteComparativoResponse(
                      nombreChute(ctx, chuteId),
                      operariosOriginales.getOrDefault(chuteId, OPERARIOS_INICIALES),
                      operariosActuales.getOrDefault(chuteId, OPERARIOS_INICIALES),
                      tiempoOriginal,
                      tiempoBalanceado,
                      estado,
                      original.bultos(),
                      balanceado.bultos(),
                      original.unidades(),
                      balanceado.unidades(),
                      original.lineas(),
                      balanceado.lineas(),
                      original.cod(),
                      balanceado.cod());
                })
            .toList();

    BigDecimal totalBultos = BigDecimal.ZERO;
    BigDecimal totalUnidades = BigDecimal.ZERO;
    BigDecimal totalLineas = BigDecimal.ZERO;
    int totalCod = 0;
    for (Estadistica estadistica : actuales.estadisticasCluster().values()) {
      totalBultos = totalBultos.add(estadistica.bultos());
      totalUnidades = totalUnidades.add(estadistica.unidades());
      totalLineas = totalLineas.add(estadistica.lineas());
      totalCod += estadistica.cod();
    }

    SimulacionKpisResponse kpis =
        new SimulacionKpisResponse(
            tiempoAhorrado,
            rutasMovidas,
            operariosAgregados,
            totalBultos,
            totalUnidades,
            totalLineas,
            totalCod);
    return new DashboardSimulacionResponse(kpis, clusters, chutes);
  }

  private Metricas calcularMetricas(
      List<RutaProyeccion> rutas,
      Map<Long, Long> chutePorRuta,
      Map<Long, Integer> operarios,
      BigDecimal velocidad) {
    Map<Long, Estadistica> estadisticasChute = new LinkedHashMap<>();
    Map<Long, Estadistica> estadisticasCluster = new LinkedHashMap<>();
    Map<Long, BigDecimal> bultosPorChute = new HashMap<>();
    for (RutaProyeccion ruta : rutas) {
      Long chuteId = chutePorRuta.getOrDefault(ruta.getId(), ruta.getChuteId());
      bultosPorChute.merge(chuteId, ruta.getTotalBultos(), BigDecimal::add);
      estadisticasChute.merge(
          chuteId,
          new Estadistica(
              ruta.getTotalBultos(),
              ruta.getTotalUnidades(),
              ruta.getTotalLineas(),
              ruta.getConteoCod() == null ? 0 : ruta.getConteoCod()),
          Estadistica::sumar);
      estadisticasCluster.merge(
          ruta.getClusterId(),
          new Estadistica(
              ruta.getTotalBultos(),
              ruta.getTotalUnidades(),
              ruta.getTotalLineas(),
              ruta.getConteoCod() == null ? 0 : ruta.getConteoCod()),
          Estadistica::sumar);
    }
    Map<Long, BigDecimal> tiemposChute = new HashMap<>();
    for (Map.Entry<Long, BigDecimal> entry : bultosPorChute.entrySet()) {
      int ops = operarios.getOrDefault(entry.getKey(), OPERARIOS_INICIALES);
      tiemposChute.put(
          entry.getKey(),
          entry
              .getValue()
              .divide(velocidad.multiply(BigDecimal.valueOf(ops)), 4, RoundingMode.HALF_UP));
    }
    Map<Long, BigDecimal> tiemposCluster = new HashMap<>();
    for (RutaProyeccion ruta : rutas) {
      Long chuteId = chutePorRuta.getOrDefault(ruta.getId(), ruta.getChuteId());
      BigDecimal tiempo = tiemposChute.getOrDefault(chuteId, BigDecimal.ZERO);
      tiemposCluster.merge(ruta.getClusterId(), tiempo, (a, b) -> a.max(b));
    }
    return new Metricas(tiemposChute, tiemposCluster, estadisticasChute, estadisticasCluster);
  }

  private record Estadistica(
      BigDecimal bultos, BigDecimal unidades, BigDecimal lineas, int cod) {
    static final Estadistica VACIA =
        new Estadistica(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

    Estadistica sumar(Estadistica otra) {
      return new Estadistica(
          bultos.add(otra.bultos),
          unidades.add(otra.unidades),
          lineas.add(otra.lineas),
          cod + otra.cod);
    }
  }

  private record Metricas(
      Map<Long, BigDecimal> tiemposChute,
      Map<Long, BigDecimal> tiemposCluster,
      Map<Long, Estadistica> estadisticasChute,
      Map<Long, Estadistica> estadisticasCluster) {}

  // ------------------------------------------------------------------
  // Contexto y respuestas
  // ------------------------------------------------------------------

  private Contexto cargarContexto(Long archivoId) {
    ArchivoExcel archivo =
        archivoRepo
            .findById(archivoId)
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
    List<ParametroCluster> parametrosCluster = parametroClusterRepo.findByIdArchivoId(archivoId);
    List<ParametroChute> parametrosChute = parametroChuteRepo.findByIdArchivoId(archivoId);
    List<RutaProyeccion> rutas = rutaRepo.findByArchivoIdOrderByIdAsc(archivoId);

    Set<Long> clusterIds = new LinkedHashSet<>();
    parametrosCluster.forEach((p) -> clusterIds.add(p.getId().getClusterId()));
    rutas.forEach((ruta) -> clusterIds.add(ruta.getClusterId()));
    Set<Long> chuteIds = new LinkedHashSet<>();
    parametrosChute.forEach((p) -> chuteIds.add(p.getId().getChuteId()));
    rutas.forEach((ruta) -> chuteIds.add(ruta.getChuteId()));

    Map<Long, Cluster> clustersPorId =
        clusterRepo.findAllById(clusterIds).stream()
            .collect(Collectors.toMap(Cluster::getId, Function.identity()));
    Map<Long, Chute> chutesPorId =
        chuteRepo.findAllById(chuteIds).stream()
            .collect(Collectors.toMap(Chute::getId, Function.identity()));

    return new Contexto(
        archivo,
        config,
        List.copyOf(clustersPorId.values()),
        List.copyOf(chutesPorId.values()),
        parametrosCluster,
        parametrosChute,
        rutas,
        clustersPorId,
        chutesPorId);
  }

  private ConfiguracionSimulacionResponse aConfiguracion(Contexto ctx) {
    Map<Long, BigDecimal> limites =
        ctx.parametrosCluster().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getClusterId(),
                    (p) -> p.getLimiteHoras() == null ? BigDecimal.ZERO : p.getLimiteHoras()));
    Map<Long, Integer> operarios =
        ctx.parametrosChute().stream()
            .collect(
                Collectors.toMap(
                    (p) -> p.getId().getChuteId(),
                    (p) -> Math.max(1, p.getOperarios() == null ? OPERARIOS_INICIALES : p.getOperarios())));

    List<ClusterParametroResponse> clusters =
        ctx.parametrosCluster().stream()
            .map(
                (p) -> {
                  Long id = p.getId().getClusterId();
                  Cluster cluster = ctx.clustersPorId().get(id);
                  return new ClusterParametroResponse(
                      id,
                      cluster == null ? "Cluster " + id : cluster.getNombre(),
                      limites.getOrDefault(id, BigDecimal.ZERO));
                })
            .sorted(Comparator.comparing(ClusterParametroResponse::nombre, ORDEN_NATURAL))
            .toList();

    List<ChuteParametroResponse> chutes =
        ctx.parametrosChute().stream()
            .map(
                (p) -> {
                  Long id = p.getId().getChuteId();
                  Chute chute = ctx.chutesPorId().get(id);
                  return new ChuteParametroResponse(
                      id,
                      chute == null ? "Chute " + id : chute.getNombre(),
                      operarios.getOrDefault(id, OPERARIOS_INICIALES));
                })
            .sorted(Comparator.comparing(ChuteParametroResponse::nombre, ORDEN_NATURAL))
            .toList();

    List<RutaSimulacionResponse> rutas =
        ctx.rutas().stream()
            .map(
                (ruta) ->
                    new RutaSimulacionResponse(
                        ruta.getId(),
                        ruta.getChuteId(),
                        nombreChute(ctx, ruta.getChuteId()),
                        ruta.getClusterId(),
                        nombreCluster(ctx, ruta.getClusterId()),
                        ruta.getCodigoRuta(),
                        ruta.getTotalBultos(),
                        ruta.getTotalUnidades(),
                        ruta.getTotalLineas(),
                        ruta.getConteoCod()))
            .toList();

    return new ConfiguracionSimulacionResponse(
        aArchivo(ctx), ctx.config().getVelocidadBase(), clusters, chutes, rutas);
  }

  private ArchivoSimulacionResponse aArchivo(Contexto ctx) {
    BigDecimal bultos = BigDecimal.ZERO;
    BigDecimal unidades = BigDecimal.ZERO;
    BigDecimal lineas = BigDecimal.ZERO;
    Set<Long> chutes = new HashSet<>();
    for (RutaProyeccion ruta : ctx.rutas()) {
      bultos = bultos.add(ruta.getTotalBultos());
      unidades = unidades.add(ruta.getTotalUnidades());
      lineas = lineas.add(ruta.getTotalLineas());
      chutes.add(ruta.getChuteId());
    }
    ArchivoExcel archivo = ctx.archivo();
    return new ArchivoSimulacionResponse(
        archivo.getId(),
        archivo.getNombreRegistro(),
        archivo.getNombreArchivo(),
        archivo.getEtiqueta(),
        archivo.getColorHex(),
        archivo.getEstado(),
        ctx.rutas().size(),
        chutes.size(),
        bultos,
        unidades,
        lineas,
        archivo.getCreadoEn(),
        archivo.getActualizadoEn(),
        archivo.getCreadoPor(),
        archivo.getActualizadoPor());
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
        int comparacion =
            Character.compare(Character.toUpperCase(ca), Character.toUpperCase(cb));
        if (comparacion != 0) {
          return comparacion;
        }
        i++;
        j++;
      }
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }

  private String nombreCluster(Contexto ctx, Long id) {
    Cluster cluster = ctx.clustersPorId().get(id);
    return cluster == null ? "Cluster " + id : cluster.getNombre();
  }

  private String nombreChute(Contexto ctx, Long id) {
    if (id == null) {
      return null;
    }
    Chute chute = ctx.chutesPorId().get(id);
    return chute == null ? "Chute " + id : chute.getNombre();
  }

  private String nombreRuta(Contexto ctx, Long rutaId) {
    if (rutaId == null) {
      return null;
    }
    return ctx.rutas().stream()
        .filter((ruta) -> ruta.getId().equals(rutaId))
        .map(RutaProyeccion::getCodigoRuta)
        .findFirst()
        .orElse(null);
  }

  private void registrarMovimiento(
      Long archivoId, Long rutaId, Long origen, Long destino, Long actor) {
    HistorialBalanceo log = new HistorialBalanceo();
    log.setArchivoId(archivoId);
    log.setTipoAccion(HistorialBalanceo.ACCION_MOVER_RUTA);
    log.setRutaId(rutaId);
    log.setChuteOrigenId(origen);
    log.setChuteDestinoId(destino);
    log.setCreadoPor(actor);
    log.setCreadoEn(LocalDateTime.now());
    historialRepo.save(log);
  }

  private HistorialBalanceo logMovimiento(
      Long archivoId, Long rutaId, Long origen, Long destino, Long actor) {
    HistorialBalanceo log = new HistorialBalanceo();
    log.setArchivoId(archivoId);
    log.setTipoAccion(HistorialBalanceo.ACCION_MOVER_RUTA);
    log.setRutaId(rutaId);
    log.setChuteOrigenId(origen);
    log.setChuteDestinoId(destino);
    log.setCreadoPor(actor);
    log.setCreadoEn(LocalDateTime.now());
    return log;
  }

  private HistorialBalanceo logOperario(Long archivoId, Long chuteId, Long actor) {
    HistorialBalanceo log = new HistorialBalanceo();
    log.setArchivoId(archivoId);
    log.setTipoAccion(HistorialBalanceo.ACCION_ANADIR_OPERARIO);
    log.setChuteModificadoId(chuteId);
    log.setCreadoPor(actor);
    log.setCreadoEn(LocalDateTime.now());
    return log;
  }

  private record Contexto(
      ArchivoExcel archivo,
      SimulacionConfig config,
      List<Cluster> clusters,
      List<Chute> chutes,
      List<ParametroCluster> parametrosCluster,
      List<ParametroChute> parametrosChute,
      List<RutaProyeccion> rutas,
      Map<Long, Cluster> clustersPorId,
      Map<Long, Chute> chutesPorId) {}
}
