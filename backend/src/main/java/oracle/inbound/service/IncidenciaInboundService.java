package oracle.inbound.service;

import oracle.common.support.EjecutorAnalitica;
import oracle.inbound.repository.AnaliticaInboundRepository;
import oracle.inbound.response.AnaliticaInboundResponse;

import oracle.common.support.ExportacionJson;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.inbound.model.DataIncidenciaInbound;
import oracle.inbound.model.DataProveedor;
import oracle.inbound.model.IncidenciaInbound;
import oracle.inbound.repository.DataIncidenciaInboundRepository;
import oracle.inbound.repository.DataProveedorRepository;
import oracle.inbound.repository.EstadisticaInboundRepository;
import oracle.inbound.repository.IncidenciaInboundRepository;
import oracle.inbound.request.FiltroIncidenciaInbound;
import oracle.inbound.request.IncidenciaInboundActualizarRequest;
import oracle.inbound.request.IncidenciaInboundCrearRequest;
import oracle.common.response.ApiResponse;
import oracle.inbound.response.EstadisticaInboundResponse;
import oracle.inbound.response.IncidenciaInboundResponse;
import oracle.common.response.PaginaResponse;
import oracle.inbound.response.ProveedorMensualEstadistica;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import oracle.common.config.CacheRespuestas;
import oracle.common.config.Transacciones;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class IncidenciaInboundService {
  private static final int MAX_TAMANO_PAGINA = 200;
  private static final int TAMANO_LOTE_EXPORT = 1_000;
  private static final int TOP_MENSUAL = 5;

  private final IncidenciaInboundRepository repo;
  private final DataProveedorRepository proveedorRepo;
  private final DataIncidenciaInboundRepository tipoRepo;
  private final EstadisticaInboundRepository estadisticasRepo;
  private final CatalogoInbound catalogo;
  private final CacheRespuestas cacheRespuestas;
  private final Transacciones transacciones;
  private final ObjectMapper json;
  private final AnaliticaInboundRepository analiticaRepo;
  private final EjecutorAnalitica ejecutor;

  public IncidenciaInboundService(
      IncidenciaInboundRepository repo,
      DataProveedorRepository proveedorRepo,
      DataIncidenciaInboundRepository tipoRepo,
      EstadisticaInboundRepository estadisticasRepo,
      CatalogoInbound catalogo,
      CacheRespuestas cacheRespuestas,
      Transacciones transacciones,
      ObjectMapper json,
      AnaliticaInboundRepository analiticaRepo,
      EjecutorAnalitica ejecutor) {
    this.analiticaRepo = analiticaRepo;
    this.ejecutor = ejecutor;
    this.repo = repo;
    this.proveedorRepo = proveedorRepo;
    this.tipoRepo = tipoRepo;
    this.estadisticasRepo = estadisticasRepo;
    this.catalogo = catalogo;
    this.cacheRespuestas = cacheRespuestas;
    this.transacciones = transacciones;
    this.json = json;
  }

  public Mono<ApiResponse<PaginaResponse<IncidenciaInboundResponse>>> listar(
      FiltroIncidenciaInbound filtro, int pagina, int tamano) {
    int p = Math.max(pagina, 1) - 1;
    int t = Math.min(Math.max(tamano, 1), MAX_TAMANO_PAGINA);
    Pageable pageable =
        PageRequest.of(
            p,
            t,
            Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
    Specification<IncidenciaInbound> spec = especificacion(filtro);
    return Mono.fromCallable(() -> repo.findAll(spec, pageable))
        .map(page -> PaginaResponse.de(page, aRespuestas(page.getContent())))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  /**
   * Exporta el filtro completo escribiendo el JSON directamente al flujo de
   * respuesta: lee por lotes de {@value #TAMANO_LOTE_EXPORT} filas y nunca
   * materializa la lista completa, así el pico de memoria queda acotado.
   *
   * Produce el mismo envoltorio que {@code ApiResponse&lt;List&lt;...&gt;&gt;}
   * (exito, mensaje, datos, timestamp), así el contrato no cambia.
   */
  public StreamingResponseBody exportarEnFlujo(FiltroIncidenciaInbound filtro) {
    Specification<IncidenciaInbound> spec = especificacion(filtro);
    Sort orden = Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"));
    return salida -> {
      try {
        ExportacionJson.escribir(json, salida, "OK", gen -> {
          Map<Long, String> proveedores = catalogo.proveedores();
          Map<Long, String> tipos = catalogo.tipos();
          int pagina = 0;
          while (true) {
            Page<IncidenciaInbound> lote =
                repo.findAll(spec, PageRequest.of(pagina, TAMANO_LOTE_EXPORT, orden));
            for (IncidenciaInbound i : lote.getContent()) {
              json.writeValue(gen, new IncidenciaInboundResponse(
                  i.getId(),
                  i.getOrdenCompra(),
                  i.getIdDataProveedor(),
                  proveedores.get(i.getIdDataProveedor()),
                  i.getIdDataIncidenciaInbound(),
                  tipos.get(i.getIdDataIncidenciaInbound()),
                  i.getSkuProducto(),
                  i.getNUnidades(),
                  i.getCreadoEn(),
                  i.getActualizadoEn(),
                  i.getCreadoPor(),
                  i.getActualizadoPor()));
            }
            if (lote.getContent().size() < TAMANO_LOTE_EXPORT) {
              break;
            }
            pagina++;
          }
        });
      } catch (Exception e) {
        log.error("Exportación de incidencias inbound interrumpida: {}", e.getMessage(), e);
      }
    };
  }

  public Mono<ApiResponse<EstadisticaInboundResponse>> estadisticas(FiltroIncidenciaInbound filtro) {
    String clave = "inbound-stats:" + filtro;
    return Mono.fromCallable(() -> cacheRespuestas.obtener(clave, () -> {
          long[] totales = estadisticasRepo.totales(filtro);
          return new EstadisticaInboundResponse(
              totales[0],
              totales[1],
              estadisticasRepo.porProveedor(filtro),
              estadisticasRepo.porTipo(filtro));
        }))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<List<ProveedorMensualEstadistica>>> estadisticaProveedoresMensual(
      FiltroIncidenciaInbound filtro) {
    String clave = "inbound-mensual:" + filtro;
    return Mono.fromCallable(() -> cacheRespuestas.obtener(clave, () -> estadisticasRepo.porProveedorMensual(filtro)))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  /**
   * Analitica del dashboard en torno al proveedor: 7 agregaciones en paralelo
   * sobre el ejecutor analitico, cacheadas por filtro.
   */
  public Mono<ApiResponse<AnaliticaInboundResponse>> analitica(FiltroIncidenciaInbound filtro) {
    String clave = "inbound-analitica:" + filtro;
    return Mono.fromCallable(() -> cacheRespuestas.obtener(clave, () -> ejecutor.esperar(calcularAnalitica(filtro))))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("Analitica calculada.", d));
  }

  private Mono<AnaliticaInboundResponse> calcularAnalitica(FiltroIncidenciaInbound filtro) {
    return Mono.zip(
        r -> new AnaliticaInboundResponse(
            (AnaliticaInboundResponse.Kpis) r[0],
            lista(r[1]), lista(r[2]), lista(r[3]), lista(r[4]), lista(r[5]),
            analiticaRepo.granularidad(filtro),
            topMensual(lista(r[6]))),
        ejecutor.consulta(() -> analiticaRepo.kpis(filtro)),
        ejecutor.consulta(() -> analiticaRepo.proveedores(filtro)),
        ejecutor.consulta(() -> analiticaRepo.tipos(filtro)),
        ejecutor.consulta(() -> analiticaRepo.proveedorPorTipo(filtro)),
        ejecutor.consulta(() -> analiticaRepo.skusRecurrentes(filtro)),
        ejecutor.consulta(() -> analiticaRepo.tendencia(filtro)),
        ejecutor.consulta(() -> estadisticasRepo.porProveedorMensual(filtro)));
  }

  /** Evolucion mensual solo de los 5 proveedores con mas incidencias (ya vienen ordenados). */
  private static List<ProveedorMensualEstadistica> topMensual(List<ProveedorMensualEstadistica> todos) {
    return todos.size() <= TOP_MENSUAL ? todos : todos.subList(0, TOP_MENSUAL);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> lista(Object valor) {
    return (List<T>) valor;
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> obtenerPorId(Long id) {
    return Mono.fromCallable(() -> repo.findById(id))
        .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> Mono.error(new RecursoNoEncontradoException("Incidencia inbound no encontrada"))))
        .map(this::aRespuesta)
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> crear(IncidenciaInboundCrearRequest req) {
    Long actor = oracle.common.support.ActorActual.id();
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      String oc = req.ordenCompra().trim();
      String sku = req.skuProducto().trim();
      IncidenciaInbound i = new IncidenciaInbound();
      i.setOrdenCompra(oc);
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(sku);
      i.setNUnidades(req.nUnidades());
      oracle.common.support.Auditoria.alCrear(i, actor);
      IncidenciaInbound guardada = repo.save(i);
      cacheRespuestas.invalidarTodo();
      return guardada;
    })).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> actualizar(Long id, IncidenciaInboundActualizarRequest req) {
    Long actor = oracle.common.support.ActorActual.id();
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      IncidenciaInbound i = repo.findById(id)
          .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia inbound no encontrada"));
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      i.setOrdenCompra(req.ordenCompra().trim());
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(req.skuProducto().trim());
      i.setNUnidades(req.nUnidades());
      oracle.common.support.Auditoria.alActualizar(i, actor);
      IncidenciaInbound guardada = repo.save(i);
      cacheRespuestas.invalidarTodo();
      return guardada;
    })).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Incidencia inbound no encontrada");
      repo.deleteById(id);
      cacheRespuestas.invalidarTodo();
      return null;
    })).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.ok("OK", null));
  }

  private Specification<IncidenciaInbound> especificacion(FiltroIncidenciaInbound filtro) {
    return (root, query, cb) -> {
      List<Predicate> pred = new ArrayList<>();
      if (filtro.proveedor() != null) {
        pred.add(cb.equal(root.get("idDataProveedor"), filtro.proveedor()));
      }
      if (filtro.tipo() != null) {
        pred.add(cb.equal(root.get("idDataIncidenciaInbound"), filtro.tipo()));
      }
      if (filtro.desde() != null && filtro.hasta() != null) {
        LocalDateTime d = filtro.desde().atStartOfDay();
        LocalDateTime h = filtro.hasta().atTime(LocalTime.MAX);
        pred.add(cb.between(root.get("creadoEn"), d, h));
      } else if (filtro.desde() != null) {
        pred.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), filtro.desde().atStartOfDay()));
      } else if (filtro.hasta() != null) {
        pred.add(cb.lessThanOrEqualTo(root.get("creadoEn"), filtro.hasta().atTime(LocalTime.MAX)));
      }
      if (filtro.texto() != null && !filtro.texto().isBlank()) {
        String t = "%" + filtro.texto().trim().toLowerCase() + "%";
        Predicate p1 = cb.like(cb.lower(root.get("ordenCompra")), t);
        Predicate p2 = cb.like(cb.lower(root.get("skuProducto")), t);
        pred.add(cb.or(p1, p2));
      }
      return pred.isEmpty() ? cb.conjunction() : cb.and(pred.toArray(new Predicate[0]));
    };
  }

  private void validarReferencias(Long proveedorId, Long tipoId) {
    DataProveedor prov = proveedorRepo.findById(proveedorId)
        .orElseThrow(() -> new ConflictoException("Proveedor no válido"));
    DataIncidenciaInbound tip = tipoRepo.findById(tipoId)
        .orElseThrow(() -> new ConflictoException("Tipo de incidencia inbound no válido"));
    if (prov == null || tip == null) throw new ConflictoException("Referencias inválidas");
  }

  private List<IncidenciaInboundResponse> aRespuestas(List<IncidenciaInbound> lista) {
    if (lista.isEmpty()) {
      return List.of();
    }
    Map<Long, String> proveedores = catalogo.proveedores();
    Map<Long, String> tipos = catalogo.tipos();
    return lista.stream()
        .map(i -> new IncidenciaInboundResponse(
            i.getId(),
            i.getOrdenCompra(),
            i.getIdDataProveedor(),
            proveedores.get(i.getIdDataProveedor()),
            i.getIdDataIncidenciaInbound(),
            tipos.get(i.getIdDataIncidenciaInbound()),
            i.getSkuProducto(),
            i.getNUnidades(),
            i.getCreadoEn(),
            i.getActualizadoEn(),
            i.getCreadoPor(),
            i.getActualizadoPor()))
        .toList();
  }

  private IncidenciaInboundResponse aRespuesta(IncidenciaInbound i) {
    return aRespuestas(List.of(i)).get(0);
  }
}
