package oracle.services;

import reactor.core.scheduler.Schedulers;
import oracle.model.DataIncidenciaInbound;
import oracle.repository.DataIncidenciaInboundRepository;
import oracle.request.IncidenciaInboundTipoActualizarRequest;
import oracle.request.IncidenciaInboundTipoCrearRequest;
import oracle.exception.ConflictoException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.response.ApiResponse;
import oracle.response.DataIncidenciaInboundResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IncidenciaInboundTipoService {
  private final DataIncidenciaInboundRepository repo;

  public IncidenciaInboundTipoService(DataIncidenciaInboundRepository repo) {
    this.repo = repo;
  }

  public Mono<ApiResponse<java.util.List<DataIncidenciaInboundResponse>>> listarTodos() {
    return Mono.fromCallable(() -> repo.findAll(Sort.by(Sort.Direction.ASC, "incidenciaInbound")))
        .map(list -> list.stream().map(this::aResponse).toList())
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<oracle.response.PaginaResponse<DataIncidenciaInboundResponse>>> listarPaginado(int pagina, int tamano, String texto) {
    var pageable = PageRequest.of(Math.max(pagina,1)-1, Math.min(Math.max(tamano,1),100),
        Sort.by(Sort.Direction.DESC,"creadoEn").and(Sort.by(Sort.Direction.DESC,"id")));
    String termino = texto == null ? "" : texto.trim().toLowerCase();
    Specification<DataIncidenciaInbound> spec = (root, query, cb) ->
        cb.like(cb.lower(root.get("incidenciaInbound")), "%" + termino + "%");
    return Mono.fromCallable(() -> repo.findAll(spec, pageable))
        .map(page -> new oracle.response.PaginaResponse<DataIncidenciaInboundResponse>(page.getContent().stream().map(this::aResponse).toList(),
            page.getTotalElements(), pagina, page.getSize(), page.getTotalPages()))
        .subscribeOn(Schedulers.boundedElastic())
        .map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<DataIncidenciaInboundResponse>> crear(IncidenciaInboundTipoCrearRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      String nombre = req.incidenciaInbound().trim();
      if (repo.existsByIncidenciaInboundIgnoreCase(nombre))
        throw new ConflictoException("Ya existe un tipo de incidencia inbound con ese nombre");
      DataIncidenciaInbound t = new DataIncidenciaInbound();
      t.setIncidenciaInbound(nombre);
      oracle.services.Auditoria.alCrear(t, actor);
      return repo.save(t);
    }).map(this::aResponse).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<DataIncidenciaInboundResponse>> actualizar(Long id, IncidenciaInboundTipoActualizarRequest req) {
    Long actor = oracle.services.ActorActual.id();
    return Mono.fromCallable(() -> {
      DataIncidenciaInbound t = repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia inbound no encontrado"));
      if (req.incidenciaInbound() != null && !req.incidenciaInbound().isBlank()) {
        String nombre = req.incidenciaInbound().trim();
        if (repo.existsByIncidenciaInboundIgnoreCaseAndIdNot(nombre, id))
          throw new ConflictoException("Ya existe un tipo de incidencia inbound con ese nombre");
        t.setIncidenciaInbound(nombre);
      }
      oracle.services.Auditoria.alActualizar(t, actor);
      return repo.save(t);
    }).map(this::aResponse).subscribeOn(Schedulers.boundedElastic()).map(d -> ApiResponse.ok("OK", d));
  }

  public Mono<ApiResponse<Void>> eliminar(Long id) {
    return Mono.fromCallable(() -> {
      if (!repo.existsById(id)) throw new RecursoNoEncontradoException("Tipo de incidencia inbound no encontrado");
      repo.deleteById(id);
      return null;
    }).subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.ok("OK", null));
  }

  private DataIncidenciaInboundResponse aResponse(DataIncidenciaInbound t) {
    return new DataIncidenciaInboundResponse(t.getId(), t.getIncidenciaInbound(), t.getCreadoEn(),
        t.getActualizadoEn(), t.getCreadoPor(), t.getActualizadoPor());
  }
}
