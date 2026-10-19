package oracle.services;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import reactor.core.scheduler.Schedulers;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.DataIncidenciaInbound;
import oracle.model.DataProveedor;
import oracle.model.IncidenciaInbound;
import oracle.repository.DataIncidenciaInboundRepository;
import oracle.repository.DataProveedorRepository;
import oracle.repository.EstadisticaInboundRepository;
import oracle.repository.IncidenciaInboundRepository;
import oracle.request.FiltroIncidenciaInbound;
import oracle.request.IncidenciaInboundActualizarRequest;
import oracle.request.IncidenciaInboundCrearRequest;
import oracle.response.ApiResponse;
import oracle.response.EstadisticaInboundResponse;
import oracle.response.IncidenciaInboundResponse;
import oracle.response.PaginaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IncidenciaInboundService {
  private static final int MAX_TAMANO_PAGINA = 200;
  private static final int MAX_FILAS_EXPORT = 100_000;

  private final IncidenciaInboundRepository repo;
  private final DataProveedorRepository proveedorRepo;
  private final DataIncidenciaInboundRepository tipoRepo;
  private final EstadisticaInboundRepository estadisticasRepo;

  public IncidenciaInboundService(
      IncidenciaInboundRepository repo,
      DataProveedorRepository proveedorRepo,
      DataIncidenciaInboundRepository tipoRepo,
      EstadisticaInboundRepository estadisticasRepo) {
    this.repo = repo;
    this.proveedorRepo = proveedorRepo;
    this.tipoRepo = tipoRepo;
    this.estadisticasRepo = estadisticasRepo;
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

  public Mono<ApiResponse<List<IncidenciaInboundResponse>>> listarParaExportar(
      FiltroIncidenciaInbound filtro) {
    Specification<IncidenciaInbound> spec = especificacion(filtro);
    Pageable pageable =
        PageRequest.of(0, MAX_FILAS_EXPORT, Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
    return Mono.fromCallable(() -> repo.findAll(spec, pageable))
        .map(page -> aRespuestas(page.getContent()))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<EstadisticaInboundResponse>> estadisticas(FiltroIncidenciaInbound filtro) {
    return Mono.fromCallable(() -> {
          long[] totales = estadisticasRepo.totales(filtro);
          return new EstadisticaInboundResponse(
              totales[0],
              totales[1],
              estadisticasRepo.porProveedor(filtro),
              estadisticasRepo.porTipo(filtro));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> obtenerPorId(Long id) {
    return Mono.fromCallable(() -> repo.findById(id))
        .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> Mono.error(new RecursoNoEncontradoException("Incidencia inbound no encontrada"))))
        .map(this::aRespuesta)
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> crear(IncidenciaInboundCrearRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      String oc = req.ordenCompra().trim();
      String sku = req.skuProducto().trim();
      IncidenciaInbound i = new IncidenciaInbound();
      i.setOrdenCompra(oc);
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(sku);
      i.setNUnidades(req.nUnidades());
      oracle.services.Auditoria.alCrear(i, actor);
      return repo.save(i);
    }).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<IncidenciaInboundResponse>> actualizar(Long id, IncidenciaInboundActualizarRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      IncidenciaInbound i = repo.findById(id)
          .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia inbound no encontrada"));
      validarReferencias(req.idDataProveedor(), req.idDataIncidenciaInbound());
      i.setOrdenCompra(req.ordenCompra().trim());
      i.setIdDataProveedor(req.idDataProveedor());
      i.setIdDataIncidenciaInbound(req.idDataIncidenciaInbound());
      i.setSkuProducto(req.skuProducto().trim());
      i.setNUnidades(req.nUnidades());
      oracle.services.Auditoria.alActualizar(i, actor);
      return repo.save(i);
    }).map(this::aRespuesta).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(() -> {
      if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Incidencia inbound no encontrada");
      repo.deleteById(id);
      return null;
    }).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.ok("OK", null));
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
    Map<Long, String> proveedores = proveedorRepo.findAll().stream()
        .collect(Collectors.toMap(DataProveedor::getId, DataProveedor::getProveedor));
    Map<Long, String> tipos = tipoRepo.findAll().stream()
        .collect(Collectors.toMap(DataIncidenciaInbound::getId, DataIncidenciaInbound::getIncidenciaInbound));
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
    String proveedor = proveedorRepo.findById(i.getIdDataProveedor())
        .map(DataProveedor::getProveedor).orElse(null);
    String tipo = tipoRepo.findById(i.getIdDataIncidenciaInbound())
        .map(DataIncidenciaInbound::getIncidenciaInbound).orElse(null);
    return new IncidenciaInboundResponse(
        i.getId(),
        i.getOrdenCompra(),
        i.getIdDataProveedor(),
        proveedor,
        i.getIdDataIncidenciaInbound(),
        tipo,
        i.getSkuProducto(),
        i.getNUnidades(),
        i.getCreadoEn(),
        i.getActualizadoEn(),
        i.getCreadoPor(),
        i.getActualizadoPor());
  }
}
