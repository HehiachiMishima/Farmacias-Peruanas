package oracle.inbound.service;

import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.CursorRecientes;
import oracle.common.support.EjecutorAnalitica;
import oracle.common.support.ExportacionJson;
import oracle.common.support.Paginacion;
import oracle.inbound.repository.AnaliticaInboundRepository;
import oracle.inbound.response.AnaliticaInboundResponse;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.inbound.model.IncidenciaInbound;
import oracle.inbound.repository.DataIncidenciaInboundRepository;
import oracle.inbound.repository.DataProveedorRepository;
import oracle.inbound.repository.IncidenciaInboundRepository;
import oracle.inbound.request.FiltroIncidenciaInbound;
import oracle.inbound.request.IncidenciaInboundActualizarRequest;
import oracle.inbound.request.IncidenciaInboundCrearRequest;
import oracle.common.response.ApiResponse;
import oracle.inbound.response.IncidenciaInboundResponse;
import oracle.common.response.PaginaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import oracle.common.config.CacheRespuestas;
import oracle.common.config.Transacciones;
import oracle.common.config.ZonaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class IncidenciaInboundService {
  /**
   * Espacio de CacheRespuestas de Inbound (analítica y conteos del listado):
   * lo invalidan las escrituras de incidencias y de catálogos.
   */
  static final String ESPACIO_CACHE = "inbound";

  private static final int MAX_TAMANO_PAGINA = 200;
  /** Proveedores de la evolución mensual del dashboard. */
  private static final int TOP_MENSUAL = 5;

  private final IncidenciaInboundRepository repo;
  private final DataProveedorRepository proveedorRepo;
  private final DataIncidenciaInboundRepository tipoRepo;
  private final CatalogoInbound catalogo;
  private final CacheRespuestas cacheRespuestas;
  private final Transacciones transacciones;
  private final ObjectMapper json;
  private final AnaliticaInboundRepository analiticaRepo;
  private final EjecutorAnalitica ejecutor;
  private final ZonaNegocio zonaNegocio;

  public IncidenciaInboundService(
      IncidenciaInboundRepository repo,
      DataProveedorRepository proveedorRepo,
      DataIncidenciaInboundRepository tipoRepo,
      CatalogoInbound catalogo,
      CacheRespuestas cacheRespuestas,
      Transacciones transacciones,
      ObjectMapper json,
      AnaliticaInboundRepository analiticaRepo,
      EjecutorAnalitica ejecutor,
      ZonaNegocio zonaNegocio) {
    this.repo = repo;
    this.proveedorRepo = proveedorRepo;
    this.tipoRepo = tipoRepo;
    this.catalogo = catalogo;
    this.cacheRespuestas = cacheRespuestas;
    this.transacciones = transacciones;
    this.json = json;
    this.analiticaRepo = analiticaRepo;
    this.ejecutor = ejecutor;
    this.zonaNegocio = zonaNegocio;
  }

  public Mono<ApiResponse<PaginaResponse<IncidenciaInboundResponse>>> listar(
      FiltroIncidenciaInbound filtro, int pagina, int tamano) {
    PageRequest pageable = Paginacion.pagina(pagina, tamano, MAX_TAMANO_PAGINA, Paginacion.RECIENTES);
    Specification<IncidenciaInbound> spec = especificacion(filtro);
    return Mono.fromCallable(() -> {
          // La página se lee siempre; el total (COUNT sobre todo el filtro, lo caro
          // al paginar) se cachea por filtro hasta la próxima escritura.
          List<IncidenciaInbound> filas = repo.findBy(spec, q -> q.slice(pageable)).getContent();
          long total = cacheRespuestas.obtener(ESPACIO_CACHE + ":conteo:" + filtro, () -> repo.count(spec));
          return PaginaResponse.of(aRespuestas(filas), total, pageable.getPageNumber() + 1, pageable.getPageSize());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  /**
   * Exporta el filtro completo escribiendo el JSON directamente al flujo de
   * respuesta: lee por lotes de {@value ExportacionJson#TAMANO_LOTE} filas (por
   * cursor, sin OFFSET ni COUNT) y nunca materializa la lista completa, así el
   * pico de memoria queda acotado.
   *
   * Produce el mismo envoltorio que {@code ApiResponse&lt;List&lt;...&gt;&gt;}
   * (exito, mensaje, datos, timestamp), así el contrato no cambia.
   */
  public StreamingResponseBody exportarEnFlujo(FiltroIncidenciaInbound filtro) {
    Specification<IncidenciaInbound> spec = especificacion(filtro);
    return ExportacionJson.enLotes(json, "OK",
        (ultima, tamano) -> repo.findBy(siguientes(spec, ultima),
            q -> q.sortBy(Paginacion.RECIENTES).limit(tamano).all()),
        this::conversor, log, "incidencias inbound");
  }

  /**
   * Analitica del dashboard en torno al proveedor: 7 agregaciones en paralelo
   * sobre el ejecutor analitico, cacheadas por filtro.
   */
  public Mono<ApiResponse<AnaliticaInboundResponse>> analitica(FiltroIncidenciaInbound filtro) {
    String clave = ESPACIO_CACHE + ":analitica:" + filtro;
    return Mono.fromCallable(() -> cacheRespuestas.obtener(clave, () -> ejecutor.esperar(calcularAnalitica(filtro))))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("Analitica calculada.", d));
  }

  private Mono<AnaliticaInboundResponse> calcularAnalitica(FiltroIncidenciaInbound filtro) {
    return Mono.zip(
            ejecutor.consulta(() -> analiticaRepo.kpis(filtro)),
            ejecutor.consulta(() -> analiticaRepo.proveedores(filtro)),
            ejecutor.consulta(() -> analiticaRepo.tipos(filtro)),
            ejecutor.consulta(() -> analiticaRepo.proveedorPorTipo(filtro)),
            ejecutor.consulta(() -> analiticaRepo.skusRecurrentes(filtro)),
            ejecutor.consulta(() -> analiticaRepo.tendencia(filtro)),
            ejecutor.consulta(() -> analiticaRepo.mensualTop(filtro, TOP_MENSUAL)))
        .map(r -> new AnaliticaInboundResponse(
            r.getT1(), r.getT2(), r.getT3(), r.getT4(), r.getT5(), r.getT6(),
            analiticaRepo.granularidad(filtro),
            r.getT7()));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> crear(IncidenciaInboundCrearRequest req) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      IncidenciaInbound i = new IncidenciaInbound();
      i.setOrdenCompra(req.ordenCompra().trim());
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(req.skuProducto().trim());
      i.setNUnidades(req.nUnidades());
      Auditoria.alCrear(i, actor);
      IncidenciaInbound guardada = repo.save(i);
      invalidarCacheAlConfirmar();
      return guardada;
    })).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> actualizar(Long id, IncidenciaInboundActualizarRequest req) {
    Long actor = ActorActual.id();
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      IncidenciaInbound i = repo.findById(id)
          .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia inbound no encontrada"));
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      i.setOrdenCompra(req.ordenCompra().trim());
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(req.skuProducto().trim());
      i.setNUnidades(req.nUnidades());
      Auditoria.alActualizar(i, actor);
      IncidenciaInbound guardada = repo.save(i);
      invalidarCacheAlConfirmar();
      return guardada;
    })).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(() -> transacciones.ejecutar(() -> {
      if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Incidencia inbound no encontrada");
      repo.deleteById(id);
      invalidarCacheAlConfirmar();
      return null;
    })).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.ok("OK", null));
  }

  /** Tras el commit, descarta solo la caché de Inbound (no la de otros paneles). */
  private void invalidarCacheAlConfirmar() {
    transacciones.alConfirmar(() -> cacheRespuestas.invalidarEspacio(ESPACIO_CACHE));
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
      if (filtro.desde() != null) {
        pred.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), zonaNegocio.inicioDelDia(filtro.desde())));
      }
      if (filtro.hasta() != null) {
        pred.add(cb.lessThan(root.get("creadoEn"), zonaNegocio.inicioDelDia(filtro.hasta().plusDays(1))));
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

  /** Filtro del export restringido a las filas posteriores a la última ya escrita. */
  private static Specification<IncidenciaInbound> siguientes(
      Specification<IncidenciaInbound> spec, IncidenciaInbound ultima) {
    if (ultima == null) {
      return spec;
    }
    Specification<IncidenciaInbound> cursor = CursorRecientes.despuesDe(ultima.getCreadoEn(), ultima.getId());
    return spec.and(cursor);
  }

  /** Comprueba por PK que existen proveedor y tipo (sin cargar las entidades). */
  private void validarReferencias(Long proveedorId, Long tipoId) {
    if (!proveedorRepo.existsById(proveedorId)) {
      throw new ConflictoException("Proveedor no válido");
    }
    if (!tipoRepo.existsById(tipoId)) {
      throw new ConflictoException("Tipo de incidencia inbound no válido");
    }
  }

  private List<IncidenciaInboundResponse> aRespuestas(List<IncidenciaInbound> lista) {
    if (lista.isEmpty()) {
      return List.of();
    }
    return lista.stream().map(conversor()).toList();
  }

  /** Convierte incidencias a respuesta con un mismo snapshot de nombres de catalogo. */
  private Function<IncidenciaInbound, IncidenciaInboundResponse> conversor() {
    Map<Long, String> proveedores = catalogo.proveedores();
    Map<Long, String> tipos = catalogo.tipos();
    return i -> IncidenciaInboundResponse.of(
        i, proveedores.get(i.getIdDataProveedor()), tipos.get(i.getIdDataIncidenciaInbound()));
  }

  private IncidenciaInboundResponse aRespuesta(IncidenciaInbound i) {
    return aRespuestas(List.of(i)).get(0);
  }
}
